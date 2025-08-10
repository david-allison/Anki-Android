/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.browser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.ichi2.anki.R
import com.ichi2.anki.utils.ext.findViewById
import com.ichi2.ui.FixedTextView

/**
 * Adapter for when the SearchView of the CardBrowser/CardBrowserFragment is expanded
 */
class ExpandedSearchViewAdapter(
    val viewModel: CardBrowserViewModel,
) : ListAdapter<ExpandedSearchViewItem, RecyclerView.ViewHolder>(ExpandedSearchViewItemDiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ViewType.SaveSearch.viewType -> {
                val itemView =
                    inflater.inflate(R.layout.browser_search_expanded_current_search, parent, false)
                SaveSearchViewHolder(itemView)
            }
            ViewType.SavedSearchHeader.viewType -> {
                val itemView =
                    inflater.inflate(R.layout.browser_search_expanded_text_button, parent, false)
                SearchHeaderViewHolder(itemView)
            }
            ViewType.SavedSearchItem.viewType -> {
                val itemView =
                    inflater.inflate(R.layout.browser_search_expanded_saved_item, parent, false)
                SearchItemViewHolder(itemView)
            }
            else -> throw IllegalArgumentException("invalid viewType: $viewType")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        val item = getItem(position)
        when (holder) {
            is SaveSearchViewHolder -> holder.bind((item as ExpandedSearchViewItem.SaveSearch))
            is SearchHeaderViewHolder -> holder.bind((item as ExpandedSearchViewItem.SavedSearchHeader))
            is SearchItemViewHolder -> holder.bind((item as ExpandedSearchViewItem.SavedSearchItem))
        }
    }

    override fun getItemViewType(position: Int) =
        when (getItem(position)) {
            is ExpandedSearchViewItem.SaveSearch -> ViewType.SaveSearch.viewType
            is ExpandedSearchViewItem.SavedSearchHeader -> ViewType.SavedSearchHeader.viewType
            is ExpandedSearchViewItem.SavedSearchItem -> ViewType.SavedSearchItem.viewType
        }

    inner class SaveSearchViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        fun bind(search: ExpandedSearchViewItem.SaveSearch) {
            itemView.setOnClickListener { viewModel.launchSearchForCards(search.terms) }
            findViewById<FixedTextView>(R.id.title).apply {
                text = search.terms
            }
            findViewById<View>(R.id.save_search).setOnClickListener { viewModel.saveCurrentSearch(searchTerms = search.terms) }
        }
    }

    inner class SearchHeaderViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        fun bind(search: ExpandedSearchViewItem.SavedSearchHeader) {
            findViewById<MaterialTextView>(R.id.title).apply {
                text = itemView.context.getString(R.string.card_browser_list_my_searches)
            }
        }
    }

    inner class SearchItemViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: ExpandedSearchViewItem.SavedSearchItem) {
            val search = item.savedSearch
            itemView.setOnClickListener { viewModel.launchSearchForCards(search.terms) }
            findViewById<TextView>(R.id.title).text = search.name
            findViewById<TextView>(R.id.content).text = search.terms
        }
    }

    enum class ViewType(
        val viewType: Int,
    ) {
        SaveSearch(0),
        SavedSearchHeader(1),
        SavedSearchItem(2),
    }

    class ExpandedSearchViewItemDiffCallback : DiffUtil.ItemCallback<ExpandedSearchViewItem>() {
        override fun areItemsTheSame(
            oldItem: ExpandedSearchViewItem,
            newItem: ExpandedSearchViewItem,
        ): Boolean {
            if (oldItem::class !== newItem::class) return false

            return when (oldItem) {
                is ExpandedSearchViewItem.SavedSearchHeader -> true
                is ExpandedSearchViewItem.SaveSearch -> true
                is ExpandedSearchViewItem.SavedSearchItem -> {
                    return (newItem as ExpandedSearchViewItem.SavedSearchItem).savedSearch.name == oldItem.savedSearch.name
                }
            }
        }

        override fun areContentsTheSame(
            oldItem: ExpandedSearchViewItem,
            newItem: ExpandedSearchViewItem,
        ) = oldItem == newItem
    }
}
