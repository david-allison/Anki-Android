// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.preferences.components.PreferenceSwitchCard
import com.ichi2.anki.settings.enums.Theme
import com.ichi2.compose.ui.dialogs.TextInputDialog
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.AnkiDroidThemeProvider
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import androidx.appcompat.R as AppCompatR
import com.ichi2.anki.common.android.R as CommonR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSyncServerSettingsScreen(
    server: CustomSyncServerState,
    configuration: CustomSyncServerConfiguration,
    onSaveServer: (Boolean, String) -> Unit,
    onSaveCertificate: (String) -> Boolean,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    showNavigateUp: Boolean = true,
    enableOnOpen: Boolean = false,
) {
    val savedEnabled = server is CustomSyncServerState.Enabled
    var enabled by rememberSaveable(server, configuration.url, enableOnOpen) { mutableStateOf(savedEnabled || enableOnOpen) }
    var url by rememberSaveable(server, configuration.url) { mutableStateOf(configuration.url) }
    var editCertificate by rememberSaveable { mutableStateOf(false) }
    var certificateError by rememberSaveable { mutableStateOf(false) }
    val invalidUrl = enabled && url.toHttpUrlOrNull() == null
    val hasChanges = enabled != savedEnabled || url != configuration.url
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val certificateUpdated = stringResource(CommonString.dialog_updated_custom_certificate)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(CommonString.custom_sync_server_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (showNavigateUp) {
                        IconButton(onClick = onNavigateUp) {
                            Icon(
                                painterResource(R.drawable.ic_baseline_arrow_back_24),
                                contentDescription = stringResource(AppCompatR.string.abc_action_bar_up_description),
                            )
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                            onSaveServer(enabled, url)
                        },
                        enabled = hasChanges && !invalidUrl,
                    ) {
                        Text(stringResource(CommonString.save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                PreferenceSwitchCard(
                    title = stringResource(CommonString.custom_sync_server_enabled_title),
                    isChecked = enabled,
                    onCheckedChange = { enabled = it },
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(CommonString.dialog_hint_url)) },
                    modifier = Modifier.fillMaxWidth().testTag("custom_sync_url"),
                    enabled = enabled,
                    singleLine = true,
                    isError = invalidUrl,
                    supportingText =
                        if (invalidUrl) {
                            { Text(stringResource(CommonString.custom_sync_server_invalid_url)) }
                        } else {
                            null
                        },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                        }),
                )
                Text(
                    text =
                        AnnotatedString.fromHtml(
                            stringResource(
                                CommonString.custom_sync_server_help2,
                                stringResource(R.string.link_custom_sync_server_help_learn_more_en),
                            ),
                            linkStyles =
                                TextLinkStyles(
                                    SpanStyle(
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                ),
                        ),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(CommonString.custom_sync_certificate_title)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (configuration.certificate.isEmpty()) {
                                CommonString.custom_sync_certificate_not_configured
                            } else {
                                CommonString.custom_sync_certificate_configured
                            },
                        ),
                    )
                },
                trailingContent = {
                    Icon(painterResource(CommonR.drawable.ic_baseline_chevron_right_24), contentDescription = null)
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.clickable { editCertificate = true },
            )
        }
    }

    if (editCertificate) {
        TextInputDialog(
            title = stringResource(CommonString.custom_sync_certificate_title),
            label = stringResource(CommonString.dialog_hint_certificate),
            confirmText = stringResource(CommonString.save),
            initialText = configuration.certificate,
            capitalization = KeyboardCapitalization.None,
            singleLine = false,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onConfirm = { certificate ->
                editCertificate = false
                if (onSaveCertificate(certificate)) {
                    scope.launch { snackbar.showSnackbar(certificateUpdated) }
                } else {
                    certificateError = true
                }
            },
            onDismissRequest = { editCertificate = false },
        )
    }
    if (certificateError) {
        AlertDialog(
            onDismissRequest = { certificateError = false },
            title = { Text(stringResource(CommonString.dialog_invalid_custom_certificate_title)) },
            text = { Text(stringResource(CommonString.dialog_invalid_custom_certificate)) },
            confirmButton = {
                TextButton(onClick = { certificateError = false }) {
                    Text(stringResource(CommonString.dialog_ok))
                }
            },
        )
    }
}

@Preview(widthDp = 360)
@Composable
private fun CustomSyncServerSettingsScreenPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) {
    AnkiDroidPreview(theme = theme) {
        CustomSyncServerSettingsScreen(
            server = CustomSyncServerState.Enabled("https://sync.example.com/".toHttpUrl()),
            configuration = CustomSyncServerConfiguration(url = "https://sync.example.com/"),
            onSaveServer = { _, _ -> },
            onSaveCertificate = { true },
            onNavigateUp = {},
        )
    }
}
