// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import com.ichi2.anki.cardviewer.CardMediaPlayer
import com.ichi2.anki.cardviewer.CardMediaPlayerFactory
import com.ichi2.anki.cardviewer.SoundTagPlayer
import com.ichi2.anki.libanki.TtsPlayer
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred

/** Exercise playback coordination without constructing Android audio or TTS services. */
internal val jvmCardMediaPlayerFactory: CardMediaPlayerFactory = { _, listener ->
    CardMediaPlayer(
        soundTagPlayer = mockk<SoundTagPlayer>(relaxed = true),
        ttsPlayer = CompletableDeferred(mockk<TtsPlayer>(relaxed = true)),
        mediaErrorListener = listener,
    )
}
