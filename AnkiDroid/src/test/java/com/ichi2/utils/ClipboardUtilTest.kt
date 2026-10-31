// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.common.utils.ext.requireSystemService
import com.ichi2.testutils.EmptyApplication
import com.ichi2.utils.ClipboardUtil.AUDIO_MIME_TYPES
import com.ichi2.utils.ClipboardUtil.IMAGE_MIME_TYPES
import com.ichi2.utils.ClipboardUtil.VIDEO_MIME_TYPES
import com.ichi2.utils.ClipboardUtil.hasImage
import com.ichi2.utils.ClipboardUtil.hasMedia
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasLength
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class ClipboardUtilTest {
    private lateinit var context: Context
    private lateinit var clipboardManager: ClipboardManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clipboardManager = context.requireSystemService<ClipboardManager>()
    }

    @Test
    fun hasImageClipboardManagerNullTest() {
        val clipboardManager: ClipboardManager? = null
        assertFalse(hasImage(clipboardManager))
    }

    @Test
    fun hasImageDescriptionNullTest() {
        val clipDescription: ClipDescription? = null
        assertFalse(hasImage(clipDescription))
    }

    @Test
    fun hasMediaClipboardManagerNullTest() {
        val clipboardManager: ClipboardManager? = null
        assertFalse(hasMedia(clipboardManager))
    }

    @Test
    fun hasMediaDescriptionNullTest() {
        val clipDescription: ClipDescription? = null
        assertFalse(hasMedia(clipDescription))
    }

    @Test
    fun hasMediaWithImageMimeTypeTest() {
        val clipDescription = ClipDescription("label", IMAGE_MIME_TYPES)
        val clipData = ClipData(clipDescription, ClipData.Item("image data"))
        clipboardManager.setPrimaryClip(clipData)
        assertTrue(hasMedia(clipboardManager))
    }

    @Test
    fun hasMediaWithSVGMimeTypeTest() {
        val clipDescription = ClipDescription("label", arrayOf("image/svg+xml"))
        val clipData = ClipData(clipDescription, ClipData.Item("svg data"))
        clipboardManager.setPrimaryClip(clipData)
        assertTrue(hasMedia(clipboardManager))
    }

    @Test
    fun hasMediaWithAudioMimeTypeTest() {
        val clipDescription = ClipDescription("label", AUDIO_MIME_TYPES)
        val clipData = ClipData(clipDescription, ClipData.Item("audio data"))
        clipboardManager.setPrimaryClip(clipData)
        assertTrue(hasMedia(clipboardManager))
    }

    @Test
    fun hasMediaWithVideoMimeTypeTest() {
        val clipDescription = ClipDescription("label", VIDEO_MIME_TYPES)
        val clipData = ClipData(clipDescription, ClipData.Item("video data"))
        clipboardManager.setPrimaryClip(clipData)
        assertTrue(hasMedia(clipboardManager))
    }

    @Test
    fun `copyToClipboard copies the text to the clipboard`() {
        assertTrue(context.copyToClipboard(TruncatedString.from("hello")))
        assertThat(copiedText(), equalTo("hello"))
    }

    @Test
    fun `copyToClipboard copies oversized text truncated`() {
        assertTrue(context.copyToClipboard(TruncatedString.from("a".repeat(TruncatedString.MAX_CLIPBOARD_TEXT_LENGTH) + "b".repeat(10))))

        val copied = copiedText()
        assertThat(copied, hasLength(TruncatedString.MAX_CLIPBOARD_TEXT_LENGTH))
        assertThat(copied, equalTo("a".repeat(TruncatedString.MAX_CLIPBOARD_TEXT_LENGTH)))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun `copyToClipboard shows a success message below S_V2`() {
        assertTrue(context.copyToClipboard(TruncatedString.from("hello")))
        assertThat(
            ShadowToast.getTextOfLatestToast(),
            equalTo(context.getString(CommonString.about_ankidroid_successfully_copied_debug_info)),
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S_V2])
    fun `copyToClipboard shows no message on S_V2 and above`() {
        // the system shows its own 'copied to clipboard' message, so showing ours too would duplicate it
        assertTrue(context.copyToClipboard(TruncatedString.from("hello")))
        assertThat(ShadowToast.shownToastCount(), equalTo(0))
    }

    @Test
    fun `copyToClipboard returns false and reports failure if the clipboard is unavailable`() {
        assertFalse(contextWithClipboard(null).copyToClipboard(TruncatedString.from("hello")))
        assertThat(
            ShadowToast.getTextOfLatestToast(),
            equalTo(context.getString(CommonString.failed_to_copy)),
        )
    }

    @Test
    fun `copyToClipboard returns false and reports failure if setting the clip throws`() {
        val throwingClipboard =
            mockk<ClipboardManager> {
                every { setPrimaryClip(any()) } throws RuntimeException("android.os.TransactionTooLargeException")
            }

        assertFalse(contextWithClipboard(throwingClipboard).copyToClipboard(TruncatedString.from("hello")))
        assertThat(
            ShadowToast.getTextOfLatestToast(),
            equalTo(context.getString(CommonString.failed_to_copy)),
        )
    }

    /**
     * The text of the first item of the current primary clip.
     */
    private fun copiedText(): String =
        clipboardManager
            .primaryClip!!
            .getItemAt(0)
            .text
            .toString()

    /**
     * A [Context] whose clipboard service is [clipboard], leaving its other services intact.
     */
    private fun contextWithClipboard(clipboard: ClipboardManager?): Context =
        object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? = if (name == CLIPBOARD_SERVICE) clipboard else super.getSystemService(name)
        }
}
