// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki

import com.ichi2.anki.AnkiDroidJsAPIConstants.ApiCompatibility
import com.ichi2.anki.AnkiDroidJsAPIConstants.CURRENT_JS_API_VERSION
import com.ichi2.anki.AnkiDroidJsAPIConstants.MINIMUM_JS_API_VERSION
import com.ichi2.anki.AnkiDroidJsAPIConstants.checkApiVersion
import org.junit.Test
import kotlin.test.assertEquals

class AnkiDroidJsAPIConstantsTest {
    @Test
    fun currentVersionIsSupported() {
        assertEquals(ApiCompatibility.SUPPORTED, checkApiVersion(CURRENT_JS_API_VERSION))
    }

    @Test
    fun minimumVersionIsSupported() {
        assertEquals(ApiCompatibility.SUPPORTED, checkApiVersion(MINIMUM_JS_API_VERSION))
    }

    @Test
    fun olderThanMinimumRequiresAddonUpdate() {
        assertEquals(ApiCompatibility.ADDON_TOO_OLD, checkApiVersion("0.0.2"))
    }

    @Test
    fun newerThanCurrentRequiresAppUpdate() {
        assertEquals(ApiCompatibility.REQUIRES_NEWER_APP, checkApiVersion("9.9.9"))
    }

    @Test
    fun unparseableVersionIsInvalid() {
        assertEquals(ApiCompatibility.INVALID, checkApiVersion("not-a-version"))
        assertEquals(ApiCompatibility.INVALID, checkApiVersion(""))
    }
}
