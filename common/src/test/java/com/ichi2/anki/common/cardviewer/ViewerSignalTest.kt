// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.cardviewer

import com.ichi2.anki.common.cardviewer.ViewerSignal.Companion.toSignal
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals

class ViewerSignalTest {
    @ParameterizedTest
    @MethodSource("getSignalFromUrlTest_args")
    fun getSignalFromUrlTest(
        url: String,
        signal: ViewerSignal,
    ) {
        assertEquals(signal, url.toSignal())
    }

    companion object {
        @JvmStatic // required for @MethodSource
        fun getSignalFromUrlTest_args() =
            Stream.of(
                Arguments.of("signal:show_answer", ViewerSignal.SHOW_ANSWER),
                Arguments.of("signal:typefocus", ViewerSignal.TYPE_FOCUS),
                Arguments.of("signal:relinquishFocus", ViewerSignal.RELINQUISH_FOCUS),
                Arguments.of("signal:answer_ease1", ViewerSignal.ANSWER_ORDINAL_1),
                Arguments.of("signal:answer_ease2", ViewerSignal.ANSWER_ORDINAL_2),
                Arguments.of("signal:answer_ease3", ViewerSignal.ANSWER_ORDINAL_3),
                Arguments.of("signal:answer_ease4", ViewerSignal.ANSWER_ORDINAL_4),
                Arguments.of("signal:answer_ease0", ViewerSignal.SIGNAL_NOOP),
                Arguments.of("signal:answer_ease5", ViewerSignal.SIGNAL_NOOP),
                Arguments.of("signal:answer_ease", ViewerSignal.SIGNAL_NOOP),
                Arguments.of("signal:unknown", ViewerSignal.SIGNAL_UNHANDLED),
                Arguments.of("https://example.org", ViewerSignal.SIGNAL_UNHANDLED),
            )
    }
}
