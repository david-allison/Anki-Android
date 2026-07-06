// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddonStateStoreTest : RobolectricTest() {
    private lateinit var store: AddonStateStore

    /**
     * The store's on-disk contract, hardcoded on purpose: renaming the preferences
     * file or the 'enabled' key would silently reset every user's addon state
     */
    private val rawPrefs: SharedPreferences
        get() = targetContext.getSharedPreferences("addons", Context.MODE_PRIVATE)

    @Before
    override fun setUp() {
        super.setUp()
        store = AddonStateStore(targetContext)
    }

    @Test
    fun addonsAreDisabledByDefaultTest() {
        assertFalse("an addon with no stored state is disabled", store.isEnabled("some-addon"))
    }

    @Test
    fun setEnabledRoundTripsTest() {
        store.setEnabled("some-addon", true)
        assertTrue(store.isEnabled("some-addon"))

        store.setEnabled("some-addon", false)
        assertFalse(store.isEnabled("some-addon"))
    }

    @Test
    fun addonsAreIsolatedTest() {
        store.setEnabled("some-addon", true)

        assertFalse("state is per addon name", store.isEnabled("another-addon"))
    }

    @Test
    fun removeForgetsStateTest() {
        store.setEnabled("some-addon", true)

        store.remove("some-addon")

        assertFalse(store.isEnabled("some-addon"))
        assertFalse("no state is left behind", rawPrefs.contains("some-addon"))
    }

    @Test
    fun unknownKeysSurviveToggleTest() {
        // a newer AnkiDroid may store keys this version does not know about; toggling
        // 'enabled' must not discard them, or a downgrade would wipe addon state
        rawPrefs.edit { putString("some-addon", """{"enabled":false,"futureKey":"futureValue"}""") }

        store.setEnabled("some-addon", true)

        assertTrue(store.isEnabled("some-addon"))
        val stored = rawPrefs.getString("some-addon", null)!!
        assertTrue("keys from a newer AnkiDroid are preserved: $stored", stored.contains("\"futureKey\":\"futureValue\""))
    }

    @Test
    fun settingsDefaultToEmptyTest() {
        assertEquals(JsonObject(emptyMap()), store.getSettingsValues("some-addon"))
    }

    @Test
    fun settingValuesRoundTripTest() {
        store.setSettingValue("some-addon", "delaySeconds", JsonPrimitive(5))
        store.setSettingValue("some-addon", "position", JsonPrimitive("top"))

        val values = store.getSettingsValues("some-addon")

        assertEquals(JsonPrimitive(5), values["delaySeconds"])
        assertEquals(JsonPrimitive("top"), values["position"])
    }

    @Test
    fun settingWritesPreserveOtherSettingKeysTest() {
        // a newer AnkiDroid (or newer addon schema) may have stored keys we don't know
        rawPrefs.edit {
            putString("some-addon", """{"enabled":true,"settings":{"futureSetting":"kept"}}""")
        }

        store.setSettingValue("some-addon", "delaySeconds", JsonPrimitive(5))

        val values = store.getSettingsValues("some-addon")
        assertEquals(JsonPrimitive("kept"), values["futureSetting"])
        assertEquals(JsonPrimitive(5), values["delaySeconds"])
        assertTrue("the enabled flag is untouched by setting writes", store.isEnabled("some-addon"))
    }

    @Test
    fun settingsSurviveEnabledToggleTest() {
        store.setSettingValue("some-addon", "delaySeconds", JsonPrimitive(5))

        store.setEnabled("some-addon", true)
        store.setEnabled("some-addon", false)

        assertEquals(JsonPrimitive(5), store.getSettingsValues("some-addon")["delaySeconds"])
    }

    @Test
    fun corruptStateIsTreatedAsAbsentTest() {
        rawPrefs.edit { putString("some-addon", "{ not json") }

        assertFalse("corrupt state reads as disabled", store.isEnabled("some-addon"))

        store.setEnabled("some-addon", true)
        assertTrue("corrupt state can be overwritten", store.isEnabled("some-addon"))
    }
}
