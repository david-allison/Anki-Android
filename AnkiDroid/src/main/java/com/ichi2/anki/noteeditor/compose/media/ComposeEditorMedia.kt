// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose.media

import android.content.ClipDescription
import android.net.Uri
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.core.os.BundleCompat
import androidx.core.view.SoftwareKeyboardControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import anki.config.ConfigKey
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.MediaRegistration
import com.ichi2.anki.R
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.mediaFolder
import com.ichi2.anki.multimedia.MultimediaActionHandler
import com.ichi2.anki.multimedia.MultimediaActivityExtra
import com.ichi2.anki.multimedia.MultimediaBottomSheet
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction
import com.ichi2.anki.multimedia.MultimediaResult
import com.ichi2.anki.multimedia.MultimediaResultContract
import com.ichi2.anki.multimedia.MultimediaViewModel
import com.ichi2.anki.multimediacard.fields.IField
import com.ichi2.anki.noteeditor.compose.EditorState
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.anki.noteeditor.web.WebEditorFocusState
import com.ichi2.anki.noteeditor.web.WebEditorTarget
import com.ichi2.anki.noteeditor.web.WebEditorView
import com.ichi2.anki.servicelayer.NoteService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.URLConnection
import kotlin.coroutines.resume

/** Register during activity construction, before the host reaches STARTED. */
class ComposeEditorMedia(
    private val activity: AnkiActivity,
    private val getState: () -> EditorState?,
    private val getWeb: () -> WebEditorView?,
    private val whenReady: suspend () -> Unit,
    private val reportError: (String) -> Unit,
    private val checkpoint: suspend () -> Unit = {},
) {
    private var pending: PendingEditorMedia? = null
    private var job: Job? = null
    private var pickerTarget: WebEditorTarget? = null
    private var pickerHadKeyboard = false
    private var pickerJob: Job? = null
    private var returnFocus: WebEditorFocusState? = null
    private val launcher =
        activity.registerForActivityResult(MultimediaResultContract()) { result ->
            val operation = pending
            when {
                result !is MultimediaResult.Success -> finishOperation()
                operation == null || result.fieldIndex != operation.target.field -> {
                    finishOperation()
                    reportError(invalidTarget)
                }
                result.field.mediaFile == null -> finishOperation()
                else -> {
                    operation.result = result.field
                    insertResult(operation)
                }
            }
        }

    init {
        activity.supportFragmentManager.setFragmentResultListener(MultimediaBottomSheet.REQUEST_KEY_CANCELED, activity) { _, _ ->
            cancelPicker()
        }
    }

    fun showPicker(capturedTarget: WebEditorTarget? = null) {
        if (job?.isActive == true || pending != null || activity.supportFragmentManager.findFragmentByTag(PICKER) != null) return
        job =
            activity.lifecycleScope.launch {
                try {
                    whenReady()
                    val target = capturedTarget ?: getWeb()?.captureTarget()
                    if (target == null) {
                        reportError(activity.getString(CommonString.card_template_editor_select_field))
                        return@launch
                    }
                    check(target.matches(getState())) { invalidTarget }
                    checkpoint()
                    returnFocus = getWeb()?.captureFocus()
                    awaitPickerAction(
                        target,
                        hadKeyboard =
                            ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true,
                    )
                    MultimediaBottomSheet().show(activity.supportFragmentManager, PICKER)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    reportFailure(exception)
                }
            }
    }

    private fun awaitPickerAction(
        target: WebEditorTarget,
        hadKeyboard: Boolean,
    ) {
        pickerTarget = target
        pickerHadKeyboard = hadKeyboard
        pickerJob?.cancel()
        pickerJob =
            activity.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
                val action = ViewModelProvider(activity)[MultimediaViewModel::class.java].multimediaAction.first()
                pickerTarget = null
                pickerHadKeyboard = false
                launch(MultimediaActionHandler.forAction(action), target)
            }
    }

    private fun cancelPicker() {
        val target = pickerTarget ?: return
        val restoreKeyboard = pickerHadKeyboard
        pickerTarget = null
        pickerHadKeyboard = false
        returnFocus = null
        pickerJob?.cancel()
        val web = getWeb() ?: return
        if (!restoreKeyboard || !target.matches(getState())) return
        pickerJob =
            activity.lifecycleScope.launch {
                // The dialog still owns window focus during onCancel. Wait for its window to leave.
                if (!web.hasWindowFocus()) {
                    suspendCancellableCoroutine { continuation ->
                        val observer = web.viewTreeObserver
                        val listener =
                            object : ViewTreeObserver.OnWindowFocusChangeListener {
                                override fun onWindowFocusChanged(hasFocus: Boolean) {
                                    if (!hasFocus) return
                                    observer.removeOnWindowFocusChangeListener(this)
                                    continuation.resume(Unit)
                                }
                            }
                        observer.addOnWindowFocusChangeListener(listener)
                        continuation.invokeOnCancellation {
                            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(listener)
                        }
                    }
                }
                if (web === getWeb() && web.hasFocus() && target.matches(getState())) {
                    // Keep the DOM selection and typing format; the attachment may target another field.
                    SoftwareKeyboardControllerCompat(web).show()
                }
            }
    }

    fun launch(
        handler: MultimediaActionHandler,
        capturedTarget: WebEditorTarget? = null,
    ) {
        if (job?.isActive == true || pending != null) return
        job =
            activity.lifecycleScope.launch {
                try {
                    whenReady()
                    val web = checkNotNull(getWeb())
                    if (returnFocus == null) returnFocus = web.captureFocus()
                    val state = checkNotNull(getState())
                    val target = capturedTarget ?: web.captureTarget()
                    if (target == null) {
                        returnFocus = null
                        reportError(activity.getString(CommonString.card_template_editor_select_field))
                        return@launch
                    }
                    val snapshot = web.snapshot()
                    check(target.matches(state) && snapshot.sessionId == target.sessionId && snapshot.generation == target.generation) {
                        invalidTarget
                    }
                    check(target.field in snapshot.fields.indices) { invalidTarget }
                    checkpoint()
                    val note =
                        withCol {
                            check(mediaFolder?.absolutePath == state.mediaDirectory) { "The collection changed; reopen the editor." }
                            NoteService.createEmptyNote(NotetypeJson(state.notetypeJson)).also {
                                NoteService.updateMultimediaNoteFromFields(this, snapshot.fields.toTypedArray(), state.noteTypeId, it)
                            }
                        }
                    val field = handler.createField()
                    note.setField(target.field, field)
                    check(target.matches(getState())) { invalidTarget }
                    val intent = handler.buildIntent(activity, MultimediaActivityExtra(target.field, field, note))
                    pending = PendingEditorMedia(target, handler.action)
                    // Cursor-handle popup windows can stretch across the activity transition.
                    // The captured bookmark remains the destination for the returned media.
                    web.clearFocus()
                    launcher.launch(intent)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    finishOperation()
                    reportFailure(exception)
                }
            }
    }

    /** Clipboard media uses the same native importer as the existing note editor. */
    fun paste(
        target: WebEditorTarget,
        uri: Uri,
    ) {
        if (job?.isActive == true || pending != null) {
            reportError(invalidTarget)
            return
        }
        job =
            activity.lifecycleScope.launch {
                try {
                    whenReady()
                    val state = checkNotNull(getState())
                    check(target.matches(state)) { invalidTarget }
                    checkpoint()
                    var mediaError: MediaRegistration.MediaError? = null
                    val html =
                        withCol {
                            check(mediaFolder?.absolutePath == state.mediaDirectory) { "The collection changed; reopen the editor." }
                            val mimeType =
                                activity.contentResolver.getType(uri)
                                    ?: URLConnection.guessContentTypeFromName(uri.lastPathSegment)
                            require(mimeType != null && listOf("image/", "audio/", "video/").any { mimeType.startsWith(it) }) {
                                activity.getString(CommonString.multimedia_editor_something_wrong)
                            }
                            MediaRegistration.onPaste(
                                activity,
                                uri,
                                ClipDescription("", arrayOf(mimeType)),
                                pasteAsPng = mimeType.startsWith("image/") && config.getBool(ConfigKey.Bool.PASTE_IMAGES_AS_PNG),
                                // Avoid getColUnsafe() re-entering the serial collection queue.
                                registerMedia = { file ->
                                    media.addFile(file)
                                    true
                                },
                                showError = { mediaError = it },
                            )
                        }
                    if (html == null) {
                        reportError((mediaError ?: MediaRegistration.MediaError.GenericError).toHumanReadableString(activity))
                        return@launch
                    }
                    check(target.matches(getState())) { invalidTarget }
                    check(checkNotNull(getWeb()).execute(WebEditorAction.INSERT_HTML, target, html)) { invalidTarget }
                    checkpoint()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    reportFailure(exception)
                }
            }
    }

    fun save(state: Bundle) {
        state.putParcelable(RETURN_FOCUS, returnFocus)
        pending?.let { state.putString(PENDING_MEDIA, it.toJson().toString()) }
        pickerTarget?.let {
            state.putParcelable(PICKER_TARGET, it)
            state.putBoolean(PICKER_KEYBOARD, pickerHadKeyboard)
        }
    }

    fun restore(state: Bundle?) {
        returnFocus = state?.let { BundleCompat.getParcelable(it, RETURN_FOCUS, WebEditorFocusState::class.java) }
        state?.let { BundleCompat.getParcelable(it, PICKER_TARGET, WebEditorTarget::class.java) }?.let {
            awaitPickerAction(it, state.getBoolean(PICKER_KEYBOARD))
        }
        val json = state?.getString(PENDING_MEDIA) ?: return
        try {
            pending = PendingEditorMedia.fromJson(JSONObject(json))
            pending?.takeIf { it.result != null }?.let(::insertResult)
        } catch (exception: Exception) {
            reportFailure(exception)
        }
    }

    private fun insertResult(operation: PendingEditorMedia) {
        job =
            activity.lifecycleScope.launch {
                try {
                    whenReady()
                    val state = checkNotNull(getState())
                    check(operation.target.matches(state)) { invalidTarget }
                    val source = checkNotNull(operation.result)
                    // Keep the source until insertion succeeds: a recreated activity may retry the import.
                    val result =
                        MultimediaActionHandler.forAction(operation.action).createField().apply {
                            mediaFile = source.mediaFile
                            hasTemporaryMedia = false
                        }
                    withCol {
                        check(mediaFolder?.absolutePath == state.mediaDirectory) { "The collection changed; reopen the editor." }
                        NoteService.importMediaToDirectory(this, result)
                    }
                    val html = result.formattedValue.orEmpty()
                    if (html.isEmpty()) {
                        finishOperation()
                        return@launch
                    }
                    val applied = checkNotNull(getWeb()).execute(WebEditorAction.INSERT_HTML, operation.target, html)
                    check(applied) { invalidTarget }
                    checkpoint()
                    finishOperation(restoreSelection = false)
                    if (source.hasTemporaryMedia && source.mediaFile != result.mediaFile) source.mediaFile?.delete()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    finishOperation()
                    reportFailure(exception)
                }
            }
    }

    private fun finishOperation(restoreSelection: Boolean = true) {
        pending = null
        val focus = returnFocus ?: return
        returnFocus = null
        activity.lifecycleScope.launch {
            try {
                whenReady()
                if (focus.target?.matches(getState()) == false) return@launch
                getWeb()?.restoreFocus(focus.target.takeIf { restoreSelection }, focus.keyboardVisible)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                reportFailure(exception)
            }
        }
    }

    private fun reportFailure(exception: Exception) {
        Timber.w(exception, "Unable to insert editor media")
        reportError(exception.localizedMessage ?: exception.toString())
    }

    private val invalidTarget: String get() = activity.getString(R.string.compose_editor_stale_command)

    companion object {
        private const val PENDING_MEDIA = "composeEditorPendingMedia"
        private const val PICKER_TARGET = "composeEditorMediaPickerTarget"
        private const val PICKER_KEYBOARD = "composeEditorMediaPickerKeyboard"
        private const val PICKER = "MultimediaBottomSheet"
        private const val RETURN_FOCUS = "composeEditorMediaReturnFocus"
    }
}

