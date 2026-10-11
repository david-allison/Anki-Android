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
        store.saveCustomButtons(toolbar.customButtons)
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

    @Test
    fun `global edits update every reference while enablement remains per field`() {
        val ruby = EditorCustomToolbarButton(label = "Ruby", prefix = "<ruby>", suffix = "</ruby>")
        store.saveCustomButtons(listOf(ruby))
        assertTrue(store.load("collection", 1, "front").customButtons.isEmpty())
        store.save("collection", 1, "front", EditorFieldToolbar(customButtons = listOf(ruby)))
        store.save("other collection", 2, "back", EditorFieldToolbar(customButtons = listOf(ruby)))
        val changed = ruby.copy(label = "Heading", prefix = "<h1>", suffix = "</h1>")
        store.saveCustomButtons(listOf(changed))
        val reopened = EditorToolbarPreferences(preferences)
        assertEquals(listOf(changed), reopened.load("collection", 1, "front").customButtons)
        assertEquals(listOf(changed), reopened.load("other collection", 2, "back").customButtons)
        assertTrue(reopened.load("collection", 1, "back").customButtons.isEmpty())
        reopened.saveCustomButtons(emptyList())
        assertTrue(reopened.load("collection", 1, "front").customButtons.isEmpty())
    }

    @Test
    fun `migration preserves edited copies with colliding legacy identities`() {
        val old = CustomToolbarButton.toStringSet(arrayListOf(CustomToolbarButton(0, "H1", "<h1>", "</h1>")))
        preferences.edit().putStringSet("note_editor_custom_buttons", old).commit()

        fun copied(
            field: String,
            prefix: String,
        ) {
            preferences
                .edit()
                .putString(
                    "composeEditorFieldToolbar:" + org.json.JSONArray(listOf("collection", 1, field)),
                    org.json
                        .JSONObject()
                        .put("hidden", org.json.JSONArray(listOf("BOLD")))
                        .put(
                            "custom",
                            org.json.JSONArray().put(
                                org.json
                                    .JSONObject()
                                    .put("id", "legacy:0")
                                    .put("label", "H1")
                                    .put("prefix", prefix)
                                    .put("suffix", "</h1>"),
                            ),
                        ).toString(),
                ).commit()
        }
        copied("front", "<h1>")
        copied("back", "<h1 class=answer>")
        copied("extra", "<h1 class=answer>")
        val front = store.load("collection", 1, "front")
        val back = store.load("collection", 1, "back")
        assertEquals(setOf("BOLD"), back.hiddenButtons)
        assertEquals("<h1 class=answer>", back.customButtons.single().prefix)
        assertTrue(front.customButtons.single().id != back.customButtons.single().id)
        assertEquals(back, store.load("collection", 1, "extra"))
        assertEquals(2, store.loadCustomButtons().size)
        assertEquals(back, EditorToolbarPreferences(preferences).load("collection", 1, "back"))
        assertEquals(old, preferences.getStringSet("note_editor_custom_buttons", null))
    }
}
