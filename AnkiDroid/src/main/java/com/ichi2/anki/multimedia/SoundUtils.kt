// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2014 Timothy rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.multimedia

import com.ichi2.anki.CollectionManager
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.libanki.AvRef
import com.ichi2.anki.libanki.AvTag
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Sound
import com.ichi2.anki.libanki.Sound.AV_PLAYLINK_RE
import com.ichi2.anki.libanki.SoundOrVideoTag
import com.ichi2.anki.libanki.SoundOrVideoTag.Type
import com.ichi2.anki.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import com.ichi2.anki.utils.CollectionPreferences
import org.jetbrains.annotations.VisibleForTesting
import java.io.File

/**
 * Takes content with [AvRef]s and expands them to reference the media file
 *
 * * Videos are replaced with `<video>`
 * * Audio is replaced with <a href="playsound:">
 *
 * @param content card content to be rendered that may contain embedded audio
 *
 * @return content with [AvRef]s replaced with HTML to play the file
 */
fun expandSounds(
    content: String,
    renderOutput: TemplateRenderOutput,
    showAudioPlayButtons: Boolean,
    mediaDir: File,
): String = expandSounds(content, renderOutput, showAudioPlayButtons, mediaDir) { tag, directory -> tag.getTagType(directory) }

/** Extract av tag from playsound:q:x link */
suspend fun getAvTag(
    card: Card,
    url: String,
): AvTag? =
    AV_PLAYLINK_RE.matchEntire(url)?.let {
        val values = it.groupValues
        val questionSide = values[1] == "q"
        val index = values[2].toInt()
        val tags =
            CollectionManager.withCol {
                if (questionSide) {
                    card.questionAvTags(this)
                } else {
                    card.answerAvTags(this)
                }
            }
        if (index < tags.size) {
            tags[index]
        } else {
            null
        }
    }

/**
 * Return card text with play buttons added, or stripped.
 *
 * @param text A string, maybe containing `[anki:play]` tags to replace
 * @param renderOutput Context: whether a file is audio or video
 *
 * @see AvRef
 */
suspend fun replaceAvRefsWithPlayButtons(
    text: String,
    renderOutput: TemplateRenderOutput,
): String {
    val mediaDir = CollectionManager.withCol { media.dir }
    val hidePlayButtons = CollectionPreferences.getHidePlayAudioButtons()
    return expandSounds(text, renderOutput, showAudioPlayButtons = !hidePlayButtons, mediaDir)
}

fun SoundOrVideoTag.getTagType(mediaDir: File): Type {
    val extension = filename.substringAfterLast(".", "")
    return when (extension) {
        in Sound.VIDEO_ONLY_EXTENSIONS -> Type.VIDEO
        in Sound.AUDIO_OR_VIDEO_EXTENSIONS -> {
            val file = File(mediaDir, filename)
            if (isAudioFileInVideoContainer(file) == true) {
                Type.AUDIO
            } else {
                Type.VIDEO
            }
        }
        // assume audio if we don't know. Our audio code is more resilient than HTML video
        else -> Type.AUDIO
    }
}

/**
 * Whether a video file only contains an audio stream
 *
 * @return `null` - file is not a video, or not found
 */
@VisibleForTesting
fun isAudioFileInVideoContainer(file: File): Boolean? {
    if (file.extension !in Sound.VIDEO_ONLY_EXTENSIONS && file.extension !in Sound.AUDIO_OR_VIDEO_EXTENSIONS) {
        return null
    }

    if (file.extension in Sound.VIDEO_ONLY_EXTENSIONS) return false

    // file.extension is in AUDIO_OR_VIDEO_EXTENSIONS
    if (!file.exists()) return null

    // Also check that there is a video thumbnail, as some formats like mp4 can be audio only
    val isVideo = CompatHelper.compat.hasVideoThumbnail(file.absolutePath) ?: return null
    return !isVideo
}
