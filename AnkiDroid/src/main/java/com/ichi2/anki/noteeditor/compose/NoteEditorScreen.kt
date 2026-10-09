// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.multimedia.MultimediaActionHandler
import com.ichi2.anki.noteeditor.web.WebEditorAction

/** Native controls surround one web field editor, with a preview alongside it on tablets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    state: EditorState?,
    toolbarState: EditorToolbarState,
    ready: Boolean,
    busy: Boolean,
    error: String?,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    loadDecks: suspend () -> List<EditorChoice>,
    loadNoteTypes: suspend () -> List<EditorChoice>,
    onDeck: (Long) -> Unit,
    onType: (Long) -> Unit,
    onEditType: () -> Unit,
    onTags: () -> Unit,
    onSticky: (Int) -> Unit,
    onAction: (WebEditorAction) -> Unit,
    onMedia: (MultimediaActionHandler) -> Unit,
    onLegacy: () -> Unit,
    onPreviewVisibility: (Boolean) -> Unit,
    editor: @Composable (Modifier) -> Unit,
    preview: @Composable (Modifier) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var showPreview by rememberSaveable { mutableStateOf(true) }
    val enabled = ready && !busy && state?.isSaving == false
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val hasTabletWidth by remember(windowInfo, density) {
        derivedStateOf { with(density) { windowInfo.containerSize.width.toDp() } >= 600.dp }
    }
    Box(Modifier.fillMaxSize()) {
        val tablet = LocalConfiguration.current.smallestScreenWidthDp >= 600 && hasTabletWidth
        LaunchedEffect(tablet, showPreview) { onPreviewVisibility(tablet && showPreview) }
        Scaffold(
            modifier = Modifier.imePadding(),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(
                                if (state?.isAdding !=
                                    false
                                ) {
                                    CommonString.menu_add_note
                                } else {
                                    CommonString.cardeditor_title_edit_card
                                },
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, enabled = !busy) {
                            Icon(
                                painterResource(R.drawable.ic_baseline_arrow_back_24),
                                stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                            )
                        }
                    },
                    actions = {
                        if (tablet) {
                            TextButton(onClick = { showPreview = !showPreview }) {
                                Text(
                                    stringResource(
                                        if (showPreview) {
                                            CommonString.note_editor_hide_previewer
                                        } else {
                                            CommonString.note_editor_show_previewer
                                        },
                                    ),
                                )
                            }
                        }
                        TextButton(onClick = onSave, enabled = enabled) { Text(stringResource(CommonString.save)) }
                    },
                )
            },
        ) { padding ->
            if (state == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    if (error == null) {
                        CircularProgressIndicator()
                    } else {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onLegacy) { Text(stringResource(R.string.compose_editor_legacy)) }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxSize().padding(padding)) {
                    Column(Modifier.weight(1f)) {
                        EditorMetadata(
                            deckName = state.deckName,
                            noteTypeName = state.noteTypeName,
                            tags = state.tags.joinToString(" "),
                            enabled = enabled,
                            onDeck = { dialog = "deck" },
                            onType = {
                                if (state.isAdding) dialog = "type" else onEditType()
                            },
                            onTags = onTags,
                        )
                        HorizontalDivider()
                        EditorFields(
                            ready = ready,
                            busy = busy,
                            error = error,
                            onLegacy = onLegacy,
                            editor = editor,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        HorizontalDivider()
                        EditorToolbar(
                            isAdding = state.isAdding,
                            isCloze = state.isCloze,
                            enabled = enabled,
                            state = toolbarState,
                            onAction = onAction,
                            onMedia = { dialog = "media" },
                            onSticky = { dialog = "sticky" },
                        )
                    }
                    if (tablet && showPreview) {
                        VerticalDivider()
                        preview(Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
    }
    if (state != null && enabled) {
        when (dialog) {
            "deck", "type" -> {
                val deck = dialog == "deck"
                var choices by remember(dialog) { mutableStateOf<List<EditorChoice>?>(null) }
                LaunchedEffect(dialog) { choices = if (deck) loadDecks() else loadNoteTypes() }
                ChoiceDialog(
                    title = stringResource(if (deck) R.string.compose_editor_deck else R.string.compose_editor_type),
                    choices = choices,
                    onDismiss = { dialog = null },
                    onSelect = {
                        dialog = null
                        if (deck) onDeck(it) else onType(it)
                    },
                )
            }
            "media" ->
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(stringResource(R.string.compose_editor_media)) },
                    text = {
                        Column {
                            listOf(
                                R.string.compose_editor_image to MultimediaActionHandler.ImageFile,
                                R.string.compose_editor_camera to MultimediaActionHandler.Camera,
                                CommonString.multimedia_editor_popup_audio to MultimediaActionHandler.AudioRecording,
                                R.string.compose_editor_audio to MultimediaActionHandler.AudioFile,
                                R.string.compose_editor_video to MultimediaActionHandler.VideoFile,
                            ).forEach { (label, handler) ->
                                TextButton(onClick = {
                                    dialog = null
                                    onMedia(handler)
                                }) { Text(stringResource(label)) }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(CommonString.dialog_cancel)) } },
                )
            "sticky" ->
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(stringResource(R.string.compose_editor_sticky)) },
                    text = {
                        LazyColumn(Modifier.heightIn(max = 400.dp)) {
                            items(state.fields.size) { index ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = state.fields[index].sticky, onCheckedChange = { onSticky(index) })
                                    Text(state.fields[index].name)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(CommonString.dialog_ok)) } },
                )
        }
    }
}

@Composable
private fun EditorMetadata(
    deckName: String,
    noteTypeName: String,
    tags: String,
    enabled: Boolean,
    onDeck: () -> Unit,
    onType: () -> Unit,
    onTags: () -> Unit,
) {
    Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetadataButton(stringResource(R.string.compose_editor_deck), deckName, enabled, onDeck, Modifier.weight(1f))
        MetadataButton(stringResource(R.string.compose_editor_type), noteTypeName, enabled, onType, Modifier.weight(1f))
    }
    TextButton(onClick = onTags, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(CommonString.card_details_tags) + ": " + tags.ifEmpty { stringResource(R.string.compose_editor_no_tags) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EditorFields(
    ready: Boolean,
    busy: Boolean,
    error: String?,
    onLegacy: () -> Unit,
    editor: @Composable (Modifier) -> Unit,
    modifier: Modifier,
) {
    Box(modifier) {
        editor(Modifier.fillMaxSize())
        if (!ready || busy) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (!ready && error != null) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onLegacy) { Text(stringResource(R.string.compose_editor_legacy)) }
                    }
                } else {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun EditorToolbar(
    isAdding: Boolean,
    isCloze: Boolean,
    enabled: Boolean,
    state: EditorToolbarState,
    onAction: (WebEditorAction) -> Unit,
    onMedia: () -> Unit,
    onSticky: () -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        val formatEnabled = enabled && state.hasSelection && !state.composing
        EditorActionButton(R.drawable.ic_format_bold_black_24dp, R.string.compose_editor_bold, formatEnabled) {
            onAction(WebEditorAction.BOLD)
        }
        EditorActionButton(R.drawable.ic_format_italic_black_24dp, R.string.compose_editor_italic, formatEnabled) {
            onAction(WebEditorAction.ITALIC)
        }
        EditorActionButton(R.drawable.ic_format_underlined_black_24dp, R.string.compose_editor_underline, formatEnabled) {
            onAction(WebEditorAction.UNDERLINE)
        }
        if (isCloze) {
            EditorActionButton(R.drawable.ic_cloze_new_card, R.string.compose_editor_cloze_new, formatEnabled) {
                onAction(WebEditorAction.CLOZE_NEW)
            }
            EditorActionButton(R.drawable.ic_cloze_same_card, R.string.compose_editor_cloze_same, formatEnabled) {
                onAction(WebEditorAction.CLOZE_SAME)
            }
        }
        EditorActionButton(R.drawable.ic_undo_2, CommonString.undo, formatEnabled) { onAction(WebEditorAction.UNDO) }
        EditorActionButton(R.drawable.ic_redo_2, CommonString.redo, formatEnabled) { onAction(WebEditorAction.REDO) }
        EditorActionButton(R.drawable.ic_code, R.string.compose_editor_source, enabled && state.hasSelection) {
            onAction(WebEditorAction.SOURCE_MODE)
        }
        EditorActionButton(R.drawable.ic_attachment, R.string.compose_editor_media, formatEnabled, onMedia)
        if (isAdding) {
            TextButton(onClick = onSticky, enabled = enabled) { Text(stringResource(R.string.compose_editor_sticky)) }
        }
    }
}

@Composable
private fun MetadataButton(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EditorActionButton(
    icon: Int,
    label: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(painterResource(icon), stringResource(label)) }
}

@Composable
private fun ChoiceDialog(
    title: String,
    choices: List<EditorChoice>?,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (choices == null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(choices, key = { it.id }) { choice ->
                        TextButton(onClick = { onSelect(choice.id) }, modifier = Modifier.fillMaxWidth()) { Text(choice.name) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CommonString.dialog_cancel)) } },
    )
}
