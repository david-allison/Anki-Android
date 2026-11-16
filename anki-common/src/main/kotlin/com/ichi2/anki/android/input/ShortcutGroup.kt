// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>

package com.ichi2.anki.android.input

import android.content.Context
import android.view.KeyboardShortcutGroup
import androidx.annotation.StringRes

data class ShortcutGroup(
    val shortcuts: List<Shortcut>,
    @StringRes val id: Int,
) {
    fun toShortcutGroup(activity: Context): KeyboardShortcutGroup {
        val shortcuts = shortcuts.map { it.toShortcutInfo() }
        val groupLabel = activity.getString(id)
        return KeyboardShortcutGroup(groupLabel, shortcuts)
    }
}
