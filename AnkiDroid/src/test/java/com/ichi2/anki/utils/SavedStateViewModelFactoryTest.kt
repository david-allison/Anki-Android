// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.EmptyAnkiActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

@RunWith(ParameterizedRobolectricTestRunner::class)
class SavedStateViewModelFactoryTest(
    private val useActivityDefault: Boolean,
) : RobolectricTest() {
    private val activityClass = if (useActivityDefault) EmptyAnkiActivity::class.java else FragmentActivity::class.java

    private fun viewModel(activity: FragmentActivity): TestViewModel {
        val factory =
            if (useActivityDefault) {
                activity.defaultViewModelProviderFactory
            } else {
                savedStateViewModelFactory(create = ::TestViewModel)
            }
        return ViewModelProvider(activity, factory)[TestViewModel::class.java]
    }

    @Test
    fun `launch arguments are neither copied into saved state nor modified`() {
        val intent = Intent().putExtra("id", 42L).putExtra("unrelated", "payload")
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(activityClass, intent)
        val model = viewModel(controller.get())

        assertEquals(emptySet(), model.state.keys())
        assertEquals(42L, controller.get().intent.getLongExtra("id", -1))
        assertEquals("payload", controller.get().intent.getStringExtra("unrelated"))
    }

    @Test
    fun `process death preserves state without registering its keys`() {
        val intent = Intent().putExtra("id", 42L).putExtra("new_ui_state", "launch value")
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(activityClass, intent)
        val original = viewModel(controller.get())
        original.state["id"] = 43L
        original.state["new_ui_state"] = "edited value"

        val restored = viewModel(restoreAfterProcessDeath(controller))

        assertNotSame(original, restored)
        assertEquals(43L, restored.state.get<Long>("id"))
        assertEquals("edited value", restored.state.get<String>("new_ui_state"))
    }

    private fun restoreAfterProcessDeath(controller: ActivityController<out FragmentActivity>): FragmentActivity {
        val intent = controller.get().intent
        val savedState = Bundle()
        controller
            .pause()
            .stop()
            .saveInstanceState(savedState)
            .destroy()
        val parcel = Parcel.obtain()
        val restoredState =
            try {
                parcel.writeBundle(savedState)
                parcel.setDataPosition(0)
                requireNotNull(parcel.readBundle(javaClass.classLoader))
            } finally {
                parcel.recycle()
            }
        return startActivityControllerNormallyOpenCollectionWithIntent(activityClass, intent, restoredState).get()
    }

    class TestViewModel(
        val state: ViewModelSavedStateHandle,
    ) : ViewModel()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "activity default = {0}")
        fun factories() = listOf(arrayOf(true), arrayOf(false))
    }
}
