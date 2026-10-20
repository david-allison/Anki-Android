// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.android.R as CommonR

@Composable
internal fun EditorToolbarDisplayDialog(
    selected: EditorToolbarDisplay,
    onSelect: (EditorToolbarDisplay) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(stringResource(R.string.compose_editor_toolbar_layout)) },
        text = {
            BoxWithConstraints(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                // All three fit on a phone. At large font sizes, scroll instead of squeezing the labels.
                val choiceWidth = maxOf((maxWidth - 16.dp) / 3, 72.dp * LocalDensity.current.fontScale)
                Row(
                    Modifier.selectableGroup().horizontalScroll(rememberScrollState()).height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EditorToolbarDisplay.entries.forEach { display ->
                        val isSelected = display == selected
                        Column(
                            Modifier
                                .width(choiceWidth)
                                .fillMaxHeight()
                                .clip(MaterialTheme.shapes.small)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                                .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(display) })
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ToolbarLayoutPreview(display)
                            Text(stringResource(display.label), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                            Spacer(Modifier.weight(1f))
                            RadioButton(selected = isSelected, onClick = null)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonString.dialog_cancel)) }
        },
    )
}

/** Decorative examples share the editor's icons and colours; only the enclosing choice is interactive. */
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
            .fillMaxWidth()
            .height(72.dp)
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
                            row.forEach { PreviewIcon(it.icon, Modifier.weight(1f)) }
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        icons.take(3).forEach { PreviewIcon(it.icon, Modifier.weight(1f)) }
                        Icon(
                            painterResource(CommonR.drawable.ic_baseline_chevron_right_24),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = colors.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewIcon(
    icon: Int,
    modifier: Modifier = Modifier,
) {
    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = modifier.size(20.dp).padding(2.dp),
        tint = editorControlColor(),
    )
}
