// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Validation feedback and actions shared by the dialog's text field, button and keyboard. */
internal class TextInputValidation(
    val errorMessage: CharSequence?,
    val canConfirm: Boolean,
    val onTextChanged: () -> Unit,
    val onConfirm: () -> Unit,
)

/**
 * Keeps validation and submission state for [TextInputDialog].
 * [text] reads the live input, including edits made while asynchronous confirmation is suspended.
 */
@Composable
internal fun rememberTextInputValidation(
    text: () -> String,
    enabled: Boolean,
    validateOnInput: Boolean,
    errorMessageFor: (String) -> CharSequence?,
    asyncErrorMessageFor: (suspend (String) -> CharSequence?)?,
    validationKey: Any?,
    onConfirm: (String) -> Unit,
): TextInputValidation {
    var hasUserEdited by rememberSaveable { mutableStateOf(false) }
    var submissionAttempted by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentText by rememberUpdatedState(text)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentAsyncValidator by rememberUpdatedState(asyncErrorMessageFor)
    var validatedInput by remember { mutableStateOf<String?>(null) }
    var asyncError by remember { mutableStateOf<CharSequence?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val input = text()
    val syncError = if (validateOnInput || submissionAttempted) errorMessageFor(input) else null
    val pendingValidation = asyncErrorMessageFor != null && syncError == null && validatedInput != input
    val errorMessage = syncError ?: asyncError.takeIf { validatedInput == input }
    if (asyncErrorMessageFor != null) {
        LaunchedEffect(input, syncError, validationKey) {
            validatedInput = null
            asyncError = null
            if (syncError == null) {
                asyncError = currentAsyncValidator?.invoke(input)
                validatedInput = input
            }
        }
    }

    fun confirmIfValid() {
        if (!enabled || submitting) return
        submissionAttempted = true
        val submittedText = currentText()
        if (errorMessageFor(submittedText) != null) return
        val validate = currentAsyncValidator
        if (validate == null) {
            onConfirm(submittedText)
        } else {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                submitting = true
                try {
                    val error = validate(submittedText)
                    if (currentText() == submittedText) {
                        asyncError = error
                        validatedInput = submittedText
                        if (error == null && currentEnabled) currentOnConfirm(submittedText)
                    }
                } finally {
                    submitting = false
                }
            }
        }
    }

    return TextInputValidation(
        errorMessage = errorMessage?.takeIf { hasUserEdited || submissionAttempted },
        canConfirm = enabled && !submitting && !pendingValidation && (!validateOnInput || errorMessage == null),
        onTextChanged = {
            hasUserEdited = true
            submissionAttempted = false
        },
        onConfirm = ::confirmIfValid,
    )
}
