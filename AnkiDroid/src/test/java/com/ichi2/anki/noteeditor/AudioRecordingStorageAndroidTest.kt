// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.noteeditor

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
class AudioRecordingStorageAndroidTest : AudioRecordingViewModelTestBase() {
    @Test
    fun `recordings are stored outside the cache`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val viewModel =
                AudioRecordingViewModel(AudioRecorder(context), playerStub.factory)
                    .also { viewModelStore.put(VIEW_MODEL_KEY, it) }
            try {
                val recording = viewModel.record()

                assertEquals(File(context.noBackupFilesDir, VoiceRecorder.RECORDINGS_DIRECTORY), recording.parentFile)
            } finally {
                viewModelStore.clear()
            }
        }
}
