// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.audiorecord

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.recorder.AudioRecorder
import com.ichi2.anki.recorder.VoiceRecorder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class CheckPronunciationStorageAndroidTest : CheckPronunciationViewModelTestBase() {
    @Test
    fun `recordings are stored outside the cache`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val viewModel =
                CheckPronunciationViewModel(
                    AudioRecorder(context, createMediaRecorder = mediaRecorderStub::createRecorder),
                    playerStub.factory,
                ).also { viewModelStore.put(VIEW_MODEL_KEY, it) }
            try {
                val recording = viewModel.record()

                assertEquals(File(context.noBackupFilesDir, VoiceRecorder.RECORDINGS_DIRECTORY), recording.parentFile)
            } finally {
                viewModelStore.clear()
            }
        }
}
