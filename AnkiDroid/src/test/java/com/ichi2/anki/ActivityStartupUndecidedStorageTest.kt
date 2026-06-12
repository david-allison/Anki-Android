// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.storage.StorageDecision
import com.ichi2.testutils.ActivityList
import com.ichi2.testutils.ActivityList.ActivityLaunchParam
import com.ichi2.testutils.skipTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * All activities start crash-free when [CollectionHelper.storageDecision] returns
 * [StorageDecision.Undecided].
 *
 * Unlike [ExternalEntryPointsUndecidedStorageTest], this covers activities reachable without
 * passing an external entry point: task restoration after process death, pinned shortcuts and
 * internal navigation. Collection-requiring activities are expected to finish via
 * [ensureStorageReady][com.ichi2.anki.startup.ensureStorageReady].
 *
 * If this fails for a new activity, add the following after `super.onCreate`:
 * ```
 * if (!ensureStorageReady()) {
 *     return
 * }
 * ```
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class ActivityStartupUndecidedStorageTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField // required for Parameter
    var launcher: ActivityLaunchParam? = null

    // Only used for display, but needs to be defined
    @ParameterizedRobolectricTestRunner.Parameter(1)
    @JvmField // required for Parameter
    @Suppress("unused")
    var activityName: String? = null

    @Before
    fun setStorageUndecided() {
        CollectionHelper.storageDecisionTestOverride = StorageDecision.Undecided
    }

    @After
    fun resetStorageDecision() {
        CollectionHelper.storageDecisionTestOverride = null
    }

    @Test
    fun `startup is crash-free when storage is undecided`() {
        val controller = launcher!!.build(targetContext)
        // mirrors Android: an activity which finishes during onCreate (e.g. redirectToMainEntryPoint)
        // does not receive the remaining lifecycle callbacks; controller.setup() would force them
        controller.create()
        if (!controller.get().isFinishing) {
            controller
                .start()
                .postCreate(null)
                .resume()
                .visible()
        }
        advanceRobolectricLooper()
    }

    private fun notYetHandled(
        activityName: String,
        reason: String,
    ) {
        if (launcher!!.simpleName == activityName) {
            skipTest("$activityName does not yet handle undecided storage: $reason")
        }
    }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<Any>> = ActivityList.allActivitiesAndIntents().map { arrayOf(it, it.simpleName) }
    }
}
