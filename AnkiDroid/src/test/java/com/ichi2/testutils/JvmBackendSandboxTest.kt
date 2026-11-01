// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.generic.Empty
import anki.i18n.GeneratedTranslations
import anki.i18n.PreviewTranslations
import com.google.protobuf.GeneratedMessageLite
import com.ichi2.anki.RobolectricTest
import net.ankiweb.rsdroid.NativeMethods
import net.ankiweb.rsdroid.testing.RustBackendLoader
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.internal.bytecode.SandboxClassLoader
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [24, 36])
class JvmBackendSandboxTest : RobolectricTest() {
    @Test
    fun `SDK sandboxes share the JNI bridge and keep independent collections`() {
        assertTrue(javaClass.classLoader is SandboxClassLoader)
        assertFalse(NativeMethods::class.java.classLoader is SandboxClassLoader)
        assertSame(NativeMethods::class.java.classLoader, RustBackendLoader::class.java.classLoader)
        assertFalse(Empty::class.java.classLoader is SandboxClassLoader)
        assertFalse(GeneratedMessageLite::class.java.classLoader is SandboxClassLoader)
        assertTrue(PreviewTranslations::class.java.classLoader is SandboxClassLoader)
        assertTrue(GeneratedTranslations::class.java.classLoader is SandboxClassLoader)
        assertEquals(0, col.noteCount())

        addBasicNote("front", "back")

        assertEquals(1, col.noteCount())
    }
}
