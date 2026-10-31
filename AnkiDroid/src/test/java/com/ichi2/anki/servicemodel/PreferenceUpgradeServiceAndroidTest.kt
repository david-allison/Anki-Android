// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.servicemodel

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.common.android.getCurrentLocaleTag
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.servicelayer.PreferenceUpgradeService
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.PreferenceUpgrade
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.PreferenceUpgrade.UpgradeThemes
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.targetContext
import com.ichi2.utils.LanguageUtil
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertNotNull
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class PreferenceUpgradeServiceAndroidTest : AndroidTest {
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = targetContext.sharedPrefs()
    }

    @Test
    fun preference_upgrade_leads_to_max_version_in_preferences() {
        val result = PreferenceUpgradeService.upgradePreferences(prefs, 0)
        assertThat("preferences were upgraded", result, equalTo(true))
        val version = PreferenceUpgrade.getPreferenceVersion(prefs)
        PreferenceUpgradeService.setPreferencesUpToDate(prefs)
        val secondVersion = PreferenceUpgrade.getPreferenceVersion(prefs)
        assertThat("setPreferencesUpToDate should not change the version", secondVersion, equalTo(version))
    }

    @Test
    fun two_upgrades_does_nothing() {
        val result = PreferenceUpgradeService.upgradePreferences(prefs, 0)
        assertThat("preferences were upgraded", result, equalTo(true))
        val secondResult = PreferenceUpgradeService.upgradePreferences(prefs, 0)
        assertThat("a second preference upgrade does nothing", secondResult, equalTo(false))
    }

    @Test
    fun `Language preference value is updated to use language tags`() {
        val upgradeAppLocale = PreferenceUpgrade.UpgradeAppLocale()
        for (languageTag in LanguageUtil.APP_LANGUAGES.values) {
            prefs.edit {
                putString("language", Locale.forLanguageTag(languageTag).toString())
            }
            upgradeAppLocale.performUpgrade(prefs)
            val correctLanguage = prefs.getString("language", null)
            assertThat(languageTag, equalTo(correctLanguage))
            // The following assertion broke when updating targetSdk from 33->34 / robolectric from 32->34
            // However, a manual verification on an API33 and API34 emulator worked as follows:
            // - call sites are in AnkiDroidApp to show different manuals *if* the manual is translated
            // - follow app use path: get help / using / ankidroid manual -> it should send you to English manual
            // - manual is translated in Japanese, so set app language preference to Japanese
            // - set app language back to english, verify it goes to english manual again
            // assertThat(getCurrentLocaleTag(), equalTo(languageTag))
        }
    }

    @Test
    fun `Language preference value is set to system default correctly if it hasn't been set`() {
        PreferenceUpgrade.UpgradeAppLocale().performUpgrade(prefs)

        assertNotNull(prefs.getString("language", null))
        assertThat(getCurrentLocaleTag(), equalTo(""))
    }

    @Test
    fun `Language preference value is set to system default correctly`() {
        prefs.edit { putString("language", "") }
        PreferenceUpgrade.UpgradeAppLocale().performUpgrade(prefs)

        assertThat(prefs.getString("language", null), equalTo(""))
        assertThat(getCurrentLocaleTag(), equalTo(""))
    }

    @Test
    fun `theme constants match Android preference values`() {
        // Constants should have the correct values
        assertEquals(UpgradeThemes.THEME_FOLLOW_SYSTEM, targetContext.getString(CommonR.string.theme_follow_system_value))
        assertEquals(UpgradeThemes.THEME_LIGHT, targetContext.getString(CommonR.string.theme_light_value))
        assertEquals(UpgradeThemes.THEME_PLAIN, targetContext.getString(CommonR.string.theme_plain_value))
        assertEquals(UpgradeThemes.THEME_BLACK, targetContext.getString(CommonR.string.theme_black_value))
        assertEquals(UpgradeThemes.THEME_DARK, targetContext.getString(CommonR.string.theme_dark_value))
        assertEquals(UpgradeThemes.THEME_DAY, targetContext.getString(CommonR.string.theme_day_scheme_value))
        assertEquals(UpgradeThemes.THEME_NIGHT, targetContext.getString(CommonR.string.theme_night_scheme_value))
    }
}
