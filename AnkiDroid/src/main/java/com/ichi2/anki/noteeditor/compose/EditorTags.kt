// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.android.R as CommonR

/** Native note tags. Collection-wide choices are loaded only when the user opens the dialog. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorTags(
    tags: List<String>,
    enabled: Boolean,
    tablet: Boolean,
    onManage: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val keyboardVisible = WindowInsets.isImeVisible
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val window = LocalWindowInfo.current
    // Observe a size category, not each pixel of the keyboard animation.
    val enoughHeight by remember(window, density, ime) {
        derivedStateOf { window.containerSize.height - ime.getBottom(density) >= with(density) { 480.dp.toPx() } }
    }
    if (keyboardVisible && (!tablet || !enoughHeight)) return

    if (tags.isEmpty()) {
        TextButton(
            onClick = onManage,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RectangleShape,
            contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_tag),
                contentDescription = null,
                modifier = Modifier.size(width = 32.dp, height = 20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.compose_editor_no_tags))
            Spacer(Modifier.weight(1f))
        }
        return
    }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onManage)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_tag),
            contentDescription = null,
            modifier = Modifier.size(width = 32.dp, height = 20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(CommonString.card_details_tags), style = MaterialTheme.typography.labelMedium)
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tags, key = { it }) { tag ->
                EditorTooltip(tag) {
                    InputChip(
                        selected = false,
                        enabled = enabled,
                        onClick = onManage,
                        label = {
                            // Material's chip layout top-aligns the label beside a taller trailing icon.
                            Box(Modifier.heightIn(min = 32.dp), contentAlignment = Alignment.Center) {
                                Text(tag, Modifier.widthIn(max = 160.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        trailingIcon = {
                            val removeLabel = stringResource(R.string.compose_editor_remove_tag, tag)
                            IconButton(onClick = { onRemove(tag) }, enabled = enabled, modifier = Modifier.size(32.dp)) {
                                Icon(painterResource(R.drawable.close_icon), removeLabel, Modifier.size(18.dp))
                            }
                        },
                    )
                }
            }
        }
        EditorActionButton(CommonR.drawable.ic_add, R.string.compose_editor_no_tags, enabled, onManage)
    }
}
