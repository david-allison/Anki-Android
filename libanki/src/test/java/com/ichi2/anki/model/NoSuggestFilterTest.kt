// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.model

import com.ichi2.anki.model.FieldFilters.NoSuggestFilter
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

/** Tests the runtime side of [NoSuggestFilter] — see [FieldFilterChainTest] for picker rules. */
class NoSuggestFilterTest {
    private fun apply(
        fieldText: String,
        filterName: String = "nosuggest",
    ) = NoSuggestFilter.apply(fieldText, filterName)

    @Test
    fun `prepends nosuggest modifier to type marker`() {
        assertThat(apply("[[type:abc]]"), equalTo("[[type:nosuggest:abc]]"))
    }

    @Test
    fun `prepends nosuggest modifier alongside nc`() {
        assertThat(apply("[[type:nc:abc]]"), equalTo("[[type:nosuggest:nc:abc]]"))
    }

    @Test
    fun `prepends nosuggest modifier alongside cloze`() {
        assertThat(apply("[[type:cloze:abc]]"), equalTo("[[type:nosuggest:cloze:abc]]"))
    }

    @Test
    fun `is idempotent when already applied`() {
        assertThat(apply("[[type:nosuggest:abc]]"), equalTo("[[type:nosuggest:abc]]"))
    }

    @Test
    fun `is a no-op when no type marker is present`() {
        assertThat(apply("plain text"), equalTo("plain text"))
    }

    @Test
    fun `is a no-op when filterName is something else`() {
        assertThat(apply("[[type:abc]]", filterName = "other"), equalTo("[[type:abc]]"))
    }
}
