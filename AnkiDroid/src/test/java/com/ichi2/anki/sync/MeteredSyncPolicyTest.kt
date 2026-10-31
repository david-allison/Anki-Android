// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.EmptyApplication
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.ichi2.anki.common.android.R as CommonR

@RunWith(RobolectricTestRunner::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class MeteredSyncPolicyTest {
    @Before
    fun setUp() {
        mockkObject(MeteredSyncPolicy)
        every { MeteredSyncPolicy.shouldBlock() } returns true
    }

    @After
    fun tearDown() {
        unmockkObject(MeteredSyncPolicy)
    }

    @Test
    fun `shows dialog and notifies onDialogShown when blocked`() =
        withMeteredSyncAttempt { result ->
            assertTrue(result.dialogShown)
            assertFalse(result.onConfirmCalled)
            assertNotNull(result.dialog)
        }

    @Test
    fun `Continue runs onConfirm`() =
        withMeteredSyncAttempt { result ->
            assertFalse(result.onConfirmCalled)
            result.clickContinue()
            assertTrue(result.onConfirmCalled)
            assertEquals(MeteredSyncPermission.ALLOW_METERED_SYNC_THIS_TIME, result.permission)
        }

    @Test
    fun `Cancel does not run onConfirm`() =
        withMeteredSyncAttempt { result ->
            assertFalse(result.onConfirmCalled)
            result.clickCancel()
            assertFalse(result.onConfirmCalled)
        }

    private fun withMeteredSyncAttempt(testBody: (MeteredSyncAttemptResult) -> Unit) {
        Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
            controller.get().setTheme(CommonR.style.Theme_Light)
            val activity = controller.setup().get()
            var dialogShown = false
            var permission: MeteredSyncPermission? = null
            with(activity) {
                MeteredSyncPolicy.confirmThen(onDialogShown = { dialogShown = true }) { permission = it }
            }
            val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
            try {
                testBody(MeteredSyncAttemptResult(dialogShown, { permission }, dialog))
            } finally {
                dialog?.dismiss()
            }
        }
    }

    private class MeteredSyncAttemptResult(
        val dialogShown: Boolean,
        private val getPermission: () -> MeteredSyncPermission?,
        val dialog: AlertDialog?,
    ) {
        val permission: MeteredSyncPermission? get() = getPermission()
        val onConfirmCalled: Boolean get() = permission != null

        fun clickContinue() = clickDialogButton(DialogInterface.BUTTON_POSITIVE)

        fun clickCancel() = clickDialogButton(DialogInterface.BUTTON_NEGATIVE)

        private fun clickDialogButton(button: Int) {
            dialog!!.getButton(button).performClick()
            ShadowLooper.runUiThreadTasks()
        }
    }
}
