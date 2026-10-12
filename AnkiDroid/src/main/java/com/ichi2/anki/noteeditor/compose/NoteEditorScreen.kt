// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.multimedia.MultimediaActionHandler
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.anki.noteeditor.web.WebEditorSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** Native controls surround one web field editor, with a preview alongside it on tablets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    state: EditorState?,
    toolbarState: State<EditorToolbarState>,
    ready: Boolean,
    busy: Boolean,
    error: String?,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    loadNoteTypes: suspend () -> List<EditorChoice>,
    onDeck: () -> Unit,
    onType: (Long) -> Unit,
    onEditType: () -> Unit,
    onTags: () -> Unit,
    onRemoveTag: (String) -> Unit,
    onPinned: (EditorFieldTarget, Boolean) -> Unit,
    onLanguageHint: (EditorFieldTarget) -> Unit,
    onAction: (WebEditorAction) -> Unit,
    onMedia: (MultimediaActionHandler) -> Unit,
    onLegacy: () -> Unit,
    onPreviewVisibility: (Boolean) -> Unit,
    editor: @Composable (Modifier) -> Unit,
    preview: @Composable (Modifier) -> Unit,
    fieldTaps: Flow<Unit> = emptyFlow(),
    onSettings: (WebEditorSettings) -> Unit = {},
    onFields: () -> Unit = {},
    onCards: () -> Unit = {},
    onToolbar: (EditorFieldTarget, EditorFieldToolbar) -> Unit = { _, _ -> },
    onCustomButtonTemplates: (List<EditorCustomToolbarButton>) -> Unit = {},
    onCustomAction: (EditorFieldTarget, EditorCustomToolbarButton) -> Unit = { _, _ -> },
) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var quickActionsTarget by rememberSaveable { mutableStateOf<EditorFieldTarget?>(null) }
    var toolbarTarget by rememberSaveable { mutableStateOf<EditorFieldTarget?>(null) }
    var showCustomButtons by rememberSaveable { mutableStateOf(false) }
    var showPreview by rememberSaveable { mutableStateOf(true) }
    val enabled = ready && !busy && state?.isSaving == false
    toolbarTarget?.takeIf { it.matches(state) }?.let { target ->
        val current = requireNotNull(state)
        EditorToolbarDialog(
            target = target,
            field = current.fields[target.field],
            noteTypeName = current.noteTypeName,
            isCloze = current.isCloze,
            templates = current.customButtonTemplates,
            onDismiss = { toolbarTarget = null },
            onSave = {
                onToolbar(target, it)
                toolbarTarget = null
            },
        )
    }
    quickActionsTarget?.takeIf { it.matches(state) }?.let { target ->
        val current = requireNotNull(state)
        EditorQuickActionsDialog(
            target = target,
            field = current.fields[target.field],
            noteTypeName = current.noteTypeName,
            onDismiss = { quickActionsTarget = null },
            onSave = {
                onToolbar(target, it)
                quickActionsTarget = null
            },
        )
    }
    if (showCustomButtons && state != null) {
        EditorCustomButtonsDialog(
            templates = state.customButtonTemplates,
            onDismiss = { showCustomButtons = false },
            onSave = {
                onCustomButtonTemplates(it)
                showCustomButtons = false
            },
        )
    }
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val hasTabletWidth by remember(windowInfo, density) {
        derivedStateOf { with(density) { windowInfo.containerSize.width.toDp() } >= 600.dp }
    }
    Box(Modifier.fillMaxSize()) {
        val tablet = LocalConfiguration.current.smallestScreenWidthDp >= 600 && hasTabletWidth
        LaunchedEffect(tablet, showPreview) { onPreviewVisibility(tablet && showPreview) }
        Scaffold(
            snackbarHost = { if (state == null) EditorSnackbarHost(snackbar, Modifier.imePadding()) },
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
                        state?.let {
                            EditorOptionsMenu(it.settings, enabled && !toolbarState.value.composing, onSettings, onFields, onCards)
                        }
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
                        TextButton(
                            onClick = onSave,
                            enabled = enabled && state.removedFields.isEmpty(),
                        ) { Text(stringResource(CommonString.save)) }
                    },
                )
            },
        ) { padding ->
            if (state == null) {
                if (error != null) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onLegacy) { Text(stringResource(R.string.compose_editor_legacy)) }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    Column(Modifier.weight(1f)) {
                        EditorMetadata(
                            deckName = state.deckName,
                            noteTypeName = state.noteTypeName,
                            enabled = enabled,
                            onDeck = onDeck,
                            onType = {
                                if (state.isAdding) dialog = "type" else onEditType()
                            },
                        )
                        HorizontalDivider()
                        // Only the field area and toolbar follow the keyboard. Keep the
                        // metadata and top bar out of its changing layout constraints.
                        EditorFieldArea(
                            editorState = state,
                            snackbar = snackbar,
                            tablet = tablet,
                            enabled = enabled,
                            state = toolbarState,
                            fieldTaps = fieldTaps,
                            onAction = onAction,
                            onMedia = { dialog = "media" },
                            onRecordAudio = { onMedia(MultimediaActionHandler.AudioRecording) },
                            onPinned = onPinned,
                            onLanguageHint = onLanguageHint,
                            onToolbar = { toolbarTarget = it },
                            onQuickActions = { quickActionsTarget = it },
                            onCustomButtons = { showCustomButtons = true },
                            onCustomAction = onCustomAction,
                            onTags = onTags,
                            onRemoveTag = onRemoveTag,
                            modifier = Modifier.weight(1f).fillMaxWidth().imePadding(),
                        ) { modifier ->
                            EditorFields(
                                ready = ready,
                                busy = busy,
                                error = error,
                                onLegacy = onLegacy,
                                editor = editor,
                                modifier = modifier,
                            )
                        }
                    }
                    if (tablet && showPreview) {
                        VerticalDivider()
                        preview(Modifier.weight(1f).fillMaxSize().imePadding())
                    }
                }
            }
        }
    }
    if (state != null && enabled) {
        when (dialog) {
            "type" -> {
                var choices by remember { mutableStateOf<List<EditorChoice>?>(null) }
                LaunchedEffect(Unit) { choices = loadNoteTypes() }
                ChoiceDialog(
                    title = stringResource(R.string.compose_editor_type),
                    choices = choices,
                    onDismiss = { dialog = null },
                    onSelect = {
                        dialog = null
                        onType(it)
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
        }
    }
}

@Composable
private fun EditorMetadata(
    deckName: String,
    noteTypeName: String,
    enabled: Boolean,
    onDeck: () -> Unit,
    onType: () -> Unit,
) {
    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetadataButton(stringResource(R.string.compose_editor_deck), deckName, enabled, onDeck, Modifier.weight(1f))
        MetadataButton(stringResource(R.string.compose_editor_type), noteTypeName, enabled, onType, Modifier.weight(1f))
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
    Box(modifier, contentAlignment = Alignment.Center) {
        editor(Modifier.fillMaxSize())
        if (!ready && error != null) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onLegacy) { Text(stringResource(R.string.compose_editor_legacy)) }
            }
        } else if (busy) {
            CircularProgressIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorFieldArea(
    editorState: EditorState,
    snackbar: SnackbarHostState,
    tablet: Boolean,
    enabled: Boolean,
    state: State<EditorToolbarState>,
    fieldTaps: Flow<Unit>,
    onAction: (WebEditorAction) -> Unit,
    onMedia: () -> Unit,
    onRecordAudio: () -> Unit,
    onPinned: (EditorFieldTarget, Boolean) -> Unit,
    onLanguageHint: (EditorFieldTarget) -> Unit,
    onToolbar: (EditorFieldTarget) -> Unit,
    onQuickActions: (EditorFieldTarget) -> Unit,
    onCustomButtons: () -> Unit,
    onCustomAction: (EditorFieldTarget, EditorCustomToolbarButton) -> Unit,
    onTags: () -> Unit,
    onRemoveTag: (String) -> Unit,
    modifier: Modifier,
    fields: @Composable (Modifier) -> Unit,
) {
    val toolbarState by state
    val toolbarScrollState = rememberScrollState()
    val sheetState =
        key(editorState.sessionId, editorState.generation) {
            rememberModalBottomSheetState(skipPartiallyExpanded = true)
        }
    var fieldOptionsRequested by rememberSaveable(editorState.sessionId, editorState.generation) { mutableStateOf(false) }
    LaunchedEffect(fieldTaps, editorState.sessionId, editorState.generation) {
        fieldTaps.collect { fieldOptionsRequested = false }
    }
    val showFieldOptions = fieldOptionsRequested || sheetState.isVisible
    val target =
        toolbarState.activeField?.takeIf { it in editorState.fields.indices }?.let {
            EditorFieldTarget(editorState.sessionId, editorState.generation, it)
        }
    // IME animation moves the anchors every frame. Only observe whether expansion is available.
    val hasExpandedState by remember(sheetState) { derivedStateOf { sheetState.hasExpandedState } }
    // After recreation, the field data and sheet anchors become available after composition.
    LaunchedEffect(sheetState, fieldOptionsRequested, hasExpandedState) {
        if (fieldOptionsRequested && hasExpandedState) sheetState.expand() else sheetState.hide()
    }
    val closeOptions: () -> Unit = { fieldOptionsRequested = false }
    BackHandler(enabled = showFieldOptions, onBack = closeOptions)
    // The standard sheet shares the editor window, so opening it leaves the IME connected.
    BottomSheetScaffold(
        // A hidden sheet sits below this area; prevent it drawing into the navigation-bar inset.
        modifier = modifier.clipToBounds(),
        scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState),
        snackbarHost = { if (showFieldOptions) EditorSnackbarHost(snackbar) },
        sheetPeekHeight = 0.dp,
        sheetDragHandle = null,
        sheetSwipeEnabled = false,
        sheetContent = {
            // Retain the controls through dismissal, then remove their layout and semantics work.
            if (showFieldOptions && target != null) {
                EditorFieldOptions(
                    field = editorState.fields[target.field],
                    noteTypeName = editorState.noteTypeName,
                    enabled = enabled,
                    onPinned = { onPinned(target, it) },
                    onLanguageHint = { onLanguageHint(target) },
                    onToolbar = { onToolbar(target) },
                    onQuickActions = { onQuickActions(target) },
                    onCustomButtons = onCustomButtons,
                    onCollapse = closeOptions,
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            // Overlay feedback above the footer without resizing or obscuring the tags and toolbar.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                fields(Modifier.fillMaxSize())
                if (!showFieldOptions) EditorSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
            if (!showFieldOptions) {
                HorizontalDivider()
                EditorTags(editorState.tags, enabled, tablet, onTags, onRemoveTag)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).horizontalScroll(toolbarScrollState).padding(horizontal = 8.dp)) {
                        EditorFormattingButtons(
                            toolbar = target?.let { editorState.fields[it.field].toolbar } ?: EditorFieldToolbar(),
                            state = toolbarState,
                            enabled = enabled,
                            isCloze = editorState.isCloze,
                            onAction = onAction,
                            onCustomAction = { button -> target?.let { onCustomAction(it, button) } },
                            onMedia = onMedia,
                            onRecordAudio = onRecordAudio,
                        )
                    }
                    VerticalDivider(Modifier.padding(start = 2.dp).height(32.dp))
                    EditorActionButton(
                        R.drawable.ic_more_vertical,
                        R.string.compose_editor_field_options,
                        enabled && target != null,
                    ) { fieldOptionsRequested = true }
                }
            }
        }
    }
}

