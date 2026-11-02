// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.generic.Empty
import anki.i18n.GeneratedTranslations
import anki.i18n.PreviewTranslations
import com.google.protobuf.GeneratedMessageLite
import com.ichi2.anki.RobolectricTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.NativeMethods
import net.ankiweb.rsdroid.Translations
import net.ankiweb.rsdroid.testing.RustBackendLoader
import org.jsoup.Jsoup
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.internal.bytecode.SandboxClassLoader
import kotlin.reflect.typeOf
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
        for (library in listOf(
            "native/linux/x86_64/librobolectric-nativeruntime.so",
            "native/mac/aarch64/librobolectric-nativeruntime.dylib",
            "native/mac/x86_64/librobolectric-nativeruntime.dylib",
            "native/windows/x86_64/robolectric-nativeruntime.dll",
        )) {
            assertEquals("file", requireNotNull(javaClass.classLoader?.getResource(library)).protocol)
        }
        assertFalse(Empty::class.java.classLoader is SandboxClassLoader)
        assertFalse(GeneratedMessageLite::class.java.classLoader is SandboxClassLoader)
        assertFalse(Jsoup::class.java.classLoader is SandboxClassLoader)
        assertEquals("front", Jsoup.parse("<b>front</b>").text())
        assertFalse(Class.forName("com.google.common.collect.ImmutableList").classLoader is SandboxClassLoader)
        assertFalse(Class.forName("com.google.common.util.concurrent.ListenableFuture").classLoader is SandboxClassLoader)
        assertFalse(PreviewTranslations::class.java.classLoader is SandboxClassLoader)
        assertFalse(GeneratedTranslations::class.java.classLoader is SandboxClassLoader)
        assertEquals("Edit", PreviewTranslations.studyingEdit())
        assertTrue(Backend::class.java.classLoader is SandboxClassLoader)
        assertTrue(BackendFactory::class.java.classLoader is SandboxClassLoader)
        assertTrue(Translations::class.java.classLoader is SandboxClassLoader)
        assertFalse(KSerializer::class.java.classLoader is SandboxClassLoader)
        assertTrue(Json::class.java.classLoader is SandboxClassLoader)
        assertTrue(SdkValue.serializer()::class.java.classLoader is SandboxClassLoader)
        val values = listOf(SdkValue(Build.VERSION.SDK_INT))
        val serializer = serializer(typeOf<List<SdkValue>>())
        val json = Json.encodeToString(serializer, values)
        assertEquals(values, Json.decodeFromString(serializer, json))
        assertEquals(0, col.noteCount())
        val version = "sdk${Build.VERSION.SDK_INT}"
        assertTrue(col.tr.aboutVersion(version).contains(version))

        addBasicNote("front", "back")

        assertEquals(1, col.noteCount())
    }

    @Serializable
    private data class SdkValue(
        val sdk: Int,
    )
}
