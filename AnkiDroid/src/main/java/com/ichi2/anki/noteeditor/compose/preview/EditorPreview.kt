// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose.preview

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Displays the current unsaved note, retaining the selected card and side during content updates. */
@Composable
fun EditorPreview(
    input: EditorPreviewInput,
    modifier: Modifier = Modifier,
) {
    var fragment by remember { mutableStateOf<EditorPreviewFragment?>(null) }
    SideEffect { fragment?.viewModel?.update(input) }
    Column(modifier) {
        fragment?.viewModel?.let { viewModel ->
            val cards by viewModel.cards.collectAsStateWithLifecycle()
            if (cards.choices.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    for (choice in cards.choices) {
                        Tab(
                            selected = cards.selectedOrdinal == choice.ordinal,
                            onClick = { viewModel.selectCard(choice.ordinal) },
                            text = { Text(choice.name) },
                        )
                    }
                }
            }
        }
        AndroidFragment<EditorPreviewFragment>(modifier = Modifier.fillMaxSize()) { fragment = it }
    }
}
