// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 voczi <dev@voczi.com>

package com.ichi2.anki.pages

import android.app.Activity
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.importAnkiPackageUndoable
import com.ichi2.anki.importCsvRaw
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.searchInBrowser
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * A request that runs on the activity. A method that opens another screen calls `awaitPageShown` first,
 * which suspends until the page that sent the request is shown.
 */
typealias UIBackendInterface = FragmentActivity.(bytes: ByteArray, awaitPageShown: suspend () -> Unit) -> Deferred<ByteArray>

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
val uiMethods =
    hashMapOf<String, UIBackendInterface>(
        "searchInBrowser" to { bytes, awaitPageShown -> lifecycleScope.async { searchInBrowser(bytes, awaitPageShown) } },
        "updateDeckConfigs" to { bytes, _ -> lifecycleScope.async { updateDeckConfigsRaw(bytes) } },
        "latestProgress" to { bytes, _ ->
            lifecycleScope.async {
                withContext(Dispatchers.IO) {
                    CollectionManager.getBackend().latestProgressRaw(bytes)
                }
            }
        },
        "i18nResources" to { bytes, _ ->
            lifecycleScope.async {
                withContext(Dispatchers.IO) {
                    CollectionManager.getBackend().i18nResourcesRaw(bytes)
                }
            }
        },
        "importCsv" to { bytes, _ -> lifecycleScope.async { importCsvRaw(bytes) } },
        "importAnkiPackage" to { bytes, _ -> lifecycleScope.async { importAnkiPackageUndoable(bytes) } },
        "importDialogRequireClose" to { _, _ ->
            lifecycleScope.async {
                finish()
                byteArrayOf()
            }
        },
        "addImageOcclusionNote" to { bytes, _ ->
            lifecycleScope.async {
                withCol { addImageOcclusionNoteRaw(bytes) }
            }
        },
        "updateImageOcclusionNote" to { bytes, _ ->
            lifecycleScope.async {
                withCol { updateImageOcclusionNoteRaw(bytes) }
            }
        },
        "deckOptionsReady" to { bytes, _ -> lifecycleScope.async { deckOptionsReady(bytes) } },
        "deckOptionsRequireClose" to { bytes, _ -> lifecycleScope.async { deckOptionsRequireClose(bytes) } },
    )

sealed class UiPostRequestResponse {
    /** The requested method was not a valid UI POST request */
    data object UnknownMethod : UiPostRequestResponse()

    /**
     * A valid method could not be executed
     *
     * For example: if the calling fragment was not attached to an activity
     */
    data object Ignored : UiPostRequestResponse()

    /**
     * The request was handled by the backend (success/failure)
     */
    data class Handled(
        val data: ByteArray,
    ) : UiPostRequestResponse() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Handled

            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }
}

suspend fun FragmentActivity?.handleUiPostRequest(
    methodName: String,
    bytes: ByteArray,
    awaitPageShown: suspend () -> Unit = {},
): UiPostRequestResponse {
    // an unknown method may be valid for another request handler
    val uiMethod = uiMethods[methodName] ?: return UiPostRequestResponse.UnknownMethod

    // a resolved but ignored method should not be retried
    if (this == null) {
        Timber.w("ignored UI request '%s' - activity == null", methodName)
        return UiPostRequestResponse.Ignored
    }

    val data = uiMethod.invoke(this, bytes, awaitPageShown).await()
    when (methodName) {
        "addImageOcclusionNote" -> {
            undoableOp { OpChanges.parseFrom(data) }
            launchCatchingTask {
                // Allow time for toast message to appear before closing editor
                delay(1.seconds)
                setResult(Activity.RESULT_OK)
                finish()
            }
        }

        "updateImageOcclusionNote" -> {
            undoableOp { OpChanges.parseFrom(data) }
            launchCatchingTask {
                // Allow time for toast message to appear before closing editor
                delay(1.seconds)
                setResult(NoteEditorFragment.RESULT_UPDATED_IO_NOTE)
                finish()
            }
        }
    }
    return UiPostRequestResponse.Handled(data)
}
