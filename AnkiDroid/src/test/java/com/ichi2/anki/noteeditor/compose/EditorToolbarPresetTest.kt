// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class EditorToolbarPresetTest {
    private val preferences = SPMockBuilder().createSharedPreferences()
    private val store = EditorToolbarPreferences(preferences)
    private val default get() = store.loadPresets().first { it.id == EditorToolbarPreset.DEFAULT_ID }

    @Test
    fun `configuration saves new preset binding and visibility without changing quick actions`() {
        val copy = default.copy(id = "reading", name = "Reading", hiddenButtons = setOf("BOLD"))
        store.saveQuickActions("collection", 1, "front", listOf("gallery"))
        store.saveConfiguration("collection", 1, "front", EditorToolbarConfiguration(listOf(default, copy), copy.id, false))
        val reopened = EditorToolbarPreferences(preferences)
        assertEquals(copy.id, reopened.presetId("collection", 1, "front"))
        assertEquals(false, reopened.load("collection", 1, "front").showToolbar)
        assertEquals(setOf("BOLD"), reopened.load("collection", 1, "front").hiddenButtons)
        assertEquals(listOf("gallery"), reopened.load("collection", 1, "front").quickActions)
        assertEquals(true, reopened.load("collection", 1, "back").showToolbar)
        assertEquals(emptySet(), reopened.load("collection", 1, "back").hiddenButtons)
    }

    @Test
    fun `configuration applies shared edits and deleted preset fallback across note types`() {
        val removed = EditorToolbarPreset(name = "Reading")
        store.savePresets(listOf(default, removed))
        store.selectPreset("collection", 2, "back", removed.id)
        val updated = default.copy(hiddenButtons = setOf("ITALIC"))
        store.saveConfiguration("collection", 1, "front", EditorToolbarConfiguration(listOf(updated), updated.id, false))
        assertEquals(updated.id, store.presetId("collection", 2, "back"))
        assertEquals(setOf("ITALIC"), store.load("collection", 2, "back").hiddenButtons)
        assertEquals(true, store.load("collection", 2, "back").showToolbar)
    }

    @Test
    fun `invalid configuration changes neither the presets nor the field`() {
        val initial = store.loadPresets()
        val before = preferences.all.toMap()
        for (invalid in listOf(
            EditorToolbarConfiguration(initial, "missing", false),
            EditorToolbarConfiguration(emptyList(), EditorToolbarPreset.DEFAULT_ID, false),
        )) {
            assertFailsWith<IllegalArgumentException> { store.saveConfiguration("collection", 1, "front", invalid) }
            assertEquals(before, preferences.all)
        }
    }

    @Test
    fun `legacy field customization becomes a reusable preset without changing its appearance`() {
        val old =
            EditorFieldToolbar(
                hiddenButtons = setOf("ITALIC"),
                buttonOrder = listOf("BOLD", "ITALIC"),
                quickActions = listOf("gallery"),
                showToolbar = false,
            )
        preferences.seedLegacyToolbar("collection", 1, "front", old)
        val presets = store.loadPresets()
        val id = assertNotNull(store.presetId("collection", 1, "front", presets))
        val imported = presets.single { it.id == id }
        assertEquals(old, store.load("collection", 1, "front", presets))
        store.selectPreset("collection", 2, "back", id)
        store.savePresets(presets.map { if (it.id == id) imported.copy(hiddenButtons = setOf("BOLD")) else it })
        assertEquals(setOf("BOLD"), store.load("collection", 1, "front").hiddenButtons)
        assertEquals(setOf("BOLD"), store.load("collection", 2, "back").hiddenButtons)
        assertEquals(false, store.load("collection", 1, "front").showToolbar)
        assertEquals(true, store.load("collection", 2, "back").showToolbar)
    }

    @Test
    fun `hiding a toolbar keeps its preset and follows shared edits while hidden`() {
        val preset = EditorToolbarPreset(name = "Minimal", hiddenButtons = setOf("BOLD"))
        store.savePresets(listOf(default, preset))
        store.selectPreset("collection", 1, "front", preset.id)
        store.saveToolbarVisibility("collection", 1, "front", false)
        store.savePresets(listOf(default, preset.copy(hiddenButtons = setOf("ITALIC"))))
        val reopened = EditorToolbarPreferences(preferences)
        assertEquals(preset.id, reopened.presetId("collection", 1, "front"))
        assertEquals(false, reopened.load("collection", 1, "front").showToolbar)
        assertEquals(setOf("ITALIC"), reopened.load("collection", 1, "front").hiddenButtons)
        assertEquals(true, reopened.load("collection", 1, "back").showToolbar)
        reopened.saveToolbarVisibility("collection", 1, "front", true)
        assertEquals(preset.id, reopened.presetId("collection", 1, "front"))
        assertEquals(true, reopened.load("collection", 1, "front").showToolbar)
    }

    @Test
    fun `preset changes and quick action edits keep a toolbar hidden`() {
        store.saveToolbarVisibility("collection", 1, "front", false)
        val shared = EditorToolbarPreset(name = "Minimal", hiddenButtons = setOf("BOLD"))
        store.savePresets(listOf(default, shared))
        store.selectPreset("collection", 1, "front", shared.id)
        store.saveQuickActions("collection", 1, "front", emptyList())
        assertEquals(false, store.load("collection", 1, "front").showToolbar)
        store.selectPreset("collection", 1, "front", EditorToolbarPreset.DEFAULT_ID)
        assertEquals(false, store.load("collection", 1, "front").showToolbar)
        assertEquals(emptyList(), store.load("collection", 1, "front").quickActions)
    }

    @Test
    fun `shared default updates unconfigured fields across note types and survives reopening`() {
        store.savePresets(listOf(default.copy(hiddenButtons = setOf("BOLD"), buttonOrder = listOf("ITALIC", "BOLD"))))
        val reopened = EditorToolbarPreferences(preferences)
        for ((type, field) in listOf(1L to "front", 1L to "back", 2L to "front")) {
            assertEquals(setOf("BOLD"), reopened.load("collection", type, field).hiddenButtons)
            assertEquals(listOf("ITALIC", "BOLD"), reopened.load("collection", type, field).buttonOrder)
            assertEquals(EditorToolbarPreset.DEFAULT_ID, reopened.presetId("collection", type, field))
        }
    }

    @Test
    fun `existing field overrides and quick actions survive adopting presets`() {
        val old = EditorFieldToolbar(hiddenButtons = setOf("ITALIC"), quickActions = listOf("gallery"))
        preferences.seedLegacyToolbar("collection", 1, "front", old)
        store.savePresets(
            store.loadPresets().map {
                if (it.id ==
                    EditorToolbarPreset.DEFAULT_ID
                ) {
                    it.copy(hiddenButtons = setOf("BOLD"))
                } else {
                    it
                }
            },
        )
        assertEquals(old, store.load("collection", 1, "front"))
        assertNotNull(store.loadPresets().singleOrNull { it.id == store.presetId("collection", 1, "front") })
        store.selectPreset("collection", 1, "front", EditorToolbarPreset.DEFAULT_ID)
        assertEquals(setOf("BOLD"), store.load("collection", 1, "front").hiddenButtons)
        assertEquals(listOf("gallery"), store.load("collection", 1, "front").quickActions)
    }

    @Test
    fun `named preset edits affect references across types but not independent fields`() {
        val shared = EditorToolbarPreset(name = "Minimal", hiddenButtons = setOf("BOLD"))
        store.savePresets(listOf(default, shared))
        store.selectPreset("collection", 1, "front", shared.id)
        store.selectPreset("collection", 2, "back", shared.id)
        store.savePresets(listOf(default, shared.copy(name = "Reading", hiddenButtons = setOf("ITALIC"))))
        assertEquals(setOf("ITALIC"), store.load("collection", 1, "front").hiddenButtons)
        assertEquals(setOf("ITALIC"), store.load("collection", 2, "back").hiddenButtons)
        assertEquals(emptySet(), store.load("collection", 1, "back").hiddenButtons)
        assertEquals(emptySet(), store.load("another collection", 1, "front").hiddenButtons)
        assertEquals("Reading", EditorToolbarPreferences(preferences).loadPresets().last().name)
    }

    @Test
    fun `quick action changes preserve shared binding and deleting a preset falls back to default`() {
        val shared = EditorToolbarPreset(name = "Minimal", hiddenButtons = setOf("BOLD"))
        store.savePresets(listOf(default, shared))
        store.selectPreset("collection", 1, "front", shared.id)
        store.saveQuickActions("collection", 1, "front", emptyList())
        assertEquals(shared.id, store.presetId("collection", 1, "front"))
        store.savePresets(listOf(default.copy(hiddenButtons = setOf("ITALIC"))))
        assertEquals(EditorToolbarPreset.DEFAULT_ID, store.presetId("collection", 1, "front"))
        assertEquals(setOf("ITALIC"), store.load("collection", 1, "front").hiddenButtons)
        assertEquals(emptyList(), store.load("collection", 1, "front").quickActions)
    }

    @Test
    fun `migration reuses matching presets and is stable after reopening and deletion`() {
        val shared = EditorToolbarPreset(name = "Minimal", hiddenButtons = setOf("BOLD"))
        store.savePresets(listOf(default, shared))
        preferences.seedLegacyToolbar("collection", 1, "front", EditorFieldToolbar(hiddenButtons = setOf("BOLD")))
        preferences.seedLegacyToolbar("collection", 1, "back", EditorFieldToolbar())
        assertEquals(listOf(default, shared), store.loadPresets())
        assertEquals(shared.id, store.presetId("collection", 1, "front"))
        assertEquals(EditorToolbarPreset.DEFAULT_ID, store.presetId("collection", 1, "back"))
        assertEquals(store.loadPresets(), EditorToolbarPreferences(preferences).loadPresets())
        store.savePresets(listOf(default))
        val reopened = EditorToolbarPreferences(preferences)
        assertEquals(listOf(default), reopened.loadPresets(), "Do not import the same legacy settings again")
        assertEquals(EditorToolbarPreset.DEFAULT_ID, reopened.presetId("collection", 1, "front"))
    }

    @Test
    fun `migration preserves custom buttons and ordering with unique reusable names`() {
        val button = EditorCustomToolbarButton(id = "ruby", label = "Ruby", prefix = "<ruby>", suffix = "</ruby>")
        store.saveCustomButtons(listOf(button))
        val existing = EditorToolbarPreset(name = "Imported toolbar 1", hiddenButtons = setOf("BOLD"))
        store.savePresets(listOf(default, existing))
        val old =
            EditorFieldToolbar(customButtons = listOf(button), hiddenButtons = setOf("ITALIC"), buttonOrder = listOf("custom:ruby", "BOLD"))
        preferences.seedLegacyToolbar("collection", 1, "front", old)
        preferences.seedLegacyToolbar("collection", 2, "back", old)
        val presets = store.loadPresets()
        assertEquals(3, presets.size)
        assertEquals("Imported toolbar 2", presets.last().name)
        assertEquals(old, store.load("collection", 1, "front"))
        assertEquals(old, store.load("collection", 2, "back"))
        assertEquals(presets.last().id, store.presetId("collection", 1, "front"))
        assertEquals(presets.last().id, store.presetId("collection", 2, "back"))
    }

    @Test
    fun `shared custom definitions stay live in presets and deletion removes their buttons`() {
        val button = EditorCustomToolbarButton(label = "Ruby", prefix = "<ruby>", suffix = "</ruby>")
        store.saveCustomButtons(listOf(button))
        store.savePresets(listOf(default.copy(customButtonIds = listOf(button.id), buttonOrder = listOf("custom:${button.id}", "BOLD"))))
        store.saveCustomButtons(listOf(button.copy(label = "Reading")))
        assertEquals(
            "Reading",
            store
                .load("collection", 1, "front")
                .customButtons
                .single()
                .label,
        )
        store.saveCustomButtons(emptyList())
        assertEquals(emptyList(), store.load("collection", 1, "front").customButtons)
    }

    @Test
    fun `quick actions alone do not create a private toolbar override`() {
        store.saveQuickActions("collection", 1, "front", listOf("gallery"))
        store.savePresets(listOf(default.copy(hiddenButtons = setOf("BOLD"))))
        assertEquals(EditorToolbarPreset.DEFAULT_ID, store.presetId("collection", 1, "front"))
        assertEquals(setOf("BOLD"), store.load("collection", 1, "front").hiddenButtons)
    }

    @Test
    fun `invalid preset libraries do not overwrite the previous choices`() {
        val initial = store.loadPresets()
        assertFailsWith<IllegalArgumentException> { store.savePresets(emptyList()) }
        assertFailsWith<IllegalArgumentException> { store.savePresets(listOf(default, default)) }
        assertFailsWith<IllegalArgumentException> { store.savePresets(listOf(default, EditorToolbarPreset(name = " "))) }
        assertFailsWith<IllegalArgumentException> {
            store.savePresets(listOf(default, EditorToolbarPreset(name = "Minimal"), EditorToolbarPreset(name = " minimal ")))
        }
        assertEquals(initial, store.loadPresets())
    }
}
