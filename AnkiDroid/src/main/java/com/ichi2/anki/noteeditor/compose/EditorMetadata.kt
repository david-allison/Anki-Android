// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R

/** Show the collection's deck path syntax with subdued separators. */
@Composable
internal fun EditorMetadata(
    deckName: String,
    noteTypeName: String,
    enabled: Boolean,
    onDeck: () -> Unit,
    onType: () -> Unit,
) {
    val separatorColor = MaterialTheme.colorScheme.outline
    val deckLabel =
        remember(deckName, separatorColor) {
            buildAnnotatedString {
                deckName.split("::").forEachIndexed { index, name ->
                    if (index > 0) {
                        withStyle(SpanStyle(color = separatorColor)) { append("::") }
                    }
                    append(name)
                }
            }
        }
    val deck: @Composable (Modifier) -> Unit = { modifier ->
        MetadataRow(stringResource(R.string.compose_editor_deck), deckLabel, enabled, onDeck, modifier)
    }
    val type: @Composable (Modifier) -> Unit = { modifier ->
        MetadataRow(stringResource(R.string.compose_editor_type), AnnotatedString(noteTypeName), enabled, onType, modifier)
    }
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
    ) {
        // The preview may leave a tablet's editor pane narrower than a phone landscape window.
        // Match reminder troubleshooting: 16dp outer corners, 4dp inner corners and a 2dp gap.
        if (maxWidth >= 600.dp) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                deck(
                    Modifier.weight(1f).fillMaxHeight().clip(
                        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 4.dp, bottomStart = 16.dp),
                    ),
                )
                type(
                    Modifier.weight(1f).fillMaxHeight().clip(
                        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp),
                    ),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                deck(
                    Modifier.fillMaxWidth().clip(
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 4.dp),
                    ),
                )
                type(
                    Modifier.fillMaxWidth().clip(
                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                    ),
                )
            }
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: AnnotatedString,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainer.compositeOver(MaterialTheme.colorScheme.surface))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (enabled) 1f else 0.38f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(painterResource(R.drawable.ic_expand_more_black), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
