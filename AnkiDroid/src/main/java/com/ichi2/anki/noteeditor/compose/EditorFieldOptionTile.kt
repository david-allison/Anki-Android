// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R

/** A settings link or toggle with the same layout and an accessible checked state. */
@Composable
internal fun EditorFieldOptionTile(
    option: EditorFieldOption,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    summary: String? = null,
    checked: Boolean? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(option.label)
    val description = if (option == EditorFieldOption.KEYBOARD) stringResource(R.string.compose_editor_language_hint) else label
    val background = if (checked == true) colors.primaryContainer else colors.surface
    val foreground = if (checked == true) colors.onPrimaryContainer else colors.onSurface
    val alpha = if (enabled) 1f else 0.38f
    val controlColor = editorControlColor().let { it.copy(alpha = it.alpha * alpha) }
    val content: @Composable () -> Unit = {
        Row(
            Modifier.heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(option.icon), null, Modifier.size(24.dp), tint = controlColor)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                if (summary != null) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant.copy(alpha = alpha),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            // Keep text in place when toggling. The tick conveys state without relying on color.
            when (checked) {
                true -> Icon(painterResource(R.drawable.ic_done), null, Modifier.size(16.dp))
                false -> Spacer(Modifier.size(16.dp))
                null -> Icon(painterResource(R.drawable.ic_baseline_chevron_right_24), null, Modifier.size(16.dp), tint = controlColor)
            }
        }
    }
    EditorTooltip(stringResource(option.help), modifier) {
        val tile = Modifier.fillMaxSize().testTag("editor_field_option_${option.name}").semantics { contentDescription = description }
        if (checked == null) {
            Surface(
                onClick = onClick,
                enabled = enabled,
                modifier = tile,
                shape = MaterialTheme.shapes.medium,
                color = background,
                contentColor = foreground.copy(alpha = alpha),
                content = content,
            )
        } else {
            Surface(
                checked = checked,
                onCheckedChange = { onClick() },
                enabled = enabled,
                modifier = tile,
                shape = MaterialTheme.shapes.medium,
                color = background,
                contentColor = foreground.copy(alpha = alpha),
                content = content,
            )
        }
    }
}
