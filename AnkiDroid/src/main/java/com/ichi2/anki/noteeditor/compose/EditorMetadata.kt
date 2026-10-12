// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.libanki.Decks

/** Keep the selected deck visible even when its parent path is too long to fit. */
@Composable
internal fun EditorMetadata(
    deckName: String,
    noteTypeName: String,
    enabled: Boolean,
    onDeck: () -> Unit,
    onType: () -> Unit,
) {
    val deckPath = remember(deckName) { Decks.path(deckName) }
    val parentPath = remember(deckPath) { deckPath.dropLast(1).joinToString(" › ") }
    val deck: @Composable (Modifier) -> Unit = { modifier ->
        MetadataRow(stringResource(R.string.compose_editor_deck), deckPath.last(), parentPath, enabled, onDeck, modifier)
    }
    val type: @Composable (Modifier) -> Unit = { modifier ->
        MetadataRow(stringResource(R.string.compose_editor_type), noteTypeName, "", enabled, onType, modifier)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer.compositeOver(MaterialTheme.colorScheme.surface),
    ) {
        // The preview may leave a tablet's editor pane narrower than a phone landscape window.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 600.dp) {
                Row {
                    deck(Modifier.weight(1f))
                    type(Modifier.weight(1f))
                }
            } else {
                Column {
                    deck(Modifier.fillMaxWidth())
                    type(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
    parentPath: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (enabled) 1f else 0.38f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (parentPath.isNotEmpty()) {
                Text(
                    parentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(painterResource(R.drawable.ic_expand_more_black), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
