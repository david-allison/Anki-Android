// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.text.TextUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.multimedia.MultimediaActionHandler
import com.ichi2.anki.noteeditor.compose.preview.EditorPreviewDialog
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.anki.noteeditor.web.WebEditorSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
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
    onAttachMedia: () -> Unit,
    onLegacy: () -> Unit,
    onPreviewVisibility: (Boolean) -> Unit,
    editor: @Composable (Modifier) -> Unit,
    preview: @Composable (Modifier) -> Unit,
    fieldTaps: Flow<Unit> = emptyFlow(),
    headerVisibility: Flow<Boolean> = emptyFlow(),
    onFieldOptionsVisibility: (Boolean) -> Unit = {},
    onSettings: (WebEditorSettings) -> Unit = {},
    onToolbarDisplay: (EditorToolbarDisplay) -> Unit = {},
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
    var showPhonePreview by rememberSaveable { mutableStateOf(false) }
    val fieldOptionsRequested = rememberSaveable(state?.sessionId, state?.generation) { mutableStateOf(false) }
    val fieldOptionsBounds = remember(state?.sessionId, state?.generation) { mutableStateOf<Rect?>(null) }
    ObserveFieldOptionsVisibility(fieldOptionsRequested, ready, onFieldOptionsVisibility)
    var headerVisible by remember(state?.sessionId, state?.generation) { mutableStateOf(true) }
    LaunchedEffect(headerVisibility, state?.sessionId, state?.generation) {
        headerVisibility.collect { headerVisible = it }
    }
    val enabled = ready && !busy && state?.isSaving == false
    toolbarTarget?.takeIf { it.matches(state) }?.let { target ->
        val current = requireNotNull(state)
        EditorToolbarDialog(
            target = target,
            field = current.fields[target.field],
            isCloze = current.isCloze,
            templates = current.customButtonTemplates,
            onCustomButtons = { showCustomButtons = true },
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
    val keyboard = LocalSoftwareKeyboardController.current
    val hasTabletWidth by remember(windowInfo, density) {
        derivedStateOf { with(density) { windowInfo.containerSize.width.toDp() } >= 600.dp }
    }
    Box(
        Modifier.fillMaxSize().dismissFieldOptionsOnOutsideTap(fieldOptionsRequested, fieldOptionsBounds) {
            fieldOptionsRequested.value = false
        },
    ) {
        val tablet = LocalConfiguration.current.smallestScreenWidthDp >= 600 && hasTabletWidth
        LaunchedEffect(tablet, showPreview, showPhonePreview) { onPreviewVisibility(if (tablet) showPreview else showPhonePreview) }
        if (!tablet && showPhonePreview) {
            EditorPreviewDialog(onDismiss = { showPhonePreview = false }, preview = preview)
        }
        Scaffold(
            containerColor =
                if (state?.toolbarDisplay == EditorToolbarDisplay.HIDDEN) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceContainer.compositeOver(MaterialTheme.colorScheme.surface)
                },
            contentColor = MaterialTheme.colorScheme.onSurface,
            snackbarHost = { if (state == null) EditorSnackbarHost(snackbar, Modifier.imePadding()) },
            topBar = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface,
                        ).windowInsetsPadding(TopAppBarDefaults.windowInsets),
                ) {
                    EditorCollapsingHeader(headerVisible) {
                        TopAppBar(
                            windowInsets = WindowInsets(0),
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
                                    EditorOptionsMenu(
                                        settings = it.settings,
                                        enabled = enabled && !toolbarState.value.composing,
                                        onSettings = onSettings,
                                        onFields = onFields,
                                        onCards = onCards,
                                        toolbarDisplay = it.toolbarDisplay,
                                        onToolbarDisplay = onToolbarDisplay,
                                        onCustomButtons = { showCustomButtons = true },
                                        fieldOptionsEnabled = toolbarState.value.activeField in it.fields.indices,
                                        onFieldOptions = { fieldOptionsRequested.value = true },
                                    )
                                }
                                EditorActionButton(
                                    icon = R.drawable.ic_remove_red_eye_white,
                                    label =
                                        when {
                                            tablet && showPreview -> CommonString.note_editor_hide_previewer
                                            tablet -> CommonString.note_editor_show_previewer
                                            else -> CommonString.card_editor_preview_card
                                        },
                                    enabled = enabled,
                                ) {
                                    if (tablet) {
                                        showPreview = !showPreview
                                    } else {
                                        keyboard?.hide()
                                        showPhonePreview = true
                                    }
                                }
                                // The Save button stays at this position as the rest of the header hides.
                                Spacer(Modifier.padding(end = 8.dp).size(48.dp))
                            },
                        )
                    }
                }
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
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding)
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.weight(1f)) {
                        EditorCollapsingHeader(headerVisible) {
                            EditorMetadata(
                                deckName = state.deckName,
                                noteTypeName = state.noteTypeName,
                                enabled = enabled,
                                onDeck = onDeck,
                                onType = {
                                    if (state.isAdding) dialog = "type" else onEditType()
                                },
                            )
                        }
                        // Only the field area and toolbar follow the keyboard. Keep the
                        // metadata and top bar out of its changing layout constraints.
                        EditorFieldArea(
                            editorState = state,
                            snackbar = snackbar,
                            tablet = tablet,
                            enabled = enabled,
                            state = toolbarState,
                            fieldTaps = fieldTaps,
                            fieldOptionsRequested = fieldOptionsRequested,
                            onFieldOptionsRequested = { fieldOptionsRequested.value = it },
                            onFieldOptionsBounds = { fieldOptionsBounds.value = it },
                            onAction = onAction,
                            onMedia = onAttachMedia,
                            onRecordAudio = { onMedia(MultimediaActionHandler.AudioRecording) },
                            onPinned = onPinned,
                            onLanguageHint = onLanguageHint,
                            onToolbar = { toolbarTarget = it },
                            onQuickActions = { quickActionsTarget = it },
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
        // The 40dp circle has another 4dp inside its touch target: align its visible edge at 16dp.
        Box(Modifier.align(Alignment.TopEnd).windowInsetsPadding(TopAppBarDefaults.windowInsets).padding(top = 8.dp, end = 12.dp)) {
            EditorSaveButton(onClick = onSave, enabled = enabled && state.removedFields.isEmpty())
        }
    }
    if (state != null && enabled) {
        when (dialog) {
            "type" -> {
                var choices by remember { mutableStateOf<List<EditorChoice>?>(null) }
                LaunchedEffect(Unit) { choices = loadNoteTypes() }
                NoteTypeDialog(
                    title = stringResource(R.string.compose_editor_type),
                    choices = choices,
                    selectedId = state.noteTypeId,
                    onDismiss = { dialog = null },
                    onSelect = {
                        dialog = null
                        onType(it)
                    },
                )
            }
        }
    }
}

