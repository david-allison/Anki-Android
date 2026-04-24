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

package com.ichi2.anki.browser.search.ui

import android.content.Context
import android.graphics.Outline
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.core.view.isVisible
import com.ichi2.anki.R
import com.ichi2.anki.browser.ColumnType

private const val MIN_TOUCH_TARGET_DP = 48
private const val VISIBLE_PILL_HEIGHT_DP = 32
private const val ELEVATION_DP = 1

/**
 * Segmented toggle for selecting a sort direction (ascending / descending).
 *
 * The pill contains two halves — [standardHalf] (ascending) and [reverseHalf] (descending) —
 * one of which may be "active" (filled with the primary color). Clicking the non-active half
 * invokes [onDirectionClicked].
 *
 * The arrow + label content is driven by [columnType]:
 * * [ColumnType.TEXT]: `A ↓` / `Z ↓` (start letter + descending arrow on both halves)
 * * [ColumnType.NUMERIC]: `1 ↓` / `9 ↓`
 * * [ColumnType.DATE] / [ColumnType.UNSPECIFIED]: `↑↑` / `↓↓` (double-arrow drawable, no labels)
 *
 * The halves are visually compact but each exposes a ≥48dp touch target via a composite
 * [TouchDelegate] installed on layout.
 */
class SortPill
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : LinearLayout(context, attrs, defStyleAttr) {
        @VisibleForTesting
        val standardHalf: LinearLayout

        @VisibleForTesting
        val reverseHalf: LinearLayout

        private val standardArrow: ImageView
        private val standardLabel: TextView
        private val reverseArrow: ImageView
        private val reverseLabel: TextView

        /** Drives the arrow drawables and label text. */
        var columnType: ColumnType = ColumnType.UNSPECIFIED
            set(value) {
                field = value
                applyColumnType()
            }

        /**
         * The currently active sort direction, or null when neither half is active.
         *
         * * `null` — neither half is filled
         * * `false` — [standardHalf] (ascending) is filled
         * * `true` — [reverseHalf] (descending) is filled
         */
        var activeReverse: Boolean? = null
            set(value) {
                field = value
                standardHalf.isActivated = value == false
                reverseHalf.isActivated = value == true
                updateEnabledStates()
            }

        /**
         * Invoked when the user clicks a half that is not the active direction.
         * `reverse = false` for the ascending half, `true` for the descending half.
         */
        var onDirectionClicked: ((reverse: Boolean) -> Unit)? = null

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(MIN_TOUCH_TARGET_DP)
            LayoutInflater.from(context).inflate(R.layout.view_sort_pill, this, true)

            // Elevation shadow follows the visible pill (a 32dp band centered in the
            // 48dp touch target) rather than the view's full rectangular bounds.
            elevation = dp(ELEVATION_DP).toFloat()
            outlineProvider =
                object : ViewOutlineProvider() {
                    override fun getOutline(
                        view: View,
                        outline: Outline,
                    ) {
                        val pillHeight = dp(VISIBLE_PILL_HEIGHT_DP)
                        val top = (view.height - pillHeight) / 2
                        outline.setRoundRect(
                            view.paddingLeft,
                            top,
                            view.width - view.paddingRight,
                            top + pillHeight,
                            pillHeight / 2f,
                        )
                    }
                }

            standardHalf = findViewById(R.id.sort_pill_standard)
            reverseHalf = findViewById(R.id.sort_pill_reverse)
            standardArrow = findViewById(R.id.sort_pill_standard_arrow)
            standardLabel = findViewById(R.id.sort_pill_standard_label)
            reverseArrow = findViewById(R.id.sort_pill_reverse_arrow)
            reverseLabel = findViewById(R.id.sort_pill_reverse_label)

            standardHalf.setOnClickListener {
                if (!standardHalf.isEnabled) return@setOnClickListener
                onDirectionClicked?.invoke(false)
            }
            reverseHalf.setOnClickListener {
                if (!reverseHalf.isEnabled) return@setOnClickListener
                onDirectionClicked?.invoke(true)
            }

            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> installTouchDelegates() }

            applyColumnType()
        }

        override fun setEnabled(enabled: Boolean) {
            super.setEnabled(enabled)
            updateEnabledStates()
        }

        private fun updateEnabledStates() {
            // a half is enabled when the pill itself is enabled AND that half is not already active
            standardHalf.isEnabled = isEnabled && activeReverse != false
            reverseHalf.isEnabled = isEnabled && activeReverse != true
        }

        private fun installTouchDelegates() {
            val minTouch = dp(MIN_TOUCH_TARGET_DP)
            val composite = TouchDelegateComposite(this)
            for (half in listOf(standardHalf, reverseHalf)) {
                val rect = Rect()
                half.getHitRect(rect)
                val yGrow = (minTouch - rect.height()).coerceAtLeast(0)
                val xGrow = (minTouch - rect.width()).coerceAtLeast(0)
                if (yGrow == 0 && xGrow == 0) continue
                rect.top -= yGrow / 2
                rect.bottom += yGrow - yGrow / 2
                rect.left -= xGrow / 2
                rect.right += xGrow - xGrow / 2
                composite.add(TouchDelegate(rect, half))
            }
            touchDelegate = composite
        }

        private fun applyColumnType() {
            when (columnType) {
                ColumnType.TEXT -> {
                    standardArrow.setImageResource(R.drawable.outline_arrow_downward_alt_24)
                    reverseArrow.setImageResource(R.drawable.outline_arrow_downward_alt_24)
                    standardArrow.isVisible = true
                    reverseArrow.isVisible = true
                    standardLabel.text = "A"
                    reverseLabel.text = "Z"
                    standardLabel.isVisible = true
                    reverseLabel.isVisible = true
                }
                ColumnType.NUMERIC -> {
                    standardArrow.setImageResource(R.drawable.outline_arrow_downward_alt_24)
                    reverseArrow.setImageResource(R.drawable.outline_arrow_downward_alt_24)
                    standardArrow.isVisible = true
                    reverseArrow.isVisible = true
                    standardLabel.text = "1"
                    reverseLabel.text = "9"
                    standardLabel.isVisible = true
                    reverseLabel.isVisible = true
                }
                ColumnType.DATE, ColumnType.UNSPECIFIED -> {
                    standardArrow.setImageResource(R.drawable.outline_arrow_downward_alt_24)
                    reverseArrow.setImageResource(R.drawable.outline_arrow_upward_alt_24)
                    standardArrow.isVisible = true
                    reverseArrow.isVisible = true
                    standardLabel.isVisible = false
                    reverseLabel.isVisible = false
                }
            }
            // The single-arrow Material assets have their path biased ~2% above the viewport
            // center, so they render visually higher than the adjacent label's cap-height.
            // Nudge them down to optically align with the letters. Double arrows are already
            // centered in their viewport and don't need the offset.
            val arrowNudgeY =
                when (columnType) {
                    ColumnType.TEXT, ColumnType.NUMERIC -> dp(1).toFloat()
                    ColumnType.DATE, ColumnType.UNSPECIFIED -> 0f
                }
            standardArrow.translationY = arrowNudgeY
            reverseArrow.translationY = arrowNudgeY

            // Optical centering nudge: the standard half's [letter][arrow] composition reads
            // slightly left of center because the arrow drawable has transparent right padding.
            // Add asymmetric paddingStart only for the text variants so the double-arrow
            // variants stay geometrically centered.
            val standardPaddingStart =
                when (columnType) {
                    ColumnType.TEXT, ColumnType.NUMERIC -> dp(4)
                    ColumnType.DATE, ColumnType.UNSPECIFIED -> 0
                }
            standardHalf.setPaddingRelative(
                standardPaddingStart,
                standardHalf.paddingTop,
                standardHalf.paddingEnd,
                standardHalf.paddingBottom,
            )
        }

        private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

        /**
         * [TouchDelegate] only supports one child per parent. This composite tries each
         * registered delegate in order until one handles the event.
         */
        private class TouchDelegateComposite(
            host: View,
        ) : TouchDelegate(Rect(), host) {
            private val delegates = mutableListOf<TouchDelegate>()

            fun add(delegate: TouchDelegate) {
                delegates.add(delegate)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                for (delegate in delegates) {
                    val copy = MotionEvent.obtain(event)
                    try {
                        if (delegate.onTouchEvent(copy)) return true
                    } finally {
                        copy.recycle()
                    }
                }
                return false
            }
        }
    }
