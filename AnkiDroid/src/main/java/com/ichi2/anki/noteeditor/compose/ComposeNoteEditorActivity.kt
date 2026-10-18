// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import androidx.core.view.SoftwareKeyboardControllerCompat
import androidx.core.view.doOnAttach
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.webkit.WebViewFeature
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import com.ichi2.anki.NoteTypeFieldEditor
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog
import com.ichi2.anki.dialogs.registerDeckSelectedHandler
import com.ichi2.anki.dialogs.startDeckSelection
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.mediaFolder
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.noteeditor.compose.media.ComposeEditorImageOcclusion
import com.ichi2.anki.noteeditor.compose.media.ComposeEditorMedia
import com.ichi2.anki.noteeditor.compose.preview.EditorPreview
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.anki.noteeditor.web.WebEditorTarget
import com.ichi2.anki.noteeditor.web.WebEditorView
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.startup.ensureStorageIsReady
import com.ichi2.compose.theme.AnkiDroidTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.Locale

/** Development editor host. Collection Save is explicit; preview and drafts use web snapshots. */
class ComposeNoteEditorActivity :
    AnkiActivity(),
    TagsDialogListener {
    private val model: EditorViewModel by viewModels()
    private val snackbar = SnackbarHostState()
    private var busy = false
    private val metadataUpdates = Mutex()
    private var showBusyState by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var discardDialog by mutableStateOf(false)
    private var typeChangeDialog by mutableStateOf(false)
    private var clozeWarning by mutableStateOf<String?>(null)
    private var changed = false
    private var changeTypeAfterSave = false
    private var languageHintTarget: EditorFieldTarget? = null
    private var formattingRequest by mutableStateOf<EditorFormattingRequest?>(null)
    private lateinit var tagsFactory: TagsDialogFactory
    private var editorView by mutableStateOf<WebEditorView?>(null)
    private var collectionMediaDirectory: File? = null
    private val webSession by lazy { EditorWebSession(model, lifecycleScope, ::reportError) }
    private val noteTypeEditor =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            launchEditorTask {
                webSession.awaitReady()
                changed = model.reloadNoteType(webSession.snapshot().fields) || changed
                webSession.checkpoint()
            }
        }
    private val media =
        ComposeEditorMedia(
            activity = this,
            getState = { model.state.value },
            getWeb = { webSession.view },
            whenReady = { webSession.awaitReady() },
            reportError = ::reportError,
            checkpoint = { webSession.checkpoint() },
        )
    private val imageOcclusion =
        ComposeEditorImageOcclusion(
            activity = this,
            getState = { model.state.value },
            whenReady = { webSession.awaitReady() },
            checkpoint = { webSession.checkpoint() },
            onReturn = { changed = true },
            reportError = ::reportError,
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        tagsFactory = TagsDialogFactory(this)
        supportFragmentManager.fragmentFactory = tagsFactory
        super.onCreate(savedInstanceState)
        if (!ensureStorageIsReady()) return
        changed = savedInstanceState?.getBoolean(STATE_CHANGED) ?: false
        changeTypeAfterSave = savedInstanceState?.getBoolean(STATE_CHANGE_TYPE) ?: false
        languageHintTarget =
            savedInstanceState?.let { BundleCompat.getParcelable(it, STATE_LANGUAGE_TARGET, EditorFieldTarget::class.java) }
        if (intent.getIntExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.NO_CALLER.value) == NoteEditorCaller.NO_CALLER.value &&
            intent.action in setOf(Intent.ACTION_SEND, Intent.ACTION_PROCESS_TEXT, NoteEditorFragment.ACTION_CREATE_FLASHCARD)
        ) {
            intent.putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.NOTEEDITOR_INTENT_ADD.value)
        }
        media.restore(savedInstanceState)
        imageOcclusion.restore(savedInstanceState)
        formattingRequest =
            savedInstanceState?.let { BundleCompat.getParcelable(it, "editorFormatting", EditorFormattingRequest::class.java) }
        enableEdgeToEdge()
        setContent { AnkiDroidTheme { EditorContent() } }
        registerDeckSelectedHandler { deck ->
            if (deck is SelectableDeck.Deck) {
                updateMetadata { model.selectDeck(deck.deckId) }
            }
        }
        supportFragmentManager.setFragmentResultListener(LocaleSelectionDialog.REQUEST_HINT_LOCALE_SELECTION, this) { _, result ->
            val locale = BundleCompat.getSerializable(result, LocaleSelectionDialog.RESULT_SELECTED_LOCALE, Locale::class.java)
            val target = languageHintTarget
            languageHintTarget = null
            (supportFragmentManager.findFragmentByTag(LANGUAGE_DIALOG_TAG) as? LocaleSelectionDialog)?.dismiss()
            if (target != null &&
                (locale != null || result.getBoolean(LocaleSelectionDialog.KEY_USE_DEFAULT))
            ) {
                setLanguageHint(target, locale)
            }
        }
        supportFragmentManager.setFragmentResultListener(ChangeNoteTypeDialog.REQUEST_KEY_NOTE_TYPE_CHANGED, this) { _, _ ->
            changed = true
            launchEditorTask {
                webSession.discard()
                closeEditor()
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    model.error.filterNotNull().collect {
                        reportError(it)
                        model.clearError()
                    }
                }
                launch {
                    model.saveResult.filterNotNull().collect { handleSaveResult(it) }
                }
            }
        }
        if (supportsEditor()) {
            // Let the native screen draw before WebView startup competes for the UI thread.
            // Collection loading and page bootstrap can then proceed independently.
            startLoadingCollection()
            if (isFinishing) return
            window.decorView.doOnPreDraw { view ->
                // One startup task, after this traversal but before a queued metadata frame.
                // This lets page loading overlap that frame without delaying the first draw.
                view.handler.postAtFrontOfQueue {
                    if (!isFinishing && !isDestroyed) {
                        editorView =
                            WebEditorView(this, collectionMediaDirectory).also {
                                it.onMediaPaste = media::paste
                                it.onQuickAction = ::quickAction
                                it.onEditEquation = { target, text, block ->
                                    val chemistry = !block && text.startsWith("\\ce{") && text.endsWith("}")
                                    val action =
                                        when {
                                            block -> WebEditorAction.MATHJAX_BLOCK
                                            chemistry -> WebEditorAction.MATHJAX_CHEMISTRY
                                            else -> WebEditorAction.MATHJAX_INLINE
                                        }
                                    formattingRequest =
                                        EditorFormattingRequest(
                                            action,
                                            target,
                                            if (chemistry) text.substring(4, text.length - 1) else text,
                                            editingEquation = true,
                                            keyboardVisible = it.keyboardVisible,
                                        )
                                }
                            }
                    }
                }
            }
        }
    }

    private fun supportsEditor(): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            reportError(getString(R.string.compose_editor_update_webview))
            return false
        }
        val fieldsAvailable =
            runCatching {
                assets.open("backend/editor-fields.json").bufferedReader().use { JSONObject(it.readText()).getInt("version") == 10 }
            }.getOrDefault(false)
        if (!fieldsAvailable) {
            reportError(getString(R.string.compose_editor_update_backend))
        }
        return fieldsAvailable
    }

    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        registerReceiver()
        launchEditorTask {
            val directory = requireNotNull(col.mediaFolder) { "The collection media folder is unavailable." }
            collectionMediaDirectory = directory
            editorView?.bindCollection(directory)
            val arguments = intent.extras ?: NoteEditorFragment.addNoteArgs()
            if (model.state.value == null && withCol { requiresLegacyEditor(arguments) }) {
                openLegacyEditor()
            } else {
                model.load(arguments)
            }
        }
    }

    @Composable
    private fun EditorContent() {
        val state by model.state.collectAsStateWithLifecycle()
        val ready by webSession.ready.collectAsStateWithLifecycle()
        val toolbarState = webSession.toolbarState.collectAsStateWithLifecycle()
        val preview by webSession.preview.collectAsStateWithLifecycle()
        BackHandler { requestClose() }
        NoteEditorScreen(
            state = state,
            toolbarState = toolbarState,
            fieldTaps = webSession.fieldTaps,
            onFieldOptionsVisibility = { webSession.view?.setFieldOptionsVisible(it) },
            onFieldOptionsTop = { webSession.view?.setFieldOptionsTop(it) },
            onFieldSelected = { target ->
                launchEditorTask {
                    if (busy || !target.matches(model.state.value)) return@launchEditorTask
                    webSession.view?.let { editor ->
                        editor.focusField(target.field, showKeyboard = false, revealAboveOptions = true)
                    }
                }
            },
            headerVisibility = webSession.headerVisibility,
            ready = ready,
            busy = showBusyState || state?.isSaving == true,
            error = error,
            snackbar = snackbar,
            onBack = ::requestClose,
            onSave = { save() },
            loadNoteTypes = model::noteTypeChoices,
            onDeck = { startDeckSelection(allowAll = false, allowFiltered = false) },
            onType = { id ->
                launchEditorTask {
                    try {
                        setBusy(true)
                        if (model.isImageOcclusionType(id)) {
                            imageOcclusion.launch(id)
                        } else {
                            model.selectNoteType(id, webSession.snapshot().fields)
                            webSession.synchronize()
                        }
                    } finally {
                        releaseBusy()
                    }
                }
            },
            onEditType = { typeChangeDialog = true },
            onTags = { tagsFactory.show(this, checkedTags = ArrayList(state?.tags.orEmpty())) },
            onRemoveTag = ::removeTag,
            onPinned = { target, pinned -> updateMetadata { model.setFieldPinned(target, pinned) } },
            onLanguageHint = { target ->
                languageHintTarget = target
                LocaleSelectionDialog.withDefaultOption().show(supportFragmentManager, LANGUAGE_DIALOG_TAG)
            },
            onAction = ::execute,
            onFieldDirection = { target, rtl ->
                updateMetadata {
                    webSession.awaitReady()
                    model.setFieldDirection(target, rtl)
                    webSession.synchronize()
                }
            },
            onFieldDescription = { target, description -> updateMetadata { model.setFieldDescription(target, description) } },
            onFieldHtmlByDefault = { target, html ->
                updateMetadata {
                    webSession.awaitReady()
                    model.setFieldHtmlByDefault(target, html)
                    webSession.synchronize()
                    val current = model.state.value
                    if (target.matches(current) && current?.fields?.get(target.field)?.sourceMode == html) {
                        webSession.view?.setFieldSourceMode(target.field, html)
                    }
                }
            },
            onFieldCollapseByDefault = { target, collapsed ->
                updateMetadata {
                    webSession.awaitReady()
                    model.setFieldCollapsedByDefault(target, collapsed)
                    webSession.synchronize()
                    val current = model.state.value
                    if (target.matches(current) && current?.fields?.get(target.field)?.collapsed == collapsed) {
                        webSession.view?.setFieldCollapsed(target.field, collapsed)
                    }
                }
            },
            onFieldTypography = { target, font, size ->
                updateMetadata {
                    webSession.awaitReady()
                    model.setFieldTypography(target, font, size)
                    webSession.synchronize()
                }
            },
            onFieldQuickActions = { target, actions -> updateMetadata { model.setFieldQuickActions(target, actions) } },
            onFieldPanelSave = { draft, onSaved ->
                updateMetadata {
                    model.saveFieldPanel(draft)
                    onSaved()
                }
            },
            onCustomButtonTemplates = { buttons -> updateMetadata { model.setCustomButtonTemplates(buttons) } },
            onCustomAction = { field, button ->
                launchEditorTask {
                    if (busy || !field.matches(model.state.value)) return@launchEditorTask
                    val editor = requireNotNull(webSession.view)
                    val target = editor.captureTarget()
                    if (target == null || target.field != field.field || !field.matches(model.state.value) ||
                        !editor.execute(
                            WebEditorAction.WRAP_HTML,
                            target,
                            JSONObject().put("prefix", button.prefix).put("suffix", button.suffix).toString(),
                        )
                    ) {
                        reportError(getString(R.string.compose_editor_stale_command))
                    }
                }
            },
            onFields = { openNoteTypeEditor(fields = true) },
            onCards = { openNoteTypeEditor(fields = false) },
            onMedia = { media.launch(it) },
            onAttachMedia = { if (!busy) media.showPicker() },
            onToolbarDisplay = { display -> updateMetadata { model.setToolbarDisplay(display) } },
            onLegacy = ::openLegacyEditor,
            onPreviewVisibility = { webSession.previewEnabled = it },
            editor = { modifier ->
                editorView?.let { web ->
                    AndroidView(
                        modifier = modifier,
                        factory = { web.apply { doOnAttach { webSession.attach(web) } } },
                        onRelease = { editor ->
                            webSession.detach()
                            editor.destroy()
                        },
                        update = { editor -> editor.setOnTouchListener { _, _ -> busy || !ready || state?.isSaving == true } },
                    )
                }
            },
            preview = { modifier ->
                preview?.let { EditorPreview(it, modifier) }
                    ?: Spacer(modifier)
            },
        )
        state?.removedFields?.takeIf { it.isNotEmpty() }?.let { removed ->
            EditorRemovedFieldsDialog(
                fields = removed,
                onDiscard = {
                    updateMetadata {
                        model.discardRemovedFields()
                        webSession.checkpoint()
                    }
                },
                onKeepDraft = {
                    launchEditorTask {
                        webSession.checkpoint()
                        model.rememberDraft(keepForNextLaunch = true)
                        closeEditor()
                    }
                },
            )
        }
        formattingRequest?.let { request ->
            EditorFormattingDialog(
                request = request,
                onDismiss = {
                    // Native colour dialogs also call onDismiss after applying their result.
                    if (formattingRequest == request) {
                        formattingRequest = null
                        launchEditorTask {
                            webSession.awaitReady()
                            webSession.view?.restoreFocus(request.target, request.keyboardVisible)
                        }
                    }
                },
                onApply = { action, value ->
                    formattingRequest = null
                    launchEditorTask {
                        webSession.awaitReady()
                        val command = if (request.editingEquation && value.isBlank()) WebEditorAction.INSERT_TEXT else action
                        val editor = requireNotNull(webSession.view)
                        val applied = editor.execute(command, request.target, value)
                        if (!applied) {
                            reportError(getString(R.string.compose_editor_stale_command))
                        } else if (action == WebEditorAction.REMOVE_FORMAT_CHOICES) {
                            Prefs.sharedPrefs.edit { putString("composeEditorRemovedFormats", value) }
                        } else if (action == WebEditorAction.TEXT_COLOR || action == WebEditorAction.HIGHLIGHT_COLOR) {
                            model.rememberToolbarColor(action, value)
                        }
                        editor.restoreFocus(request.target.takeUnless { applied }, request.keyboardVisible)
                    }
                },
            )
        }
        if (discardDialog) {
            ConfirmationDialog(
                message = stringResource(R.string.compose_editor_discard),
                confirm = stringResource(CommonString.discard),
                onDismiss = { discardDialog = false },
                onConfirm = {
                    discardDialog = false
                    if (!busy && model.state.value?.isSaving != true) {
                        launchEditorTask {
                            try {
                                setBusy(true)
                                webSession.discard()
                                closeEditor()
                            } finally {
                                if (!isFinishing) releaseBusy()
                            }
                        }
                    }
                },
            )
        }
        if (typeChangeDialog) {
            ConfirmationDialog(
                message = stringResource(R.string.compose_editor_save_type_change),
                confirm = stringResource(CommonString.save),
                onDismiss = { typeChangeDialog = false },
                onConfirm = {
                    typeChangeDialog = false
                    changeTypeAfterSave = true
                    save()
                },
            )
        }
        clozeWarning?.let { warning ->
            ConfirmationDialog(
                message = warning,
                confirm = stringResource(CommonString.save),
                onDismiss = { clozeWarning = null },
                onConfirm = {
                    clozeWarning = null
                    save(allowMissingCloze = true)
                },
            )
        }
    }

    private fun save(allowMissingCloze: Boolean = false) {
        if (busy || model.state.value?.isSaving == true) return
        launchEditorTask {
            try {
                setBusy(true)
                val snapshot = webSession.snapshot()
                model.save(snapshot.fields, allowMissingCloze)
                // The retained result is consumed by the current Activity, including after rotation.
            } catch (e: Exception) {
                releaseBusy()
                throw e
            }
        }
    }

    private suspend fun handleSaveResult(result: EditorSaveResult) {
        try {
            webSession.awaitReady()
            when (result) {
                EditorSaveResult.Saved -> {
                    changed = true
                    if (changeTypeAfterSave) {
                        webSession.synchronize()
                        setBusy(false)
                        changeTypeAfterSave = false
                        model.consumeSaveResult()
                        ChangeNoteTypeDialog
                            .newInstance(listOf(requireNotNull(model.state.value).noteId))
                            .show(supportFragmentManager, "compose-editor-change-type")
                    } else {
                        webSession.discard()
                        model.consumeSaveResult()
                        closeEditor()
                    }
                }
                is EditorSaveResult.Added -> {
                    changed = true
                    // "Keep draft" applies to the saved note, not the next Add in this session.
                    model.forgetDraft()
                    val caller = intent.getIntExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.NO_CALLER.value)
                    if (caller == NoteEditorCaller.NOTEEDITOR.value || caller == NoteEditorCaller.NOTEEDITOR_INTENT_ADD.value) {
                        webSession.discard()
                        model.consumeSaveResult()
                        closeEditor()
                    } else {
                        webSession.synchronize()
                        model.consumeSaveResult()
                        setBusy(false)
                        model.state.value
                            ?.initialFocusField
                            ?.let { webSession.view?.focusField(it) }
                        lifecycleScope.launch { snackbar.showSnackbar(getString(R.string.compose_editor_added)) }
                    }
                }
                is EditorSaveResult.Invalid -> {
                    model.consumeSaveResult()
                    setBusy(false)
                    if (result.canSaveAnyway) clozeWarning = result.message else result.message?.let(::reportError)
                    result.fieldIndex?.let { webSession.view?.focusField(it, highlightError = true) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            releaseBusy()
            reportError(e.localizedMessage ?: e.toString())
        }
    }

    private suspend fun setBusy(value: Boolean) {
        busy = value
        showBusyState = value
        webSession.view?.setInputEnabled(!value)
    }

    /** A failed renderer must not turn error-path cleanup into an uncaught exception. */
    private suspend fun releaseBusy() {
        try {
            setBusy(false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Unable to re-enable the note editor")
        }
    }

    private fun openNoteTypeEditor(fields: Boolean) =
        launchEditorTask {
            if (busy || model.state.value?.isSaving == true) return@launchEditorTask
            webSession.awaitReady()
            webSession.checkpoint()
            val state = requireNotNull(model.state.value)
            val destination =
                if (fields) {
                    Intent(this, NoteTypeFieldEditor::class.java)
                        .putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_ID, state.noteTypeId)
                        .putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_NAME, state.noteTypeName)
                } else {
                    CardTemplateEditor.getIntent(this, state.noteTypeId, state.noteId.takeUnless { state.isAdding })
                }
            noteTypeEditor.launch(destination)
        }

    private fun execute(action: WebEditorAction) =
        launchEditorTask {
            if (busy) return@launchEditorTask
            val editor = requireNotNull(webSession.view)
            val keyboardVisible = editor.keyboardVisible
            val target = editor.captureTarget()
            if (target == null) {
                reportError(getString(R.string.compose_editor_stale_command))
                return@launchEditorTask
            }
            if (action == WebEditorAction.REMOVE_FORMAT && !editor.selectionText(target).isNullOrEmpty()) {
                formattingRequest =
                    EditorFormattingRequest(
                        WebEditorAction.REMOVE_FORMAT_CHOICES,
                        target,
                        Prefs.sharedPrefs.getString("composeEditorRemovedFormats", null)
                            ?: org.json.JSONArray(editorRemovableFormats.map { it.first.name }).toString(),
                        keyboardVisible = keyboardVisible,
                    )
                return@launchEditorTask
            }
            if (action in
                setOf(
                    WebEditorAction.TEXT_COLOR,
                    WebEditorAction.HIGHLIGHT_COLOR,
                    WebEditorAction.ALIGN_LEFT,
                    WebEditorAction.MATHJAX_INLINE,
                )
            ) {
                val text =
                    when (action) {
                        WebEditorAction.TEXT_COLOR -> requireNotNull(model.state.value).toolbarTextColor
                        WebEditorAction.HIGHLIGHT_COLOR -> requireNotNull(model.state.value).toolbarHighlightColor
                        WebEditorAction.MATHJAX_INLINE -> editor.selectionText(target) ?: return@launchEditorTask
                        else -> ""
                    }
                formattingRequest = EditorFormattingRequest(action, target, text, keyboardVisible = keyboardVisible)
                return@launchEditorTask
            }
            if (editor.execute(action, target)) {
                // Repositioning the caret via focusField() would clear pending typing formats.
                SoftwareKeyboardControllerCompat(editor).show()
            } else {
                reportError(getString(R.string.compose_editor_stale_command))
            }
        }

    private fun quickAction(
        id: String,
        target: WebEditorTarget,
    ) {
        val state = model.state.value ?: return
        val fieldTarget = EditorFieldTarget(target.sessionId, target.generation, target.field)
        if (busy || state.isSaving || !fieldTarget.matches(state)) return
        val field = state.fields[target.field]
        if (id !in field.toolbar.quickActions) return
        val action = EditorQuickAction.fromId(id) ?: return
        if (action == EditorQuickAction.PIN) {
            updateMetadata { model.setFieldPinned(fieldTarget, !field.sticky) }
        } else if (action == EditorQuickAction.EDIT_HTML) {
            launchEditorTask {
                val editor = requireNotNull(webSession.view)
                if (editor.execute(WebEditorAction.SOURCE_MODE, target)) {
                    SoftwareKeyboardControllerCompat(editor).show()
                } else {
                    reportError(getString(R.string.compose_editor_stale_command))
                }
            }
        } else if (action == EditorQuickAction.ATTACH_MEDIA) {
            media.showPicker(target)
        } else {
            action.media?.let { media.launch(it, target) }
        }
    }

    private fun updateMetadata(block: suspend () -> Unit) =
        launchEditorTask {
            // Immediate settings can arrive faster than a preferences write. Preserve their order.
            metadataUpdates.withLock {
                if (busy || model.state.value?.isSaving == true) return@withLock
                busy = true
                try {
                    block()
                } finally {
                    busy = false
                }
            }
        }

    private fun setLanguageHint(
        target: EditorFieldTarget,
        locale: Locale?,
    ) = updateMetadata {
        webSession.awaitReady()
        model.setFieldLanguageHint(target, locale)
        webSession.synchronize()
    }

    private fun requestClose() {
        if (busy || model.state.value?.isSaving == true) return
        if (model.state.value == null || !webSession.ready.value) {
            closeEditor()
            return
        }
        launchEditorTask {
            try {
                setBusy(true)
                val snapshot = webSession.snapshot()
                if (snapshot.hasChanges || model.state.value?.hasMetadataChanges == true ||
                    !model.state.value
                        ?.removedFields
                        .isNullOrEmpty()
                ) {
                    discardDialog = true
                } else {
                    webSession.discard()
                    closeEditor()
                }
            } finally {
                if (!isFinishing) releaseBusy()
            }
        }
    }

    private fun closeEditor() {
        setResult(
            if (changed) RESULT_OK else RESULT_CANCELED,
            Intent()
                .putExtra(NoteEditorFragment.EXTRA_NOTE_CHANGED, changed)
                .putExtra(NoteEditorFragment.EXTRA_RELOAD_REQUIRED, changed)
                .putExtra(NoteEditorFragment.EXTRA_ID, intent.getStringExtra(NoteEditorFragment.EXTRA_ID)),
        )
        finish()
    }

    private fun openLegacyEditor() {
        startActivity(
            Intent(
                intent,
            ).setClass(this, NoteEditorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT).putExtra(EXTRA_USE_LEGACY, true),
        )
        finish()
    }

    private fun removeTag(tag: String) {
        if (busy) return
        val removed = model.removeTag(tag) ?: return
        lifecycleScope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(
                    message = getString(R.string.compose_editor_tag_removed, tag),
                    actionLabel = getString(CommonString.undo),
                    duration = SnackbarDuration.Short,
                ) ==
                SnackbarResult.ActionPerformed
            ) {
                model.restoreTag(removed)
            }
        }
    }

    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    ) {
        model.setTags(selectedTags)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_CHANGED, changed)
        outState.putBoolean(STATE_CHANGE_TYPE, changeTypeAfterSave)
        outState.putParcelable(STATE_LANGUAGE_TARGET, languageHintTarget)
        outState.putParcelable("editorFormatting", formattingRequest)
        media.save(outState)
        imageOcclusion.save(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Startup can be canceled before Compose attaches the already-loading WebView.
        editorView?.let {
            webSession.detach()
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        editorView = null
    }

    private fun launchEditorTask(block: suspend () -> Unit) {
        lifecycleScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportError(e.localizedMessage ?: e.toString())
            }
        }
    }

    private fun reportError(message: String) {
        error = message
        Timber.d("Compose note editor: %s", message)
        lifecycleScope.launch { snackbar.showSnackbar(message) }
    }

    companion object {
        const val EXTRA_USE_LEGACY = "composeEditorUseLegacy"
        private const val STATE_CHANGED = "composeEditorChanged"
        private const val STATE_CHANGE_TYPE = "composeEditorChangeType"
        private const val STATE_LANGUAGE_TARGET = "composeEditorLanguageTarget"
        private const val LANGUAGE_DIALOG_TAG = "composeEditorLanguageDialog"
    }
}

@Composable
private fun ConfirmationDialog(
    message: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CommonString.dialog_cancel)) } },
    )
}
