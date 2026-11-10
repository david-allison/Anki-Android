// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.preferences.components.PreferenceActionCard
import com.ichi2.anki.preferences.components.PreferenceHelpSection
import com.ichi2.anki.preferences.components.PreferenceTextWithSwitchCard
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.ui.dialogs.TextInputDialog
import com.ichi2.compose.ui.preview.ThemePreviews
import kotlinx.coroutines.launch
import androidx.appcompat.R as AppCompatR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSyncServerSettingsScreen(
    server: CustomSyncServerState,
    configuration: CustomSyncServerConfiguration,
    onToggleEnabled: (Boolean) -> Unit,
    onSaveUrl: (String) -> Unit,
    onSaveCertificate: (String) -> Boolean,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    showNavigateUp: Boolean = true,
) {
    var dialog by rememberSaveable { mutableStateOf<SyncServerDialog?>(null) }
    var showCertificateError by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val certificateUpdatedMessage = stringResource(CommonString.dialog_updated_custom_certificate)
    val urlTitle = stringResource(CommonString.custom_sync_server_base_url_title)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonString.custom_sync_server_title)) },
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
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreferenceTextWithSwitchCard(
                title = urlTitle,
                summary =
                    configuration.url.ifEmpty {
                        stringResource(
                            CommonString.custom_sync_server_summary_none_of_the_two_servers_used,
                        )
                    },
                isChecked = server is CustomSyncServerState.Enabled,
                onClick = { dialog = SyncServerDialog.URL },
                onCheckedChange = { enabled ->
                    if (enabled && configuration.url.isEmpty()) {
                        dialog = SyncServerDialog.URL
                    } else {
                        onToggleEnabled(enabled)
                    }
                },
            )

            PreferenceActionCard(
                title = stringResource(CommonString.custom_sync_certificate_title),
                onClick = { dialog = SyncServerDialog.CERTIFICATE },
            )

            PreferenceHelpSection(
                text =
                    AnnotatedString.fromHtml(
                        stringResource(
                            CommonString.custom_sync_server_help2,
                            stringResource(R.string.link_custom_sync_server_help_learn_more_en),
                        ),
                        linkStyles = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary)),
                    ),
            )
        }
    }

    when (dialog) {
        SyncServerDialog.URL ->
            CustomSyncServerTextDialog(
                title = urlTitle,
                label = stringResource(CommonString.dialog_hint_url),
                initialValue = configuration.url,
                singleLine = true,
                onSave = onSaveUrl,
                onDismiss = { dialog = null },
            )
        SyncServerDialog.CERTIFICATE ->
            CustomSyncServerTextDialog(
                title = stringResource(CommonString.custom_sync_certificate_title),
                label = stringResource(CommonString.dialog_hint_certificate),
                initialValue = configuration.certificate,
                singleLine = false,
                onSave = { certificate ->
                    if (onSaveCertificate(certificate)) {
                        scope.launch { snackbarHostState.showSnackbar(certificateUpdatedMessage) }
                    } else {
                        showCertificateError = true
                    }
                },
                onDismiss = { dialog = null },
            )
        null -> Unit
    }

    if (showCertificateError) {
        AlertDialog(
            onDismissRequest = { showCertificateError = false },
            title = { Text(stringResource(CommonString.dialog_invalid_custom_certificate_title)) },
            text = { Text(stringResource(CommonString.dialog_invalid_custom_certificate)) },
            confirmButton = {
                TextButton(onClick = { showCertificateError = false }) {
                    Text(stringResource(CommonString.dialog_ok))
                }
            },
        )
    }
}

private enum class SyncServerDialog { URL, CERTIFICATE }

@Composable
private fun CustomSyncServerTextDialog(
    title: String,
    label: String,
    initialValue: String,
    singleLine: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val invalidValueMessage = stringResource(CommonString.invalid_value)
    TextInputDialog(
        title = title,
        label = label,
        confirmText = stringResource(CommonString.save),
        initialText = initialValue,
        singleLine = singleLine,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (singleLine) KeyboardType.Uri else KeyboardType.Text,
                autoCorrectEnabled = false,
                imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
            ),
        errorMessageFor = { if (singleLine && !isValidSyncUrl(it)) invalidValueMessage else null },
        onConfirm = {
            onSave(it)
            onDismiss()
        },
        onDismissRequest = onDismiss,
    )
}

@ThemePreviews
@Composable
private fun CustomSyncServerSettingsScreenPreview() {
    AnkiDroidTheme {
        CustomSyncServerSettingsScreen(
            server = CustomSyncServerState.Disabled,
            configuration = CustomSyncServerConfiguration(url = "https://sync.example.com/"),
            onToggleEnabled = {},
            onSaveUrl = {},
            onSaveCertificate = { true },
            onNavigateUp = {},
        )
    }
}
