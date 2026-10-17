// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.noteeditor.web.WebEditorDocument
import com.ichi2.anki.noteeditor.web.WebEditorDraft
import com.ichi2.anki.noteeditor.web.WebEditorField
import com.ichi2.anki.noteeditor.web.WebEditorFieldTypography
import com.ichi2.anki.noteeditor.web.WebEditorStatus
import com.ichi2.anki.noteeditor.web.WebEditorView
import com.ichi2.testutils.JvmTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EditorWebSessionTest : JvmTest() {
    @Test
    fun `HTML defaults load with the document and changes wait for the next note`() =
        runTest {
            val originalDispatcher = ioDispatcher
            ioDispatcher = StandardTestDispatcher(testScheduler)
            val model = EditorViewModel(SavedStateHandle())
            val store = ViewModelStore().apply { put("editor", model) }
            val errors = mutableListOf<String>()
            val session = EditorWebSession(model, backgroundScope, errors::add)
            val web = mockk<WebEditorView>(relaxed = true)
            every { web.context } returns ApplicationProvider.getApplicationContext()
            try {
                model.load(NoteEditorFragment.addNoteArgs())
                val original = checkNotNull(model.state.value)
                val target = EditorFieldTarget(original.sessionId, original.generation, 0)
                model.setFieldHtmlByDefault(target, true)
                session.attach(web)
                runCurrent()
                assertTrue(session.ready.value)
                coVerify(exactly = 1) {
                    web.loadDocumentAndCreateDraft(
                        match { it.fields.map { field -> field.sourceMode } == listOf(true, false) },
                        any(),
                        any(),
                        any(),
                    )
                }
                model.setFieldHtmlByDefault(target, false)
                runCurrent()
                coVerify(exactly = 1) { web.loadDocumentAndCreateDraft(any(), any(), any(), any()) }
                coVerify(exactly = 0) { web.execute(any(), any(), any()) }
                assertEquals(EditorSaveResult.Added(1), model.save(listOf("<b>front</b>", "back")))
                runCurrent()
                coVerify(exactly = 1) {
                    web.loadDocumentAndCreateDraft(
                        match { it.generation == original.generation + 1 && it.fields.none { field -> field.sourceMode } },
                        any(),
                        any(),
                        any(),
                    )
                }
                assertTrue(errors.isEmpty(), errors.joinToString())
            } finally {
                session.detach()
                store.clear()
                ioDispatcher = originalDispatcher
            }
        }

    @Test
    fun `initial focus skips collapsed fields and stays closed when all fields are collapsed`() =
        runTest {
            val originalDispatcher = ioDispatcher
            ioDispatcher = StandardTestDispatcher(testScheduler)
            try {
                for (collapsedFields in listOf(setOf(0), setOf(0, 1))) {
                    val type = col.notetypes.basic.deepClone()
                    type.fields.forEachIndexed { index, field -> field.jsonObject.put("collapsed", index in collapsedFields) }
                    col.notetypes.save(type)
                    val model = EditorViewModel(SavedStateHandle())
                    val store = ViewModelStore().apply { put("editor", model) }
                    val errors = mutableListOf<String>()
                    val session = EditorWebSession(model, backgroundScope, errors::add)
                    val web = mockk<WebEditorView>(relaxed = true)
                    every { web.context } returns ApplicationProvider.getApplicationContext()
                    try {
                        model.load(NoteEditorFragment.addNoteArgs())
                        session.attach(web)
                        runCurrent()
                        assertTrue(session.ready.value)
                        val state = checkNotNull(model.state.value)
                        assertEquals(if (1 in collapsedFields) null else 1, state.initialFocusField)
                        // Updating the saved default must not reload the current fields or move focus.
                        model.setFieldCollapsedByDefault(EditorFieldTarget(state.sessionId, state.generation, 0), false)
                        runCurrent()
                        assertFalse(checkNotNull(model.state.value).fields[0].collapsed)
                        coVerify(exactly = 0) { web.focusField(0, any()) }
                        coVerify(exactly = if (1 in collapsedFields) 0 else 1) { web.focusField(1, any()) }
                        coVerify(exactly = 1) {
                            web.loadDocumentAndCreateDraft(
                                match { document -> document.fields.map { it.collapsed } == listOf(true, 1 in collapsedFields) },
                                any(),
                                any(),
                                any(),
                            )
                        }
                        assertTrue(errors.isEmpty(), errors.joinToString())
                    } finally {
                        session.detach()
                        store.clear()
                    }
                }
            } finally {
                ioDispatcher = originalDispatcher
            }
        }

    @Test
    fun `field typography updates in place with no extra first-load request`() =
        runTest {
            val originalDispatcher = ioDispatcher
            ioDispatcher = StandardTestDispatcher(testScheduler)
            val model = EditorViewModel(SavedStateHandle())
            val store = ViewModelStore().apply { put("editor", model) }
            val errors = mutableListOf<String>()
            val session = EditorWebSession(model, backgroundScope, errors::add)
            val web = mockk<WebEditorView>(relaxed = true)
            every { web.context } returns ApplicationProvider.getApplicationContext()
            try {
                model.load(NoteEditorFragment.addNoteArgs())
                session.attach(web)
                runCurrent()
                val original = checkNotNull(model.state.value)
                assertTrue(session.ready.value)
                coVerify(exactly = 0) { web.updateFieldTypography(any()) }
                model.setFieldTypography(EditorFieldTarget(original.sessionId, original.generation, 1), "serif", 30)
                runCurrent()
                model.setFieldDirection(EditorFieldTarget(original.sessionId, original.generation, 1), true)
                runCurrent()
                val fonts = checkNotNull(model.state.value).fields.map { WebEditorFieldTypography(it.font, it.fontSize, it.rtl) }
                coVerify(exactly = 1) { web.updateFieldTypography(fonts) }
                coVerify(exactly = 1) { web.loadDocumentAndCreateDraft(any(), any(), any(), any()) }
                session.detach()

                val draft =
                    WebEditorDraft(
                        WebEditorDocument(
                            original.sessionId,
                            original.generation,
                            original.fields.map { WebEditorField(it.name, it.html, it.font, it.fontSize) },
                        ),
                        original.fields.map { it.html },
                        model.hostState(),
                    )
                val restored = mockk<WebEditorView>(relaxed = true)
                every { restored.context } returns ApplicationProvider.getApplicationContext()
                coEvery { restored.restoreDraft(model.draftId) } returns draft
                session.attach(restored)
                runCurrent()
                assertTrue(session.ready.value)
                coVerify(exactly = 1) { restored.updateFieldTypography(fonts) }
                coVerify(exactly = 0) { restored.loadDocumentAndCreateDraft(any(), any(), any(), any()) }
                assertTrue(errors.isEmpty(), errors.joinToString())
            } finally {
                session.detach()
                store.clear()
                ioDispatcher = originalDispatcher
            }
        }

    @Test
    fun `restoring a draft reapplies settings only when native preferences differ`() =
        runTest {
            val originalDispatcher = ioDispatcher
            ioDispatcher = StandardTestDispatcher(testScheduler)
            val model = EditorViewModel(SavedStateHandle())
            val store = ViewModelStore().apply { put("editor", model) }
            val errors = mutableListOf<String>()
            val session = EditorWebSession(model, backgroundScope, errors::add)
            try {
                model.load(NoteEditorFragment.addNoteArgs())
                model.hasAttachedEditor = true
                model.takeDraftRecoveryRequired()
                val current = checkNotNull(model.state.value)
                val document =
                    WebEditorDocument(
                        current.sessionId,
                        current.generation,
                        current.fields.map { WebEditorField(it.name, it.html) },
                        settings = current.settings,
                    )
                val draft = WebEditorDraft(document, current.fields.map { it.html }, model.hostState())
                val restored = mockk<WebEditorView>(relaxed = true)
                every { restored.context } returns ApplicationProvider.getApplicationContext()
                coEvery { restored.restoreDraft(model.draftId) } returns draft
                session.attach(restored)
                runCurrent()
                assertTrue(session.ready.value)
                // Redundant setSettings invalidates every captured command target in the host.
                coVerify(exactly = 0) { restored.updateSettings(any()) }
                coVerify(exactly = 0) { restored.loadDocumentAndCreateDraft(any(), any(), any(), any()) }
                session.detach()

                val older = mockk<WebEditorView>(relaxed = true)
                every { older.context } returns ApplicationProvider.getApplicationContext()
                coEvery { older.restoreDraft(model.draftId) } returns
                    draft.copy(
                        document = document.copy(settings = current.settings.copy(shrinkImages = !current.settings.shrinkImages)),
                    )
                session.attach(older)
                runCurrent()
                assertTrue(session.ready.value)
                coVerify(exactly = 1) { older.updateSettings(current.settings) }
                assertTrue(errors.isEmpty(), errors.joinToString())
            } finally {
                session.detach()
                store.clear()
                ioDispatcher = originalDispatcher
            }
        }

    @Test
    fun `native controls become ready only after draft persistence and initial focus`() =
        runTest {
            val model = EditorViewModel(SavedStateHandle())
            val store = ViewModelStore().apply { put("editor", model) }
            model.load(NoteEditorFragment.addNoteArgs())
            val web = mockk<WebEditorView>(relaxed = true)
            every { web.context } returns ApplicationProvider.getApplicationContext()
            var onChanged: ((WebEditorStatus) -> Unit)? = null
            every { web.onChanged = any() } answers { onChanged = firstArg() }
            val focus = CompletableDeferred<Unit>()
            coEvery { web.focusField(any()) } coAnswers { focus.await() }
            val writes = ArrayDeque<Runnable>()
            val originalDispatcher = ioDispatcher
            ioDispatcher =
                object : CoroutineDispatcher() {
                    override fun dispatch(
                        context: CoroutineContext,
                        block: Runnable,
                    ) {
                        writes.addLast(block)
                    }
                }
            val errors = mutableListOf<String>()
            val session = EditorWebSession(model, backgroundScope, errors::add)
            try {
                session.attach(web)
                runCurrent()
                coVerify(exactly = 1) { web.loadDocumentAndCreateDraft(any(), any(), any(), any()) }
                assertFalse(session.ready.value, "Native controls must wait for the durable discovery pointer")
                coVerify(exactly = 0) { web.focusField(any()) }

                writes.removeFirst().run()
                runCurrent()
                coVerify(exactly = 1) { web.focusField(any()) }
                assertFalse(session.ready.value, "The enabled-state redraw must wait for initial focus")

                // Focusing emits selection updates before its bridge response. Publishing
                // those early schedules a toolbar frame ahead of the keyboard show request.
                val selected = WebEditorStatus(0, false, hasSelection = true, activeField = 0)
                checkNotNull(onChanged)(selected)
                assertEquals(EditorToolbarState(), session.toolbarState.value)

                focus.complete(Unit)
                runCurrent()
                assertTrue(session.ready.value)
                assertEquals(EditorToolbarState(hasSelection = true, activeField = 0), session.toolbarState.value)
                checkNotNull(onChanged)(selected.copy(activeField = 1))
                assertEquals(1, session.toolbarState.value.activeField, "Ready editors must publish focus changes immediately")
                checkNotNull(onChanged)(selected.copy(canUndo = true))
                assertTrue(session.toolbarState.value.canUndo)
                assertFalse(session.toolbarState.value.canRedo)
                checkNotNull(onChanged)(selected.copy(canRedo = true))
                assertFalse(session.toolbarState.value.canUndo)
                assertTrue(session.toolbarState.value.canRedo)
                checkNotNull(onChanged)(selected.copy(activeField = 1))
                assertFalse(session.toolbarState.value.canUndo, "Untouched fields have no history")
                assertFalse(session.toolbarState.value.canRedo)
                assertTrue(model.hasAttachedEditor)
                assertTrue(errors.isEmpty(), errors.joinToString())
            } finally {
                session.detach()
                while (writes.isNotEmpty()) writes.removeFirst().run()
                runCurrent()
                ioDispatcher = originalDispatcher
                store.clear()
            }
        }
}
