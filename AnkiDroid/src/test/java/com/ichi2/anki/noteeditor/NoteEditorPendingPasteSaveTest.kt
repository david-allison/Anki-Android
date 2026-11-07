// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.content.ClipData
import android.content.ContentProvider
import android.database.MatrixCursor
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.core.view.ContentInfoCompat
import androidx.core.view.ViewCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.utils.ext.pasteImagesAsPng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver
import kotlin.test.assertContains

@RunWith(AndroidJUnit4::class)
class NoteEditorPendingPasteSaveTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `save includes a pending dropped image`() {
        assertSaveIncludesPendingImage(ContentInfoCompat.SOURCE_DRAG_AND_DROP)
    }

    @Test
    fun `save includes a pending clipboard image`() {
        assertSaveIncludesPendingImage(ContentInfoCompat.SOURCE_CLIPBOARD)
    }

    private fun assertSaveIncludesPendingImage(source: Int) =
        runTest {
            col.config.pasteImagesAsPng = false
            val note = addBasicNote("Front", "Back")
            val editor =
                startRegularActivity<NoteEditorActivity>(
                    NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
                ).getNoteEditorFragment()
            val image = "content://clipboard/image.png".toUri()
            val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf("image.png")) }
            val provider = mock<ContentProvider>()
            whenever(provider.query(image, null, null, null, null)).thenReturn(cursor)
            ShadowContentResolver.registerProviderInternal("clipboard", provider)
            shadowOf(targetContext.contentResolver).registerInputStreamSupplier(image) { "pasted image".byteInputStream() }
            val payload =
                ContentInfoCompat
                    .Builder(
                        ClipData("image", arrayOf("image/png"), ClipData.Item(image)),
                        source,
                    ).build()

            // Queue the paste so Save must wait for it before reading the fields.
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            ViewCompat.performReceiveContent(editor.editFieldAt(1)!!, payload)
            editor.saveNote()

            assertContains(col.getNote(note.id).fields[1], "Back<img src=\"")
        }
}
