// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.media.MediaPlayer
import android.net.Uri
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import androidx.annotation.VisibleForTesting
import androidx.core.net.toFile
import com.ichi2.anki.common.utils.MissingMediaNotifications
import com.ichi2.anki.libanki.TtsPlayer
import com.ichi2.anki.pages.PostRequestBridge
import com.ichi2.anki.pages.PostRequestUri.Companion.ANKIDROID_JS_PREFIX
import timber.log.Timber
import java.io.File

/** Handles logic for displaying help for missing media files  */
class MediaErrorHandler : MediaErrorListener {
    companion object {
        /** Specify a maximum number of times to display, as it's somewhat annoying  */
        const val MAX_DISPLAY_TIMES = 2
    }

    constructor()
    constructor(onMediaError: ((String) -> Unit), onTtsError: ((TtsPlayer.TtsError) -> Unit)) {
        this.onMediaError = onMediaError
        this.onTtsError = onTtsError
    }

    // TODO turn into `val` once the legacy study screen is removed
    @VisibleForTesting
    var onMediaError: ((String) -> Unit)? = null

    @VisibleForTesting
    var onTtsError: ((TtsPlayer.TtsError) -> Unit)? = null

    private val missingMedia = MissingMediaNotifications(PostRequestBridge.HOST, ANKIDROID_JS_PREFIX, MAX_DISPLAY_TIMES)

    private var automaticTtsFailureCount = 0

    override fun onError(uri: Uri): MediaErrorBehavior {
        if (uri.scheme != "file") {
            return MediaErrorBehavior.CONTINUE_MEDIA
        }

        val file = uri.toFile()
        // There is a multitude of transient issues with the MediaPlayer.
        // Retrying fixes most of these
        if (file.exists()) return MediaErrorBehavior.RETRY_MEDIA

        onMediaError?.let { callback ->
            processMissingMedia(file, callback)
        }
        return MediaErrorBehavior.CONTINUE_MEDIA
    }

    override fun onMediaPlayerError(
        mp: MediaPlayer?,
        which: Int,
        extra: Int,
        uri: Uri,
    ): MediaErrorBehavior {
        Timber.w("Media Error: (%d, %d)", which, extra)
        return onError(uri)
    }

    override fun onTtsError(
        error: TtsPlayer.TtsError,
        isAutomaticPlayback: Boolean,
    ) {
        onTtsError?.let { callback ->
            processTtsFailure(error, isAutomaticPlayback, callback)
        }
    }

    fun processFailure(
        request: WebResourceRequest,
        onFailure: (String) -> Unit,
    ) {
        val url = request.url
        missingMedia.processFailure(
            host = url.host,
            path = url.path,
            resolveFileName = { URLUtil.guessFileName(url.toString(), null, null) },
            onFailure = onFailure,
        )
    }

    fun processMissingMedia(
        file: File,
        onFailure: (String) -> Unit,
    ) = missingMedia.processMissingMedia(file, onFailure)

    fun onCardSideChange() = missingMedia.onCardSideChange()

    fun processTtsFailure(
        error: TtsPlayer.TtsError,
        playingAutomatically: Boolean,
        errorHandler: (TtsPlayer.TtsError) -> Unit,
    ) {
        // if the user is playing a single sound explicitly, we want to provide feedback
        if (playingAutomatically && automaticTtsFailureCount++ >= 3) {
            Timber.v("Ignoring TTS Error: %s. failure limit exceeded", error)
            return
        }

        Timber.w("displaying error for %s", error)
        // Maybe specifically check for APP_TTS_INIT_TIMEOUT

        errorHandler.invoke(error)
    }
}
