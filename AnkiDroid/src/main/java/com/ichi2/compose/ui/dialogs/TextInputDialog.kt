// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.compose.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.CommonString
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.DialogPreviews
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Dialog asking the user for a single line of text. Compose counterpart of
 * the View based `AlertDialog.input(...)` helper.
 *
 * @param onConfirm called with the text, but only while it is valid
 * @param initialText initial value shown in the field
 * @param initialSelection initial cursor position or selected range; defaults to the end of [initialText]
 * @param errorMessageFor plain or annotated error to show for the current input, null when valid
 * @param helperText shown when no error is displayed; an empty string reserves feedback space
 * @param description optional context below the title
 * @param transformInput applied to text edits, not cursor-only changes
 * @param validateOnInput when false, validate on confirmation instead of while editing
 * @param enabled whether confirmation is allowed from the button or keyboard
 * @param asyncErrorMessageFor optional background validation, also rechecked before confirming
 * @param validationKey invalidates background validation when external data changes
 */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
    dismissText: String = stringResource(CommonString.dialog_cancel),
    initialText: String = "",
    initialSelection: TextRange = TextRange(initialText.length),
    maxLengthCounter: Int? = null,
    lengthOf: (String) -> Int = { it.length },
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    errorMessageFor: (String) -> CharSequence? = { null },
    helperText: String? = null,
    description: String? = null,
    transformInput: (TextFieldValue) -> TextFieldValue = { it },
    validateOnInput: Boolean = true,
    enabled: Boolean = true,
    asyncErrorMessageFor: (suspend (String) -> CharSequence?)? = null,
    validationKey: Any? = null,
) {
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, selection = initialSelection))
    }
    var hasUserEdited by rememberSaveable { mutableStateOf(false) }
    var submissionAttempted by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentAsyncValidator by rememberUpdatedState(asyncErrorMessageFor)
    var validatedInput by remember { mutableStateOf<String?>(null) }
    var asyncError by remember { mutableStateOf<CharSequence?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val syncError = if (validateOnInput || submissionAttempted) errorMessageFor(input.text) else null
    val pendingValidation = asyncErrorMessageFor != null && syncError == null && validatedInput != input.text
    val errorMessage = syncError ?: asyncError.takeIf { validatedInput == input.text }
    if (asyncErrorMessageFor != null) {
        LaunchedEffect(input.text, syncError, validationKey) {
            validatedInput = null
            asyncError = null
            if (syncError == null) {
                val text = input.text
                asyncError = currentAsyncValidator?.invoke(text)
                validatedInput = text
            }
        }
    }
    val displayedError = errorMessage?.takeIf { hasUserEdited || submissionAttempted }
    val supportingMessage =
        (displayedError ?: helperText)?.let { it as? AnnotatedString ?: AnnotatedString(it.toString()) }
    val hasSupportingText = maxLengthCounter != null || supportingMessage != null || !validateOnInput
    val focusRequester = remember { FocusRequester() }

    fun confirmIfValid() {
        if (!enabled || submitting) return
        submissionAttempted = true
        val text = input.text
        if (errorMessageFor(text) != null) return
        val validate = currentAsyncValidator
        if (validate == null) {
            onConfirm(text)
        } else {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                submitting = true
                try {
                    val error = validate(text)
                    if (input.text == text) {
                        asyncError = error
                        validatedInput = text
                        if (error == null && currentEnabled) currentOnConfirm(text)
                    }
                } finally {
                    submitting = false
                }
            }
        }
    }
    AnkiDroidDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(alertDialogWidthFraction()),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Layout(modifier = Modifier.padding(24.dp), content = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = AlertDialogDefaults.titleContentColor,
                        modifier = Modifier.padding(bottom = if (description == null) 16.dp else 8.dp),
                    )
                    if (description != null) {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlertDialogDefaults.textContentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { proposed ->
                            if (input.text != proposed.text) {
                                input = transformInput(proposed)
                                hasUserEdited = true
                                submissionAttempted = false
                            } else {
                                input = proposed
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        label = { Text(label, Modifier.testTag("text_input_label")) },
                        isError = displayedError != null,
                        supportingText =
                            if (!hasSupportingText) {
                                null
                            } else {
                                {
                                    Row(Modifier.fillMaxWidth()) {
                                        if (supportingMessage != null || !validateOnInput) {
                                            Text(supportingMessage ?: AnnotatedString(""), Modifier.weight(1f))
                                        } else {
                                            Spacer(Modifier.weight(1f))
                                        }
                                        if (maxLengthCounter != null) Text("${lengthOf(input.text)}/$maxLengthCounter")
                                    }
                                }
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { confirmIfValid() }),
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = if (hasSupportingText) 8.dp else 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = onDismissRequest) { Text(dismissText) }
                    TextButton(
                        onClick = ::confirmIfValid,
                        enabled = enabled && !submitting && !pendingValidation && (!validateOnInput || errorMessage == null),
                    ) { Text(confirmText) }
                }
            }) { children, constraints ->
                val actions = children[1].measure(constraints.copy(minHeight = 0))
                val body =
                    children[0].measure(
                        constraints.copy(minHeight = 0, maxHeight = (constraints.maxHeight - actions.height).coerceAtLeast(0)),
                    )
                layout(constraints.maxWidth, body.height + actions.height) {
                    body.placeRelative(0, 0)
                    actions.placeRelative(0, body.height)
                }
            }
            LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
        }
    }
}

/** @see TextInputDialog */
@DialogPreviews
@Composable
private fun TextInputDialogPreview() {
    AnkiDroidPreview {
        TextInputDialog("Rename deck", "Deck name", "Rename", {}, {}, maxLengthCounter = 50)
    }
}
