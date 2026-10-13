// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** The same handle dragging, item animation and edge scrolling as Manage columns, with Compose rows. */
@Composable
internal fun EditorToolbarList(
    items: List<String>,
    onMove: (String, String) -> Unit,
    canMove: (String, String) -> Boolean = { _, _ -> true },
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    row: @Composable (String, Int, Boolean, () -> Unit, (Int) -> Boolean) -> Unit,
) {
    val composition = rememberCompositionContext()
    val currentHeader by rememberUpdatedState(header)
    val currentRow by rememberUpdatedState(row)
    val currentMove by rememberUpdatedState(onMove)
    val currentCanMove by rememberUpdatedState(canMove)
    var firstVisible by rememberSaveable { mutableIntStateOf(0) }
    var firstOffset by rememberSaveable { mutableIntStateOf(0) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            RecyclerView(context).apply {
                val manager = LinearLayoutManager(context)
                layoutManager = manager
                val buttons =
                    EditorToolbarAdapter(
                        items,
                        composition,
                        onMove = { from, to -> currentMove(from, to) },
                        canMove = { from, to -> currentCanMove(from, to) },
                        header = { currentHeader() },
                        row = { item, index, dragging, startDrag, move -> currentRow(item, index, dragging, startDrag, move) },
                    )
                adapter = buttons
                buttons.touchHelper.attachToRecyclerView(this)
                manager.scrollToPositionWithOffset(firstVisible, firstOffset)
                addOnScrollListener(
                    object : RecyclerView.OnScrollListener() {
                        override fun onScrolled(
                            recyclerView: RecyclerView,
                            dx: Int,
                            dy: Int,
                        ) {
                            val first = manager.findFirstVisibleItemPosition()
                            if (first != RecyclerView.NO_POSITION) {
                                firstVisible = first
                                firstOffset = manager.findViewByPosition(first)?.top ?: 0
                            }
                        }
                    },
                )
            }
        },
        update = { list -> (list.adapter as EditorToolbarAdapter).updateItems(items) },
        onRelease = { list ->
            (list.adapter as EditorToolbarAdapter).touchHelper.attachToRecyclerView(null)
            list.adapter = null
        },
    )
}

private class EditorToolbarAdapter(
    initialItems: List<String>,
    private val composition: CompositionContext,
    private val onMove: (String, String) -> Unit,
    private val canMove: (String, String) -> Boolean,
    private val header: @Composable () -> Unit,
    private val row: @Composable (String, Int, Boolean, () -> Unit, (Int) -> Boolean) -> Unit,
) : RecyclerView.Adapter<EditorToolbarAdapter.Holder>() {
    private val items = mutableStateListOf<String>().apply { addAll(initialItems) }
    private var draggingKey by mutableStateOf<String?>(null)
    val touchHelper =
        ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
                override fun getDragDirs(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ): Int = if (viewHolder.bindingAdapterPosition > 0) super.getDragDirs(recyclerView, viewHolder) else 0

                override fun canDropOver(
                    recyclerView: RecyclerView,
                    current: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean {
                    val from = items.getOrNull(current.bindingAdapterPosition - 1) ?: return false
                    val to = items.getOrNull(target.bindingAdapterPosition - 1) ?: return false
                    return canMove(from, to)
                }

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean = move(viewHolder.bindingAdapterPosition - 1, target.bindingAdapterPosition - 1)

                override fun onSelectedChanged(
                    viewHolder: RecyclerView.ViewHolder?,
                    actionState: Int,
                ) {
                    super.onSelectedChanged(viewHolder, actionState)
                    draggingKey = (viewHolder as? Holder)?.item
                }

                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder,
                    direction: Int,
                ) = Unit
            },
        )

    private fun move(
        from: Int,
        to: Int,
    ): Boolean {
        if (from !in items.indices || to !in items.indices || from == to) return false
        val source = items[from]
        val destination = items[to]
        if (!canMove(source, destination)) return false
        items.add(to, items.removeAt(from))
        // Rebinding during a drag can end it early. Compose observes the order/selection state directly.
        notifyItemMoved(from + 1, to + 1)
        onMove(source, destination)
        return true
    }

    /** Toggling visibility moves a row between groups; keep RecyclerView in sync without rebinding a drag. */
    fun updateItems(updated: List<String>) {
        val previous = items.toList()
        if (previous == updated) return
        val difference =
            DiffUtil.calculateDiff(
                object : DiffUtil.Callback() {
                    override fun getOldListSize() = previous.size + 1

                    override fun getNewListSize() = updated.size + 1

                    override fun areItemsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ) = if (oldItemPosition == 0 || newItemPosition == 0) {
                        oldItemPosition == newItemPosition
                    } else {
                        previous[oldItemPosition - 1] == updated[newItemPosition - 1]
                    }

                    override fun areContentsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ) = true
                },
            )
        items.clear()
        items.addAll(updated)
        difference.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = items.size + 1

    override fun getItemViewType(position: Int): Int = if (position == 0) HEADER else BUTTON

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): Holder =
        Holder(
            ComposeView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setParentCompositionContext(composition)
            },
            isHeader = viewType == HEADER,
        )

    override fun onBindViewHolder(
        holder: Holder,
        position: Int,
    ) {
        holder.item = if (position == 0) null else items[position - 1]
    }

    inner class Holder(
        view: ComposeView,
        isHeader: Boolean,
    ) : RecyclerView.ViewHolder(view) {
        var item by mutableStateOf<String?>(null)

        init {
            view.setContent {
                if (isHeader) {
                    header()
                } else {
                    item?.let { button ->
                        row(
                            button,
                            items.indexOfFirst { it == button },
                            draggingKey == button,
                            { touchHelper.startDrag(this@Holder) },
                            { offset ->
                                val index = items.indexOfFirst { it == button }
                                move(index, index + offset)
                            },
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val HEADER = 0
        private const val BUTTON = 1
    }
}
