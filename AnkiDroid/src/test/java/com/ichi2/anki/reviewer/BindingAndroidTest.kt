// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.reviewer.Binding.KeyCode
import com.ichi2.anki.reviewer.Binding.ModifierKeys.Companion.alt
import com.ichi2.anki.reviewer.Binding.ModifierKeys.Companion.ctrl
import com.ichi2.anki.reviewer.Binding.ModifierKeys.Companion.shift
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.targetContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class BindingAndroidTest : AndroidTest {
    @Test
    fun testFromString() {
        val character = BindingUnicodeCharacter('Ä')
        assertEquals(Binding.unicode(character), Binding.fromString(BindingTest.UNICODE_PREFIX + "Ä"))
        assertEquals(Binding.unicode(character, ctrl()), Binding.fromString(BindingTest.UNICODE_PREFIX + "Ctrl+Ä"))
        assertEquals(Binding.unicode(character, shift()), Binding.fromString(BindingTest.UNICODE_PREFIX + "Shift+Ä"))
        assertEquals(Binding.unicode(character, alt()), Binding.fromString(BindingTest.UNICODE_PREFIX + "Alt+Ä"))
        assertEquals(
            KeyCode(KeyEvent.KEYCODE_MEDIA_NEXT),
            Binding.fromString(BindingTest.KEY_PREFIX + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_MEDIA_NEXT)),
        )
        assertEquals(
            KeyCode(KeyEvent.KEYCODE_MEDIA_PREVIOUS, ctrl()),
            Binding.fromString(BindingTest.KEY_PREFIX + "Ctrl+" + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_MEDIA_PREVIOUS)),
        )
        assertEquals(
            KeyCode(KeyEvent.KEYCODE_VOLUME_DOWN, shift()),
            Binding.fromString(BindingTest.KEY_PREFIX + "Shift+" + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_VOLUME_DOWN)),
        )
        assertEquals(
            KeyCode(KeyEvent.KEYCODE_VOLUME_UP, alt()),
            Binding.fromString(BindingTest.KEY_PREFIX + "Alt+" + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_VOLUME_UP)),
        )
        assertEquals(Binding.gesture(Gesture.TAP_TOP), Binding.fromString(BindingTest.GESTURE_PREFIX + Gesture.TAP_TOP.name))
    }

    @Test
    @Config(qualifiers = "en")
    fun gesture_toDisplayString() {
        assertEquals("${BindingTest.GESTURE_PREFIX} Touch top", Binding.gesture(Gesture.TAP_TOP).toDisplayString())
    }

    private fun Binding.toDisplayString(): String = this.toDisplayString(targetContext)
}
