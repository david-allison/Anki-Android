// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Activity
import android.content.Intent
import android.os.Looper
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import com.ichi2.anki.EntryPointStorageDecisionTest.Outcome.FINISHING
import com.ichi2.anki.EntryPointStorageDecisionTest.Outcome.RESUMED
import com.ichi2.anki.EntryPointStorageDecisionTest.Outcome.RESUMED_THEN_THREW
import com.ichi2.anki.EntryPointStorageDecisionTest.Outcome.THREW
import com.ichi2.anki.storage.StorageDecision
import com.ichi2.testutils.ExternalEntryPoints
import com.ichi2.testutils.ExternalEntryPoints.EntryPoint
import com.ichi2.testutils.skipTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestParameterInjector
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/**
 * Characterizes how each [entry point][ExternalEntryPoints] behaves when the
 * [storage decision][CollectionHelper.storageDecision] is [StorageDecision.Undecided].
 *
 * // TODO: Add all classes to [ENABLED]
 *
 * @see EXPECTED_OUTCOMES
 * @see ExternalEntryPointsTest.EXPECTED
 */
@RunWith(RobolectricTestParameterInjector::class)
class EntryPointStorageDecisionTest : RobolectricTest() {
    @TestParameter(valuesProvider = ActivityEntryPoints::class)
    lateinit var entryPoint: EntryPoint

    @After
    fun clearStorageDecisionOverride() {
        CollectionHelper.storageDecisionTestOverride = null
    }

    @Test
    fun behavesAsExpectedUnderUndecidedStorage() {
        if (entryPoint.className !in ENABLED) {
            skipTest("${entryPoint.className} is unverified.")
        }
        CollectionHelper.storageDecisionTestOverride = StorageDecision.Undecided

        // An alias resolves to its target, so its outcome is looked up by the class it launches.
        val launchClassName = entryPoint.toActivityName()

        val expected =
            EXPECTED_OUTCOMES[launchClassName]
                ?: error("No expected outcome recorded for $launchClassName")

        val actual = driveToResume(launchClassName)
        assertThat(entryPoint.className, actual, equalTo(expected))
    }

    /** Builds the activity, runs create → start → resume, idles the looper, and reports the outcome. */
    private fun driveToResume(className: String): Outcome {
        var asyncFailure: Throwable? = null
        return try {
            withActivityController(className, onUncaughtException = {
                @Suppress("AssignedValueIsNeverRead")
                asyncFailure = it
            }) { controller ->
                controller.setup()
                shadowOf(Looper.getMainLooper()).idle()
                when {
                    asyncFailure != null -> RESUMED_THEN_THREW
                    controller.get().isFinishing -> FINISHING
                    else -> RESUMED
                }
            }
        } catch (_: Throwable) {
            THREW
        }
    }

    /**
     * Builds [className]'s activity and runs [block] with its controller.
     *
     * Uncaught exceptions are forwarded to [onUncaughtException].
     */
    private fun <T> withActivityController(
        className: String,
        onUncaughtException: (Throwable) -> Unit,
        block: (ActivityController<out Activity>) -> T,
    ): T {
        val activityClass = Class.forName(className).asSubclass(Activity::class.java)
        val controller = Robolectric.buildActivity(activityClass, Intent())
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable -> onUncaughtException(throwable) }
        return try {
            block(controller)
        } finally {
            controller.destroy()
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    /** The state an activity reaches when launched while storage is [Undecided][StorageDecision.Undecided]. */
    enum class Outcome {
        /** Activity was [finishing][Activity.isFinishing]. */
        FINISHING,

        /** Reached `onResume()` and is still running. */
        RESUMED,

        /** Threw synchronously during the lifecycle. */
        THREW,

        /** Resumed, then threw once the looper idled. */
        RESUMED_THEN_THREW,
    }

    /** Supplies the activity-like entry points; sourced from the manifest-pinned inventory. */
    class ActivityEntryPoints : TestParameterValuesProvider() {
        override fun provideValues(context: Context?): List<EntryPoint> =
            ExternalEntryPointsTest.EXPECTED.filter {
                it is EntryPoint.Activity ||
                    it is EntryPoint.ActivityAlias ||
                    it is EntryPoint.WidgetConfig ||
                    it is EntryPoint.WidgetConfigAlias
            }
    }

    companion object {
        /**
         * Entry points whose case is currently enforced; every other case is skipped.
         */
        private val ENABLED = emptySet<String>()

        /**
         * Current behavior under [Undecided][StorageDecision.Undecided] storage.
         */
        private val EXPECTED_OUTCOMES =
            mapOf(
                "com.ichi2.anki.IntentHandler" to FINISHING,
                "com.ichi2.anki.IntentHandler2" to FINISHING,
                "com.ichi2.anki.Reviewer" to RESUMED_THEN_THREW,
                "com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity" to RESUMED,
                "com.ichi2.anki.ui.windows.managespace.ManageSpaceActivity" to RESUMED,
                "com.ichi2.anki.ui.windows.permissions.AllPermissionsExplanationActivity" to RESUMED,
                "com.ichi2.widget.deckpicker.DeckPickerWidgetConfig" to FINISHING,
                "com.ichi2.widget.cardanalysis.CardAnalysisWidgetConfig" to FINISHING,
            )
    }
}

private fun EntryPoint.toActivityName(): String =
    when (val entry = this) {
        is EntryPoint.Activity -> entry.className
        is EntryPoint.ActivityAlias -> entry.targetActivity
        is EntryPoint.WidgetConfig -> entry.className
        is EntryPoint.WidgetConfigAlias -> entry.targetActivity
        else -> error("$entry is not an activity; the provider should not yield it")
    }