internal fun WebEditorTarget.matches(state: EditorState?): Boolean =
    state != null && sessionId == state.sessionId && generation == state.generation && field in state.fields.indices

/** Contains only a bookmark and media file metadata; editor fields stay in the WebView draft. */
internal data class PendingEditorMedia(
    val target: WebEditorTarget,
    val action: MultimediaAction,
    var result: IField? = null,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("sessionId", target.sessionId)
            .put("generation", target.generation)
            .put("field", target.field)
            .put("revision", target.revision)
            .put("bookmark", target.bookmark)
            .put("action", action.name)
            .apply {
                result?.let {
                    put("mediaPath", it.mediaFile?.absolutePath)
                    put("temporary", it.hasTemporaryMedia)
                }
            }

    companion object {
        fun fromJson(json: JSONObject): PendingEditorMedia {
            val action = MultimediaAction.valueOf(json.getString("action"))
            return PendingEditorMedia(
                target =
                    WebEditorTarget(
                        sessionId = json.getString("sessionId"),
                        generation = json.getInt("generation"),
                        field = json.getInt("field"),
                        revision = json.getInt("revision"),
                        bookmark = json.getString("bookmark"),
                    ),
                action = action,
                result =
                    if (json.has("mediaPath")) {
                        MultimediaActionHandler.forAction(action).createField().apply {
                            mediaFile = File(json.getString("mediaPath"))
                            hasTemporaryMedia = json.optBoolean("temporary")
                        }
                    } else {
                        null
                    },
            )
        }
    }
}
