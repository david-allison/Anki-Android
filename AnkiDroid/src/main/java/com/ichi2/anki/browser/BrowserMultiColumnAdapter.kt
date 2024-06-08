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

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.ThemeUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import anki.search.BrowserRow
import anki.search.BrowserRow.Color
import com.ichi2.anki.AnkiDroidApp.Companion.sharedPrefs
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber
import kotlin.math.abs

typealias RowIsSelected = Boolean

/**
 * An adapter over a list of [CardOrNoteId].
 *
 * This has two states: regular and multi-select
 */
class BrowserMultiColumnAdapter(
    private val context: Context,
    // TODO: move move to the ViewModel
    private val viewModel: CardBrowserViewModel,
    private val browserRowTransformation: (CardOrNoteId) -> Pair<BrowserRow, RowIsSelected>,
    private val onLongPress: (CardOrNoteId) -> Unit,
    private val onTap: (CardOrNoteId) -> Unit
) : RecyclerView.Adapter<BrowserMultiColumnAdapter.MultiColumnViewHolder>() {

    val fontSizeScalePercent =
        sharedPrefs().getInt("relativeCardBrowserFontSize", DEFAULT_FONT_SIZE_RATIO)

    private val rowCollection: BrowserRowCollection
        get() = viewModel.cards

    private var originalTextSize = -1.0f

    inner class MultiColumnViewHolder(holder: View) : RecyclerView.ViewHolder(holder) {

        var id: CardOrNoteId? = null
        private val checkBoxView = this.itemView.findViewById<CheckBox>(R.id.card_checkbox)
        private val firstColumnView = this.itemView.findViewById<TextView>(R.id.card_sfld)
        private val secondColumnView = this.itemView.findViewById<TextView>(R.id.card_column2)

        init {
            this.itemView.setOnClickListener {
                id?.let { id ->
                    Timber.d("Tapped: %s", id)
                    onTap(id)
                }
            }
            this.itemView.setOnLongClickListener {
                val id = id ?: return@setOnLongClickListener false
                Timber.d("Long press: %s", id)
                onLongPress(id)
                return@setOnLongClickListener true
            }

            checkBoxView.setOnClickListener {
                id?.let { id ->
                    Timber.d("Tapped on checkbox: %s", id)
                    onTap(id)
                }
            }

            firstColumnView.setupTextSize()
            secondColumnView.setupTextSize()
        }

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

        fun setInMultiSelect(inMultiSelect: Boolean) {
            checkBoxView.isVisible = inMultiSelect
        }

        fun setIsSelected(value: RowIsSelected) {
            checkBoxView.isChecked = value
        }

        fun setColor(colorValue: Int) {
            itemView.setBackgroundColor(colorValue)
        }

        fun setIsTruncated(truncated: Boolean) {
            firstColumnView.setIsTruncated(truncated)
            secondColumnView.setIsTruncated(truncated)
        }

        fun setIsDeleted(isDeleted: Boolean) {
            // TODO: Grey out text color if deleted
        }

        private fun TextView.setIsTruncated(isTruncated: Boolean) {
            if (isTruncated) {
                maxLines = LINES_VISIBLE_WHEN_COLLAPSED
                ellipsize = TextUtils.TruncateAt.END
            } else {
                maxLines = Int.MAX_VALUE
                ellipsize = null
            }
        }

        fun TextView.setupTextSize() {
            // Set the font and font size for a TextView v
            val currentSize = textSize
            if (originalTextSize < 0) {
                originalTextSize = currentSize
            }
            // do nothing when pref is 100% and apply scaling only once
            if (fontSizeScalePercent != 100 && abs(originalTextSize - currentSize) < 0.1) {
                // getTextSize returns value in absolute PX so use that in the setter
                setTextSize(TypedValue.COMPLEX_UNIT_PX, originalTextSize * (fontSizeScalePercent / 100.0f))
            }
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
        val id = try {
            rowCollection[position]
        } catch (e: Exception) {
            Timber.w(e)
            return
        }

        try {
            val (row, isSelected) = browserRowTransformation(id)
            holder.firstColumn = row.getCells(0).text
            holder.secondColumn = row.getCells(1).text
            holder.setIsSelected(isSelected)
            holder.setColor(backendColorToColor(row.color))
            holder.setIsDeleted(false)
        } catch (e: BackendException) {
            holder.firstColumn = e.localizedMessage
            holder.secondColumn = e.localizedMessage
            // deleted rows cannot be selected
            holder.setColor(backendColorToColor(Color.UNRECOGNIZED))
            // deleted rows may not be selected
            holder.setIsSelected(false)
            holder.setIsDeleted(true)
        }

        holder.setInMultiSelect(viewModel.isInMultiSelectMode)
        holder.setIsTruncated(viewModel.isTruncated)
        holder.id = id
    }

    private fun backendColorToColor(color: Color): Int {
        return when (color) {
            Color.COLOR_FLAG_RED -> context.getColor(Flag.RED.browserColorRes!!)
            Color.COLOR_FLAG_ORANGE -> context.getColor(Flag.ORANGE.browserColorRes!!)
            Color.COLOR_FLAG_GREEN -> context.getColor(Flag.GREEN.browserColorRes!!)
            Color.COLOR_FLAG_BLUE -> context.getColor(Flag.BLUE.browserColorRes!!)
            Color.COLOR_FLAG_PINK -> context.getColor(Flag.PINK.browserColorRes!!)
            Color.COLOR_FLAG_TURQUOISE -> context.getColor(Flag.TURQUOISE.browserColorRes!!)
            Color.COLOR_FLAG_PURPLE -> context.getColor(Flag.PURPLE.browserColorRes!!)

            Color.COLOR_SUSPENDED -> ThemeUtils.getThemeAttrColor(context, R.attr.suspendedColor)
            Color.COLOR_MARKED -> ThemeUtils.getThemeAttrColor(context, R.attr.markedColor)

            // TODO
            Color.COLOR_BURIED -> ThemeUtils.getThemeAttrColor(context, android.R.attr.colorBackground)

            Color.COLOR_DEFAULT, Color.UNRECOGNIZED ->
                ThemeUtils.getThemeAttrColor(context, android.R.attr.colorBackground)
        }
    }

    companion object {
        private const val DEFAULT_FONT_SIZE_RATIO = 100

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val LINES_VISIBLE_WHEN_COLLAPSED = 3
    }
}
