// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import com.ichi2.anki.noteeditor.compose.preview.EditorPreviewInput
import com.ichi2.anki.noteeditor.web.WebEditorDocument
import com.ichi2.anki.noteeditor.web.WebEditorField
import com.ichi2.anki.noteeditor.web.WebEditorSnapshot
import com.ichi2.anki.noteeditor.web.WebEditorView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
    private var binding: Job? = null
    private var previewJob: Job? = null
    private val updates = Mutex()

    fun attach(editor: WebEditorView) {
        binding?.cancel()
        previewJob?.cancel()
        view = editor
        documentToken = null
        lastHostState = null
        lastContentRevision = null
        mutableReady.value = false
        editor.onChanged = {
            mutableToolbarState.value = EditorToolbarState(composing = it.composing, hasSelection = it.hasSelection)
            if (lastContentRevision != it.revision) {
                lastContentRevision = it.revision
                previewDirty = true
            }
        }
        editor.onError = reportError
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
                        // A completed Save in this retained ViewModel supersedes the old checkpoint.
                        val saved = model.saveResult.value
                        val draft =
                            if (saved is EditorSaveResult.Added ||
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
                            lastHostState = draft.hostStateJson
                        }
                        synchronize(editor)
                        if (!model.hasAttachedEditor && draft == null && current.isAdding) editor.focusField()
                        model.hasAttachedEditor = true
                        mutableReady.value = true
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

    private suspend fun synchronize(editor: WebEditorView) {
        val state = requireNotNull(model.state.value)
        val token = state.sessionId to state.generation
        val hostState = model.hostState()
        if (documentToken != token) {
            mutableReady.value = false
            editor.loadDocument(state.toWebDocument(), resetBaseline = state.resetFieldBaseline)
            editor.createDraft(model.draftId, hostState)
            documentToken = token
            mutableReady.value = true
            previewDirty = true
        } else if (lastHostState != hostState) {
            editor.updateHostState(hostState)
            previewDirty = true
        }
        lastHostState = hostState
        model.rememberDraft()
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
)

private fun EditorState.toWebDocument() =
    WebEditorDocument(
        sessionId = sessionId,
        generation = generation,
        fields =
            fields.map {
                WebEditorField(it.name, it.html, it.font, it.fontSize, it.rtl, it.languageTag, it.sticky, it.collapsed, it.sourceMode)
            },
        isCloze = isCloze,
    )
