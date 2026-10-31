// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.pages.PostRequestBridge
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.jvmCardMediaPlayerFactory
import com.ichi2.testutils.mockWebResourceRequest
import com.ichi2.testutils.withViewModel
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Exercises the Android URI and WebView request handling used by missing-media warnings. */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class PreviewerMissingMediaTest : JvmTest() {
    @Test
    fun `a missing image on the previous answer does not suppress the next question warning`() =
        runTest {
            val cardIds = (0..1).flatMap { addBasicNote().cards().map { it.id } }
            updateDeckConfig(col.decks.selected()) { autoplay = false }
            val originalViewModel =
                PreviewerViewModel(SavedStateHandle(), cardIds, initialIndex = 0, mediaPlayerFactory = jvmCardMediaPlayerFactory)
            withViewModel(originalViewModel) {
                val viewModel = spyk(this)
                coEvery { viewModel.prepareCardTextForDisplay(any()) } answers { firstArg() }
                viewModel.currentCard.await()

                viewModel.onNextButtonClick()
                advanceUntilIdle()
                assertTrue(viewModel.showingAnswer.value)
                val reportedFiles = mutableListOf<String>()
                viewModel.mediaErrorHandler.processFailure(
                    mockWebResourceRequest("${PostRequestBridge.BASE_URL}first-missing.jpg", mainFrame = false),
                ) { reportedFiles.add(it) }
                assertEquals(listOf("first-missing.jpg"), reportedFiles)

                viewModel.onNextButtonClick()
                advanceUntilIdle()
                assertEquals(1, viewModel.currentIndex.value)
                assertFalse(viewModel.showingAnswer.value)
                viewModel.mediaErrorHandler.processFailure(
                    mockWebResourceRequest("${PostRequestBridge.BASE_URL}second-missing.jpg", mainFrame = false),
                ) { reportedFiles.add(it) }

                assertEquals(listOf("first-missing.jpg", "second-missing.jpg"), reportedFiles)
            }
        }
}
