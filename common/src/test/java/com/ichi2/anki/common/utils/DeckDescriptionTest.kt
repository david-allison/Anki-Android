// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckDescriptionTest {
    @Test
    fun scriptTagContentsAreRemoved() {
        val result = prepareDeckDescription("a<script>a=1</script>a")
        assertEquals("aa", result)
    }

    @Test
    fun upperCaseScriptTagContentsAreRemoved() {
        val result = prepareDeckDescription("a<SCRIPT>a=1</script>a")
        assertEquals("aa", result)
    }

    @Test
    fun scriptTagWithAttributesContentsAreRemoved() {
        val result = prepareDeckDescription("""a<script type="application/javascript">a=1</script>a""")
        assertEquals("aa", result)
    }

    @Test
    fun styleTagContentsAreRemoved() {
        val result = prepareDeckDescription("a<style>a=1</style>a")
        assertEquals("aa", result)
    }

    @Test
    fun upperCaseStyleTagContentsAreRemoved() {
        val result = prepareDeckDescription("a<STYLE>a:1</style>a")
        assertEquals("aa", result)
    }

    @Test
    fun styleTagWithAttributesContentsAreRemoved() {
        val result = prepareDeckDescription("""a<style type="text/css">a:1</style>a""")
        assertEquals("aa", result)
    }

    @Test
    fun windowsNewlinesArePreparedForHtmlRendering() {
        assertEquals("a<br/>a", prepareDeckDescription("a\r\na"))
    }

    @Test
    fun unixNewlinesArePreparedForHtmlRendering() {
        assertEquals("a<br/>a", prepareDeckDescription("a\na"))
    }
}
