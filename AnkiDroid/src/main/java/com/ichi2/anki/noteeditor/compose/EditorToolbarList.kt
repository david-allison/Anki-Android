// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.utils.android.getColorFromAttr

/** The same handle dragging, item animation and edge scrolling as Manage columns, with Compose rows. */
@Composable
internal fun EditorToolbarList(
    items: List<String>,
    activeCount: Int,
    onMove: (String, String) -> Unit,
    canMove: (String, String) -> Boolean = { _, _ -> true },
    modifier: Modifier = Modifier,
    onDraggingChange: (Boolean) -> Unit = {},
    bottomContentPadding: Dp = 0.dp,
    header: @Composable () -> Unit,
    row: @Composable (String, Int, Boolean, () -> Unit, (Int) -> Boolean) -> Unit,
) {
    val entries =
        buildList {
            add(ToolbarListEntry.Header)
            add(ToolbarListEntry.Section(CommonString.user_active_columns))
            addAll(items.take(activeCount).map { ToolbarListEntry.Button(it) })
            add(ToolbarListEntry.Section(CommonString.user_potential_columns))
            addAll(items.drop(activeCount).map { ToolbarListEntry.Button(it) })
        }
    val pageActive by rememberUpdatedState(LocalEditorFieldPageActive.current)
    val composition = rememberCompositionContext()
    val currentHeader by rememberUpdatedState(header)
    val currentRow by rememberUpdatedState(row)
    val currentDragging by rememberUpdatedState(onDraggingChange)
    val currentMove by rememberUpdatedState(onMove)
    val currentCanMove by rememberUpdatedState(canMove)
    var firstVisible by rememberSaveable { mutableIntStateOf(0) }
    var firstOffset by rememberSaveable { mutableIntStateOf(0) }
    val bottomPadding = with(LocalDensity.current) { bottomContentPadding.roundToPx() }
    AndroidView(
        modifier = modifier.testTag("editor_toolbar_list"),
        factory = { context ->
            RecyclerView(context).apply {
                clipToPadding = false
                val manager = LinearLayoutManager(context)
                layoutManager = manager
                val buttons =
                    EditorToolbarAdapter(
                        entries,
                        composition,
                        onDraggingChange = { currentDragging(it) },
                        onMove = { from, to -> currentMove(from, to) },
                        canMove = { from, to -> currentCanMove(from, to) },
                        header = { Box(if (pageActive) Modifier else Modifier.clearAndSetSemantics {}) { currentHeader() } },
                        row = { item, index, dragging, startDrag, move ->
                            Box(
                                if (pageActive) Modifier else Modifier.clearAndSetSemantics {},
                            ) { currentRow(item, index, dragging, startDrag, move) }
                        },
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
        update = { list ->
            if (list.paddingBottom != bottomPadding) list.setPadding(0, 0, 0, bottomPadding)
            list.importantForAccessibility =
                if (pageActive) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            (list.adapter as EditorToolbarAdapter).updateItems(entries)
        },
        onRelease = { list ->
            currentDragging(false)
            (list.adapter as EditorToolbarAdapter).touchHelper.attachToRecyclerView(null)
            list.adapter = null
        },
    )
}

private sealed interface ToolbarListEntry {
    data object Header : ToolbarListEntry

    data class Section(
        val label: Int,
    ) : ToolbarListEntry

    data class Button(
        val key: String,
    ) : ToolbarListEntry
}

/** Match the Manage columns headings, including their theme-specific accent colour. */
@Composable
private fun ToolbarSectionHeading(label: Int) {
    Text(
        stringResource(label),
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = Color(getColorFromAttr(LocalContext.current, androidx.preference.R.attr.preferenceCategoryTitleTextColor)),
    )
}

private class EditorToolbarAdapter(
    initialItems: List<ToolbarListEntry>,
    private val composition: CompositionContext,
    private val onDraggingChange: (Boolean) -> Unit,
    private val onMove: (String, String) -> Unit,
    private val canMove: (String, String) -> Boolean,
    private val header: @Composable () -> Unit,
    private val row: @Composable (String, Int, Boolean, () -> Unit, (Int) -> Boolean) -> Unit,
) : RecyclerView.Adapter<EditorToolbarAdapter.Holder>() {
    private val items = mutableStateListOf<ToolbarListEntry>().apply { addAll(initialItems) }
    private var draggingKey by mutableStateOf<String?>(null)
    val touchHelper =
        ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
                override fun getDragDirs(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ): Int {
                    val button = items.getOrNull(viewHolder.bindingAdapterPosition) as? ToolbarListEntry.Button ?: return 0
                    // ItemTouchHelper also starts drags on a long press outside the handle.
                    return if (canMove(button.key, button.key)) {
                        super.getDragDirs(recyclerView, viewHolder)
                    } else {
                        0
                    }
                }

                override fun canDropOver(
                    recyclerView: RecyclerView,
                    current: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean {
                    val from = items.getOrNull(current.bindingAdapterPosition) as? ToolbarListEntry.Button ?: return false
                    val to = items.getOrNull(target.bindingAdapterPosition) as? ToolbarListEntry.Button ?: return false
                    return canMove(from.key, to.key)
                }

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean = move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)

                override fun onSelectedChanged(
                    viewHolder: RecyclerView.ViewHolder?,
                    actionState: Int,
                ) {
                    super.onSelectedChanged(viewHolder, actionState)
                    draggingKey = ((viewHolder as? Holder)?.item as? ToolbarListEntry.Button)?.key
                    onDraggingChange(draggingKey != null)
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
        val source = items[from] as? ToolbarListEntry.Button ?: return false
        val destination = items[to] as? ToolbarListEntry.Button ?: return false
        if (!canMove(source.key, destination.key)) return false
        items.add(to, items.removeAt(from))
        // Rebinding during a drag can end it early. Compose observes the order/selection state directly.
        notifyItemMoved(from, to)
        onMove(source.key, destination.key)
        return true
    }

    /** Toggling visibility moves a row between groups; keep RecyclerView in sync without rebinding a drag. */
    fun updateItems(updated: List<ToolbarListEntry>) {
        val previous = items.toList()
        if (previous == updated) return
        val difference =
            DiffUtil.calculateDiff(
                object : DiffUtil.Callback() {
                    override fun getOldListSize() = previous.size

                    override fun getNewListSize() = updated.size

                    override fun areItemsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ) = previous[oldItemPosition] == updated[newItemPosition]

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

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = if (items[position] is ToolbarListEntry.Button) BUTTON else HEADER

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): Holder =
        Holder(
            ComposeView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setParentCompositionContext(composition)
            },
        )

    override fun onBindViewHolder(
        holder: Holder,
        position: Int,
    ) {
        holder.item = items[position]
    }

    inner class Holder(
        view: ComposeView,
    ) : RecyclerView.ViewHolder(view) {
        var item by mutableStateOf<ToolbarListEntry?>(null)

        init {
            view.setContent {
                when (val entry = item) {
                    ToolbarListEntry.Header -> header()
                    is ToolbarListEntry.Section -> {
                        Box(if (LocalEditorFieldPageActive.current) Modifier else Modifier.clearAndSetSemantics {}) {
                            ToolbarSectionHeading(entry.label)
                        }
                    }
                    is ToolbarListEntry.Button -> {
                        val buttons = items.filterIsInstance<ToolbarListEntry.Button>()
                        val index = buttons.indexOf(entry)
                        row(
                            entry.key,
                            index,
                            draggingKey == entry.key,
                            { touchHelper.startDrag(this@Holder) },
                            { offset ->
                                val destination = buttons.getOrNull(index + offset)
                                destination?.let { move(items.indexOf(entry), items.indexOf(it)) } ?: false
                            },
                        )
                    }
                    null -> Unit
                }
            }
        }
    }

    companion object {
        private const val HEADER = 0
        private const val BUTTON = 1
    }
}
