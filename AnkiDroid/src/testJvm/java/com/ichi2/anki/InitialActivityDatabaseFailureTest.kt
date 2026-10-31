// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.CollectionManager.CollectionOpenFailure
import com.ichi2.anki.InitialActivity.StartupFailure
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.startup.collectionOpenFailure
import com.ichi2.testutils.JvmTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Failures raised while opening the collection.
 */
class InitialActivityDatabaseFailureTest : JvmTest() {
    @Before
    override fun setUp() {
        super.setUp()
        DatabaseCorruption.isDetected = false
    }

    @After
    override fun tearDown() {
        CollectionManager.emulatedOpenFailure = null
        DatabaseCorruption.isDetected = false
        super.tearDown()
    }

    @Test
    fun `a full disk is reported as DiskFull`() {
        CollectionManager.emulatedOpenFailure = CollectionOpenFailure.DISK_FULL

        val failure = classifyOpenFailure()

        assertThat("a full disk should be reported", failure, equalTo(StartupFailure.DiskFull))
    }

    @Test
    fun `a corrupt collection is flagged as corruption`() {
        CollectionManager.emulatedOpenFailure = CollectionOpenFailure.DATABASE_CORRUPT

        val failure = classifyOpenFailure()

        assertThat("a corrupt collection is a DB error", failure, instanceOf(StartupFailure.DBError::class.java))
        assertThat("corruption should be detected", DatabaseCorruption.isDetected, equalTo(true))
    }

    private fun classifyOpenFailure(): StartupFailure {
        val error =
            runCatching { CollectionManager.getColUnsafe() }.exceptionOrNull()
                ?: error("collection opening must fail")
        return collectionOpenFailure(error as Exception) {
            error("expected a known collection error, got $it")
        }
    }
}
