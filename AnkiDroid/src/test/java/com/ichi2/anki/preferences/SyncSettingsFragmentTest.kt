// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceViewHolder
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.materialswitch.MaterialSwitch
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.preferences.SwitchScreenPreference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.appcompat.R as AppCompatR

@RunWith(AndroidJUnit4::class)
class SyncSettingsFragmentTest : RobolectricTest() {
    @get:Rule
    val compose = createEmptyComposeRule()

    private fun openSync(): PreferencesActivity {
        val controller =
            Robolectric
                .buildActivity(
                    PreferencesActivity::class.java,
                    PreferencesActivity.getIntent(targetContext, SyncSettingsFragment::class),
                ).setup()
        saveControllerForCleanup(controller)
        advanceRobolectricLooper()
        return controller.get()
    }

    private fun current(activity: PreferencesActivity): Fragment? =
        (activity.fragment as PreferencesFragment).childFragmentManager.findFragmentById(R.id.settings_container)

    private fun entry(fragment: SyncSettingsFragment): SwitchScreenPreference =
        requireNotNull(fragment.findPreference(fragment.getString(R.string.custom_sync_server_key)))

    private fun row(fragment: SyncSettingsFragment): View {
        val preference = entry(fragment)
        val inflater = LayoutInflater.from(fragment.requireContext())
        val root = inflater.inflate(preference.layoutResource, fragment.listView, false)
        inflater.inflate(preference.widgetLayoutResource, root.findViewById<ViewGroup>(android.R.id.widget_frame))
        preference.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(root))
        return root
    }

    private fun View.toggle(): MaterialSwitch = findViewById(androidx.preference.R.id.switchWidget)

    @Test
    fun `tapping the row opens the editor without changing sync selection`() {
        Prefs.customSyncUri = "https://sync.example.com/"
        Prefs.isCustomSyncEnabled = true
        Prefs.currentSyncUri = "https://redirect.example.com/"
        val activity = openSync()
        val sync = assertIs<SyncSettingsFragment>(current(activity))

        row(sync).performClick()
        advanceRobolectricLooper()

        assertIs<CustomSyncServerSettingsFragment>(current(activity))
        assertTrue(Prefs.isCustomSyncEnabled)
        assertEquals("https://sync.example.com/", Prefs.customSyncUri)
        assertEquals("https://redirect.example.com/", Prefs.currentSyncUri)
    }

    @Test
    fun `the configured switch toggles sync without opening the editor`() {
        Prefs.customSyncUri = "https://sync.example.com/"
        Prefs.currentSyncUri = "https://redirect.example.com/"
        val activity = openSync()
        val sync = assertIs<SyncSettingsFragment>(current(activity))
        val toggle = row(sync).toggle()
        assertEquals(sync.getString(CommonString.custom_sync_server_title), toggle.contentDescription)
        assertEquals("https://redirect.example.com/", Prefs.currentSyncUri)

        for (enabled in listOf(true, false)) {
            Prefs.currentSyncUri = "https://redirect.example.com/"
            toggle.performClick()
            advanceRobolectricLooper()

            assertEquals(sync, current(activity))
            assertEquals(enabled, Prefs.isCustomSyncEnabled)
            assertEquals(enabled, entry(sync).isChecked)
            assertEquals("https://sync.example.com/", Prefs.customSyncUri)
            assertNull(Prefs.currentSyncUri)
        }
        assertFalse(Prefs.sharedPrefs.contains(sync.getString(R.string.custom_sync_server_key)))
    }

    @Test
    fun `enabling an unconfigured server opens setup and only save activates it`() {
        Prefs.currentSyncUri = "https://redirect.example.com/"
        val activity = openSync()
        val sync = assertIs<SyncSettingsFragment>(current(activity))
        val toggle = row(sync).toggle()

        toggle.performClick()
        advanceRobolectricLooper()

        assertIs<CustomSyncServerSettingsFragment>(current(activity))
        assertFalse(toggle.isChecked)
        assertFalse(Prefs.isCustomSyncEnabled)
        assertNull(Prefs.customSyncUri)
        assertEquals("https://redirect.example.com/", Prefs.currentSyncUri)
        compose.onNodeWithTag("custom_sync_url").assertIsEnabled().performTextReplacement("https://new.example.com/")
        compose.onNodeWithText(activity.getString(CommonString.save)).performClick()
        advanceRobolectricLooper()

        val returned = assertIs<SyncSettingsFragment>(current(activity))
        assertTrue(entry(returned).isChecked)
        assertEquals("https://new.example.com/", entry(returned).summary)
        assertTrue(Prefs.isCustomSyncEnabled)
        assertNull(Prefs.currentSyncUri)
    }

    @Test
    fun `leaving setup keeps the switch off and does not force enable on the next row click`() {
        val activity = openSync()
        row(assertIs<SyncSettingsFragment>(current(activity))).toggle().performClick()
        advanceRobolectricLooper()
        compose.onNodeWithTag("custom_sync_url").performTextReplacement("https://unsaved.example.com/")
        compose.onNodeWithContentDescription(activity.getString(AppCompatR.string.abc_action_bar_up_description)).performClick()
        advanceRobolectricLooper()

        val returned = assertIs<SyncSettingsFragment>(current(activity))
        assertFalse(entry(returned).isChecked)
        assertFalse(Prefs.isCustomSyncEnabled)
        assertNull(Prefs.customSyncUri)
        row(returned).performClick()
        advanceRobolectricLooper()

        assertIs<CustomSyncServerSettingsFragment>(current(activity))
        compose.onNodeWithTag("custom_sync_url").assertIsNotEnabled()
    }

    @Test
    fun `saving a changed enabled URL refreshes the calling summary`() {
        Prefs.customSyncUri = "https://old.example.com/"
        Prefs.isCustomSyncEnabled = true
        val activity = openSync()
        row(assertIs<SyncSettingsFragment>(current(activity))).performClick()
        advanceRobolectricLooper()
        compose.onNodeWithTag("custom_sync_url").performTextReplacement("https://new.example.com/")
        compose.onNodeWithText(activity.getString(CommonString.save)).performClick()
        advanceRobolectricLooper()

        val returned = assertIs<SyncSettingsFragment>(current(activity))
        assertTrue(entry(returned).isChecked)
        assertEquals("https://new.example.com/", entry(returned).summary)
        assertTrue(row(returned).toggle().isChecked)
    }
}
