// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.ui.TransitionDirection
import com.ichi2.anki.common.utils.ext.AddingDefaultsMode
import com.ichi2.anki.common.utils.ext.addingDefaultsMode
import com.ichi2.anki.noteeditor.toIntent
import com.ichi2.anki.settings.Prefs
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ComposeEditorRoutingTest : RobolectricTest() {
    @After
    fun clearDevelopmentFlag() {
        getPreferences().edit { remove(getResourceString(R.string.dev_compose_note_editor_key)) }
    }

    @Test
    fun `Compose note editor is disabled by default`() {
        getPreferences().edit { remove(getResourceString(R.string.dev_compose_note_editor_key)) }
        assertFalse(Prefs.isComposeNoteEditorEnabled)
        assertEquals(
            NoteEditorActivity::class.java.name,
            NoteEditorDestination
                .AddNote()
                .toIntent(targetContext)
                .component
                ?.className,
        )
    }

    @Test
    fun `standard destinations stay on the legacy editor while flag is off`() {
        Prefs.isComposeNoteEditorEnabled = false
        standardDestinations().forEach { destination ->
            val intent = destination.toIntent(targetContext)
            assertEquals(NoteEditorActivity::class.java.name, intent.component?.className, destination.toString())
            assertFalse(intent.shouldUseComposeEditor(), destination.toString())
        }
    }

    @Test
    fun `flag enables Compose for Add Edit previewer copy and text share`() {
        Prefs.isComposeNoteEditorEnabled = true
        standardDestinations().forEach { destination ->
            val intent = destination.toIntent(targetContext)
            assertEquals(ComposeNoteEditorActivity::class.java.name, intent.component?.className, destination.toString())
            assertTrue(intent.shouldUseComposeEditor(), destination.toString())
        }
    }

    @Test
    fun `Compose routing preserves edit selection copy and share payloads`() {
        Prefs.isComposeNoteEditorEnabled = true
        val edit = NoteEditorDestination.EditSelection(listOf(21L, 22L), TransitionDirection.DEFAULT).toIntent(targetContext)
        assertEquals(21L, edit.getLongExtra(NoteEditorFragment.EXTRA_CARD_ID, 0L))
        assertContentEquals(longArrayOf(21L, 22L), edit.getLongArrayExtra(NoteEditorFragment.EXTRA_CARD_IDS))
        val previewer = NoteEditorDestination.EditNoteFromPreviewer(23L).toIntent(targetContext)
        assertEquals(23L, previewer.getLongExtra(NoteEditorFragment.EXTRA_EDIT_FROM_CARD_ID, 0L))
        val copy = NoteEditorDestination.CopyNote(24L, "<b>front</b>\u001fback", listOf("tag")).toIntent(targetContext)
        assertEquals(24L, copy.getLongExtra(NoteEditorFragment.EXTRA_DID, 0L))
        assertEquals("<b>front</b>\u001fback", copy.getStringExtra(NoteEditorFragment.EXTRA_CONTENTS))
        assertContentEquals(arrayOf("tag"), copy.getStringArrayExtra(NoteEditorFragment.EXTRA_TAGS))
        val share = textShare().toIntent(targetContext)
        assertEquals(Intent.ACTION_SEND, share.action)
        assertEquals("shared text", share.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `Image Occlusion and image share keep their dedicated legacy flow`() {
        Prefs.isComposeNoteEditorEnabled = true
        val imageUri = Uri.parse("content://test/image.png")
        val imageShare =
            NoteEditorDestination.PassArguments(
                Bundle().apply { putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.ADD_IMAGE.value) },
                Intent.ACTION_SEND,
            )
        listOf(NoteEditorDestination.ImageOcclusion(imageUri), imageShare).forEach { destination ->
            val intent = destination.toIntent(targetContext)
            assertEquals(NoteEditorActivity::class.java.name, intent.component?.className)
            assertFalse(intent.shouldUseComposeEditor())
        }
    }

    @Test
    fun `loaded Edit and previewer destinations use the actual note type for IO routing`() =
        runTest {
            val occlusion =
                col.newNote(col.notetypes.imageOcclusion).apply {
                    fields[0] = "{{c1::x}}"
                    col.addNote(this, col.decks.selected())
                }
            val basic = addBasicNote()
            col.notetypes.setCurrent(col.notetypes.imageOcclusion)

            listOf(occlusion to true, basic to false).forEach { (note, requiresLegacy) ->
                val cardId = note.firstCard().id
                val destinations =
                    listOf(
                        NoteEditorDestination.EditSelection(listOf(cardId), TransitionDirection.DEFAULT),
                        NoteEditorDestination.EditNoteFromPreviewer(cardId),
                    )
                destinations.forEach { destination ->
                    val arguments = requireNotNull(destination.toIntent(targetContext).extras)
                    assertEquals(requiresLegacy, col.requiresLegacyEditor(arguments), destination.toString())
                }
            }
        }

    @Test
    fun `loaded Add follows the default note type and redirects only IO to legacy`() =
        runTest {
            col.config.addingDefaultsMode = AddingDefaultsMode.DECIDE_BY_NOTE_TYPE
            val args = NoteEditorFragment.addNoteArgs()
            col.notetypes.setCurrent(col.notetypes.imageOcclusion)
            assertEquals(col.notetypes.imageOcclusion.id, col.defaultsForAdding().notetypeId)
            assertTrue(col.requiresLegacyEditor(args))

            col.notetypes.setCurrent(col.notetypes.basic)
            assertEquals(col.notetypes.basic.id, col.defaultsForAdding().notetypeId)
            assertFalse(col.requiresLegacyEditor(args))
        }

    @Test
    fun `explicit legacy bypass wins over the development flag`() {
        Prefs.isComposeNoteEditorEnabled = true
        val destination =
            NoteEditorDestination.PassArguments(
                NoteEditorFragment.addNoteArgs().apply { putBoolean(ComposeNoteEditorActivity.EXTRA_USE_LEGACY, true) },
            )
        val intent = destination.toIntent(targetContext)
        assertFalse(intent.shouldUseComposeEditor())
        assertEquals(NoteEditorActivity::class.java.name, intent.component?.className)
    }

    @Test
    fun `Aedict Notepad keeps the legacy multi-entry share parser`() {
        Prefs.isComposeNoteEditorEnabled = true
        val destination =
            NoteEditorDestination.PassArguments(
                Bundle().apply {
                    putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.NOTEEDITOR_INTENT_ADD.value)
                    putString(Intent.EXTRA_SUBJECT, "Aedict Notepad")
                    putString(Intent.EXTRA_TEXT, "[default]\nword:translation")
                },
                Intent.ACTION_SEND,
            )
        val intent = destination.toIntent(targetContext)
        assertFalse(intent.shouldUseComposeEditor())
        assertEquals(NoteEditorActivity::class.java.name, intent.component?.className)
    }

    private fun standardDestinations(): List<NoteEditorDestination> =
        listOf(
            NoteEditorDestination.AddNote(deckId = 20L),
            NoteEditorDestination.AddNoteFromReviewer(),
            NoteEditorDestination.AddNoteFromCardBrowser("search", 20L),
            NoteEditorDestination.AddInstantNote("instant text"),
            NoteEditorDestination.EditSelection(listOf(21L, 22L), TransitionDirection.DEFAULT),
            NoteEditorDestination.EditNoteFromPreviewer(23L),
            NoteEditorDestination.CopyNote(24L, "front\u001fback", listOf("tag")),
            textShare(),
        )

    private fun textShare(): NoteEditorDestination.PassArguments =
        NoteEditorDestination.PassArguments(
            Bundle().apply {
                putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.NOTEEDITOR_INTENT_ADD.value)
                putString(Intent.EXTRA_TEXT, "shared text")
            },
            Intent.ACTION_SEND,
        )
}
