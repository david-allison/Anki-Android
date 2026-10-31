// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.services

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.canUserAccessDeck
import com.ichi2.anki.common.utils.ext.allDecksCounts
import com.ichi2.anki.libanki.EpochMilliseconds
import com.ichi2.anki.libanki.Utils
import com.ichi2.anki.libanki.sched.Counts
import com.ichi2.anki.reviewreminders.ReviewReminder
import com.ichi2.anki.reviewreminders.ReviewReminderId
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import com.ichi2.anki.reviewreminders.reminderLogPrefix
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.days

/** Validates reminder deliveries and queries due cards before invoking the platform notification. */
internal class ReviewReminderNotifier(
    private val scheduleNext: (ReviewReminder) -> Unit,
    private val sendNotification: suspend (ReviewReminder, dueCardsTotal: Int) -> Unit,
) {
    suspend fun notify(
        reviewReminderId: ReviewReminderId,
        reviewReminderScope: ReviewReminderScope,
        isRecurringNotification: Boolean,
    ) {
        val reminderForNotification =
            if (isRecurringNotification) {
                ReviewRemindersDatabase.retrieveRefreshedReminder(
                    reviewReminderId,
                    reviewReminderScope,
                )
            } else {
                ReviewRemindersDatabase.getRemindersForScope(reviewReminderScope)[reviewReminderId]
            }
        if (reminderForNotification == null) {
            Timber.i(
                "${reminderLogPrefix(reviewReminderId)} skip: Aborting notification for review reminder " +
                    "${reviewReminderId.value} (recurring: $isRecurringNotification) due to database validation failure.",
            )
            return
        }
        if (!reminderForNotification.enabled) {
            // This should never happen for recurring notifications because disabling a reminder should cancel all of its notification alarms, but we check just in case
            // It can happen for snoozed notifications if the user disables the reminder after snoozing it
            Timber.i(
                "${reminderLogPrefix(reviewReminderId)} skip: Aborting notification for review reminder " +
                    "${reviewReminderId.value} (recurring: $isRecurringNotification) because the reminder is disabled.",
            )
            return
        }

        if (isRecurringNotification) {
            Timber.d("Scheduling next review reminder notification for review reminder $reviewReminderId")
            // Because this scheduling is already the result of a notification, we do not trigger an immediate notification
            scheduleNext(reminderForNotification)
        }

        // Send the notification
        try {
            val dueCardsTotal = dueCardsForNotification(reminderForNotification) ?: return
            sendNotification(reminderForNotification, dueCardsTotal)
        } catch (e: BackendException) {
            // This may occur if the collection is blocked, in which case we should fail gracefully
            Timber.w(e, "${reminderLogPrefix(reviewReminderId)} Aborted review reminder notification due to backend exception")
        } catch (e: CancellationException) {
            Timber.w(
                e,
                "${reminderLogPrefix(reviewReminderId)} Aborted review reminder notification due to cancellation exception",
            )
            throw e // Rethrow to propagate to parent coroutine
        }
    }

    private suspend fun dueCardsForNotification(reviewReminder: ReviewReminder): Int? {
        Timber.i("${reminderLogPrefix(reviewReminder.id)} sendReviewReminderNotification for ${reviewReminder.id.value}")
        Timber.v("Review reminder: $reviewReminder")

        if (reviewReminder.scope is ReviewReminderScope.DeckSpecific) {
            val isDeckAccessible = canUserAccessDeck(reviewReminder.scope.did)
            if (!isDeckAccessible) {
                Timber.i("${reminderLogPrefix(reviewReminder.id)} skip: Deck with ID ${reviewReminder.scope.did} not found, aborting")
                return null
            }
        }

        // Cancel if the user wants notifications to only fire if no reviews have been done today AND there has been a review today
        if (reviewReminder.onlyNotifyIfNoReviews && wasScopeReviewedToday(reviewReminder.scope)) {
            Timber.i("${reminderLogPrefix(reviewReminder.id)} skip: Aborting notification due to onlyNotifyIfNoReviews")
            return null
        }

        val dueCardsCount =
            when (reviewReminder.scope) {
                is ReviewReminderScope.Global -> withCol { sched.allDecksCounts() }
                is ReviewReminderScope.DeckSpecific -> {
                    val deckNode = withCol { sched.deckDueTree().find(reviewReminder.scope.did) }
                    if (deckNode == null) {
                        Timber.e(
                            "${reminderLogPrefix(reviewReminder.id)} Aborting notification: " +
                                "deck ${reviewReminder.scope.did} is not in the deck tree",
                        )
                        return null
                    }
                    Counts(new = deckNode.newCount, lrn = deckNode.lrnCount, rev = deckNode.revCount)
                }
            }
        val dueCardsTotal = dueCardsCount.count()
        val consideredCardsCount = reviewReminder.thresholdFilter.filterCounts(dueCardsCount)
        val consideredCardsTotal = consideredCardsCount.count()

        if (consideredCardsTotal < reviewReminder.cardTriggerThreshold.threshold) {
            Timber.i(
                "${reminderLogPrefix(reviewReminder.id)} skip: Aborting notification due to cardTriggerThreshold " +
                    "(due: $dueCardsCount, considered: $consideredCardsCount, threshold: ${reviewReminder.cardTriggerThreshold.threshold})",
            )
            return null
        }

        return dueCardsTotal
    }

    /**
     * Checks if a deck, or any decks, have been reviewed since the latest day cutoff.
     * Used for the "only notify me if no reviews have been done today" review reminder feature.
     */
    private suspend fun wasScopeReviewedToday(scope: ReviewReminderScope): Boolean {
        // Handles the global and deck-specific scope cases separately.
        // Checks for existence rather than counting to increase efficiency.
        val queryResult =
            when (scope) {
                is ReviewReminderScope.Global ->
                    withCol {
                        val startOfToday: EpochMilliseconds = sched.dayCutoff * 1000 - 1.days.inWholeMilliseconds
                        // For each card in the user's collection, retrieve and JOIN information about its review history
                        // Then check if there exists at least one card with a review log entry after the start of today
                        val query = """
                    SELECT EXISTS (
                        SELECT 1
                        FROM cards
                        JOIN revlog ON revlog.cid = cards.id
                        WHERE revlog.id > ?
                    )
                """
                        db.queryScalar(query, startOfToday)
                    }
                is ReviewReminderScope.DeckSpecific ->
                    withCol {
                        val startOfToday: EpochMilliseconds = sched.dayCutoff * 1000 - 1.days.inWholeMilliseconds
                        // Essentially the same as above, but only check through cards in the provided deck or its subdecks,
                        // including cards temporarily moved into a filtered deck (whose home deck is stored in odid)
                        // Deck IDs are numbers, so building the IN list from them is safe
                        val deckIds = Utils.ids2str(decks.deckAndChildIds(scope.did))
                        val query = """
                    SELECT EXISTS (
                        SELECT 1
                        FROM cards
                        JOIN revlog ON revlog.cid = cards.id
                        WHERE revlog.id > ?
                        AND (cards.did IN $deckIds OR cards.odid IN $deckIds)
                    )
                """
                        db.queryScalar(query, startOfToday)
                    }
            }
        return (queryResult == 1)
    }
}
