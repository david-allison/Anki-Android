// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.ichi2.anki.R

/** A row of the addons browser */
data class AddonListItem(
    /** The addon's npm name (or its directory name, if the manifest is invalid) */
    val name: String,
    val title: String,
    val subtitle: String,
    val isEnabled: Boolean,
    val isValid: Boolean,
)

class AddonsBrowserAdapter(
    private val onToggled: (AddonListItem, Boolean) -> Unit,
    private val onDeleteRequested: (AddonListItem) -> Unit,
    private val onConfigureRequested: (AddonListItem) -> Unit,
) : ListAdapter<AddonListItem, AddonsBrowserAdapter.ViewHolder>(diffCallback) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_addon, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) = holder.bind(getItem(position))

    inner class ViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.addon_title)
        private val subtitle: TextView = view.findViewById(R.id.addon_subtitle)
        private val toggle: MaterialSwitch = view.findViewById(R.id.addon_toggle)
        private val delete: ImageButton = view.findViewById(R.id.addon_delete)

        fun bind(item: AddonListItem) {
            title.text = item.title
            subtitle.text = item.subtitle
            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = item.isEnabled
            toggle.isEnabled = item.isValid
            toggle.setOnCheckedChangeListener { _, isChecked -> onToggled(item, isChecked) }
            delete.setOnClickListener { onDeleteRequested(item) }
            // valid addons open a settings screen; the manifest may declare a schema, and
            // the raw JSON editor is always available
            itemView.setOnClickListener { if (item.isValid) onConfigureRequested(item) }
        }
    }

    companion object {
        private val diffCallback =
            object : DiffUtil.ItemCallback<AddonListItem>() {
                override fun areItemsTheSame(
                    oldItem: AddonListItem,
                    newItem: AddonListItem,
                ) = oldItem.name == newItem.name

                override fun areContentsTheSame(
                    oldItem: AddonListItem,
                    newItem: AddonListItem,
                ) = oldItem == newItem
            }
    }
}
