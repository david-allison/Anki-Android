// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasLength
import org.hamcrest.Matchers.lessThan
import org.junit.Test

class TruncatedStringTest {
    @Test
    fun `max clipboard text length should be less than system limit`() {
        // At most 1MB can be present in the Binder IPC transaction buffer at a time, but we should not exceed half of that
        // since that limit is shared between all ongoing transactions. The truncation is based on characters, but each character
        // in Kotlin is 2 bytes, so we multiply the max length by 2 to get the byte size.
        assertThat(TruncatedString.MAX_CLIPBOARD_TEXT_LENGTH * 2, lessThan(1_000_000 / 2))
    }

    @Test
    fun `TruncatedString keeps text shorter than the limit unchanged`() {
        assertThat(TruncatedString.from("hello").value, equalTo("hello"))
    }

    @Test
    fun `TruncatedString keeps text exactly at the limit unchanged`() {
        val text = "a".repeat(TruncatedString.MAX_CLIPBOARD_TEXT_LENGTH)
        assertThat(TruncatedString.from(text).value, equalTo(text))
    }

    @Test
    fun `TruncatedString truncates text longer than the limit`() {
        val truncated = TruncatedString.from("a".repeat(TruncatedString.MAX_CLIPBOARD_TEXT_LENGTH) + "b".repeat(10)).value

        assertThat(truncated, hasLength(TruncatedString.MAX_CLIPBOARD_TEXT_LENGTH))
        assertThat(truncated, equalTo("a".repeat(TruncatedString.MAX_CLIPBOARD_TEXT_LENGTH)))
    }
}