@Composable
private fun EditorFieldOptions(
    field: EditorFieldState,
    noteTypeName: String,
    enabled: Boolean,
    onPinned: (Boolean) -> Unit,
    onLanguageHint: () -> Unit,
    onToolbar: () -> Unit,
    onQuickActions: () -> Unit,
    onCustomButtons: () -> Unit,
    onCollapse: () -> Unit,
) {
    val fieldDescription = stringResource(R.string.compose_editor_field_options_title, field.name)
    val scopeDescription = stringResource(R.string.compose_editor_field_options_scope, noteTypeName)
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).clearAndSetSemantics {
                    contentDescription = "$fieldDescription. $scopeDescription"
                },
            ) {
                Text(field.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(noteTypeName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FieldOptionsHelp(field.name, noteTypeName)
            IconButton(onClick = onCollapse) {
                Icon(painterResource(R.drawable.ic_expand_more_black), stringResource(R.string.compose_editor_field_options))
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            EditorTooltip(
                text = stringResource(R.string.compose_editor_pin_field_summary),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Surface(
                    checked = field.sticky,
                    onCheckedChange = onPinned,
                    enabled = enabled,
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.medium,
                    color = if (field.sticky) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (field.sticky) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                ) {
                    Row(
                        Modifier.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_baseline_push_pin_24),
                            stringResource(R.string.compose_editor_pin_field),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.compose_editor_pin_field), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            VerticalDivider(Modifier.fillMaxHeight().padding(horizontal = 16.dp))
            EditorTooltip(
                text = stringResource(R.string.compose_editor_language_hint_summary),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Surface(
                    onClick = onLanguageHint,
                    enabled = enabled,
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent,
                ) {
                    Row(
                        Modifier.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_language_black_24dp),
                            stringResource(R.string.compose_editor_language_hint),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.keyboard), style = MaterialTheme.typography.labelLarge)
                            Text(
                                field.languageTag?.let { Locale.forLanguageTag(it).displayName }
                                    ?: stringResource(R.string.compose_editor_language_hint_default),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
        TextButton(onClick = onQuickActions, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_touch), null, Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.compose_editor_quick_actions))
        }
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onToolbar, enabled = enabled, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.ic_toolbar), null, Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.compose_editor_toolbar))
            }
            TextButton(onClick = onCustomButtons, enabled = enabled, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.ic_code), null, Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.compose_editor_custom_buttons))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldOptionsHelp(
    fieldName: String,
    noteTypeName: String,
) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = state.isVisible) { state.dismiss() }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        state = state,
        focusable = false,
        tooltip = {
            RichTooltip(
                caretShape = TooltipDefaults.caretShape(),
                // The XML themes use a translucent surfaceContainer overlay; a popup needs an opaque background.
                colors =
                    TooltipDefaults.richTooltipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer.compositeOver(MaterialTheme.colorScheme.surface),
                    ),
            ) {
                Column(
                    // Add to RichTooltip's 16.dp horizontal / 4.dp vertical padding for 24.dp on every side.
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Text(stringResource(R.string.compose_editor_field_options_help_scope, fieldName, noteTypeName))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_baseline_push_pin_24),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(stringResource(R.string.compose_editor_pin_field), style = MaterialTheme.typography.titleSmall)
                        }
                        Text(stringResource(R.string.compose_editor_pin_field_summary))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_language_black_24dp),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(stringResource(R.string.keyboard), style = MaterialTheme.typography.titleSmall)
                        }
                        Text(stringResource(R.string.compose_editor_language_hint_summary))
                    }
                }
            }
        },
    ) {
        IconButton(onClick = {
            if (state.isVisible) state.dismiss() else scope.launch { state.show() }
        }) {
            Icon(painterResource(R.drawable.ic_outline_info_24), stringResource(R.string.compose_editor_field_options_help))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(text) } },
            state = rememberTooltipState(),
            focusable = false,
            content = content,
        )
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
internal fun EditorActionButton(
    icon: Int,
    label: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val text = stringResource(label)
    EditorTooltip(text) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(painterResource(icon), text) }
    }
}

/** Checked semantics expose the selected format to TalkBack as well as sighted users. */
@Composable
internal fun EditorToggleButton(
    icon: Int,
    label: Int,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val text = stringResource(label)
    EditorTooltip(text) {
        IconToggleButton(
            checked = selected,
            onCheckedChange = { onClick() },
            enabled = enabled,
            colors =
                IconButtonDefaults.iconToggleButtonColors(
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        ) { Icon(painterResource(icon), text) }
    }
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