/** Keep visibility observation out of the editor screen's composition scope. */
@Composable
private fun ObserveFieldOptionsVisibility(
    requested: State<Boolean>,
    ready: Boolean,
    onVisibility: (Boolean) -> Unit,
) {
    if (requested.value && ready) {
        DisposableEffect(onVisibility) {
            onVisibility(true)
            onDispose { onVisibility(false) }
        }
    }
}

/** Observe taps without consuming them: a WebView field must still receive the same gesture. */
@Composable
private fun Modifier.dismissFieldOptionsOnOutsideTap(
    enabled: State<Boolean>,
    sheetBounds: State<Rect?>,
    onDismiss: () -> Unit,
): Modifier {
    val dismiss by rememberUpdatedState(onDismiss)
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { rootOffset = it.positionInRoot() }.pointerInput(sheetBounds) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!enabled.value) return@awaitEachGesture
            if (sheetBounds.value?.contains(down.position + rootOffset) != false) return@awaitEachGesture
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.singleOrNull() ?: return@awaitEachGesture
                if (change.id != down.id || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    return@awaitEachGesture
                }
                if (!change.pressed) {
                    if (change.previousPressed &&
                        change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis &&
                        sheetBounds.value?.contains(change.position + rootOffset) == false
                    ) {
                        dismiss()
                    }
                    return@awaitEachGesture
                }
            }
        }
    }
}

