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
