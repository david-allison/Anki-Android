// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import com.ichi2.anki.R

/** A settings entry with separate navigation and switch targets. */
class SwitchScreenPreference(
    context: Context,
    attrs: AttributeSet?,
) : SwitchPreferenceCompat(context, attrs) {
    init {
        widgetLayoutResource = R.layout.preference_widget_switch_screen
    }

    override fun onClick() {
        // Row clicks open the configured fragment; only the switch changes the value.
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.findViewById(androidx.preference.R.id.switchWidget)?.contentDescription = title
    }
}
