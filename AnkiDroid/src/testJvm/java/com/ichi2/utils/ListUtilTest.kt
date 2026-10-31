// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.utils

import com.ichi2.utils.ListUtil.Companion.assertListEquals
import org.junit.Test
import org.junit.internal.ArrayComparisonFailure
import kotlin.test.assertFailsWith

class ListUtilTest {
    @Test
    fun equalsTest() {
        assertFailsWith<ArrayComparisonFailure> { assertListEquals(listOf(2L, 3L), listOf(2L, 4L)) }
        assertFailsWith<ArrayComparisonFailure> { assertListEquals(listOf(2L, 3L), listOf(2L)) }
        assertFailsWith<ArrayComparisonFailure> { assertListEquals(listOf(2L, 3L), listOf(5L)) }
        assertFailsWith<ArrayComparisonFailure> { assertListEquals(listOf(2L, 3L), listOf(2L, 3L, 5L)) }
        assertFailsWith<AssertionError> { assertListEquals(listOf(2L, 3L), null) }
        assertFailsWith<AssertionError> { assertListEquals(null, listOf(2L, 4L)) }
        assertListEquals(null, null)
        assertListEquals(listOf(2L, 3L), listOf(2L, 3L))
    }
}
