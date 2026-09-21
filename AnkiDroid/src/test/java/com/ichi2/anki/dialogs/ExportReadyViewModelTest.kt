// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.viewmodel.ExportReadyViewModel
import com.ichi2.anki.dialogs.viewmodel.ExportReadyViewModel.ExportReadyParams
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportReadyViewModelTest : RobolectricTest() {
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
