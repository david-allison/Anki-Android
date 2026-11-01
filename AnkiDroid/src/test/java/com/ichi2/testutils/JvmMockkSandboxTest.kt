// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.android.getCurrentLocaleTag
import io.mockk.MockKGateway
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.internal.bytecode.SandboxClassLoader
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class, sdk = [24, 36])
class JvmMockkSandboxTest {
    @Test
    fun `JVM MockK mocks Android types and functions from the current SDK sandbox`() {
        assertFalse(MockKGateway::class.java.classLoader is SandboxClassLoader)
        assertTrue(Context::class.java.classLoader is SandboxClassLoader)
        val context = mockk<Context>()
        val name = "sandbox-${Build.VERSION.SDK_INT}"
        every { context.packageName } returns name
        assertEquals(name, context.packageName)
        val manager = mockk<ActivityManager>()
        context.mockSystemService(ActivityManager::class, manager)
        assertSame(manager, context.getSystemService(ActivityManager::class.java))

        mockkStatic(::getCurrentLocaleTag)
        try {
            every { getCurrentLocaleTag() } returns name
            assertEquals(name, getCurrentLocaleTag())
        } finally {
            unmockkStatic(::getCurrentLocaleTag)
        }
    }
}
