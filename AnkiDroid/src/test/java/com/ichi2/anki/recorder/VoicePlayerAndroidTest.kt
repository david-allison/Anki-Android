// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.recorder

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.recorder.PlayerState.Loading
import com.ichi2.testutils.EmptyApplication
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class VoicePlayerAndroidTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val playerStub = PlayerStub()
    private val recording by lazy { File(tempFolder.root, "recording.3gp") }

    @Test
    fun `load pauses, sets the recording and prepares`() =
        runTest {
            val voicePlayer = VoicePlayer(backgroundScope, playerStub.factory)

            voicePlayer.load(recording)

            assertThat(voicePlayer.state.value, equalTo(Loading))
            verifyOrder {
                playerStub.player.pause()
                playerStub.player.setMediaItem(MediaItem.fromUri(Uri.fromFile(recording)))
                playerStub.player.prepare()
            }
        }
}
