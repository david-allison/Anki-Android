// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose.media

import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.libanki.mediaFolder
import com.ichi2.anki.noteeditor.compose.EditorState
import com.ichi2.anki.pages.ImageOcclusion
import com.ichi2.anki.pages.viewmodel.ImageOcclusionArgs
import com.ichi2.utils.ImportUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize

/** Launch the existing mask editor without replacing or saving the current field-editor draft. */
internal class ComposeEditorImageOcclusion(
    private val activity: AnkiActivity,
    private val getState: () -> EditorState?,
    private val whenReady: suspend () -> Unit,
    private val checkpoint: suspend () -> Unit,
    private val onReturn: () -> Unit,
    private val reportError: (String) -> Unit,
) {
    private var pending: Request? = null
    private var job: Job? = null
    private val editor = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { onReturn() }
    private val picker =
        activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            pending = pending?.copy(image = uri)?.takeIf { uri != null }
            pending?.let(::openImage)
        }

    fun launch(noteTypeId: Long = 0) {
        if (pending != null || job?.isActive == true) return
        runTask {
            whenReady()
            val state = checkNotNull(getState())
            check(state.isAdding)
            checkpoint()
            pending = Request(state.sessionId, state.generation, state.mediaDirectory, state.deckId, noteTypeId)
            picker.launch("image/*")
        }
    }

    private fun openImage(request: Request) {
        runTask {
            whenReady()
            val state = checkNotNull(getState())
            check(state.sessionId == request.sessionId && state.generation == request.generation)
            withCol {
                check(mediaFolder?.absolutePath == request.collection)
                check(request.noteTypeId == 0L || notetypes.get(request.noteTypeId)?.isImageOcclusion == true)
            }
            val path = withContext(ioDispatcher) { ImportUtils.getFileCachedCopy(activity, checkNotNull(request.image)) }
            checkNotNull(path) { activity.getString(CommonString.multimedia_editor_something_wrong) }
            check(getState()?.let { it.sessionId == request.sessionId && it.generation == request.generation && !it.isSaving } == true)
            editor.launch(ImageOcclusion.getIntent(activity, ImageOcclusionArgs.Add(path, request.noteTypeId, request.deckId)))
            pending = null
        }
    }

    private fun runTask(block: suspend () -> Unit) {
        job =
            activity.lifecycleScope.launch {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    pending = null
                    reportError(e.localizedMessage ?: e.toString())
                }
            }
    }

    fun save(state: Bundle) = state.putParcelable(PENDING, pending)

    fun restore(state: Bundle?) {
        pending = state?.let { BundleCompat.getParcelable(it, PENDING, Request::class.java) }
        pending?.takeIf { it.image != null }?.let(::openImage)
    }

    @Parcelize
    private data class Request(
        val sessionId: String,
        val generation: Int,
        val collection: String,
        val deckId: Long,
        val noteTypeId: Long,
        val image: Uri? = null,
    ) : Parcelable

    private companion object {
        const val PENDING = "editorImageOcclusion"
    }
}
