// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.dialogs.customstudy

import androidx.annotation.VisibleForTesting
import anki.scheduler.CustomStudyDefaultsResponse
import com.ichi2.anki.CollectionManager.TR

/**
 * Default values for extending deck limits, and default tag selection
 *
 * Adapter which documents [anki.scheduler.CustomStudyDefaultsResponse]
 *
 * Upstream: [sched.proto: CustomStudyDefaultsResponse](https://github.com/search?q=repo%3Aankitects%2Fanki+CustomStudyDefaultsResponse+language%3A%22Protocol+Buffer%22&type=code&l=Protocol+Buffer)
 */
@VisibleForTesting
class CustomStudyDefaults(
    val extendNew: ExtendLimits,
    val extendReview: ExtendLimits,
    @Suppress("unused")
    val tags: List<CustomStudyDefaultsResponse.Tag>,
) {
    /** Available new cards: 1 (2 in subdecks) */
    fun labelForNewQueueAvailable(): String = TR.customStudyAvailableNewCards2(extendNew.labelForCountWithChildren())

    /** Available review cards: 1 (2 in subdecks) */
    fun labelForReviewQueueAvailable(): String = TR.customStudyAvailableReviewCards2(extendReview.labelForCountWithChildren())

    /**
     * Data displayed to a user wanting to temporarily extend the daily limits of
     * either new/review cards for a deck
     *
     * Displays `Available new cards: 1 (2 in subdecks)` when a limit is reached with remaining cards:
     * ```
     * Deck (1)
     *   Deck::Child1 (1)
     *   Deck::Child2 (1)
     * ```
     */
    class ExtendLimits(
        /** The initial value to display in the input dialog */
        val initialValue: Int,
        /**
         * The number of pending cards in only the parent deck
         *
         * **Example**
         * Returns **1** when a limit is reached with remaining cards:
         * ```
         * Deck (1)
         *   Deck::Child1 (1)
         *   Deck::Child2 (1)
         * ```
         */
        val available: Int,
        /**
         * The sum of cards in only the child decks
         *
         * **Example**
         * * Returns **2** when a limit is reached  with remaining cards:
         * ```
         * Deck (1)
         *   Deck::Child1 (1)
         *   Deck::Child2 (1)
         * ```
         */
        val availableInChildren: Int,
    ) {
        /**
         * **Temporarily Disabled** - logic may be incorrect
         *
         * "Extend" only has an effect if there are pending cards in the target deck
         *
         * The number of pending cards in child decks is only informative
         */
        val isUsable
            get() = true // TODO: Confirm `available > 0` is correct; user feedback states subdecks are taken into account

        /**
         * A string representing the count of cards which have exceeded a deck limit
         *
         * `123 (456 in subdecks)` or `123`
         *
         * For use in either
         * [net.ankiweb.rsdroid.Translations.customStudyAvailableReviewCards2] or
         * [net.ankiweb.rsdroid.Translations.customStudyAvailableNewCards2]
         *
         */
        fun labelForCountWithChildren(): String =
            if (availableInChildren == 0) {
                available.toString()
            } else {
                "$available ${TR.customStudyAvailableChildCount(availableInChildren)}"
            }
    }

    companion object {
        fun CustomStudyDefaultsResponse.toDomainModel(): CustomStudyDefaults =
            CustomStudyDefaults(
                extendNew =
                    ExtendLimits(
                        initialValue = extendNew,
                        available = availableNew,
                        availableInChildren = availableNewInChildren,
                    ),
                extendReview =
                    ExtendLimits(
                        initialValue = extendReview,
                        available = availableReview,
                        availableInChildren = availableReviewInChildren,
                    ),
                tags = this.tagsList,
            )
    }
}
