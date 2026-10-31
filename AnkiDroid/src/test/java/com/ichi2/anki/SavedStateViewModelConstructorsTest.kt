// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.lifecycle.SavedStateHandle
import com.ichi2.anki.browser.search.CardBrowserSearchViewModel
import com.ichi2.anki.previewer.TemplatePreviewerViewModel
import org.junit.Test

class SavedStateViewModelConstructorsTest {
    @Test
    fun `default AndroidX factory can find saved state constructors`() {
        // SavedStateViewModelFactory matches the Java signature exactly. Kotlin default
        // parameters alone do not expose this constructor when dependencies are added.
        for (model in listOf(CardBrowserSearchViewModel::class.java, TemplatePreviewerViewModel::class.java)) {
            model.getConstructor(SavedStateHandle::class.java)
        }
    }
}
