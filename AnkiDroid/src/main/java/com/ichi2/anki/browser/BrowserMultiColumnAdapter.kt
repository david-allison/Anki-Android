/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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
import androidx.recyclerview.widget.RecyclerView
import anki.search.BrowserRow
import com.ichi2.anki.R
import timber.log.Timber

/**
 * An adapter over a list of [CardOr]
 */
class BrowserMultiColumnAdapter(
    @Suppress("UNUSED_PARAMETER") sflRelativeFontSize: Int,
    private val rowCollection: BrowserRowCollection,
    val browserRowTransformation: (CardOrNoteId) -> BrowserRow
) : RecyclerView.Adapter<BrowserMultiColumnAdapter.MultiColumnViewHolder>() {

    class MultiColumnViewHolder(holder: View) : RecyclerView.ViewHolder(holder) {

        private val firstColumnView = this.itemView.findViewById<TextView>(R.id.card_sfld)
        private val secondColumnView = this.itemView.findViewById<TextView>(R.id.card_column2)

        // TODO: Can we delegate here?
        var firstColumn: CharSequence?
            get() = firstColumnView.text
            set(value) {
                firstColumnView.text = value
            }

        var secondColumn: CharSequence?
            get() = secondColumnView.text
            set(value) {
                secondColumnView.text = value
            }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MultiColumnViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_item_browser, parent, false)
        return MultiColumnViewHolder(view)
    }

    override fun getItemCount(): Int {
        return rowCollection.size
    }

    override fun onBindViewHolder(holder: MultiColumnViewHolder, position: Int) {
        val row = try {
            browserRowTransformation(rowCollection[position])
        } catch (e: Exception) {
            return
        }
        Timber.w("onBindViewHolder: %s")
        holder.firstColumn = row.getCells(0).text
        holder.secondColumn = row.getCells(1).text
    }
}
