// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import com.ichi2.anki.noteeditor.web.WebEditorAction
import com.ichi2.anki.noteeditor.web.WebEditorSettings
import com.ichi2.anki.servicelayer.LanguageHintService.languageHint
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.JvmTest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EditorViewModelTest : JvmTest() {
    @Test
    fun `toolbar customization saves shared edits and field selection without reloading the document`() =
        runTest {
            val vm = loadAdd()
            val before = assertNotNull(vm.state.value)
            val target = EditorFieldTarget(before.sessionId, before.generation, 1)
            val shared = before.toolbarPresets.single().copy(hiddenButtons = setOf("BOLD"))
            val copy = EditorToolbarPreset(name = "Reading", hiddenButtons = setOf("ITALIC"))
            val configuration = EditorToolbarConfiguration(listOf(shared, copy), copy.id, false)
            vm.setToolbarConfiguration(target, configuration)
            val after = assertNotNull(vm.state.value)
            assertEquals(copy.id, after.fields[1].toolbarPresetId)
            assertEquals(setOf("ITALIC"), after.fields[1].toolbar.hiddenButtons)
            assertFalse(after.fields[1].toolbar.showToolbar)
            assertEquals(setOf("BOLD"), after.fields[0].toolbar.hiddenButtons)
            assertTrue(after.fields[0].toolbar.showToolbar)
            assertEquals(before.generation, after.generation)
            assertEquals(before.fields.map { it.html }, after.fields.map { it.html })
            assertEquals(after.fields, loadAdd().state.value?.fields)
            vm.setToolbarConfiguration(
                target.copy(generation = target.generation + 1),
                configuration.copy(presets = listOf(shared), presetId = shared.id),
            )
            assertEquals(after, vm.state.value, "A stale field target must not change even the shared library")
        }

    @Test
    fun `field toolbar visibility preserves its preset and applies only to the focused field`() =
        runTest {
            val vm = loadAdd()
            val before = assertNotNull(vm.state.value)
            val target = EditorFieldTarget(before.sessionId, before.generation, 1)
            vm.setFieldToolbarVisibility(target, false)
            val hidden = assertNotNull(vm.state.value)
            assertEquals(before.fields[1].toolbarPresetId, hidden.fields[1].toolbarPresetId)
            assertEquals(before.fields[0], hidden.fields[0])
            assertEquals(before.fields[1].copy(toolbar = before.fields[1].toolbar.copy(showToolbar = false)), hidden.fields[1])
            assertEquals(before.generation, hidden.generation)
            assertFalse(hidden.showsToolbar(1))
            assertTrue(hidden.showsToolbar(0))
            assertFalse(hidden.copy(toolbarDisplay = EditorToolbarDisplay.HIDDEN).showsToolbar(0))
            assertFalse(assertNotNull(loadAdd().state.value).fields[1].toolbar.showToolbar)
            vm.setFieldToolbarVisibility(target, true)
            assertEquals(before.fields, assertNotNull(vm.state.value).fields)
            vm.setFieldToolbarVisibility(target.copy(field = 0), false)
            assertFalse(assertNotNull(vm.state.value).showsToolbar(null), "Initial chrome should already reflect the first field")
        }

    @Test
    fun `shared presets refresh every assigned field without changing the note or its document generation`() =
        runTest {
            val vm = loadAdd()
            val before = assertNotNull(vm.state.value)
            val preset = EditorToolbarPreset(name = "Minimal", hiddenButtons = setOf("BOLD"))
            vm.saveToolbarPreset(preset)
            before.fields.indices.forEach { index ->
                vm.setFieldToolbarPreset(EditorFieldTarget(before.sessionId, before.generation, index), preset.id)
            }
            vm.setFieldQuickActions(EditorFieldTarget(before.sessionId, before.generation, 0), listOf("gallery"))
            vm.saveToolbarPreset(preset.copy(name = "Reading", hiddenButtons = setOf("ITALIC")))
            val after = assertNotNull(vm.state.value)
            assertTrue(after.fields.all { it.toolbarPresetId == preset.id && it.toolbar.hiddenButtons == setOf("ITALIC") })
            assertEquals(
                listOf("gallery"),
                after.fields
                    .first()
                    .toolbar.quickActions,
            )
            assertEquals(before.generation, after.generation)
            assertEquals(before.fields.map { it.html }, after.fields.map { it.html })
            assertEquals(before.notetypeJson, after.notetypeJson)
            assertFalse(after.hasMetadataChanges)
            assertEquals(after.fields, loadAdd().state.value?.fields)
            vm.deleteToolbarPreset(preset.id)
            assertTrue(
                vm.state.value!!.fields.all {
                    it.toolbarPresetId == EditorToolbarPreset.DEFAULT_ID &&
                        it.toolbar.hiddenButtons.isEmpty()
                },
            )
            assertEquals(
                listOf("gallery"),
                vm.state.value!!
                    .fields
                    .first()
                    .toolbar.quickActions,
            )
            assertEquals(0, col.noteCount())
        }

    @Test
    fun `fields can choose independent presets and stale assignments do nothing`() =
        runTest {
            val vm = loadAdd()
            val before = assertNotNull(vm.state.value)
            val default = before.toolbarPresets.single()
            vm.saveToolbarPreset(default.copy(hiddenButtons = setOf("BOLD")))
            val target = EditorFieldTarget(before.sessionId, before.generation, 0)
            val independent = EditorToolbarPreset(name = "Minimal", hiddenButtons = setOf("BOLD"))
            vm.saveToolbarPreset(independent)
            vm.setFieldToolbarPreset(target, independent.id)
            vm.saveToolbarPreset(default.copy(hiddenButtons = setOf("ITALIC")))
            assertEquals(
                independent.id,
                vm.state.value!!
                    .fields
                    .first()
                    .toolbarPresetId,
            )
            assertEquals(
                setOf("BOLD"),
                vm.state.value!!
                    .fields
                    .first()
                    .toolbar.hiddenButtons,
            )
            assertEquals(
                setOf("ITALIC"),
                vm.state.value!!
                    .fields
                    .last()
                    .toolbar.hiddenButtons,
            )
            assertIs<EditorSaveResult.Added>(vm.save(listOf("front", "back")))
            vm.setFieldToolbarPreset(target, EditorToolbarPreset.DEFAULT_ID)
            assertEquals(
                independent.id,
                vm.state.value!!
                    .fields
                    .first()
                    .toolbarPresetId,
            )
        }

    @Test
    fun `toolbar colours are independent and survive reopening without changing note content`() =
        runTest {
            val vm = loadAdd()
            val before = assertNotNull(vm.state.value)
            assertEquals("#000000", before.toolbarTextColor)
            assertEquals("#ffff00", before.toolbarHighlightColor)
            vm.rememberToolbarColor(WebEditorAction.TEXT_COLOR, "#1976d2")
            assertEquals("#ffff00", vm.state.value?.toolbarHighlightColor)
            vm.rememberToolbarColor(WebEditorAction.HIGHLIGHT_COLOR, "#ffc107")
            assertEquals("#1976d2", vm.state.value?.toolbarTextColor)
            val reopened = assertNotNull(loadAdd().state.value)
            assertEquals("#1976d2", reopened.toolbarTextColor)
            assertEquals("#ffc107", reopened.toolbarHighlightColor)
            assertEquals(before.fields, vm.state.value?.fields)
            assertFalse(vm.state.value!!.hasMetadataChanges)
            assertIs<EditorSaveResult.Added>(vm.save(listOf("front", "back")))
            assertEquals("#1976d2", vm.state.value?.toolbarTextColor)
            assertEquals("#ffc107", vm.state.value?.toolbarHighlightColor)
        }

    @Test
    fun `toolbar colours retain the previous picker colour and ignore malformed preferences`() =
        runTest {
            Prefs.sharedPrefs
                .edit()
                .putString("composeEditorRecentColours", "#1976d2")
                .commit()
            val vm = loadAdd()
            assertEquals("#1976d2", vm.state.value?.toolbarTextColor)
            assertEquals("#1976d2", vm.state.value?.toolbarHighlightColor)
            Prefs.sharedPrefs
                .edit()
                .putString("composeEditorTextColor", "bad colour")
                .remove("composeEditorRecentColours")
                .commit()
            assertEquals("#000000", loadAdd().state.value?.toolbarTextColor)
        }

    @Test
    fun `removing a tag changes the draft and undo preserves later edits`() =
        runTest {
            val note = addBasicNote()
            note.tags = mutableListOf("first", "second", "third")
            col.updateNote(note)
            val vm = loadEdit(note.firstCard().id)
            val removed = assertNotNull(vm.removeTag("second"))
            assertEquals(listOf("first", "third"), vm.state.value?.tags)
            assertTrue(vm.state.value!!.hasMetadataChanges)
            assertEquals(listOf("first", "second", "third"), col.getNote(note.id).tags)
            vm.setTags(listOf("first", "third", "new"))
            vm.restoreTag(removed)
            assertEquals(listOf("first", "second", "third", "new"), vm.state.value?.tags)
            vm.restoreTag(removed)
            assertEquals(
                4,
                vm.state.value!!
                    .tags.size,
            )
            assertNull(vm.removeTag("absent"))
        }

    @Test
    fun `undoing a tag removal cannot change the next added note`() =
        runTest {
            val vm = loadAdd()
            vm.setTags(listOf("removed", "retained"))
            val removed = assertNotNull(vm.removeTag("removed"))
            assertIs<EditorSaveResult.Added>(vm.save(listOf("front", "back")))
            vm.restoreTag(removed)
            assertEquals(listOf("retained"), vm.state.value?.tags)
        }

    @Test
    fun `template changes refresh all enabled fields without changing note content`() =
        runTest {
            val vm = loadAdd()
            val before = assertNotNull(vm.state.value)
            val ruby = EditorCustomToolbarButton(label = "Ruby", prefix = "<ruby>", suffix = "</ruby>")
            vm.setCustomButtonTemplates(listOf(ruby))
            vm.saveToolbarPreset(before.toolbarPresets.single().copy(customButtonIds = listOf(ruby.id)))
            val updated = ruby.copy(label = "Heading", prefix = "<h1>", suffix = "</h1>")
            vm.setCustomButtonTemplates(listOf(updated))
            val after = assertNotNull(vm.state.value)
            assertEquals(before.generation, after.generation)
            assertEquals(before.fields.map { it.html }, after.fields.map { it.html })
            assertEquals(before.notetypeJson, after.notetypeJson)
            assertFalse(after.hasMetadataChanges)
            assertTrue(after.fields.all { it.toolbar.customButtons == listOf(updated) })
            assertEquals(listOf(updated), loadAdd().state.value?.customButtonTemplates)
            assertTrue(
                loadAdd()
                    .state.value!!
                    .fields
                    .all { it.toolbar.customButtons == listOf(updated) },
            )
            vm.setCustomButtonTemplates(emptyList())
            assertTrue(
                vm.state.value!!
                    .fields
                    .all { it.toolbar.customButtons.isEmpty() },
            )
            assertEquals(0, col.noteCount())
        }

    @Test
    fun `toolbar settings follow field identity after rename and reorder without changing the note type`() =
        runTest {
            val vm = loadAdd()
            val before = assertNotNull(vm.state.value)
            val target = EditorFieldTarget(before.sessionId, before.generation, 1)
            val toolbar = EditorFieldToolbar(hiddenButtons = setOf("BOLD"), buttonOrder = listOf("ITALIC", "BOLD"))
            val preset = EditorToolbarPreset(name = "Answer").withToolbar(toolbar)
            vm.saveToolbarPreset(preset)
            vm.setFieldToolbarPreset(target, preset.id)
            assertEquals(before.notetypeJson, col.notetypes.get(before.noteTypeId).toString())
            assertEquals(before.generation, vm.state.value?.generation)
            assertFalse(vm.state.value!!.hasMetadataChanges)
            assertEquals(
                toolbar,
                loadAdd()
                    .state.value!!
                    .fields[1]
                    .toolbar,
            )
            assertEquals(
                EditorFieldToolbar(),
                vm.state.value!!
                    .fields[0]
                    .toolbar,
            )

            val type = col.notetypes.get(before.noteTypeId)!!.deepClone()
            col.notetypes.renameField(type, type.fields[1], "Answer")
            col.notetypes.repositionField(type, type.fields[1], 0)
            col.notetypes.save(type)
            vm.reloadNoteType(listOf("question", "answer"))
            assertEquals(
                toolbar,
                vm.state.value!!
                    .fields[0]
                    .toolbar,
            )
            assertEquals(
                EditorFieldToolbar(),
                vm.state.value!!
                    .fields[1]
                    .toolbar,
            )
            vm.setFieldToolbarPreset(target, EditorToolbarPreset.DEFAULT_ID)
            assertEquals(
                toolbar,
                vm.state.value!!
                    .fields[0]
                    .toolbar,
            )
            assertEquals(
                EditorFieldToolbar(),
                vm.state.value!!
                    .fields[1]
                    .toolbar,
            )
            assertEquals(0, col.noteCount())
        }

    @Test
    fun `toolbar layout persists without changing the note`() =
        runTest {
            val vm = loadAdd()
            val before = assertNotNull(vm.state.value)
            for (display in EditorToolbarDisplay.entries) {
                vm.setToolbarDisplay(display)
                assertEquals(display, loadAdd().state.value?.toolbarDisplay)
                assertEquals(before.fields, vm.state.value?.fields)
                assertEquals(before.generation, vm.state.value?.generation)
                assertFalse(assertNotNull(vm.state.value).hasMetadataChanges)
            }
            assertEquals(0, col.noteCount())
        }

    @Test
    fun `global options persist without changing the note or draft generation`() =
        runTest {
            val vm = loadAdd()
            val before = assertNotNull(vm.state.value)
            val settings = WebEditorSettings(shrinkImages = false, mathjaxPreview = false, autoCloseTags = false, fontSize = 28)
            vm.setEditorSettings(settings)
            val after = assertNotNull(vm.state.value)
            assertEquals(before.generation, after.generation)
            assertEquals(before.fields, after.fields)
            assertFalse(after.hasMetadataChanges)
            assertEquals(settings, loadAdd().state.value?.settings)
            assertEquals(0, col.noteCount())
        }

    @Test
    fun `field management maps unsaved text by identity after rename reorder and add`() =
        runTest {
            val vm = loadAdd()
            val before = assertNotNull(vm.state.value)
            val type = col.notetypes.get(before.noteTypeId)!!.deepClone()
            col.notetypes.renameField(type, type.fields[1], "Answer")
            col.notetypes.repositionField(type, type.fields[1], 0)
            col.notetypes.addField(type, col.notetypes.newField("Extra"))
            col.notetypes.save(type)

            assertTrue(vm.reloadNoteType(listOf("<b>unsaved question</b>", "unsaved answer")))
            val after = assertNotNull(vm.state.value)
            assertEquals(listOf("Answer", "Front", "Extra"), after.fields.map { it.name })
            assertEquals(listOf("unsaved answer", "<b>unsaved question</b>", ""), after.fields.map { it.html })
            assertEquals(listOf(1, 0, -1), after.baselineFieldMap)
            assertEquals(before.generation + 1, after.generation)
            assertTrue(after.removedFields.isEmpty())
            assertFalse(vm.reloadNoteType(after.fields.map { it.html }))
            assertEquals(0, col.noteCount())
        }

    @Test
    fun `removed field text survives draft recovery and blocks Save until discarded`() =
        runTest {
            val vm = loadAdd()
            val type = col.notetypes.get(vm.state.value!!.noteTypeId)!!.deepClone()
            col.notetypes.removeField(type, type.fields[1])
            col.notetypes.save(type)
            vm.reloadNoteType(listOf("question", "unsaved answer"))
            assertEquals(listOf(EditorRemovedField("Back", "unsaved answer")), vm.state.value?.removedFields)
            assertIs<EditorSaveResult.Invalid>(vm.save(listOf("question")))
            assertEquals(0, col.noteCount())
            val recovered = loadAdd()
            assertTrue(recovered.restoreHostState(vm.hostState(), listOf("question")))
            assertEquals(vm.state.value?.removedFields, recovered.state.value?.removedFields)
            recovered.discardRemovedFields()
            assertIs<EditorSaveResult.Added>(recovered.save(listOf("question")))
            assertNull(recovered.state.value?.baselineFieldMap)
        }

    @Test
    fun `process death during field management restores the old draft into the new schema`() =
        runTest {
            val vm = loadAdd()
            val host = vm.hostState()
            val type = col.notetypes.get(vm.state.value!!.noteTypeId)!!.deepClone()
            col.notetypes.repositionField(type, type.fields[1], 0)
            col.notetypes.save(type)
            val recovered = loadAdd()
            assertTrue(recovered.restoreHostState(host, listOf("question", "answer")))
            assertEquals(
                listOf("answer", "question"),
                recovered.state.value
                    ?.fields
                    ?.map { it.html },
            )
            assertEquals(listOf(1, 0), recovered.state.value?.baselineFieldMap)
            assertEquals(vm.state.value!!.generation + 1, recovered.state.value?.generation)
        }

    @Test
    fun `template changes refresh metadata without replacing fields`() =
        runTest {
            val vm = loadAdd()
            val before = vm.state.value!!
            val type = col.notetypes.get(before.noteTypeId)!!.deepClone()
            type.css += "\n.card { color: red; }"
            col.notetypes.save(type)
            assertTrue(vm.reloadNoteType(listOf("question", "answer")))
            assertEquals(before.generation, vm.state.value?.generation)
            assertEquals(
                listOf("question", "answer"),
                vm.state.value
                    ?.fields
                    ?.map { it.html },
            )
            assertNull(vm.state.value?.baselineFieldMap)
        }

    @Test
    fun `pin state changes before the collection operation runs`() =
        runTest {
            val vm = loadAdd()
            val before = assertNotNull(vm.state.value)
            val target = EditorFieldTarget(before.sessionId, before.generation, 1)
            val collection = col
            val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
            try {
                for (pinned in listOf(true, false)) {
                    val operation = launch(start = CoroutineStart.UNDISPATCHED) { vm.setFieldPinned(target, pinned) }
                    assertEquals(
                        pinned,
                        vm.state.value!!
                            .fields[1]
                            .sticky,
                        "Show the requested pin state before backend confirmation",
                    )
                    assertFalse(operation.isCompleted)
                    assertEquals(
                        !pinned,
                        collection.notetypes
                            .get(before.noteTypeId)!!
                            .fields[1]
                            .sticky,
                    )
                    advanceUntilIdle()
                    operation.join()
                    assertEquals(
                        pinned,
                        collection.notetypes
                            .get(before.noteTypeId)!!
                            .fields[1]
                            .sticky,
                    )
                }
            } finally {
                advanceUntilIdle()
                CollectionManager.setTestDispatcher(previousQueue)
            }
        }

    @Test
    fun `failed pin change restores the previous visible state`() =
        runTest {
            val vm = loadAdd()
            val before = assertNotNull(vm.state.value)
            val target = EditorFieldTarget(before.sessionId, before.generation, 1)
            val type = col.notetypes.get(before.noteTypeId)!!.deepClone()
            type.fields[1].name = "Renamed elsewhere"
            col.notetypes.save(type)
            val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
            try {
                val operation = launch(start = CoroutineStart.UNDISPATCHED) { vm.setFieldPinned(target, true) }
                assertTrue(
                    vm.state.value!!
                        .fields[1]
                        .sticky,
                )
                advanceUntilIdle()
                operation.join()
                assertFalse(
                    vm.state.value!!
                        .fields[1]
                        .sticky,
                )
                assertNotNull(vm.error.value)
                assertEquals(before.generation, vm.state.value?.generation)
            } finally {
                advanceUntilIdle()
                CollectionManager.setTestDispatcher(previousQueue)
            }
        }

    @Test
    fun `field settings persist on the note type without changing the draft generation`() =
        runTest {
            val vm = loadAdd()
            val original = assertNotNull(vm.state.value)
            val target = EditorFieldTarget(original.sessionId, original.generation, 1)
            vm.setFieldPinned(target, true)
            vm.setFieldLanguageHint(target, Locale.JAPANESE)

            val updated = assertNotNull(vm.state.value)
            val type = assertNotNull(col.notetypes.get(original.noteTypeId))
            assertTrue(type.fields[1].sticky)
            assertEquals(Locale.JAPANESE, type.fields[1].languageHint)
            assertNull(type.fields[0].languageHint)
            assertTrue(updated.fields[1].sticky)
            assertEquals("ja", updated.fields[1].languageTag)
            assertEquals(original.generation, updated.generation)
            assertEquals(original.fields.map { it.html }, updated.fields.map { it.html })
            assertFalse(updated.hasMetadataChanges)

            vm.setFieldLanguageHint(target, null)
            assertNull(
                vm.state.value
                    ?.fields
                    ?.get(1)
                    ?.languageTag,
            )
            assertNull(
                col.notetypes
                    .get(original.noteTypeId)!!
                    .fields[1]
                    .languageHint,
            )
        }

    @Test
    fun `field typography persists without changing text history or other fields`() =
        runTest {
            val vm = loadAdd()
            val original = assertNotNull(vm.state.value)
            val target = EditorFieldTarget(original.sessionId, original.generation, 1)
            vm.setFieldTypography(target, " monospace ", 27)
            val updated = assertNotNull(vm.state.value)
            val saved = assertNotNull(col.notetypes.get(original.noteTypeId))
            assertEquals("monospace", saved.fields[1].font)
            assertEquals(27, saved.fields[1].fontSize)
            assertEquals(saved.toString(), updated.notetypeJson)
            assertEquals(original.fields[0], updated.fields[0])
            assertEquals(original.fields[1].copy(font = "monospace", fontSize = 27), updated.fields[1])
            assertEquals(original.generation, updated.generation)
            assertFalse(updated.hasMetadataChanges)
            val reopened = assertNotNull(loadAdd().state.value)
            assertEquals("monospace", reopened.fields[1].font)
            assertEquals(27, reopened.fields[1].fontSize)
        }

    @Test
    fun `field direction persists without changing HTML or other fields`() =
        runTest {
            val vm = loadAdd()
            val original = assertNotNull(vm.state.value)
            val target = EditorFieldTarget(original.sessionId, original.generation, 1)
            vm.setFieldDirection(target, true)
            val updated = assertNotNull(vm.state.value)
            assertEquals(original.fields[1].copy(rtl = true), updated.fields[1])
            assertEquals(original.fields[0], updated.fields[0])
            assertEquals(original.generation, updated.generation)
            assertFalse(updated.hasMetadataChanges)
            assertTrue(assertNotNull(col.notetypes.get(original.noteTypeId)).fields[1].jsonObject.getBoolean("rtl"))
            assertTrue(assertNotNull(loadAdd().state.value).fields[1].rtl)
            vm.setFieldDirection(target, false)
            assertEquals(original.fields, assertNotNull(vm.state.value).fields)
        }

    @Test
    fun `HTML editor defaults persist for add and edit without changing content or other fields`() =
        runTest {
            val vm = loadAdd()
            val original = assertNotNull(vm.state.value)
            val target = EditorFieldTarget(original.sessionId, original.generation, 1)
            vm.setFieldHtmlByDefault(target, true)
            val updated = assertNotNull(vm.state.value)
            val saved = assertNotNull(col.notetypes.get(original.noteTypeId))
            assertTrue(saved.fields[1].jsonObject.getBoolean("plainText"))
            assertEquals(saved.toString(), updated.notetypeJson)
            assertEquals(original.fields[1].copy(sourceMode = true), updated.fields[1])
            assertEquals(original.fields[0], updated.fields[0])
            assertEquals(original.generation, updated.generation)
            assertFalse(updated.hasMetadataChanges)
            assertTrue(assertNotNull(loadAdd().state.value).fields[1].sourceMode)
            val note = addBasicNote()
            assertTrue(assertNotNull(loadEdit(note.firstCard().id).state.value).fields[1].sourceMode)

            assertIs<EditorSaveResult.Added>(vm.save(listOf("new front", "new back")))
            val next = assertNotNull(vm.state.value)
            assertTrue(next.fields[1].sourceMode, "The next Add must use the saved default")
            vm.setFieldHtmlByDefault(target.copy(generation = next.generation), false)
            assertFalse(assertNotNull(vm.state.value).fields[1].sourceMode)
            assertFalse(assertNotNull(loadAdd().state.value).fields[1].sourceMode)
            assertEquals(listOf("", ""), assertNotNull(vm.state.value).fields.map { it.html })
        }

    @Test
    fun `collapse defaults persist for add and edit without changing content or other fields`() =
        runTest {
            val vm = loadAdd()
            val original = assertNotNull(vm.state.value)
            val target = EditorFieldTarget(original.sessionId, original.generation, 1)
            vm.setFieldCollapsedByDefault(target, true)
            val updated = assertNotNull(vm.state.value)
            val saved = assertNotNull(col.notetypes.get(original.noteTypeId))
            assertTrue(saved.fields[1].jsonObject.getBoolean("collapsed"))
            assertEquals(saved.toString(), updated.notetypeJson)
            assertEquals(original.fields[1].copy(collapsed = true), updated.fields[1])
            assertEquals(original.fields[0], updated.fields[0])
            assertEquals(original.generation, updated.generation)
            assertFalse(updated.hasMetadataChanges)
            assertTrue(assertNotNull(loadAdd().state.value).fields[1].collapsed)
            val note = addBasicNote()
            assertTrue(assertNotNull(loadEdit(note.firstCard().id).state.value).fields[1].collapsed)

            assertIs<EditorSaveResult.Added>(vm.save(listOf("new front", "new back")))
            val next = assertNotNull(vm.state.value)
            assertTrue(next.fields[1].collapsed, "The next Add must use the saved default")
            vm.setFieldCollapsedByDefault(target.copy(generation = next.generation), false)
            assertFalse(assertNotNull(vm.state.value).fields[1].collapsed)
            assertFalse(assertNotNull(loadAdd().state.value).fields[1].collapsed)
            assertEquals(listOf("", ""), assertNotNull(vm.state.value).fields.map { it.html })
        }

    @Test
    fun `invalid field typography cannot change the note type`() =
        runTest {
            val vm = loadAdd()
            val original = assertNotNull(vm.state.value)
            val target = EditorFieldTarget(original.sessionId, original.generation, 0)
            for ((font, size) in listOf("" to 20, "Arial" to 4, "Arial" to 301)) {
                assertFailsWith<IllegalArgumentException> { vm.setFieldTypography(target, font, size) }
            }
            assertEquals(original, vm.state.value)
            assertEquals(
                original.fields[0].fontSize,
                col.notetypes
                    .get(original.noteTypeId)!!
                    .fields[0]
                    .fontSize,
            )
        }

    @Test
    fun `a field menu from a previous document cannot change settings`() =
        runTest {
            val vm = loadAdd()
            val original = assertNotNull(vm.state.value)
            val target = EditorFieldTarget(original.sessionId, original.generation, 1)
            vm.selectNoteType(col.notetypes.basicOptionalReversed.id, listOf("front", "back"))
            val changed = assertNotNull(vm.state.value)

            vm.setFieldPinned(target, true)
            vm.setFieldLanguageHint(target, Locale.JAPANESE)
            vm.setFieldTypography(target, "serif", 30)
            vm.setFieldDirection(target, true)
            vm.setFieldCollapsedByDefault(target, true)
            vm.setFieldHtmlByDefault(target, true)

            assertEquals(changed, vm.state.value)
            assertNull(
                col.notetypes
                    .get(original.noteTypeId)!!
                    .fields[1]
                    .languageHint,
            )
            assertFalse(
                col.notetypes
                    .get(original.noteTypeId)!!
                    .fields[1]
                    .sticky,
            )
            assertNull(
                col.notetypes
                    .get(changed.noteTypeId)!!
                    .fields[1]
                    .languageHint,
            )
        }

    @Test
    fun `Add saves HTML and tags then retains only sticky fields`() =
        runTest {
            val type = col.notetypes.basic
            type.fields[0].sticky = true
            col.notetypes.save(type)
            val vm = loadAdd()
            vm.setTags(listOf("retained"))
            val result = vm.save(listOf("<b>front</b>", "back"))

            assertEquals(EditorSaveResult.Added(1), result)
            val saved = col.getNote(col.findNotes("").single())
            assertEquals(listOf("<b>front</b>", "back"), saved.fields)
            assertEquals(listOf("retained"), saved.tags)
            val next = assertNotNull(vm.state.value)
            assertEquals(listOf("<b>front</b>", ""), next.fields.map { it.html })
            assertEquals(listOf("retained"), next.tags)
            assertEquals(1, next.generation)
            assertTrue(next.resetFieldBaseline)
            assertEquals(0L, next.noteId)
            assertFalse(next.hasMetadataChanges)
            assertFalse(next.isSaving)

            col.undo()
            assertEquals(0, col.noteCount())
        }

    @Test
    fun `invalid Add does not clear the document or add a note`() =
        runTest {
            col.notetypes.setCurrent(col.notetypes.basic)
            val vm = loadAdd()
            val original = vm.state.value
            assertIs<EditorSaveResult.Invalid>(vm.save(listOf("", "answer")))
            assertEquals(original, vm.state.value)
            assertEquals(0, col.noteCount())
        }

    @Test
    fun `Save outlives the activity caller and retains its result for the replacement`() =
        runTest {
            val vm = loadAdd()
            val collection = col
            val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
            try {
                val activityJob = launch(start = CoroutineStart.UNDISPATCHED) { vm.save(listOf("front", "back")) }
                assertTrue(assertNotNull(vm.state.value).isSaving)
                activityJob.cancel()
                advanceUntilIdle()
                assertEquals(1, collection.noteCount())
                assertEquals(1, vm.state.value?.generation)
                assertEquals(EditorSaveResult.Added(1), vm.saveResult.value)
                vm.consumeSaveResult()
                assertNull(vm.saveResult.value)
            } finally {
                CollectionManager.setTestDispatcher(previousQueue)
            }
        }

    @Test
    fun `concurrent Save clicks share one collection operation`() =
        runTest {
            val vm = loadAdd()
            val collection = col
            val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
            try {
                val first = async(start = CoroutineStart.UNDISPATCHED) { vm.save(listOf("front", "back")) }
                val second = async(start = CoroutineStart.UNDISPATCHED) { vm.save(listOf("front", "back")) }
                advanceUntilIdle()
                assertEquals(EditorSaveResult.Added(1), first.await())
                assertEquals(first.await(), second.await())
                assertEquals(1, collection.noteCount())
            } finally {
                CollectionManager.setTestDispatcher(previousQueue)
            }
        }

    @Test
    fun `missing cloze permits explicit override while an empty first field still fails`() =
        runTest {
            val vm = loadAdd()
            vm.selectNoteType(col.notetypes.cloze.id, listOf("", ""))
            val warning = assertIs<EditorSaveResult.Invalid>(vm.save(listOf("no deletion", "")))
            assertTrue(warning.canSaveAnyway)
            assertNull(warning.fieldIndex)
            val empty = assertIs<EditorSaveResult.Invalid>(vm.save(listOf("<br>", "back"), allowMissingCloze = true))
            assertEquals(0, empty.fieldIndex)
            assertIs<EditorSaveResult.Added>(vm.save(listOf("no deletion", ""), allowMissingCloze = true))
            assertEquals(1, col.noteCount())
        }

    @Test
    fun `Edit saves fields and tags and moves only the selected card`() =
        runTest {
            val note = addBasicAndReversedNote()
            val cards = note.cards(col)
            val destination = addDeck("Destination")
            val vm = loadEdit(cards.first().id)
            vm.selectDeck(destination)
            vm.setTags(listOf("updated"))

            assertEquals(EditorSaveResult.Saved, vm.save(listOf("new front", "new back")))
            assertEquals(listOf("new front", "new back"), col.getNote(note.id).fields)
            assertEquals(listOf("updated"), col.getNote(note.id).tags)
            assertEquals(destination, col.getCard(cards.first().id).did)
            assertEquals(cards.last().did, col.getCard(cards.last().id).did)
            assertEquals(1, col.noteCount())
        }

    @Test
    fun `Edit preserves the browser card selection for deck changes`() =
        runTest {
            val first = addBasicNote("first").firstCard()
            val second = addBasicNote("second").firstCard()
            val destination = addDeck("Destination")
            val vm = loadEdit(first.id, listOf(first.id, second.id))
            vm.selectDeck(destination)
            assertEquals(EditorSaveResult.Saved, vm.save(listOf("edited", "answer")))
            assertEquals(destination, col.getCard(first.id).did)
            assertEquals(destination, col.getCard(second.id).did)
            assertEquals("second", col.getNote(second.nid).fields[0])
        }

    @Test
    fun `successful Edit establishes the current document and native clean baseline`() =
        runTest {
            val note = addBasicNote()
            val vm = loadEdit(note.firstCard().id)
            val destination = addDeck("Destination")
            val savedFields = listOf("<b>saved front</b>", "saved back")
            vm.selectDeck(destination)
            vm.setTags(listOf("saved"))
            assertTrue(assertNotNull(vm.state.value).hasMetadataChanges)

            assertEquals(EditorSaveResult.Saved, vm.save(savedFields))

            val saved = assertNotNull(vm.state.value)
            assertEquals(savedFields, saved.fields.map { it.html })
            assertEquals(1, saved.generation)
            assertTrue(saved.resetFieldBaseline)
            assertFalse(saved.hasMetadataChanges)
            assertFalse(saved.isSaving)
            vm.setTags(listOf("changed"))
            assertTrue(assertNotNull(vm.state.value).hasMetadataChanges)
            vm.setTags(listOf("saved"))
            assertFalse(assertNotNull(vm.state.value).hasMetadataChanges)
        }

    @Test
    fun `unchanged Edit closes without adding an undo step`() =
        runTest {
            val note = addBasicNote()
            val vm = loadEdit(note.firstCard().id)
            val previousUndo = col.undoStatus()
            assertEquals(EditorSaveResult.Saved, vm.save(note.fields))
            assertEquals(previousUndo, col.undoStatus())
        }

    @Test
    fun `explicit Add deck is retained while filtered destination falls back`() =
        runTest {
            val defaults = col.defaultsForAdding()
            val destination = addDeck("Explicit")
            assertEquals(destination, loadAdd(destination).state.value?.deckId)
            val filtered = addDynamicDeck("Filtered")
            assertEquals(defaults.deckId, loadAdd(filtered).state.value?.deckId)
        }

    @Test
    fun `Image Occlusion is selectable before its note type exists without changing the draft or collection`() =
        runTest {
            col.notetypes
                .all()
                .filter { it.isImageOcclusion }
                .forEach { col.notetypes.remove(it.id) }
            val vm = loadAdd()
            vm.setTags(listOf("unfinished"))
            val before = vm.state.value
            val originalTypes = col.notetypes.all().map { it.id }
            assertFalse(col.notetypes.all().any { it.isImageOcclusion })

            val choices = vm.noteTypeChoices()

            assertTrue(EditorChoice(0, TR.notetypesImageOcclusionName()) in choices)
            assertTrue(vm.isImageOcclusionType(0))
            assertEquals(before, vm.state.value)
            assertEquals(originalTypes, col.notetypes.all().map { it.id })
            assertEquals(0, col.noteCount())

            col.addImageOcclusionNoteType()
            val imageOcclusion = col.notetypes.imageOcclusion
            val updatedChoices = vm.noteTypeChoices()
            assertTrue(EditorChoice(imageOcclusion.id, imageOcclusion.name) in updatedChoices)
            assertFalse(updatedChoices.any { it.id == 0L })
        }

    @Test
    fun `note type choices reflect collection changes since the editor opened`() =
        runTest {
            val vm = loadAdd()
            val newType = col.notetypes.copy(col.notetypes.basic)
            val imageOcclusion = col.notetypes.imageOcclusion
            val types = vm.noteTypeChoices()
            assertTrue(types.any { it.id == newType.id })
            assertTrue(types.any { it.id == imageOcclusion.id })
            assertTrue(vm.isImageOcclusionType(imageOcclusion.id))
            assertFalse(vm.isImageOcclusionType(newType.id))
            assertEquals(types.sortedBy { it.name.lowercase() }, types)
        }

    @Test
    fun `delayed note type choices preserve metadata changed while loading`() =
        runTest {
            val vm = loadAdd()
            val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
            try {
                val choices = async(start = CoroutineStart.UNDISPATCHED) { vm.noteTypeChoices() }
                assertFalse(choices.isCompleted)
                vm.setTags(listOf("typed while loading"))
                val edited = vm.state.value
                advanceUntilIdle()
                assertTrue(choices.await().isNotEmpty())
                assertEquals(edited, vm.state.value)
            } finally {
                CollectionManager.setTestDispatcher(previousQueue)
            }
        }

    @Test
    fun `type change retains field values and increments command generation`() =
        runTest {
            col.notetypes.setCurrent(col.notetypes.basic)
            val vm = loadAdd()
            val original = assertNotNull(vm.state.value)
            vm.selectNoteType(col.notetypes.basicOptionalReversed.id, listOf("front", "back"))
            val next = assertNotNull(vm.state.value)
            assertEquals(listOf("front", "back", ""), next.fields.map { it.html })
            assertEquals(original.sessionId, next.sessionId)
            assertEquals(original.generation + 1, next.generation)
            assertFalse(next.resetFieldBaseline)
            assertTrue(next.hasMetadataChanges)
        }

    @Test
    fun `fresh Add does not reopen the previous checkpoint`() =
        runTest {
            val previous = loadAdd()
            val previousId = previous.draftId
            previous.rememberDraft()
            ViewModelStore().apply { put("editor", previous) }.clear()

            val fresh = loadAdd()
            try {
                assertNotEquals(previousId, fresh.draftId)
                assertFalse(fresh.takeDraftRecoveryRequired())
            } finally {
                ViewModelStore().apply { put("editor", fresh) }.clear()
            }
        }

    @Test
    fun `restored Android instance still reuses its Add checkpoint`() =
        runTest {
            val savedState = SavedStateHandle()
            val previous = EditorViewModel(savedState)
            previous.load(NoteEditorFragment.addNoteArgs())
            previous.rememberDraft()
            val previousId = previous.draftId
            val restoredState = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any?>(it) })
            ViewModelStore().apply { put("editor", previous) }.clear()

            val restored = EditorViewModel(restoredState)
            try {
                restored.load(NoteEditorFragment.addNoteArgs())
                assertEquals(previousId, restored.draftId)
                assertTrue(restored.takeDraftRecoveryRequired())
            } finally {
                ViewModelStore().apply { put("editor", restored) }.clear()
            }
        }

    @Test
    fun `draft restores native edits and the original clean metadata`() =
        runTest {
            col.notetypes.setCurrent(col.notetypes.basic)
            val destination = addDeck("Draft destination")
            val vm = loadAdd()
            val initialDeck = assertNotNull(vm.state.value).deckId
            vm.selectDeck(destination)
            vm.setTags(listOf("unsaved"))
            val hostState = vm.hostState()
            val replacement = loadAdd()
            assertTrue(replacement.restoreHostState(hostState, listOf("unsaved field", "")))
            assertEquals(destination, replacement.state.value?.deckId)
            assertEquals(listOf("unsaved"), replacement.state.value?.tags)
            assertEquals(
                "unsaved field",
                replacement.state.value
                    ?.fields
                    ?.first()
                    ?.html,
            )
            assertTrue(assertNotNull(replacement.state.value).hasMetadataChanges)

            replacement.selectDeck(initialDeck)
            replacement.setTags(emptyList())
            assertFalse(assertNotNull(replacement.state.value).hasMetadataChanges)
        }

    @Test
    fun `draft restore preserves the generation used by delayed commands`() =
        runTest {
            val vm = loadAdd()
            vm.selectNoteType(col.notetypes.basicOptionalReversed.id, listOf("", ""))
            val before = assertNotNull(vm.state.value)
            val replacement = loadAdd()
            assertTrue(replacement.restoreHostState(vm.hostState(), before.fields.map { it.html }))
            assertEquals(before.generation, replacement.state.value?.generation)
        }

    @Test
    fun `shared text is interpreted as plain text instead of executable HTML`() =
        runTest {
            val vm = EditorViewModel(SavedStateHandle())
            vm.load(NoteEditorFragment.addNoteArgs().apply { putString(Intent.EXTRA_TEXT, "<b>text</b>\n& more") })
            assertEquals(
                "&lt;b&gt;text&lt;/b&gt;<br>&amp; more",
                vm.state.value
                    ?.fields
                    ?.first()
                    ?.html,
            )
        }

    @Test
    fun `shared subject and text populate the first two fields`() =
        runTest {
            val vm = EditorViewModel(SavedStateHandle())
            vm.load(
                NoteEditorFragment.addNoteArgs().apply {
                    putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.NOTEEDITOR_INTENT_ADD.value)
                    putString(Intent.EXTRA_SUBJECT, "subject")
                    putString(Intent.EXTRA_TEXT, "body")
                },
            )
            assertEquals(
                listOf("subject", "body"),
                vm.state.value
                    ?.fields
                    ?.map { it.html },
            )
        }

    @Test
    fun `processed text and create flashcard source fields are retained`() =
        runTest {
            val process = EditorViewModel(SavedStateHandle())
            process.load(NoteEditorFragment.addNoteArgs().apply { putString(Intent.EXTRA_PROCESS_TEXT, "selected text") })
            assertEquals(
                "selected text",
                process.state.value
                    ?.fields
                    ?.first()
                    ?.html,
            )
            val flashcard = EditorViewModel(SavedStateHandle())
            flashcard.load(
                NoteEditorFragment.addNoteArgs().apply {
                    putString(NoteEditorFragment.SOURCE_TEXT, "source")
                    putString(NoteEditorFragment.TARGET_TEXT, "translation")
                },
            )
            assertEquals(
                listOf("source", "translation"),
                flashcard.state.value
                    ?.fields
                    ?.map { it.html },
            )
        }

    @Test
    fun `Add draft discovery separates plain Add copy and distinct shares`() {
        val plain = NoteEditorFragment.addNoteArgs()
        val copy =
            Bundle(plain).apply {
                putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.NOTEEDITOR.value)
                putString(NoteEditorFragment.EXTRA_CONTENTS, "front\u001fback")
            }
        val share =
            Bundle(plain).apply {
                putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.NOTEEDITOR_INTENT_ADD.value)
                putString(Intent.EXTRA_TEXT, "shared A")
            }
        val anotherShare = Bundle(share).apply { putString(Intent.EXTRA_TEXT, "shared B") }
        val targets = listOf(plain, copy, share, anotherShare).map(::editorAddDraftTarget)
        assertEquals(4, targets.toSet().size)
        assertEquals("add", targets.first())
        assertEquals(editorAddDraftTarget(share), editorAddDraftTarget(Bundle(share)))
        assertFalse(editorAddDraftTarget(share).contains("shared A"))
    }

    @Test
    fun `Add can start when the default note type is Image Occlusion`() =
        runTest {
            col.notetypes.setCurrent(col.notetypes.imageOcclusion)
            val vm = loadAdd()
            assertTrue(vm.noteTypeChoices().any { it.id == col.notetypes.imageOcclusion.id })
            assertFalse(assertNotNull(vm.state.value).noteTypeId == col.notetypes.imageOcclusion.id)
        }

    @Test
    fun `draft from another target or collection is rejected`() =
        runTest {
            val first = addBasicNote("first").firstCard()
            val second = addBasicNote("second").firstCard()
            val hostState = loadEdit(first.id).hostState()
            val differentNote = loadEdit(second.id)
            assertFalse(differentNote.restoreHostState(hostState, listOf("wrong note", "")))
            val otherCollection = JSONObject(hostState).put("collection", "another-collection").toString()
            assertFalse(loadEdit(first.id).restoreHostState(otherCollection, listOf("wrong collection", "")))
            assertEquals(
                "second",
                differentNote.state.value
                    ?.fields
                    ?.first()
                    ?.html,
            )
        }

    @Test
    fun `draft with reordered fields is rejected instead of applying by ordinal`() =
        runTest {
            val vm = loadAdd()
            val host = JSONObject(vm.hostState())
            host.getJSONArray("fields").put(0, "Changed field")
            assertFalse(vm.restoreHostState(host.toString(), listOf("front", "back")))
        }

    @Test
    fun `deleted note fails Save without creating a replacement`() =
        runTest {
            val note = addBasicNote()
            val vm = loadEdit(note.firstCard().id)
            col.removeNotes(listOf(note.id))
            assertIs<EditorSaveResult.Invalid>(vm.save(listOf("unsaved", "answer")))
            assertEquals(0, col.noteCount())
            assertFalse(assertNotNull(vm.state.value).isSaving)
        }

    private suspend fun loadAdd(deckId: Long? = null): EditorViewModel =
        EditorViewModel(SavedStateHandle()).apply {
            load(NoteEditorFragment.addNoteArgs().apply { deckId?.let { putLong(NoteEditorFragment.EXTRA_DID, it) } })
            assertNotNull(state.value, error.value)
        }

    private suspend fun loadEdit(
        cardId: Long,
        selectedCards: List<Long> = listOf(cardId),
    ): EditorViewModel =
        EditorViewModel(SavedStateHandle()).apply {
            load(
                Bundle().apply {
                    putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.EDIT.value)
                    putLong(NoteEditorFragment.EXTRA_CARD_ID, cardId)
                    putLongArray(NoteEditorFragment.EXTRA_CARD_IDS, selectedCards.toLongArray())
                },
            )
            assertNotNull(state.value, error.value)
        }
}
