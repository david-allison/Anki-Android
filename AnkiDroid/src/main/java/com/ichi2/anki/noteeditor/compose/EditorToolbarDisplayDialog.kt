// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorToolbarDisplayDialog(
    selected: EditorToolbarDisplay,
    onSelect: (EditorToolbarDisplay) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compose_editor_toolbar)) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                EditorToolbarDisplay.entries.forEach { display ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = display == selected, role = Role.RadioButton, onClick = { onSelect(display) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = display == selected, onClick = null)
                        // Let the example wrap beneath long labels or large text instead of squeezing the label.
                        FlowRow(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(display.label), Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodyLarge)
                            ToolbarLayoutPreview(display)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CommonString.dialog_cancel)) } },
    )
}

/** Decorative examples share the editor's icons and colours; only the enclosing radio row is interactive. */
@Composable
private fun ToolbarLayoutPreview(display: EditorToolbarDisplay) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(6.dp)
    val icons =
        listOf(
            EditorToolbarButton.BOLD,
            EditorToolbarButton.ITALIC,
            EditorToolbarButton.UNDERLINE,
            EditorToolbarButton.UNDO,
            EditorToolbarButton.REDO,
            EditorToolbarButton.SOURCE_MODE,
            EditorToolbarButton.MEDIA,
            EditorToolbarButton.SUPERSCRIPT,
        )
    Column(
        Modifier
            .width(132.dp)
            .height(88.dp)
            .clearAndSetSemantics {}
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.outlineVariant, shape),
    ) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(if (display == EditorToolbarDisplay.HIDDEN) 2 else 1) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Spacer(Modifier.fillMaxWidth(0.7f).height(3.dp).background(colors.outlineVariant))
                    Spacer(Modifier.fillMaxWidth(0.45f).height(3.dp).background(colors.outlineVariant))
                }
            }
        }
        if (display != EditorToolbarDisplay.HIDDEN) {
            Column(Modifier.fillMaxWidth().background(colors.surfaceContainer.compositeOver(colors.surface)).padding(4.dp)) {
                if (display == EditorToolbarDisplay.STACK) {
                    icons.chunked(4).asReversed().forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { PreviewIcon(it.icon) }
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        icons.take(4).forEach { PreviewIcon(it.icon) }
                        Icon(
                            painterResource(R.drawable.ic_baseline_chevron_right_24),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewIcon(icon: Int) {
    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(24.dp).padding(4.dp),
        tint = MaterialTheme.colorScheme.onSurface,
    )
}
