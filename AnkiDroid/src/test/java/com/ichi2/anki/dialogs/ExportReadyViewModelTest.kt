// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.dialogs.viewmodel.ExportReadyViewModel
import com.ichi2.anki.dialogs.viewmodel.ExportReadyViewModel.ExportReadyParams
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ExportReadyViewModelTest : RobolectricTest() {
    @Test
    fun `restoring state from the default factory drops copied fragment arguments`() {
        val intent = Intent().putExtra(SingleFragmentActivity.EXTRA_FRAGMENT_ARGS, bundleOf("payload" to ByteArray(300_000)))
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(FragmentActivity::class.java, intent)
        // Reproduce the default factory used before argument filtering.
        val original = ViewModelProvider(controller.get())[ExportReadyViewModel::class.java]
        val params = ExportReadyParams("export.apkg", asText = true)
        original.registerExportReadyRequest(params)
        val savedState = Bundle()
        controller
            .pause()
            .stop()
            .saveInstanceState(savedState)
            .destroy()

        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(savedState)
            assertTrue(parcel.dataSize() > 300_000, "The old factory should reproduce the copied payload")
            parcel.setDataPosition(0)
            val restoredState = requireNotNull(parcel.readBundle(javaClass.classLoader))
            val restoredController =
                startActivityControllerNormallyOpenCollectionWithIntent(FragmentActivity::class.java, intent, restoredState)
            val restored = ViewModelProvider(restoredController.get(), ExportReadyViewModel.factory)[ExportReadyViewModel::class.java]

            assertNotSame(original, restored)
            assertThat(restored.exportReadyDestination.value, equalTo(params))
            val newState = Bundle()
            restoredController.pause().stop().saveInstanceState(newState)
            parcel.setDataSize(0)
            parcel.setDataPosition(0)
            parcel.writeBundle(newState)
            assertTrue(parcel.dataSize() < 10_000, "Restored export state should not retain the copied fragment payload")
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun `restored pending export can be cleared`() {
        val params = ExportReadyParams("export.apkg", asText = true)
        val savedStateHandle =
            SavedStateHandle(
                mapOf(
                    "arg_export_ready_params" to params,
                ),
            )
        val viewModel = ExportReadyViewModel(savedStateHandle)

        assertThat(viewModel.exportReadyDestination.value, equalTo(params))
        assertThat(savedStateHandle.keys(), equalTo(setOf("arg_export_ready_params")))

        viewModel.clearExportReadyRequest()
        assertThat(viewModel.exportReadyDestination.value, nullValue())
        assertThat(savedStateHandle.get<ExportReadyParams>("arg_export_ready_params"), nullValue())
    }
}
