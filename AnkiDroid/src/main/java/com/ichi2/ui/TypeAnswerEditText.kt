// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.ui

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.google.android.material.textfield.TextInputEditText

/** The Material answer field, including support for `{{nosuggest:type:}}`. */
class TypeAnswerEditText(
    context: Context,
    attrs: AttributeSet?,
) : TextInputEditText(context, attrs) {
    var noSuggest: Boolean = false

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? =
        super.onCreateInputConnection(outAttrs).also { connection ->
            // Setting the view's inputType to TYPE_NULL disables its editor, cursor, and normal
            // keyboard reopening. Keep the editor and Done action; only change what the IME sees.
            if (connection != null && noSuggest) outAttrs.inputType = InputType.TYPE_NULL
        }
}
