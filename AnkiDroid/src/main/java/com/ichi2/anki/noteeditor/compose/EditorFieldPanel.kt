// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.background
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

internal val LocalEditorFieldPageActive = staticCompositionLocalOf { true }

internal enum class EditorFieldPanelSection { OPTIONS, TOOLBAR, QUICK_ACTIONS }

/** Keep settings and editor focus on the same field, without resetting focus when opening the panel. */
@Composable
internal fun EditorFieldPanel(
    state: EditorState,
    availableHeight: Dp,
    draft: EditorFieldPanelDraft,
    section: EditorFieldPanelSection,
    enabled: Boolean,
    onChange: (EditorFieldPanelDraft) -> Unit,
    onSectionChange: (EditorFieldPanelSection) -> Unit,
    onFieldSelected: (EditorFieldTarget) -> Unit,
    onQuickActions: (EditorFieldTarget, List<String>) -> Unit,
    onSave: () -> Unit,
    onCustomButtons: () -> Unit,
    options: @Composable (EditorFieldTarget) -> Unit,
) {
    val pager = rememberPagerState(initialPage = draft.target.field) { state.fields.size }
    val scope = rememberCoroutineScope()
    val selectField by rememberUpdatedState(onFieldSelected)
    var showFieldPicker by rememberSaveable { mutableStateOf(false) }
    var draggingPage by remember { mutableStateOf<Int?>(null) }
    // Page animations must not lock navigation; another selection redirects them.
    val canNavigate = enabled && draggingPage == null
    val field = state.fields[pager.currentPage]
    val pending = section != EditorFieldPanelSection.OPTIONS
    LaunchedEffect(pager) {
        // Wait for swipes to settle; passing through a page must not focus its web field.
        snapshotFlow { pager.settledPage }.drop(1).collect { selectField(draft.target.copy(field = it)) }
    }
    if (showFieldPicker && !pending) {
        EditorFieldPickerDialog(
            fields = state.fields,
            selected = pager.currentPage,
            onSelect = { index ->
                showFieldPicker = false
                scope.launch { pager.scrollToPage(index) }
            },
            onDismiss = { showFieldPicker = false },
        )
    }
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = minOf(availableHeight, if (pending) availableHeight else 560.dp))) {
        val compact = maxHeight / LocalDensity.current.fontScale < 300.dp
        Column(if (pending) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
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
                    val fieldTitle = stringResource(R.string.compose_editor_field_options_title, field.name)
                    if (pending) {
                        Text(
                            stringResource(
                                if (section == EditorFieldPanelSection.TOOLBAR) {
                                    R.string.compose_editor_toolbar
                                } else {
                                    R.string.compose_editor_quick_actions
                                },
                            ),
                            Modifier.semantics { heading() },
                            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            maxLines = if (compact) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            field.name,
                            Modifier.semantics { contentDescription = fieldTitle },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            field.name,
                            Modifier.semantics {
                                heading()
                                contentDescription = fieldTitle
                            },
                            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = if (compact) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!pending) FieldOptionsHelp(field.name, state.noteTypeName, editorControlColor())
                if (section == EditorFieldPanelSection.TOOLBAR) {
                    EditorSaveButton(onClick = onSave, enabled = enabled && !pager.isScrollInProgress)
                }
            }
            if (!pending && state.fields.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.targetPage - 1) } },
                        enabled = canNavigate && pager.targetPage > 0,
                        colors = editorIconButtonColors(),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_baseline_chevron_left_24),
                            stringResource(R.string.compose_editor_previous_field),
                        )
                    }
                    val chooseField = stringResource(R.string.compose_editor_choose_field)
                    TextButton(
                        onClick = {
                            val page = pager.currentPage
                            scope.launch {
                                // Keep the displayed field selected while opening the picker mid-swipe.
                                pager.scrollToPage(page)
                                showFieldPicker = true
                            }
                        },
                        enabled = canNavigate,
                        modifier = Modifier.semantics { contentDescription = chooseField },
                    ) {
                        Text(
                            stringResource(R.string.compose_editor_field_position, pager.currentPage + 1, state.fields.size),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    IconButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.targetPage + 1) } },
                        enabled = canNavigate && pager.targetPage < state.fields.lastIndex,
                        colors = editorIconButtonColors(),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_baseline_chevron_right_24),
                            stringResource(R.string.compose_editor_next_field),
                        )
                    }
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f, fill = pending).testTag("editor_field_pages"),
                userScrollEnabled = enabled && !pending && draggingPage == null,
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
                                    toolbar = pageField.toolbar,
                                    onChange = { onQuickActions(draft.target.copy(field = index), it.quickActions) },
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(horizontal = 16.dp),
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
