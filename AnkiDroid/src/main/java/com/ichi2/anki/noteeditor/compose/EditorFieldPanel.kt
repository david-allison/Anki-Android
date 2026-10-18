// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import kotlinx.coroutines.launch

internal val LocalEditorFieldPageActive = staticCompositionLocalOf { true }

internal enum class EditorFieldPanelSection { OPTIONS, TOOLBAR, QUICK_ACTIONS }

/** Paging only changes the settings target. It never focuses or initializes a web field. */
@Composable
internal fun EditorFieldPanel(
    state: EditorState,
    availableHeight: Dp,
    draft: EditorFieldPanelDraft,
    section: EditorFieldPanelSection,
    enabled: Boolean,
    onChange: (EditorFieldPanelDraft) -> Unit,
    onSectionChange: (EditorFieldPanelSection) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onCustomButtons: () -> Unit,
    options: @Composable (EditorFieldTarget) -> Unit,
) {
    val pager = rememberPagerState(initialPage = draft.target.field) { state.fields.size }
    val scope = rememberCoroutineScope()
    var draggingPage by remember { mutableStateOf<Int?>(null) }
    val field = state.fields[pager.currentPage]
    val pending = section != EditorFieldPanelSection.OPTIONS
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = minOf(availableHeight, if (pending) 680.dp else 560.dp))) {
        val compact = maxHeight / LocalDensity.current.fontScale < 300.dp
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = if (pending) 8.dp else 24.dp, end = 8.dp, top = if (compact) 4.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pending) {
                    IconButton(onClick = { onSectionChange(EditorFieldPanelSection.OPTIONS) }, colors = editorIconButtonColors()) {
                        Icon(painterResource(R.drawable.ic_baseline_arrow_back_24), stringResource(R.string.compose_editor_field_options))
                    }
                }
                Column(Modifier.weight(1f)) {
                    // At large text sizes, preserve room for a complete control above the IME.
                    if (pending && !compact) {
                        Text(
                            stringResource(
                                if (section ==
                                    EditorFieldPanelSection.TOOLBAR
                                ) {
                                    R.string.compose_editor_toolbar
                                } else {
                                    R.string.compose_editor_quick_actions
                                },
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val title = stringResource(R.string.compose_editor_field_options_title, field.name)
                    Text(
                        field.name,
                        Modifier.semantics {
                            heading()
                            contentDescription = title
                        },
                        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!pending) FieldOptionsHelp(field.name, state.noteTypeName, editorControlColor())
                if (pending || draft.hasChanges) EditorSaveButton(onClick = onSave, enabled = enabled && !pager.isScrollInProgress)
                IconButton(onClick = onDismiss, colors = editorIconButtonColors()) {
                    Icon(painterResource(R.drawable.ic_clear_white), stringResource(R.string.compose_editor_close_field_options))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    state.noteTypeName,
                    Modifier.weight(1f).padding(vertical = if (compact) 0.dp else 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!pending && state.fields.size > 1) {
                    IconButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                        enabled = pager.currentPage > 0 && !pager.isScrollInProgress && draggingPage == null,
                        colors = editorIconButtonColors(),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_baseline_chevron_left_24),
                            stringResource(R.string.compose_editor_previous_field),
                        )
                    }
                }
                if (!pending && state.fields.size > 1) {
                    Text(
                        stringResource(R.string.compose_editor_field_position, pager.currentPage + 1, state.fields.size),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    IconButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        enabled = pager.currentPage < state.fields.lastIndex && !pager.isScrollInProgress && draggingPage == null,
                        colors = editorIconButtonColors(),
                    ) {
                        Icon(painterResource(R.drawable.ic_baseline_chevron_right_24), stringResource(R.string.compose_editor_next_field))
                    }
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f, fill = pending).testTag("editor_field_pages"),
                userScrollEnabled = !pending && draggingPage == null,
                verticalAlignment = Alignment.Top,
            ) { index ->
                val pageField = state.fields[index]
                // Pager retains neighbouring pages. Hide their controls from TalkBack until current.
                CompositionLocalProvider(LocalEditorFieldPageActive provides (index == pager.currentPage)) {
                    Box(if (index == pager.currentPage) Modifier else Modifier.clearAndSetSemantics {}) {
                        when (section) {
                            EditorFieldPanelSection.OPTIONS -> options(draft.target.copy(field = index))
                            EditorFieldPanelSection.TOOLBAR ->
                                EditorCustomizeToolbarContent(
                                    pending = draft.toolbar(index, pageField),
                                    onChange = { onChange(draft.withToolbar(index, pageField, it)) },
                                    templates = state.customButtonTemplates,
                                    field = pageField,
                                    noteTypeName = state.noteTypeName,
                                    onSave = { onSave() },
                                    onCustomButtons = onCustomButtons,
                                    onDismiss = onDismiss,
                                    enabled = enabled,
                                    toolbarHiddenGlobally = state.toolbarDisplay == EditorToolbarDisplay.HIDDEN,
                                    topBar = {},
                                    embedded = true,
                                    scrollCustomButtons = compact,
                                    onDraggingChange = { dragging ->
                                        if (dragging) {
                                            draggingPage = index
                                        } else if (draggingPage == index) {
                                            draggingPage = null
                                        }
                                    },
                                )
                            EditorFieldPanelSection.QUICK_ACTIONS ->
                                EditorQuickActionsContent(
                                    toolbar = pageField.toolbar.copy(quickActions = draft.settings(index, pageField).quickActions),
                                    onChange = { onChange(draft.withQuickActions(index, pageField, it.quickActions)) },
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    onDraggingChange = { dragging ->
                                        if (dragging) {
                                            draggingPage = index
                                        } else if (draggingPage == index) {
                                            draggingPage = null
                                        }
                                    },
                                )
                        }
                    }
                }
            }
        }
    }
}
