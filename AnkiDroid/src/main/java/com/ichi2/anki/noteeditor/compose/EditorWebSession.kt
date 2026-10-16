// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import com.ichi2.anki.noteeditor.compose.preview.EditorPreviewInput
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.anki.noteeditor.web.WebEditorDocument
import com.ichi2.anki.noteeditor.web.WebEditorField
import com.ichi2.anki.noteeditor.web.WebEditorFieldAction
import com.ichi2.anki.noteeditor.web.WebEditorFieldTypography
import com.ichi2.anki.noteeditor.web.WebEditorSettings
import com.ichi2.anki.noteeditor.web.WebEditorSnapshot
import com.ichi2.anki.noteeditor.web.WebEditorView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Binds one native session to one WebView. HTML snapshots are requested only by consumers. */
class EditorWebSession(
    private val model: EditorViewModel,
    private val scope: CoroutineScope,
    private val reportError: (String) -> Unit,
) {
    private val mutableReady = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = mutableReady
    private val mutableToolbarState = MutableStateFlow(EditorToolbarState())
    val toolbarState: StateFlow<EditorToolbarState> = mutableToolbarState
    private var latestToolbarState = EditorToolbarState()
    private val mutableFieldTaps = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val fieldTaps: SharedFlow<Unit> = mutableFieldTaps
    private val mutableHeaderVisibility = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val headerVisibility: SharedFlow<Boolean> = mutableHeaderVisibility
    private val mutablePreview = MutableStateFlow<EditorPreviewInput?>(null)
    val preview: StateFlow<EditorPreviewInput?> = mutablePreview
    var view: WebEditorView? = null
        private set
    var previewEnabled = false
        set(value) {
            field = value
            previewDirty = true
            if (!value) mutablePreview.value = null
        }
    private var previewDirty = true
    private var lastContentRevision: Int? = null
    private var documentToken: Pair<String, Int>? = null
    private var lastHostState: String? = null
    private var lastFieldActions: List<List<WebEditorFieldAction>>? = null
    private var lastFieldTypography: List<WebEditorFieldTypography>? = null
    private var lastSettings: WebEditorSettings? = null
    private var previewNoteType: String? = null
    private var binding: Job? = null
    private var previewJob: Job? = null
    private val updates = Mutex()

    fun attach(editor: WebEditorView) {
        binding?.cancel()
        previewJob?.cancel()
        view = editor
        documentToken = null
        lastHostState = null
        lastSettings = null
        lastFieldTypography = null
        lastFieldActions = null
        lastContentRevision = null
        mutableReady.value = false
        latestToolbarState = EditorToolbarState()
        mutableToolbarState.value = latestToolbarState
        editor.onChanged = {
            latestToolbarState =
                EditorToolbarState(
                    composing = it.composing,
                    hasSelection = it.hasSelection,
                    activeField = it.activeField,
                    sourceMode = it.sourceMode,
                    activeFormats = it.activeFormats,
                )
            if (mutableReady.value) mutableToolbarState.value = latestToolbarState
            if (lastContentRevision != it.revision) {
                lastContentRevision = it.revision
                previewDirty = true
            }
        }
        editor.onError = reportError
        editor.onFieldTap = { mutableFieldTaps.tryEmit(Unit) }
        editor.onHeaderVisibility = { mutableHeaderVisibility.tryEmit(it) }
        editor.onRendererGone = {
            mutableReady.value = false
            binding?.cancel()
            previewJob?.cancel()
        }
        binding =
            scope.launch {
                try {
                    updates.withLock {
                        val current = model.state.filterNotNull().first { !it.isSaving }
                        val recoveryRequired = model.takeDraftRecoveryRequired()
                        // A completed Save in this retained ViewModel supersedes the old checkpoint.
                        val saved = model.saveResult.value
                        val draft =
                            if (!recoveryRequired || saved is EditorSaveResult.Added ||
                                saved is EditorSaveResult.Saved
                            ) {
                                null
                            } else {
                                editor.restoreDraft(model.draftId)
                            }
                        if (draft != null) {
                            check(draft.document.sessionId == current.sessionId) { "This draft belongs to another editor." }
                            if (!model.hasAttachedEditor) {
                                check(model.restoreHostState(draft.hostStateJson, draft.document.fields.map { it.html })) {
                                    "This draft no longer matches the note or collection."
                                }
                            }
                            check(
                                draft.document.generation <= requireNotNull(model.state.value).generation,
                            ) { "The draft versions do not match." }
                            documentToken = draft.document.sessionId to draft.document.generation
                            lastSettings = draft.document.settings
                            lastFieldTypography = draft.document.fields.map { WebEditorFieldTypography(it.fontName, it.fontSize) }
                            lastHostState = draft.hostStateJson
                        }
                        synchronize(editor, focusFirstField = !model.hasAttachedEditor && draft == null && current.isAdding)
                        model.hasAttachedEditor = true
                    }
                    model.state.filterNotNull().collect {
                        updates.withLock { synchronize(editor) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mutableReady.value = false
                    runCatching { editor.setInputEnabled(false) }
                    reportError(e.localizedMessage ?: e.toString())
                }
            }
        previewJob =
            scope.launch {
                while (isActive) {
                    if (previewEnabled && previewDirty && ready.value) {
                        previewDirty = false
                        try {
                            val snapshot = snapshot()
                            val state = requireNotNull(model.state.value)
                            mutablePreview.value =
                                EditorPreviewInput(state.notetypeJson, snapshot.fields, state.tags, state.noteId, state.deckId)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            reportError(e.localizedMessage ?: e.toString())
                        }
                    }
                    delay(100)
                }
            }
    }

    suspend fun awaitReady() {
        withTimeout(15_000) { ready.first { it } }
    }

    suspend fun snapshot(): WebEditorSnapshot =
        updates.withLock {
            val editor = requireNotNull(view)
            val result = editor.snapshot()
            val state = requireNotNull(model.state.value)
            check(
                result.sessionId == state.sessionId && result.generation == state.generation,
            ) { "The edited note changed. Please try again." }
            result
        }

    suspend fun synchronize() = updates.withLock { synchronize(requireNotNull(view)) }

    private suspend fun synchronize(
        editor: WebEditorView,
        focusFirstField: Boolean = false,
    ) {
        val state = requireNotNull(model.state.value)
        val token = state.sessionId to state.generation
        if (previewNoteType != state.notetypeJson) {
            previewNoteType = state.notetypeJson
            previewDirty = true
        }
        val hostState = model.hostState()
        val fieldActions = state.fieldActions(editor.context)
        val typography = state.fields.map { WebEditorFieldTypography(it.font, it.fontSize) }
        if (documentToken != token) {
            mutableReady.value = false
            editor.loadDocumentAndCreateDraft(
                state.toWebDocument(fieldActions),
                model.draftId,
                hostState,
                resetBaseline = state.resetFieldBaseline,
            )
            documentToken = token
            lastSettings = state.settings
            lastFieldTypography = typography
            lastFieldActions = fieldActions
            previewDirty = true
        } else if (lastHostState != hostState) {
            editor.updateHostState(hostState)
            previewDirty = true
        }
        if (lastSettings != state.settings) {
            editor.updateSettings(state.settings)
            lastSettings = state.settings
        }
        if (lastFieldTypography != typography) {
            editor.updateFieldTypography(typography)
            lastFieldTypography = typography
        }
        if (lastFieldActions != fieldActions) {
            editor.updateFieldActions(fieldActions)
            lastFieldActions = fieldActions
        }
        lastHostState = hostState
        editor.updateLanguageHints(state.fields.map { it.languageTag })
        model.rememberDraft()
        if (focusFirstField) editor.focusField()
        // Both selection updates and enabling native controls schedule a frame.
        // Publish them together after persistence and the initial keyboard request.
        mutableToolbarState.value = latestToolbarState
        mutableReady.value = true
    }

    suspend fun checkpoint() =
        updates.withLock {
            val editor = requireNotNull(view)
            synchronize(editor)
            editor.updateHostState(model.hostState())
        }

    suspend fun discard() =
        updates.withLock {
            binding?.cancel()
            previewJob?.cancel()
            requireNotNull(view).discardDraft(model.draftId)
            model.forgetDraft()
        }

    fun detach() {
        binding?.cancel()
        previewJob?.cancel()
        view?.onChanged = null
        view?.onFieldTap = null
        view?.onHeaderVisibility = null
        view?.onError = null
        view?.onRendererGone = null
        view = null
        mutableReady.value = false
    }
}

/** Content revisions do not affect native controls or need to recompose the editor screen. */
data class EditorToolbarState(
    val composing: Boolean = false,
    val hasSelection: Boolean = false,
    val activeField: Int? = null,
    val sourceMode: Boolean = false,
    val activeFormats: Set<WebEditorAction> = emptySet(),
)

private fun EditorState.toWebDocument(actions: List<List<WebEditorFieldAction>>) =
    WebEditorDocument(
        sessionId = sessionId,
        generation = generation,
        fields =
            fields.mapIndexed { index, field ->
                WebEditorField(
                    field.name,
                    field.html,
                    field.font,
                    field.fontSize,
                    field.rtl,
                    field.languageTag,
                    field.sticky,
                    field.collapsed,
                    field.sourceMode,
                    actions[index],
                )
            },
        isCloze = isCloze,
        settings = settings,
        baselineFieldMap = baselineFieldMap,
    )
