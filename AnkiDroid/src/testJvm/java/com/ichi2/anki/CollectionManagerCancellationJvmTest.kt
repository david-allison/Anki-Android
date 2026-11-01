// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.testutils.JvmTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class CollectionManagerCancellationJvmTest : JvmTest() {
    @Test
    fun `withCol rejects an already cancelled coroutine`() =
        runTest {
            assertFalse(CollectionManager.isOpenUnsafe())
            launch {
                cancel()
                assertFailsWith<CancellationException> {
                    withCol { error("cancelled work must not run") }
                }
            }.join()
            assertFalse(CollectionManager.isOpenUnsafe(), "cancelled work must not open the collection")
        }
}
