// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import com.ichi2.anki.noteeditor.web.WebEditorSettings
import com.ichi2.anki.servicelayer.LanguageHintService.languageHint
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EditorViewModelTest : JvmTest() {
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
            before.fields.indices.forEach { index ->
                vm.setFieldToolbar(
                    EditorFieldTarget(before.sessionId, before.generation, index),
                    EditorFieldToolbar(customButtons = listOf(ruby)),
                )
            }
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
            vm.setFieldToolbar(target, toolbar)
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
            vm.setFieldToolbar(target, EditorFieldToolbar(hiddenButtons = setOf("ITALIC")))
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
    fun `a field menu from a previous document cannot change settings`() =
        runTest {
            val vm = loadAdd()
            val original = assertNotNull(vm.state.value)
            val target = EditorFieldTarget(original.sessionId, original.generation, 1)
            vm.selectNoteType(col.notetypes.basicOptionalReversed.id, listOf("front", "back"))
            val changed = assertNotNull(vm.state.value)

            vm.setFieldPinned(target, true)
            vm.setFieldLanguageHint(target, Locale.JAPANESE)

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
