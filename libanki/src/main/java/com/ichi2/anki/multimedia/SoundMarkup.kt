// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2014 Timothy rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.multimedia

import com.ichi2.anki.common.utils.htmlEncode
import com.ichi2.anki.libanki.AvRef
import com.ichi2.anki.libanki.Sound.replaceAvRefsWith
import com.ichi2.anki.libanki.SoundOrVideoTag
import com.ichi2.anki.libanki.SoundOrVideoTag.Type
import com.ichi2.anki.libanki.TTSTag
import com.ichi2.anki.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import com.ichi2.anki.libanki.getFileUri
import org.intellij.lang.annotations.Language
import java.io.File
import java.nio.file.Paths

/** Expands audio/video references using the supplied media classification. */
@Suppress("HtmlUnknownAttribute", "HtmlDeprecatedAttribute")
fun expandSounds(
    content: String,
    renderOutput: TemplateRenderOutput,
    showAudioPlayButtons: Boolean,
    mediaDir: File,
    classifyMedia: (SoundOrVideoTag, File) -> Type,
) = replaceAvRefsWith(content, renderOutput) { tag, playTag ->

    fun AvRef.asHtmlAudio(): String {
        if (!showAudioPlayButtons) return ""
        val playsound = "playsound:${this.side}:${this.index}"

        @Language("HTML")
        val result = """<a class="replay-button soundLink" href=$playsound><span>
                        <svg class="playImage" viewBox="0 0 64 64" version="1.1">
                            <circle cx="32" cy="32" r="29" fill="lightgrey"/>
                            <path d="M56.502,32.301l-37.502,20.101l0.329,-40.804l37.173,20.703Z" fill="black"/>Replay
                        </svg>
                    </span></a>"""
        return result
    }

    fun SoundOrVideoTag.asHtmlVideo(): String {
        val filename = this.filename
        val path = Paths.get(mediaDir.absolutePath, filename).toString()
        val uri = getFileUri(path)

        val playsound = "${playTag.side}:${playTag.index}"

        val onEnded = """window.location.href = "videoended:$playsound";"""
        val onPause = """if (this.currentTime != this.duration) { window.location.href = "videopause:$playsound"; }"""

        // TODO: Make the loading screen nicer if the video doesn't autoplay
        @Language("HTML")
        val result =
            """<video
                    | src="$uri"
                    | controls
                    | data-file="${filename.htmlEncode()}"
                    | onended='$onEnded'
                    | onpause='$onPause'
                    | data-play="$playsound" controlsList="nodownload"></video>
            """.trimMargin()
        return result
    }

    when (tag) {
        is TTSTag -> playTag.asHtmlAudio()
        is SoundOrVideoTag -> {
            when (classifyMedia(tag, mediaDir)) {
                Type.AUDIO -> playTag.asHtmlAudio()
                Type.VIDEO -> tag.asHtmlVideo()
            }
        }
    }
}
