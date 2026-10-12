// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.R

/** Accessibility actions must update RecyclerView on its UI thread, like a real accessibility service. */
internal fun ComposeTestRule.moveToolbarButton(
    label: String,
    actionLabel: String,
) {
    val description = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.compose_editor_reorder_button, label)
    val action =
        onNodeWithContentDescription(description).fetchSemanticsNode().config[SemanticsActions.CustomActions].single {
            it.label ==
                actionLabel
        }
    runOnIdle { check(action.action()) }
}
