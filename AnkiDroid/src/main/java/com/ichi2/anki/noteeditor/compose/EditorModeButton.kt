// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R

/** Keep both destinations visible and the touch target stable while a field changes mode. */
@Composable
internal fun EditorModeButton(
    sourceMode: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val action = stringResource(if (sourceMode) R.string.compose_editor_rich_text else R.string.compose_editor_source)
    val mode = stringResource(if (sourceMode) R.string.compose_editor_mode_html else R.string.compose_editor_mode_rich_text)
    val colors = MaterialTheme.colorScheme
    val selectionOffset by animateDpAsState(
        targetValue = if (sourceMode) 2.dp else 22.dp,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "Editing mode",
    )
    EditorTooltip(action) {
        IconToggleButton(
            checked = !sourceMode,
            onCheckedChange = { onClick() },
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            modifier =
                Modifier.size(48.dp).semantics {
                    role = Role.Switch
                    contentDescription = action
                    stateDescription = mode
                },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    Modifier
                        .requiredSize(44.dp, 28.dp)
                        .alpha(if (enabled) 1f else 0.38f)
                        .background(colors.surfaceContainerHighest, RoundedCornerShape(8.dp)),
                ) {
                    Box(
                        Modifier
                            .offset(x = selectionOffset, y = 2.dp)
                            .size(20.dp, 24.dp)
                            .background(colors.primaryContainer, RoundedCornerShape(6.dp)),
                    )
                    Row(Modifier.align(Alignment.Center).padding(2.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_code),
                            null,
                            Modifier.size(20.dp),
                            tint = if (sourceMode) colors.onPrimaryContainer else colors.onSurface.copy(alpha = 0.65f),
                        )
                        Icon(
                            painterResource(R.drawable.ic_editor_rich_text),
                            null,
                            Modifier.size(20.dp),
                            tint = if (sourceMode) colors.onSurface.copy(alpha = 0.65f) else colors.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}
