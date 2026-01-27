/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser.search

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CheckResult
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import anki.search.BrowserColumns.Sorting
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.R
import com.ichi2.anki.browser.BrowserColumnKey
import com.ichi2.anki.browser.CardBrowserColumn
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.ColumnType
import com.ichi2.anki.browser.getLabel
import com.ichi2.anki.browser.humanReadableExplanation
import com.ichi2.anki.browser.search.SortOrderBottomSheetFragment.ColumnUiModel.AnkiColumn
import com.ichi2.anki.databinding.FragmentBottomSheetListBinding
import com.ichi2.anki.databinding.ViewBrowserSortOrderBottomSheetItemBinding
import com.ichi2.anki.databinding.ViewBrowserSortOrderSectionHeaderBinding
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.SortType
import com.ichi2.anki.utils.ext.behavior
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

private typealias Reverse = Boolean?

/**
 * A [BottomSheetDialogFragment] allowing selection of the sort order of the Card Browser
 *
 * @param viewModelProviderFactory A factory producing a [CardBrowserViewModel]
 */
class SortOrderBottomSheetFragment(
    private val viewModelProviderFactory: ViewModelProvider.Factory = ViewModelProvider.NewInstanceFactory(),
) : BottomSheetDialogFragment(R.layout.fragment_bottom_sheet_list) {
    @VisibleForTesting
    val viewModel: CardBrowserViewModel by activityViewModels { viewModelProviderFactory }

    @VisibleForTesting
    val binding by viewBinding(FragmentBottomSheetListBinding::bind)

    @VisibleForTesting
    lateinit var currentSortType: SortType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.currentSortType =
            requireNotNull(
                BundleCompat.getParcelable(requireArguments(), ARG_CURRENT_SORT_TYPE, SortType::class.java),
            ) { ARG_CURRENT_SORT_TYPE }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        with(binding.title) {
            isVisible = true
            text = getString(R.string.card_browser_change_display_order_title)
        }

        with(this.behavior) {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isDraggable = false
        }

        val adapter =
            SortOrderHolderAdapter(
                columns = ColumnUiModel.buildList(viewModel),
                currentlySelectedSort = currentSortType,
            )

        adapter.onItemClickedListener = { sortType ->
            Timber.i("selected sort type '%s'", sortType)
            viewModel.setSortType(sortType)
            dismiss()
        }

        binding.list.adapter = adapter
    }

    /**
     * Display the dialog, adding the fragment to the given [FragmentManager].
     *
     * @param manager The [FragmentManager] this fragment will be added to.
     *
     * @see BottomSheetDialogFragment.show
     */
    fun show(manager: FragmentManager) = this.show(manager, TAG)

    /**
     * The data to display a column (or 'no ordering') for selection as the sort column
     */
    sealed class ColumnUiModel {
        data object NoOrdering : ColumnUiModel()

        /** A non-interactive section header placed between groups of columns. */
        data class SectionHeader(
            @StringRes val titleRes: Int,
        ) : ColumnUiModel()

        /**
         * @see anki.search.BrowserColumns.Column
         */
        data class AnkiColumn(
            val key: BrowserColumnKey,
            val label: String,
            val tooltipValue: String?,
            val canBeSorted: Boolean,
            val isShownInUI: Boolean,
            val type: ColumnType,
        ) : ColumnUiModel() {
            override fun toString() = this.label
        }

        /**
         * Whether the item is usable in the current state
         *
         * 'Question' is unavailable in Cards mode
         */
        val available: Boolean
            get() =
                when (this) {
                    is NoOrdering -> true
                    is SectionHeader -> false
                    is AnkiColumn -> this.canBeSorted
                }

        val tooltip: String? get() =
            when (this) {
                NoOrdering, is SectionHeader -> null
                is AnkiColumn -> this.tooltipValue
            }

        @CheckResult
        fun getLabel(context: Context): String =
            when (this) {
                NoOrdering -> context.getString(R.string.card_browser_order_no_sorting_title)
                is SectionHeader -> context.getString(this.titleRes)
                is AnkiColumn -> this.label
            }

        @CheckResult
        fun toSortType(reverse: Reverse): SortType =
            when (this) {
                is NoOrdering -> SortType.NoOrdering
                is SectionHeader -> error("SectionHeader has no SortType")
                is AnkiColumn ->
                    SortType.CollectionOrdering(
                        key = this.key,
                        reverse = requireNotNull(reverse),
                    )
            }

        companion object {
            @CheckResult
            fun buildList(viewModel: CardBrowserViewModel): List<ColumnUiModel> {
                // obtain the columns
                val allColumns = viewModel.flowOfAllColumns.value.values

                // start with our default order. Anything unknown is at the end of the list
                val orderIndex = CardBrowserColumn.entries.withIndex().associate { it.value.ankiColumnKey to it.index }
                val sortedColumns = allColumns.sortedWith(compareBy { orderIndex[it.key] ?: Int.MAX_VALUE })

                // active columns are visible in the UI. These come first, in UI order
                val activeColumnMap = viewModel.activeColumns.withIndex().associate { it.value.ankiColumnKey to it.index }
                val sortedWithActive = sortedColumns.sortedWith(compareBy { activeColumnMap[it.key] ?: Int.MAX_VALUE })

                // columns not in [CardBrowserColumn] fall back to [ColumnType.UNSPECIFIED]
                val typeByKey = CardBrowserColumn.entries.associate { it.ankiColumnKey to it.type }

                val ankiColumnsList =
                    sortedWithActive.map { column ->
                        // some Anki columns can't be sorted on.
                        // Display them, but mark them as unavailable
                        val canBeSorted =
                            when (viewModel.cardsOrNotes) {
                                CardsOrNotes.CARDS -> column.sortingCards != Sorting.SORTING_NONE
                                CardsOrNotes.NOTES -> column.sortingNotes != Sorting.SORTING_NONE
                            }

                        // TODO: tooltip
                        val label = column.getLabel(viewModel.cardsOrNotes)
                        // val tooltip = it.getTooltip(viewModel.cardsOrNotes)

                        val isActive = activeColumnMap.containsKey(column.key)

                        AnkiColumn(
                            key = BrowserColumnKey.from(column),
                            label = label,
                            tooltipValue = null,
                            isShownInUI = isActive,
                            canBeSorted = canBeSorted,
                            type = typeByKey[column.key] ?: ColumnType.UNSPECIFIED,
                        )
                    }

                // three groups: shown in the browser, sortable but hidden, unsortable
                val selected = ankiColumnsList.filter { it.isShownInUI && it.canBeSorted }
                val available = ankiColumnsList.filter { !it.isShownInUI && it.canBeSorted }
                val unavailable = ankiColumnsList.filter { !it.canBeSorted }

                return buildList {
                    add(NoOrdering)
                    if (selected.isNotEmpty()) {
                        add(SectionHeader(R.string.user_active_columns))
                        addAll(selected)
                    }
                    if (available.isNotEmpty()) {
                        add(SectionHeader(R.string.user_potential_columns))
                        addAll(available)
                    }
                    if (unavailable.isNotEmpty()) {
                        add(SectionHeader(R.string.card_browser_order_section_unavailable))
                        addAll(unavailable)
                    }
                }
            }
        }
    }

    /**
     * @see ViewBrowserSortOrderBottomSheetItemBinding
     */
    @VisibleForTesting
    inner class SortOrderHolderAdapter(
        @VisibleForTesting
        val columns: List<ColumnUiModel>,
        private val currentlySelectedSort: SortType,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var onItemClickedListener: ((SortType) -> Unit) = { }

        override fun getItemViewType(position: Int): Int =
            when (columns[position]) {
                is ColumnUiModel.SectionHeader -> VIEW_TYPE_HEADER
                else -> VIEW_TYPE_COLUMN
            }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                VIEW_TYPE_HEADER ->
                    HeaderHolder(ViewBrowserSortOrderSectionHeaderBinding.inflate(inflater, parent, false))
                else ->
                    Holder(ViewBrowserSortOrderBottomSheetItemBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(
            holder: RecyclerView.ViewHolder,
            position: Int,
        ) {
            when (holder) {
                is HeaderHolder -> {
                    val header = columns[position] as ColumnUiModel.SectionHeader
                    holder.binding.sectionHeader.setText(header.titleRes)
                }
                is Holder -> bindColumnHolder(holder, position)
            }
        }

        private fun bindColumnHolder(
            holder: Holder,
            position: Int,
        ) {
            val column = this.columns[position]
            val context = holder.binding.root.context

            setupAvailability(holder, available = column.available)

            // highlight the current row
            holder.binding.root.background =
                if (column.isCurrentSortOrder()) {
                    ContextCompat.getDrawable(context, R.drawable.background_sort_order_selected_row)
                } else {
                    null
                }

            holder.binding.text.text = column.getLabel(context)

            setupSubtitle(column, holder)
            setupSortControls(column, holder)
        }

        private fun setupSubtitle(
            column: ColumnUiModel,
            holder: Holder,
        ) {
            val sort = currentlySelectedSort as? SortType.CollectionOrdering
            val subtitleRes: Int? =
                when (column) {
                    // No Ordering's subtitle is 'Faster'
                    is ColumnUiModel.NoOrdering -> R.string.card_browser_order_no_sorting_subtitle
                    // the selected column has an explanation of the sort: "Low to high"
                    is AnkiColumn if sort != null && sort.key == column.key ->
                        column.type.humanReadableExplanation(descending = sort.reverse)
                    // if a column is unavailable, the subtitle is the reason that it's unavailable
                    is AnkiColumn if !column.canBeSorted -> column.sortUnavailableReason
                    else -> null
                }

            if (subtitleRes != null) {
                holder.binding.subtitle.setText(subtitleRes)
            }
            holder.binding.subtitle.isVisible = subtitleRes != null
        }

        private fun setupSortControls(
            column: ColumnUiModel,
            holder: Holder,
        ) {
            val pill = holder.binding.sortPill
            val root = holder.binding.root

            when (column) {
                is ColumnUiModel.SectionHeader -> return // rendered by HeaderHolder
                is AnkiColumn -> {
                    pill.isVisible = true

                    pill.columnType = column.type
                    val sort = currentlySelectedSort as? SortType.CollectionOrdering
                    val activeReverse = if (sort != null && sort.key == column.key) sort.reverse else null
                    pill.activeReverse = activeReverse
                    pill.isEnabled = column.available
                    pill.onDirectionClicked = { reverse ->
                        Timber.i("sort direction clicked: reverse=%s for column %s", reverse, column)
                        onItemClickedListener(column.toSortType(reverse))
                    }

                    // Row tap shortcut: select with ascending if not yet selected; flip direction
                    // if already selected. The pill halves keep their own listeners and consume
                    // their own taps, so this only fires for taps outside the pill.
                    root.isClickable = column.available
                    if (column.available) {
                        root.setOnClickListener {
                            val nextReverse = activeReverse?.not() ?: false
                            Timber.i("row tap: column=%s nextReverse=%s", column, nextReverse)
                            onItemClickedListener(column.toSortType(nextReverse))
                        }
                    } else {
                        root.setOnClickListener(null)
                    }
                }
                is ColumnUiModel.NoOrdering -> {
                    pill.isVisible = false

                    // the whole row is tappable for the NoOrdering entry
                    root.isClickable = true
                    root.setOnClickListener {
                        if (column.isCurrentSortOrder()) {
                            Timber.i("ignored click on NoOrdering row (already selected)")
                            return@setOnClickListener
                        }
                        Timber.i("NoOrdering row clicked")
                        onItemClickedListener(column.toSortType(null))
                    }
                }
            }
        }

        /**
         * Reflects whether a row's column is sortable in the current state.
         *
         * Dims the title, pill, and leading icon when unavailable to signal disabled state,
         * but keeps the subtitle at full opacity so the explanation of *why* it's unavailable
         * stays readable.
         */
        private fun setupAvailability(
            holder: Holder,
            available: Boolean,
        ) {
            holder.binding.root.isEnabled = available
            val disabledAlpha = if (available) 1.0f else 0.4f
            holder.binding.text.alpha = disabledAlpha
            holder.binding.sortPill.alpha = disabledAlpha
            holder.binding.subtitle.alpha = 1.0f
        }

        private fun ColumnUiModel.isCurrentSortOrder() =
            when (this) {
                is ColumnUiModel.SectionHeader -> false
                is ColumnUiModel.NoOrdering -> currentlySelectedSort is SortType.NoOrdering
                is AnkiColumn ->
                    currentlySelectedSort is SortType.CollectionOrdering &&
                        this.key == currentlySelectedSort.key
            }

        override fun getItemCount() = columns.size

        inner class Holder(
            val binding: ViewBrowserSortOrderBottomSheetItemBinding,
        ) : RecyclerView.ViewHolder(binding.root)

        inner class HeaderHolder(
            val binding: ViewBrowserSortOrderSectionHeaderBinding,
        ) : RecyclerView.ViewHolder(binding.root)
    }

    companion object {
        const val TAG = "SortOrderBottomSheetFragment"

        const val ARG_CURRENT_SORT_TYPE = "currentSortType"

        private const val VIEW_TYPE_COLUMN = 0
        private const val VIEW_TYPE_HEADER = 1

        suspend fun createInstance(cardsOrNotes: CardsOrNotes) =
            SortOrderBottomSheetFragment().apply {
                val sortData = SortType.build(cardsOrNotes)

                Timber.i("creating SortOrderBottomSheetFragment with %s", sortData)

                arguments = bundleOf(ARG_CURRENT_SORT_TYPE to sortData)
            }
    }
}

/**
 * The reason why a key is unavailable for sorting
 *
 * This assumes that the column is already confirmed to be unavailable
 * (e.g. FSRS columns return a reason, but are usable in cards mode)
 */
@get:StringRes
private val AnkiColumn.sortUnavailableReason: Int?
    get() =
        when (this.key.value) {
            CardBrowserColumn.QUESTION.ankiColumnKey,
            CardBrowserColumn.ANSWER.ankiColumnKey,
            -> R.string.card_browser_order_subtitle_use_sort_field
            CardBrowserColumn.FSRS_DIFFICULTY.ankiColumnKey,
            CardBrowserColumn.FSRS_STABILITY.ankiColumnKey,
            CardBrowserColumn.FSRS_RETRIEVABILITY.ankiColumnKey,
            -> R.string.card_browser_order_subtitle_cards_mode_only
            else -> null
        }
