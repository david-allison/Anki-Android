// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.audio

import com.ichi2.anki.common.time.formatAsString
import com.ichi2.anki.multimedia.audio.AudioRecordingController
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Tests [AudioRecordingController] */
class AudioRecordingControllerTest {
    @Test
    fun `original value is correctly formatted`() {
        // given that DEFAULT_TIME is a constant, we can't initialise it directly
        assertThat(AudioRecordingController.DEFAULT_TIME, equalTo(0.milliseconds.formatAsString()))
    }
}
