// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.noteeditor.web.WebEditorView
import com.ichi2.testutils.JvmTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EditorWebSessionTest : JvmTest() {
    @Test
    fun `native controls become ready only after draft persistence and initial focus`() =
        runTest {
            val model = EditorViewModel(SavedStateHandle())
            val store = ViewModelStore().apply { put("editor", model) }
            model.load(NoteEditorFragment.addNoteArgs())
            val web = mockk<WebEditorView>(relaxed = true)
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

                focus.complete(Unit)
                runCurrent()
                assertTrue(session.ready.value)
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
