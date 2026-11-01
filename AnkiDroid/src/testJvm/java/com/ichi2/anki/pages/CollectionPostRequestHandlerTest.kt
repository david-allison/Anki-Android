// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import com.ichi2.testutils.JvmTest
import org.junit.Test
import kotlin.test.assertNotNull

class CollectionPostRequestHandlerTest : JvmTest() {
    @Test
    fun `saveCustomColours does not throw`() =
        runTest {
            // This frontend-only service is intentionally a no-op in mobile clients.
            assertNotNull(handleCollectionPostRequest("saveCustomColours", byteArrayOf()))
        }
}
