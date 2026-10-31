// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.services

import androidx.core.content.edit
import anki.scheduler.CardAnswer
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.storage.StorageDecision
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.EpochMilliseconds
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.reviewreminders.ReviewReminder
import com.ichi2.anki.reviewreminders.ReviewReminderCardTriggerThreshold
import com.ichi2.anki.reviewreminders.ReviewReminderScope.DeckSpecific
import com.ichi2.anki.reviewreminders.ReviewReminderScope.Global
import com.ichi2.anki.reviewreminders.ReviewReminderThresholdFilter
import com.ichi2.anki.reviewreminders.ReviewReminderTime
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.JvmApplicationRule
import com.ichi2.testutils.JvmPreferencesRule
import com.ichi2.testutils.JvmTest
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Calendar
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ReviewReminderNotifierTest : JvmTest() {
    @get:Rule
    val application = JvmApplicationRule()

    @get:Rule
    val preferences = JvmPreferencesRule()

    private var previousStorageDecision: StorageDecision? = null
    private val notifications = mutableListOf<Pair<ReviewReminder, Int>>()
    private val scheduled = mutableListOf<ReviewReminder>()
    private val notifier =
        ReviewReminderNotifier(
            scheduleNext = { scheduled += it },
            sendNotification = { reminder, total -> notifications += reminder to total },
        )

    companion object {
        // Keep reminder times away from midnight in the local time zone.
        private val today =
            MockTime(
                MockTime(2025, Calendar.JANUARY, 2, 12, 0, 0, 0, 0)
                    .calendar()
                    .apply { set(Calendar.HOUR_OF_DAY, 12) }
                    .timeInMillis,
            )
        private val yesterday = MockTime(today.intTimeMS() - 1.days.inWholeMilliseconds)
    }

    @Before
    override fun setUp() {
        previousStorageDecision = CollectionHelper.storageDecisionTestOverride
        CollectionHelper.storageDecisionTestOverride = StorageDecision.Decided
        super.setUp()
        TimeManager.resetWith(yesterday)
        Prefs.newReviewRemindersEnabled = true
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }

        // Keep the native scheduler aligned with the mock review timestamps
        val scheduler = col.sched
        mockkObject(scheduler)
        every { scheduler.dayCutoff } returns today.intTime() + 16.hours.inWholeSeconds
    }

    @After
    override fun tearDown() {
        try {
            CollectionManager.emulatedOpenFailure = null
            super.tearDown()
        } finally {
            unmockkAll()
            TimeManager.reset()
            CollectionHelper.storageDecisionTestOverride = previousStorageDecision
        }
    }

    @Test
    fun `triggering with less cards than card threshold should not fire notification but schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminderDeckSpecific = createTestReminder(deckId = did1, thresholdInt = 3)
            val reviewReminderAppWide = createTestReminder(thresholdInt = 3)
            val deckSpecificCreationTime = reviewReminderDeckSpecific.latestNotifTime
            val appWideCreationTime = reviewReminderAppWide.latestNotifTime
            ReviewRemindersDatabase.insertReminder(reviewReminderDeckSpecific)
            ReviewRemindersDatabase.insertReminder(reviewReminderAppWide)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminderDeckSpecific)
            attemptNotif(reviewReminderAppWide)

            verifyNoNotifsSent()
            verifyNextNotifScheduled(reviewReminderDeckSpecific)
            verifyNextNotifScheduled(reviewReminderAppWide)
            reviewReminderDeckSpecific.verifyLatestNotifTime(previousTime = deckSpecificCreationTime, shouldHaveUpdated = true)
            reviewReminderAppWide.verifyLatestNotifTime(previousTime = appWideCreationTime, shouldHaveUpdated = true)
        }

    @Test
    fun `triggering with happy path for single deck should fire notification and schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminder = createTestReminder(deckId = did1)
            val creationTime = reviewReminder.latestNotifTime
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder)

            verifyNotifSent(reviewReminder)
            verifyNextNotifScheduled(reviewReminder)
            reviewReminder.verifyLatestNotifTime(previousTime = creationTime, shouldHaveUpdated = true)
        }

    @Test
    fun `triggering with happy path for global reminder should fire notification and schedule next`() =
        runTest {
            addDeck("Deck1").withNotes(count = 2)
            addDeck("Deck2").withNotes(count = 2)
            val reviewReminder = createTestReminder(thresholdInt = 4)
            val creationTime = reviewReminder.latestNotifTime
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder)

            verifyNotifSent(reviewReminder)
            verifyNextNotifScheduled(reviewReminder)
            reviewReminder.verifyLatestNotifTime(previousTime = creationTime, shouldHaveUpdated = true)
        }

    @Test
    fun `triggering with non-existent deck should not fire notification but schedule next`() =
        runTest {
            val did1 = addDeck("Deck")
            val reviewReminder = createTestReminder(deckId = did1 + 1)
            val creationTime = reviewReminder.latestNotifTime
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder)

            verifyNoNotifsSent()
            verifyNextNotifScheduled(reviewReminder)
            reviewReminder.verifyLatestNotifTime(previousTime = creationTime, shouldHaveUpdated = true)
        }

    @Test
    fun `triggering with reminder which is not present in database should not fire notification nor schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminder = createTestReminder(deckId = did1)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder)

            verifyNoNotifsSent()
            verifyNextNotifNotScheduled()
        }

    @Test
    fun `triggering with reminder which is disabled should not fire notification nor schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminder = createTestReminder(deckId = did1, enabled = false)
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder)

            verifyNoNotifsSent()
            verifyNextNotifNotScheduled()
        }

    @Test
    fun `triggering with scheduled time in the future should not fire notification nor schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminder = createTestReminder(deckId = did1, scheduledTimeOffsetFromNow = 1.minutes)
            val creationTime = reviewReminder.latestNotifTime
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            // Here, we do NOT resetWith(today) in order to test what happens if a notification is triggered before its scheduled time
            attemptNotif(reviewReminder)

            verifyNoNotifsSent()
            verifyNextNotifNotScheduled()
            reviewReminder.verifyLatestNotifTime(previousTime = creationTime, shouldHaveUpdated = false)
        }

    @Test
    fun `triggering with scheduled time equal to creation time should not fire notification nor schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminder = createTestReminder(deckId = did1, scheduledTimeOffsetFromNow = 0.minutes)
            val creationTime = reviewReminder.latestNotifTime
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            // Here, we do NOT resetWith(today) in order to test what happens if a notification is scheduled at its creation time
            attemptNotif(reviewReminder)

            verifyNoNotifsSent()
            verifyNextNotifNotScheduled()
            reviewReminder.verifyLatestNotifTime(previousTime = creationTime, shouldHaveUpdated = false)
        }

    @Test
    fun `onReceive with rev cards not counted and only rev cards present should not fire notification`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2, queueType = QueueType.Rev)
            val reviewReminder = createTestReminder(deckId = did1, countRev = false)
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder)

            verifyNoNotifsSent()
        }

    @Test
    fun `onReceive with new cards not counted and only new cards present should not fire notification`() =
        runTest {
            val did1 = addDeck("Deck").withNotes(count = 2)
            val reviewReminder = createTestReminder(deckId = did1, countNew = false)
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder)

            verifyNoNotifsSent()
        }

    @Test
    fun `onReceive with lrn cards not counted and only lrn cards present should not fire notification`() =
        runTest {
            val did1 = addDeck("Deck").withNotes(count = 2, queueType = QueueType.Lrn)
            val reviewReminder = createTestReminder(deckId = did1, countLrn = false)
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder)

            verifyNoNotifsSent()
        }

    @Test
    fun `onReceive with all cards not counted and many cards present should not fire notification`() =
        runTest {
            val did1 =
                addDeck("Deck")
                    .withNote(queueType = QueueType.New)
                    .withNote(queueType = QueueType.Lrn)
                    .withNote(queueType = QueueType.Rev)
            val reviewReminder = createTestReminder(deckId = did1, countNew = false, countLrn = false, countRev = false)
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder)

            verifyNoNotifsSent()
        }

    @Test
    fun `onReceive with new cards not counted but other kinds present should fire notification`() =
        runTest {
            val did1 = addDeck("Deck").withNote(queueType = QueueType.Rev).withNote(queueType = QueueType.Lrn)
            val reviewReminder = createTestReminder(deckId = did1, countNew = false)
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder)

            verifyNotifSent(reviewReminder)
        }

    @Test
    fun `onReceive with new cards not counted and not enough non new cards to trigger threshold should not fire notification`() =
        runTest {
            val did1 = addDeck("Deck").withNote(queueType = QueueType.New).withNote(queueType = QueueType.Lrn)
            val reviewReminder = createTestReminder(deckId = did1, thresholdInt = 2, countNew = false)
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder)

            verifyNoNotifsSent()
        }

    @Test
    fun `triggering with reviews today and onlyNotifyIfNoReviews is true should not fire notification`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminderDeckSpecific = createTestReminder(deckId = did1, thresholdInt = 1, onlyNotifyIfNoReviews = true)
            val reviewReminderAppWide = createTestReminder(thresholdInt = 1, onlyNotifyIfNoReviews = true)
            ReviewRemindersDatabase.insertReminder(reviewReminderDeckSpecific)
            ReviewRemindersDatabase.insertReminder(reviewReminderAppWide)

            TimeManager.resetWith(today)
            col.sched.answerCard(col.sched.card!!, CardAnswer.Rating.GOOD)
            attemptNotif(reviewReminderDeckSpecific)
            attemptNotif(reviewReminderAppWide)

            verifyNoNotifsSent()
        }

    @Test
    fun `triggering with reviews today and onlyNotifyIfNoReviews is false should fire notification`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminderDeckSpecific = createTestReminder(deckId = did1, thresholdInt = 1, onlyNotifyIfNoReviews = false)
            val reviewReminderAppWide = createTestReminder(thresholdInt = 1, onlyNotifyIfNoReviews = false)
            ReviewRemindersDatabase.insertReminder(reviewReminderDeckSpecific)
            ReviewRemindersDatabase.insertReminder(reviewReminderAppWide)

            TimeManager.resetWith(today)
            col.sched.answerCard(col.sched.card!!, CardAnswer.Rating.GOOD)
            attemptNotif(reviewReminderDeckSpecific)
            attemptNotif(reviewReminderAppWide)

            verifyNotifSent(reviewReminderDeckSpecific)
            verifyNotifSent(reviewReminderAppWide)
        }

    @Test
    fun `triggering with no reviews ever should fire notification regardless of onlyNotifyIfNoReviews`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNote()
            val reviewReminderDeckSpecific = createTestReminder(deckId = did1, thresholdInt = 1, onlyNotifyIfNoReviews = true)
            val reviewReminderAppWide = createTestReminder(thresholdInt = 1, onlyNotifyIfNoReviews = false)
            ReviewRemindersDatabase.insertReminder(reviewReminderDeckSpecific)
            ReviewRemindersDatabase.insertReminder(reviewReminderAppWide)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminderDeckSpecific)
            attemptNotif(reviewReminderAppWide)

            verifyNotifSent(reviewReminderDeckSpecific)
            verifyNotifSent(reviewReminderAppWide)
        }

    @Test
    fun `triggering with review yesterday but none today should fire notification regardless of onlyNotifyIfNoReviews`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            col.sched.answerCard(col.sched.card!!, CardAnswer.Rating.GOOD)

            val reviewReminderDeckSpecific =
                createTestReminder(deckId = did1, thresholdInt = 1, onlyNotifyIfNoReviews = false)
            val reviewReminderAppWide =
                createTestReminder(thresholdInt = 1, onlyNotifyIfNoReviews = true)
            ReviewRemindersDatabase.insertReminder(reviewReminderDeckSpecific)
            ReviewRemindersDatabase.insertReminder(reviewReminderAppWide)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminderDeckSpecific)
            attemptNotif(reviewReminderAppWide)

            verifyNotifSent(reviewReminderDeckSpecific)
            verifyNotifSent(reviewReminderAppWide)
        }

    @Test
    fun `triggering with reviews today in a subdeck and onlyNotifyIfNoReviews is true should not fire notification`() =
        runTest {
            val parentDid = addDeck("Parent", setAsSelected = true)
            val childDid = addDeck("Parent::Child").withNote()
            col.decks.select(parentDid)
            val reviewReminder = createTestReminder(deckId = parentDid, thresholdInt = 1, onlyNotifyIfNoReviews = true)
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            val card = col.sched.card!!
            assertThat(card.did, equalTo(childDid))
            col.sched.answerCard(card, CardAnswer.Rating.GOOD)
            attemptNotif(reviewReminder)

            verifyNoNotifsSent()
        }

    @Test
    fun `triggering with reviews today in a filtered deck and onlyNotifyIfNoReviews is true should not fire notification`() =
        runTest {
            val homeDid = addDeck("Deck").withNotes(count = 2)
            val firstNid = col.findNotes("deck:Deck").first()
            val filteredDid = addDynamicDeck("Filtered", search = "nid:$firstNid")
            col.decks.select(filteredDid)
            val reviewReminder = createTestReminder(deckId = homeDid, thresholdInt = 1, onlyNotifyIfNoReviews = true)
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            val card = col.sched.card!!
            assertThat(card.did, equalTo(filteredDid))
            assertThat(card.oDid, equalTo(homeDid))
            col.sched.answerCard(card, CardAnswer.Rating.GOOD)
            attemptNotif(reviewReminder)

            verifyNoNotifsSent()
        }

    @Test
    fun `triggering with reviews today only in parent deck and onlyNotifyIfNoReviews is true should fire subdeck notification`() =
        runTest {
            val parentDid = addDeck("Parent", setAsSelected = true).withNote()
            val childDid = addDeck("Parent::Child").withNote()
            col.decks.select(parentDid)
            val reviewReminder = createTestReminder(deckId = childDid, thresholdInt = 1, onlyNotifyIfNoReviews = true)
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            val card = col.sched.card!!
            assertThat(card.did, equalTo(parentDid))
            col.sched.answerCard(card, CardAnswer.Rating.GOOD)
            attemptNotif(reviewReminder)

            verifyNotifSent(reviewReminder)
        }

    @Test
    fun `triggering with blocked collection should not fire notification but schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminderDeckSpecific = createTestReminder(deckId = did1)
            val reviewReminderAppWide = createTestReminder()
            val deckSpecificCreationTime = reviewReminderDeckSpecific.latestNotifTime
            val appWideCreationTime = reviewReminderAppWide.latestNotifTime
            ReviewRemindersDatabase.insertReminder(reviewReminderDeckSpecific)
            ReviewRemindersDatabase.insertReminder(reviewReminderAppWide)

            TimeManager.resetWith(today)
            CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
            attemptNotif(reviewReminderDeckSpecific)
            attemptNotif(reviewReminderAppWide)

            verifyNoNotifsSent()
            verifyNextNotifScheduled(reviewReminderDeckSpecific)
            verifyNextNotifScheduled(reviewReminderAppWide)
            reviewReminderDeckSpecific.verifyLatestNotifTime(previousTime = deckSpecificCreationTime, shouldHaveUpdated = true)
            reviewReminderAppWide.verifyLatestNotifTime(previousTime = appWideCreationTime, shouldHaveUpdated = true)
        }

    @Test
    fun `triggering with snoozed notification should fire notification but not schedule next regardless of recurring firing time`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminderDeckSpecific = createTestReminder(deckId = did1, scheduledTimeOffsetFromNow = (-1).minutes)
            val reviewReminderAppWide = createTestReminder(scheduledTimeOffsetFromNow = 1.minutes)
            val deckSpecificCreationTime = reviewReminderDeckSpecific.latestNotifTime
            val appWideCreationTime = reviewReminderAppWide.latestNotifTime
            ReviewRemindersDatabase.insertReminder(reviewReminderDeckSpecific)
            ReviewRemindersDatabase.insertReminder(reviewReminderAppWide)

            attemptNotif(reviewReminderDeckSpecific, isRecurring = false)
            attemptNotif(reviewReminderAppWide, isRecurring = false)

            verifyNotifSent(reviewReminderDeckSpecific)
            verifyNotifSent(reviewReminderAppWide)
            verifyNextNotifNotScheduled()
            reviewReminderDeckSpecific.verifyLatestNotifTime(previousTime = deckSpecificCreationTime, shouldHaveUpdated = false)
            reviewReminderAppWide.verifyLatestNotifTime(previousTime = appWideCreationTime, shouldHaveUpdated = false)
        }

    @Test
    fun `triggering with snoozed notification not stored in database should not fire notification nor schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminder = createTestReminder(deckId = did1, scheduledTimeOffsetFromNow = (-1).minutes)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder, isRecurring = false)

            verifyNoNotifsSent()
            verifyNextNotifNotScheduled()
        }

    @Test
    fun `triggering with snoozed notification which is disabled should not fire notification nor schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminder = createTestReminder(deckId = did1, enabled = false, scheduledTimeOffsetFromNow = (-1).minutes)
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder, isRecurring = false)

            verifyNoNotifsSent()
            verifyNextNotifNotScheduled()
        }

    @Test
    fun `triggering a deck-specific reminder should not change the selected deck`() =
        runTest {
            val studiedDeck = addDeck("Studied", setAsSelected = true).withNotes(count = 2)
            val reminderDeck = addDeck("Reminder").withNotes(count = 2)
            val reviewReminder = createTestReminder(deckId = reminderDeck)
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder)

            verifyNotifSent(reviewReminder)
            assertThat(col.decks.selected(), equalTo(studiedDeck))
        }

    private suspend fun attemptNotif(
        reviewReminder: ReviewReminder,
        isRecurring: Boolean = true,
    ) {
        notifier.notify(
            reviewReminder.id,
            reviewReminder.scope,
            isRecurringNotification = isRecurring,
        )
    }

    /**
     * Helper method for creating a review reminder to minimize verbosity in this file.
     * The review reminder's scheduled time is initialized to be equal to [today] offset by
     * [scheduledTimeOffsetFromNow].
     */
    private fun createTestReminder(
        deckId: DeckId? = null,
        thresholdInt: Int = 1,
        enabled: Boolean = true,
        onlyNotifyIfNoReviews: Boolean = false,
        scheduledTimeOffsetFromNow: Duration = (-1).minutes,
        countNew: Boolean = true,
        countLrn: Boolean = true,
        countRev: Boolean = true,
    ): ReviewReminder {
        val scheduledTime = TimeManager.time.calendar().clone() as Calendar
        scheduledTime.add(Calendar.MINUTE, scheduledTimeOffsetFromNow.inWholeMinutes.toInt())

        return ReviewReminder.createReviewReminder(
            time =
                ReviewReminderTime(
                    scheduledTime.get(Calendar.HOUR_OF_DAY),
                    scheduledTime.get(Calendar.MINUTE),
                ),
            cardTriggerThreshold = ReviewReminderCardTriggerThreshold(thresholdInt),
            scope = if (deckId != null) DeckSpecific(deckId) else Global,
            enabled = enabled,
            onlyNotifyIfNoReviews = onlyNotifyIfNoReviews,
            thresholdFilter =
                ReviewReminderThresholdFilter(
                    countNew = countNew,
                    countLrn = countLrn,
                    countRev = countRev,
                ),
        )
    }

    private fun verifyNoNotifsSent() {
        assertThat(notifications.size, equalTo(0))
    }

    private fun verifyNotifSent(
        reminder: ReviewReminder,
        times: Int = 1,
    ) {
        assertThat(notifications.count { it.first.id == reminder.id }, equalTo(times))
    }

    private fun verifyNextNotifNotScheduled() {
        assertThat(scheduled.size, equalTo(0))
    }

    private fun verifyNextNotifScheduled(reminder: ReviewReminder) {
        assertThat(scheduled.count { it.id == reminder.id }, equalTo(1))
    }

    private suspend fun ReviewReminder.verifyLatestNotifTime(
        previousTime: EpochMilliseconds,
        shouldHaveUpdated: Boolean,
    ) {
        val storedReminder =
            ReviewRemindersDatabase
                .getAllReminders()
                .getRemindersList()
                .find { it.id == id }!!

        if (shouldHaveUpdated) {
            assertThat(previousTime, not(equalTo(storedReminder.latestNotifTime)))
        } else {
            assertThat(previousTime, equalTo(storedReminder.latestNotifTime))
        }
    }
}
