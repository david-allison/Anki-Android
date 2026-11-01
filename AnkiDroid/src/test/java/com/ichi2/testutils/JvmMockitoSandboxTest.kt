// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import org.robolectric.internal.bytecode.SandboxClassLoader
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class, sdk = [24, 36])
class JvmMockitoSandboxTest {
    @Test
    fun `JVM Mockito mocks Android types from the current SDK sandbox`() {
        assertFalse(Mockito::class.java.classLoader is SandboxClassLoader)
        assertTrue(Context::class.java.classLoader is SandboxClassLoader)
        val context = mock<Context>()
        val name = "sandbox-${Build.VERSION.SDK_INT}"

        whenever(context.packageName).thenReturn(name)

        assertEquals(name, context.packageName)
    }
}
