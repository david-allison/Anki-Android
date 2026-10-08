// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.webkit.WebViewFeature
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.noteeditor.compose.media.ComposeEditorMedia
import com.ichi2.anki.noteeditor.compose.preview.EditorPreview
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.anki.noteeditor.web.WebEditorView
import com.ichi2.anki.startup.ensureStorageIsReady
import com.ichi2.compose.theme.AnkiDroidTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/** Development editor host. Collection Save is explicit; preview and drafts use web snapshots. */
class ComposeNoteEditorActivity :
    AnkiActivity(),
    TagsDialogListener {
    private val model: EditorViewModel by viewModels()
    private val snackbar = SnackbarHostState()
    private var busy by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var discardDialog by mutableStateOf(false)
    private var typeChangeDialog by mutableStateOf(false)
    private var clozeWarning by mutableStateOf<String?>(null)
    private var changed = false
    private var changeTypeAfterSave = false
    private lateinit var tagsFactory: TagsDialogFactory
    private val webSession by lazy { EditorWebSession(model, lifecycleScope, ::reportError) }
    private val media =
        ComposeEditorMedia(
            activity = this,
            getState = { model.state.value },
            getWeb = { webSession.view },
            whenReady = { webSession.awaitReady() },
            reportError = ::reportError,
            checkpoint = { webSession.checkpoint() },
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        tagsFactory = TagsDialogFactory(this)
        supportFragmentManager.fragmentFactory = tagsFactory
        super.onCreate(savedInstanceState)
        if (!ensureStorageIsReady()) return
        changed = savedInstanceState?.getBoolean(STATE_CHANGED) ?: false
        changeTypeAfterSave = savedInstanceState?.getBoolean(STATE_CHANGE_TYPE) ?: false
        if (intent.getIntExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.NO_CALLER.value) == NoteEditorCaller.NO_CALLER.value &&
            intent.action in setOf(Intent.ACTION_SEND, Intent.ACTION_PROCESS_TEXT, NoteEditorFragment.ACTION_CREATE_FLASHCARD)
        ) {
            intent.putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.NOTEEDITOR_INTENT_ADD.value)
        }
        media.restore(savedInstanceState)
        enableEdgeToEdge()
        setContent { AnkiDroidTheme { EditorContent() } }
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
        startLoadingCollection()
    }

    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        registerReceiver()
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            reportError(getString(R.string.compose_editor_update_webview))
            return
        }
        val fieldsAvailable =
            runCatching {
                assets.open("backend/editor-fields.json").bufferedReader().use { JSONObject(it.readText()).getInt("version") == 1 }
            }.getOrDefault(false)
        if (!fieldsAvailable) {
            reportError(getString(R.string.compose_editor_update_backend))
            return
        }
        launchEditorTask {
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
        val status by webSession.status.collectAsStateWithLifecycle()
        val preview by webSession.preview.collectAsStateWithLifecycle()
        BackHandler { requestClose() }
        NoteEditorScreen(
            state = state,
            status = status,
            ready = ready,
            busy = busy || state?.isSaving == true,
            error = error,
            snackbar = snackbar,
            onBack = ::requestClose,
            onSave = { save() },
            onDeck = { id -> updateMetadata { model.selectDeck(id) } },
            onType = { id ->
                launchEditorTask {
                    try {
                        setBusy(true)
                        model.selectNoteType(id, webSession.snapshot().fields)
                        webSession.synchronize()
                    } finally {
                        releaseBusy()
                    }
                }
            },
            onEditType = { typeChangeDialog = true },
            onTags = { tagsFactory.show(this, checkedTags = ArrayList(state?.tags.orEmpty())) },
            onSticky = { index -> updateMetadata { model.toggleSticky(index) } },
            onAction = ::execute,
            onMedia = media::launch,
            onLegacy = ::openLegacyEditor,
            onPreviewVisibility = { webSession.previewEnabled = it },
            editor = { modifier ->
                state?.let { current ->
                    AndroidView(
                        modifier = modifier,
                        factory = { context ->
                            WebEditorView(context, File(current.mediaDirectory)).also {
                                it.onMediaPaste = media::paste
                                webSession.attach(it)
                            }
                        },
                        onRelease = { editor ->
                            webSession.detach()
                            editor.destroy()
                        },
                        update = { editor -> editor.setOnTouchListener { _, _ -> busy || !ready || current.isSaving } },
                    )
                }
            },
            preview = { modifier ->
                preview?.let { EditorPreview(it, modifier) }
                    ?: Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            },
        )
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
                    val caller = intent.getIntExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.NO_CALLER.value)
                    if (caller == NoteEditorCaller.NOTEEDITOR.value || caller == NoteEditorCaller.NOTEEDITOR_INTENT_ADD.value) {
                        webSession.discard()
                        model.consumeSaveResult()
                        closeEditor()
                    } else {
                        webSession.synchronize()
                        model.consumeSaveResult()
                        setBusy(false)
                        webSession.view?.focusField()
                        lifecycleScope.launch { snackbar.showSnackbar(getString(R.string.compose_editor_added)) }
                    }
                }
                is EditorSaveResult.Invalid -> {
                    model.consumeSaveResult()
                    setBusy(false)
                    if (result.canSaveAnyway) clozeWarning = result.message else result.message?.let(::reportError)
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

    private fun execute(action: WebEditorAction) =
        launchEditorTask {
            if (busy) return@launchEditorTask
            val editor = requireNotNull(webSession.view)
            val target = editor.captureTarget()
            if (!editor.execute(action, target)) reportError(getString(R.string.compose_editor_stale_command))
        }

    private fun updateMetadata(block: suspend () -> Unit) =
        launchEditorTask {
            if (busy || model.state.value?.isSaving == true) return@launchEditorTask
            busy = true
            try {
                block()
            } finally {
                busy = false
            }
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
                if (snapshot.hasChanges || model.state.value?.hasMetadataChanges == true) {
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
        media.save(outState)
        super.onSaveInstanceState(outState)
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
