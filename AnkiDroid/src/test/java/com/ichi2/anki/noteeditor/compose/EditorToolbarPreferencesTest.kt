// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.noteeditor.CustomToolbarButton
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EditorToolbarPreferencesTest {
    private val preferences = SPMockBuilder().createSharedPreferences()
    private val store = EditorToolbarPreferences(preferences)

    @Test
    fun `an override is isolated by collection note type and field and survives reopening`() {
        val toolbar =
            EditorFieldToolbar(
                setOf("BOLD", "MEDIA"),
                listOf(EditorCustomToolbarButton(label = "Ruby", prefix = "<ruby>", suffix = "</ruby>")),
            )
        store.save("collection", 1, "id:front", toolbar)
        assertEquals(toolbar, EditorToolbarPreferences(preferences).load("collection", 1, "id:front"))
        assertEquals(EditorFieldToolbar(), store.load("other collection", 1, "id:front"))
        assertEquals(EditorFieldToolbar(), store.load("collection", 2, "id:front"))
        assertEquals(EditorFieldToolbar(), store.load("collection", 1, "id:back"))
    }

    @Test
    fun `existing global custom buttons become defaults without changing the legacy preference`() {
        val old = CustomToolbarButton.toStringSet(arrayListOf(CustomToolbarButton(0, "H1", "<h1>", "</h1>")))
        preferences.edit().putStringSet("note_editor_custom_buttons", old).commit()
        val original = store.load("collection", 1, "id:front")
        assertEquals("H1", original.customButtons.single().label)
        store.save("collection", 1, "id:front", original.copy(customButtons = emptyList()))
        assertTrue(store.load("collection", 1, "id:front").customButtons.isEmpty())
        assertEquals(original, store.load("collection", 1, "id:back"))
        assertEquals(old, preferences.getStringSet("note_editor_custom_buttons", null))
    }
}
