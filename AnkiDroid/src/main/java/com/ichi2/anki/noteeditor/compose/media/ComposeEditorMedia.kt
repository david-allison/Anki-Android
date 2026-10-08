// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose.media

import android.content.ClipDescription
import android.net.Uri
import android.os.Bundle
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
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction
import com.ichi2.anki.multimedia.MultimediaResult
import com.ichi2.anki.multimedia.MultimediaResultContract
import com.ichi2.anki.multimediacard.fields.IField
import com.ichi2.anki.noteeditor.compose.EditorState
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.anki.noteeditor.web.WebEditorTarget
import com.ichi2.anki.noteeditor.web.WebEditorView
import com.ichi2.anki.servicelayer.NoteService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.URLConnection

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
    private val launcher =
        activity.registerForActivityResult(MultimediaResultContract()) { result ->
            val operation = pending
            when {
                result !is MultimediaResult.Success -> pending = null
                operation == null || result.fieldIndex != operation.target.field -> {
                    pending = null
                    reportError(invalidTarget)
                }
                result.field.mediaFile == null -> pending = null
                else -> {
                    operation.result = result.field
                    insertResult(operation)
                }
            }
        }

    fun launch(handler: MultimediaActionHandler) {
        if (job?.isActive == true || pending != null) return
        job =
            activity.lifecycleScope.launch {
                try {
                    whenReady()
                    val web = checkNotNull(getWeb())
                    val state = checkNotNull(getState())
                    val target = web.captureTarget()
                    if (target == null) {
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
                    pending = PendingEditorMedia(target, handler.action)
                    launcher.launch(handler.buildIntent(activity, MultimediaActivityExtra(target.field, field, note)))
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    pending = null
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
        pending?.let { state.putString(PENDING_MEDIA, it.toJson().toString()) }
    }

    fun restore(state: Bundle?) {
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
                        pending = null
                        return@launch
                    }
                    val applied = checkNotNull(getWeb()).execute(WebEditorAction.INSERT_HTML, operation.target, html)
                    check(applied) { invalidTarget }
                    checkpoint()
                    pending = null
                    if (source.hasTemporaryMedia && source.mediaFile != result.mediaFile) source.mediaFile?.delete()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    pending = null
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
