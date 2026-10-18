// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ichi2.anki.CommonString
import com.ichi2.anki.R

/** Shared labels, icons and help for the controls in Field options. */
internal enum class EditorFieldOption(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @StringRes val help: Int,
) {
    PIN(
        R.string.compose_editor_pin_field,
        R.drawable.ic_baseline_push_pin_24,
        R.string.compose_editor_pin_field_summary,
    ),
    KEYBOARD(
        CommonString.keyboard,
        R.drawable.ic_language_black_24dp,
        R.string.compose_editor_language_hint_summary,
    ),
    DESCRIPTION(
        R.string.compose_editor_field_description,
        R.drawable.ic_notes,
        R.string.compose_editor_field_description_help,
    ),
    FONT(
        R.string.compose_editor_field_font,
        R.drawable.ic_format_font_size_24dp,
        R.string.compose_editor_field_font_help,
    ),
    HTML(
        R.string.compose_editor_start_in_html_mode,
        R.drawable.ic_code,
        R.string.compose_editor_start_in_html_mode_help,
    ),
    COLLAPSED(
        R.string.compose_editor_start_collapsed,
        R.drawable.ic_expand_less_black_24dp,
        R.string.compose_editor_start_collapsed_help,
    ),
    RIGHT_TO_LEFT(
        R.string.compose_editor_right_to_left,
        R.drawable.ic_editor_text_direction_rtl,
        R.string.compose_editor_right_to_left_help,
    ),
    TOOLBAR(
        R.string.compose_editor_toolbar,
        R.drawable.ic_toolbar,
        R.string.compose_editor_toolbar_help,
    ),
    QUICK_ACTIONS(
        R.string.compose_editor_quick_actions,
        R.drawable.ic_touch,
        R.string.compose_editor_quick_actions_help,
    ),
}
