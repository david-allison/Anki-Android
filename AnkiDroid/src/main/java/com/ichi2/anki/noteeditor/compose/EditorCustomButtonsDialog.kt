// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.CommonString
import com.ichi2.anki.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorCustomButtonsDialog(
    templates: List<EditorCustomToolbarButton>,
    onDismiss: () -> Unit,
    onSave: (List<EditorCustomToolbarButton>) -> Unit,
) {
    var buttons by rememberSaveable { mutableStateOf(templates) }
    val dismiss = rememberEditorDismiss(buttons != templates, onDismiss)
    var editing by rememberSaveable { mutableStateOf<EditorCustomToolbarButton?>(null) }
    var removing by rememberSaveable { mutableStateOf<EditorCustomToolbarButton?>(null) }
    // Keep the toolbar mounted beneath this page, including its scroll position and pending edits.
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize().testTag("editor_custom_button_library")) {
            Scaffold(
                modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding(),
                contentWindowInsets = WindowInsets(0),
                topBar = {
                    TopAppBar(
                        windowInsets = WindowInsets(0),
                        title = {
                            Text(
                                stringResource(R.string.compose_editor_custom_buttons),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        actions = {
                            EditorSaveButton(onClick = { onSave(buttons) }, modifier = Modifier.padding(end = 8.dp))
                        },
                        navigationIcon = {
                            IconButton(onClick = dismiss, colors = editorIconButtonColors()) {
                                Icon(painterResource(R.drawable.ic_baseline_arrow_back_24), stringResource(R.string.compose_editor_toolbar))
                            }
                        },
                    )
                },
                bottomBar = {
                    val height = ButtonDefaults.MediumContainerHeight
                    Button(
                        onClick = { editing = EditorCustomToolbarButton(label = "", prefix = "", suffix = "") },
                        modifier = Modifier.padding(16.dp).fillMaxWidth().heightIn(min = height),
                        shapes = ButtonDefaults.shapesFor(height),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
                    ) {
                        Icon(painterResource(R.drawable.ic_add_toolbar_icon), null, Modifier.size(ButtonDefaults.iconSizeFor(height)))
                        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
                        Text(stringResource(R.string.compose_editor_add_custom_button), style = ButtonDefaults.textStyleFor(height))
                    }
                },
            ) { padding ->
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            stringResource(R.string.compose_editor_custom_buttons_scope),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (buttons.isEmpty()) {
                        item { Text(stringResource(R.string.compose_editor_no_custom_buttons), Modifier.padding(vertical = 24.dp)) }
                    }
                    items(buttons, key = { it.id }) { custom ->
                        Surface(
                            onClick = { editing = custom },
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        custom.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${custom.prefix}…${custom.suffix}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { removing = custom }, colors = editorIconButtonColors()) {
                                    Icon(
                                        painterResource(R.drawable.ic_delete),
                                        stringResource(R.string.compose_editor_remove_custom_button, custom.label),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        editing?.let { button ->
            CustomToolbarButtonDialog(
                button = button,
                onDismiss = { editing = null },
                onSave = { updated ->
                    buttons =
                        if (buttons.any { it.id == updated.id }) {
                            buttons.map { if (it.id == updated.id) updated else it }
                        } else {
                            buttons + updated
                        }
                    editing = null
                },
            )
        }
        removing?.let { button ->
            AlertDialog(
                onDismissRequest = { removing = null },
                title = { Text(stringResource(R.string.compose_editor_remove_custom_button, button.label)) },
                text = { Text(stringResource(R.string.compose_editor_remove_custom_button_scope)) },
                confirmButton = {
                    TextButton(onClick = {
                        buttons = buttons.filterNot { it.id == button.id }
                        removing = null
                    }) { Text(stringResource(CommonString.dialog_remove)) }
                },
                dismissButton = {
                    TextButton(onClick = { removing = null }) { Text(stringResource(CommonString.dialog_cancel)) }
                },
            )
        }
    }
}

@Composable
private fun CustomToolbarButtonDialog(
    button: EditorCustomToolbarButton,
    onDismiss: () -> Unit,
    onSave: (EditorCustomToolbarButton) -> Unit,
) {
    var label by rememberSaveable(button.id) { mutableStateOf(button.label) }
    var prefix by rememberSaveable(button.id) { mutableStateOf(button.prefix) }
    var suffix by rememberSaveable(button.id) { mutableStateOf(button.suffix) }
    val dismiss = rememberEditorDismiss(label != button.label || prefix != button.prefix || suffix != button.suffix, onDismiss)
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.compose_editor_custom_button)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    label,
                    { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(CommonString.note_editor_toolbar_icon)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    prefix,
                    { prefix = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(CommonString.before_text)) },
                )
                OutlinedTextField(
                    suffix,
                    { suffix = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(CommonString.after_text)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(button.copy(label = label.trim(), prefix = prefix, suffix = suffix)) },
                enabled = label.isNotBlank() && (prefix.isNotEmpty() || suffix.isNotEmpty()),
            ) { Text(stringResource(CommonString.save)) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(CommonString.dialog_cancel)) } },
    )
}