/** Resize only after a deliberate scroll direction change, never for every scroll pixel. */
@Composable
private fun EditorCollapsingHeader(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Top),
        exit = shrinkVertically(shrinkTowards = Alignment.Top),
    ) { content() }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditorFieldArea(
    editorState: EditorState,
    snackbar: SnackbarHostState,
    tablet: Boolean,
    enabled: Boolean,
    state: State<EditorToolbarState>,
    fieldTaps: Flow<Unit>,
    fieldOptionsRequested: State<Boolean>,
    onFieldOptionsRequested: (Boolean) -> Unit,
    onFieldOptionsBounds: (Rect) -> Unit,
    onAction: (WebEditorAction) -> Unit,
    onMedia: () -> Unit,
    onRecordAudio: () -> Unit,
    onPinned: (EditorFieldTarget, Boolean) -> Unit,
    onLanguageHint: (EditorFieldTarget) -> Unit,
    onToolbar: (EditorFieldTarget) -> Unit,
    onQuickActions: (EditorFieldTarget) -> Unit,
    onCustomAction: (EditorFieldTarget, EditorCustomToolbarButton) -> Unit,
    onTags: () -> Unit,
    onRemoveTag: (String) -> Unit,
    modifier: Modifier,
    fields: @Composable (Modifier) -> Unit,
) {
    val toolbarState by state
    // A different field has a different button order. Do not retain the previous
    // field's first visible item by key and scroll past the new field's leading buttons.
    val toolbarScrollState =
        key(editorState.sessionId, editorState.generation, toolbarState.activeField ?: 0) {
            rememberLazyListState()
        }
    val sheetState =
        key(editorState.sessionId, editorState.generation) {
            rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            )
        }
    LaunchedEffect(fieldTaps, editorState.sessionId, editorState.generation) {
        fieldTaps.collect { onFieldOptionsRequested(false) }
    }
    // isVisible can flip mid-animation; retain the content and layout until the sheet settles.
    val showFieldOptions by remember(fieldOptionsRequested, sheetState) {
        derivedStateOf { fieldOptionsRequested.value || sheetState.currentValue != SheetValue.Hidden }
    }
    val target =
        toolbarState.activeField?.takeIf { it in editorState.fields.indices }?.let {
            EditorFieldTarget(editorState.sessionId, editorState.generation, it)
        }
    // Start the animation without recomposing the toolbar or screen. After recreation,
    // expansion must also wait for the sheet content to establish its anchors.
    LaunchedEffect(sheetState, fieldOptionsRequested) {
        snapshotFlow { fieldOptionsRequested.value && sheetState.hasExpandedState }.collectLatest { expand ->
            if (expand) sheetState.expand() else sheetState.hide()
        }
    }
    val closeOptions: () -> Unit = { onFieldOptionsRequested(false) }
    BackHandler(enabled = showFieldOptions, onBack = closeOptions)
    val toolbarBackground = MaterialTheme.colorScheme.surfaceContainer.compositeOver(MaterialTheme.colorScheme.surface)
    // The standard sheet shares the editor window, so opening it leaves the IME connected.
    BottomSheetScaffold(
        // A hidden sheet sits below this area; prevent it drawing into the navigation-bar inset.
        modifier = modifier.clipToBounds(),
        containerColor = MaterialTheme.colorScheme.surface,
        scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState),
        snackbarHost = { if (showFieldOptions) EditorSnackbarHost(snackbar) },
        sheetPeekHeight = 0.dp,
        sheetDragHandle = null,
        sheetSwipeEnabled = false,
        sheetContainerColor = toolbarBackground,
        sheetShadowElevation = 4.dp,
        sheetContent = {
            // Retain the controls through dismissal, then remove their layout and semantics work.
            if (showFieldOptions && target != null) {
                Box(Modifier.onGloballyPositioned { onFieldOptionsBounds(it.boundsInRoot()) }) {
                    EditorFieldOptions(
                        field = editorState.fields[target.field],
                        noteTypeName = editorState.noteTypeName,
                        enabled = enabled,
                        onPinned = { onPinned(target, it) },
                        onLanguageHint = { onLanguageHint(target) },
                        onToolbar = { onToolbar(target) },
                        onQuickActions = { onQuickActions(target) },
                        onCollapse = closeOptions,
                    )
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            // Overlay feedback above the footer without resizing or obscuring the tags and toolbar.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                fields(Modifier.fillMaxSize())
                if (!showFieldOptions) EditorSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
            // Retain the footer under the sheet to avoid rebuilding its buttons and resizing WebView.
            // Its controls are covered while options are open, including for accessibility.
            Column(if (showFieldOptions) Modifier.clearAndSetSemantics {} else Modifier) {
                EditorTags(editorState.tags, enabled, tablet, onTags, onRemoveTag)
                if (editorState.toolbarDisplay != EditorToolbarDisplay.HIDDEN) {
                    Row(
                        // The IME changes the screen height, not the toolbar's content height.
                        // Keep those constraints out of button composition and measurement.
                        Modifier.fillMaxWidth().wrapContentHeight(unbounded = true).background(toolbarBackground),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EditorFormattingButtons(
                            toolbar = target?.let { editorState.fields[it.field].toolbar } ?: EditorFieldToolbar(),
                            state = toolbarState,
                            enabled = enabled,
                            isCloze = editorState.isCloze,
                            textColor = editorState.toolbarTextColor,
                            highlightColor = editorState.toolbarHighlightColor,
                            onAction = onAction,
                            onCustomAction = { button -> target?.let { onCustomAction(it, button) } },
                            onMedia = onMedia,
                            onRecordAudio = onRecordAudio,
                        ) { items, button ->
                            if (editorState.toolbarDisplay == EditorToolbarDisplay.STACK) {
                                FlowRow(
                                    Modifier
                                        .weight(1f)
                                        .heightIn(max = 144.dp)
                                        .verticalScroll(rememberScrollState(), reverseScrolling = true)
                                        .padding(horizontal = 8.dp)
                                        .centerToolbarColumns(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalArrangement = ToolbarRowsFromBottom,
                                ) { items.forEach { button(it) } }
                            } else {
                                // Only compose visible buttons during editor/IME startup. Stable keys
                                // retain each button's tooltip state when a field changes its order.
                                LazyRow(
                                    state = toolbarScrollState,
                                    modifier = Modifier.weight(1f).toolbarEndFade(toolbarScrollState, toolbarBackground),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) {
                                    items(items, key = { it.key }) { button(it) }
                                }
                            }
                        }
                        if (editorState.toolbarDisplay == EditorToolbarDisplay.SCROLL) {
                            VerticalDivider(Modifier.height(32.dp))
                            EditorActionButton(
                                R.drawable.ic_more_vertical,
                                R.string.compose_editor_field_options,
                                enabled && target != null,
                            ) { onFieldOptionsRequested(true) }
                        }
                    }
                }
            }
        }
    }
}

/** Center whole 48dp button columns; partial rows keep the same start edge. */
private fun Modifier.centerToolbarColumns(): Modifier =
    layout { measurable, constraints ->
        val buttonWidth = 48.dp.roundToPx()
        val columns = (constraints.maxWidth / buttonWidth).coerceAtLeast(1)
        val width = (columns * buttonWidth).coerceAtMost(constraints.maxWidth)
        val content = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(constraints.maxWidth, content.height) {
            content.placeRelative((constraints.maxWidth - width) / 2, 0)
        }
    }

/** Keep the first configured buttons nearest the keyboard, preserving each row's reading order. */
private object ToolbarRowsFromBottom : Arrangement.Vertical {
    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        outPositions: IntArray,
    ) {
        var bottom = totalSize
        sizes.forEachIndexed { index, height ->
            bottom -= height
            outPositions[index] = bottom
        }
    }
}

