// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.worker.UniqueWorkNames
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class ProfileSwitchQueuedWorkTest : RobolectricTest() {
    private fun enqueueMediaSync() = targetContext.enqueueWaiting(UniqueWorkNames.SYNC_MEDIA)

    private fun enqueueSync() = targetContext.enqueueWaiting(UniqueWorkNames.SYNC)

    @Test
    fun `a media sync waiting for the network does not block the switch`() =
        runTest {
            enqueueMediaSync()

            assertNull(mediaSyncCheck().verify(), "queued work has nothing to interrupt")
        }

    @Test
    fun `a sync waiting for the network does not block the switch`() =
        runTest {
            enqueueSync()

            assertNull(syncCheck().verify(), "offline, a queued sync would block switching forever")
        }
}
