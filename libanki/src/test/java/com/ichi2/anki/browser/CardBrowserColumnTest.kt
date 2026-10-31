// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertNotNull

/** @see CardBrowserColumn */
@RunWith(Parameterized::class)
class CardBrowserColumnTest : InMemoryAnkiTest() {
    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<Any>> =
            CardBrowserColumn.entries
                .map { arrayOf(it) }
    }

    @Parameterized.Parameter
    @JvmField // required for Parameter
    var columnParam: CardBrowserColumn? = null
    private val column get() = columnParam!!

    @Test
    fun ensureAllColumnsMapped() {
        val collectionColumns = col.backend.allBrowserColumns()
        assertNotNull(collectionColumns.find(column))
    }
}
