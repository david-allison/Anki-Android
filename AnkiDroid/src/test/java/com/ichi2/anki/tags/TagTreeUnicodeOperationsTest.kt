// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.rules.IcuCaseFoldingRule
import com.ichi2.testutils.withViewModel
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TagTreeUnicodeOperationsTest : JvmTest() {
    @get:Rule
    val caseFolding = IcuCaseFoldingRule()

    @Test
    fun `renaming a parent includes every stored spelling and counts each note once`() =
        runTest {
            val first =
                addBasicNote().update {
                    addTag("Straße::a")
                    addTag("STRASSE::b")
                }
            val second = addBasicNote().update { addTag("STRASSE::c") }
            val unrelated = addBasicNote().update { addTag("STRASSE-other") }
            val originalTags = col.tags.all().sorted()
            withViewModel(ManageTagsViewModel()) {
                renameTag(TagName.asValid("Straße"), TagName.asValid("target")).join()
                assertNull((state.value as ManageTagsState.Content).error)
                assertEquals(listOf("target::a", "target::b"), col.getNote(first.id).tags)
                assertEquals(listOf("target::c"), col.getNote(second.id).tags)
                assertEquals(listOf("STRASSE-other"), col.getNote(unrelated.id).tags)
                assertEquals(UserMessage.TagRenamed(2), pendingMessages.value.single().message)
                col.undo()
                assertEquals(listOf("Straße::a", "STRASSE::b"), col.getNote(first.id).tags)
                assertEquals(originalTags, col.tags.all().sorted())
                col.redo()
                assertEquals(listOf("target::a", "target::b"), col.getNote(first.id).tags)
            }
        }

    @Test
    fun `deleting a parent removes every spelling including unused children in one undo step`() =
        runTest {
            val note =
                addBasicNote().update {
                    addTag("Straße::a")
                    addTag("STRASSE::b")
                }
            col.tags.setCollapsed("STRASSE::unused", true)
            val originalTags = col.tags.all().sorted()
            withViewModel(ManageTagsViewModel()) {
                removeTag(TagName.asValid("Straße")).join()
                assertNull((state.value as ManageTagsState.Content).error)
                assertEquals(emptyList(), col.getNote(note.id).tags)
                assertEquals(emptyList(), col.tags.all())
                assertEquals(UserMessage.TagRemoved(1), pendingMessages.value.single().message)
                col.undo()
                assertEquals(listOf("Straße::a", "STRASSE::b"), col.getNote(note.id).tags)
                assertEquals(originalTags, col.tags.all().sorted())
                col.redo()
                assertEquals(emptyList(), col.tags.all())
            }
        }

    @Test
    fun `a child whose displayed parent spelling differs can be renamed`() =
        runTest {
            addBasicNote().update { addTag("Straße::a") }
            val note = addBasicNote().update { addTag("STRASSE::b") }
            withViewModel(ManageTagsViewModel()) {
                val displayedChild = TagName.asValid("Straße::b")
                assertEquals(true, canRenameTag(displayedChild))
                renameTag(displayedChild, TagName.asValid("target")).join()
                assertEquals(listOf("target"), col.getNote(note.id).tags)
            }
        }

    @Test
    fun `renaming into a descendant replaces each stored prefix only once`() =
        runTest {
            val note =
                addBasicNote().update {
                    addTag("Straße::a")
                    addTag("STRASSE::b")
                }
            withViewModel(ManageTagsViewModel()) {
                renameTag(TagName.asValid("Straße"), TagName.asValid("STRASSE::nested")).join()
                assertNull((state.value as ManageTagsState.Content).error)
                assertEquals(listOf("STRASSE::nested::a", "STRASSE::nested::b"), col.getNote(note.id).tags)
                col.undo()
                assertEquals(listOf("Straße::a", "STRASSE::b"), col.getNote(note.id).tags)
            }
        }
}
