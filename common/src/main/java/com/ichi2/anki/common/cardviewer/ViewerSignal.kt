// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.cardviewer

import org.slf4j.LoggerFactory

/** Signals from a WebView represent actions with no parameters  */
enum class ViewerSignal {
    /** A signal which we did not know how to handle  */
    SIGNAL_UNHANDLED,

    /** A known signal which should perform a noop  */
    SIGNAL_NOOP,
    TYPE_FOCUS,

    /** Tell the app that we no longer want to focus the WebView and should instead return keyboard focus to a
     * native answer input method.  */
    RELINQUISH_FOCUS,
    SHOW_ANSWER,
    ANSWER_ORDINAL_1,
    ANSWER_ORDINAL_2,
    ANSWER_ORDINAL_3,
    ANSWER_ORDINAL_4,
    ;

    companion object {
        private val logger = LoggerFactory.getLogger(ViewerSignal::class.java)

        fun String.toSignal(): ViewerSignal {
            when (this) {
                "signal:typefocus" -> return TYPE_FOCUS
                "signal:relinquishFocus" -> return RELINQUISH_FOCUS
                "signal:show_answer" -> return SHOW_ANSWER
                "signal:answer_ease1" -> return ANSWER_ORDINAL_1
                "signal:answer_ease2" -> return ANSWER_ORDINAL_2
                "signal:answer_ease3" -> return ANSWER_ORDINAL_3
                "signal:answer_ease4" -> return ANSWER_ORDINAL_4
                else -> {}
            }
            if (this.startsWith("signal:answer_ease")) {
                logger.warn("Unhandled signal: ease value: {}", this)
                return SIGNAL_NOOP
            }
            return SIGNAL_UNHANDLED // unknown, or not a signal.
        }
    }
}
