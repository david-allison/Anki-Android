// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Match the buttons' measured widths in both scrolling and stacked toolbars. */
@Composable
internal fun rememberEditorToolbarButtonExtents(items: List<EditorToolbarItem>): List<ToolbarButtonExtent> {
    val density = LocalDensity.current
    // The usual icon-only toolbar does not need text measurement or offscreen button composition.
    val textMeasurer = if (items.any { it is EditorToolbarItem.Custom }) rememberTextMeasurer() else null
    val textStyle = MaterialTheme.typography.labelLarge
    return remember(items, density, textMeasurer, textStyle) {
        with(density) {
            items.map { item ->
                when (item) {
                    is EditorToolbarItem.Builtin ->
                        if (item.button == EditorToolbarButton.SOURCE_MODE) {
                            ToolbarButtonExtent(56.dp.roundToPx(), 52.dp.roundToPx())
                        } else {
                            ToolbarButtonExtent(48.dp.roundToPx(), 24.dp.roundToPx())
                        }
                    is EditorToolbarItem.Custom -> {
                        val textWidth =
                            requireNotNull(textMeasurer)
                                .measure(
                                    item.button.label,
                                    style = textStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    constraints = Constraints(maxWidth = 60.dp.roundToPx()),
                                ).size.width
                        val padding = ButtonDefaults.TextButtonContentPadding
                        val horizontalPadding =
                            padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) +
                                padding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                        ToolbarButtonExtent(
                            (textWidth + horizontalPadding.roundToPx()).coerceAtLeast(48.dp.roundToPx()),
                            textWidth,
                        )
                    }
                }
            }
        }
    }
}

/** Keep a partial button visible as the scroll cue, without changing touch targets or snapping. */
@Composable
internal fun EditorScrollingToolbar(
    items: List<EditorToolbarItem>,
    state: LazyListState,
    modifier: Modifier = Modifier,
    button: @Composable (EditorToolbarItem) -> Unit,
) {
    val density = LocalDensity.current
    val metrics = rememberEditorToolbarButtonExtents(items)
    BoxWithConstraints(modifier) {
        // Resolve spacing before the first layout: no second-frame correction, or changes while scrolling.
        val gap =
            remember(metrics, constraints.maxWidth, density) {
                toolbarPeekSpacing(metrics, constraints.maxWidth, with(density) { 8.dp.roundToPx() })
            }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = state,
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(with(density) { gap.toDp() }),
        ) {
            items(items, key = { it.key }) { button(it) }
        }
    }
}

/** Widths in physical pixels, including the visual content centered inside its touch target. */
internal data class ToolbarButtonExtent(
    val width: Int,
    val contentWidth: Int,
)

internal fun toolbarPeekSpacing(
    buttons: List<ToolbarButtonExtent>,
    viewport: Int,
    padding: Int,
): Int {
    if (buttons.sumOf { it.width } + padding * 2 <= viewport) return 0
    var start = padding
    val contentBounds =
        buttons.map {
            val left = start + (it.width - it.contentWidth) / 2f
            start += it.width
            left to left + it.contentWidth
        }
    // Preserve existing spacing wherever an icon, mode switch or custom label already peeks out.
    if (contentBounds.any { (left, right) -> left < viewport && right > viewport }) return 0
    for (index in contentBounds.indices.reversed()) {
        if (index == 0) break
        val (left, right) = contentBounds[index]
        if (left >= viewport || right <= left) continue
        val minGap = ceil((viewport + 1 - right) / index).toInt().coerceAtLeast(1)
        val maxGap = floor((viewport - 1 - left) / index).toInt()
        if (minGap <= maxGap) {
            // A wide custom label only needs a small clipped portion, rather than half its width.
            val clipped = minOf((right - left) / 2, padding.toFloat())
            return ((viewport - right + clipped) / index).roundToInt().coerceIn(minGap, maxGap)
        }
    }
    return 0
}