/** Fade clipped buttons into the solid toolbar surface without allocating an offscreen layer. */
private fun Modifier.toolbarEndFade(
    scrollState: LazyListState,
    background: Color,
): Modifier =
    drawWithCache {
        val width = 16.dp.toPx().coerceAtMost(size.width)
        val rtl = layoutDirection == LayoutDirection.Rtl
        val left = if (rtl) 0f else size.width - width
        val brush =
            Brush.horizontalGradient(
                colors = listOf(background.copy(alpha = 0f), background),
                startX = if (rtl) width else left,
                endX = if (rtl) 0f else size.width,
            )
        onDrawWithContent {
            drawContent()
            if (scrollState.canScrollForward) {
                drawRect(brush, topLeft = Offset(left, 0f), size = Size(width, size.height))
            }
        }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorFieldOptions(
    field: EditorFieldState,
    noteTypeName: String,
    enabled: Boolean,
    onPinned: (Boolean) -> Unit,
    onLanguageHint: () -> Unit,
    onToolbar: () -> Unit,
    onQuickActions: () -> Unit,
    onCollapse: () -> Unit,
) {
    val fieldDescription = stringResource(R.string.compose_editor_field_options_title, field.name)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
        Row(
            Modifier.padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                field.name,
                modifier =
                    Modifier.weight(1f).clearAndSetSemantics {
                        heading()
                        contentDescription = fieldDescription
                    },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FieldOptionsHelp(field.name, noteTypeName)
            IconButton(onClick = onCollapse) {
                Icon(painterResource(R.drawable.ic_clear_white), stringResource(R.string.compose_editor_close_field_options))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            FlowRow(
                Modifier.fillMaxWidth(),
                maxItemsInEachRow = if (maxWidth / LocalDensity.current.fontScale < 300.dp) 1 else 2,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorTooltip(
                    text = stringResource(R.string.compose_editor_pin_field_summary),
                    modifier = Modifier.weight(1f).fillMaxRowHeight(),
                ) {
                    Surface(
                        checked = field.sticky,
                        onCheckedChange = onPinned,
                        enabled = enabled,
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.medium,
                        color = if (field.sticky) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor =
                            if (field.sticky) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
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
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.compose_editor_pin_field), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                EditorTooltip(
                    text = stringResource(R.string.compose_editor_language_hint_summary),
                    modifier = Modifier.weight(1f).fillMaxRowHeight(),
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
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(CommonString.keyboard), style = MaterialTheme.typography.labelLarge)
                                Text(
                                    field.languageTag?.let { Locale.forLanguageTag(it).displayName }
                                        ?: stringResource(R.string.compose_editor_language_hint_default),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        EditorFieldOptionsLink(
            icon = R.drawable.ic_touch,
            title = R.string.compose_editor_quick_actions,
            summary = R.string.compose_editor_quick_actions_summary,
            enabled = enabled,
            onClick = onQuickActions,
        )
        EditorFieldOptionsLink(
            icon = R.drawable.ic_toolbar,
            title = R.string.compose_editor_toolbar,
            summary = R.string.compose_editor_toolbar_summary,
            enabled = enabled,
            onClick = onToolbar,
        )
    }
}

@Composable
private fun EditorFieldOptionsLink(
    icon: Int,
    title: Int,
    summary: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .padding(horizontal = 8.dp)
                .clip(MaterialTheme.shapes.medium),
        supportingContent = { Text(stringResource(summary)) },
        leadingContent = { Icon(painterResource(icon), null, Modifier.size(24.dp)) },
        trailingContent = { Icon(painterResource(R.drawable.ic_baseline_chevron_right_24), null, Modifier.size(24.dp)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) { Text(stringResource(title)) }
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
                modifier = Modifier.heightIn(max = 480.dp),
                caretShape = TooltipDefaults.caretShape(),
                // The XML themes use a translucent surfaceContainer overlay; a popup needs an opaque background.
                colors =
                    TooltipDefaults.richTooltipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer.compositeOver(MaterialTheme.colorScheme.surface),
                    ),
            ) {
                Column(
                    // Add to RichTooltip's 16.dp horizontal / 4.dp vertical padding for 24.dp on every side.
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 20.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Text(
                        AnnotatedString.fromHtml(
                            stringResource(
                                R.string.compose_editor_field_options_help_scope,
                                "<b>${TextUtils.htmlEncode(fieldName)}</b>",
                                "<b>${TextUtils.htmlEncode(noteTypeName)}</b>",
                            ),
                        ),
                    )
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
                            Text(stringResource(CommonString.keyboard), style = MaterialTheme.typography.titleSmall)
                        }
                        Text(stringResource(R.string.compose_editor_language_hint_summary))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(painterResource(R.drawable.ic_touch), contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.compose_editor_quick_actions), style = MaterialTheme.typography.titleSmall)
                        }
                        Text(stringResource(R.string.compose_editor_quick_actions_help))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(painterResource(R.drawable.ic_toolbar), contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.compose_editor_toolbar), style = MaterialTheme.typography.titleSmall)
                        }
                        Text(stringResource(R.string.compose_editor_toolbar_help))
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
internal fun EditorSaveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    EditorTooltip(stringResource(CommonString.save), modifier) {
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        ) {
            Icon(painterResource(R.drawable.ic_done_white), stringResource(CommonString.save))
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
