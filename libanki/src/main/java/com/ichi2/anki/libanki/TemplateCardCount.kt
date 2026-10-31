// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.backend.BackendUtils
import com.ichi2.anki.libanki.backend.model.toBackendNote
import net.ankiweb.rsdroid.exceptions.BackendTemplateException
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val logger = LoggerFactory.getLogger("TemplateCardCount")

/** Count cards a proposed template would create, falling back to the note count after [timeout]. */
fun Collection.countCardsCreatedBy(
    noteType: NotetypeJson,
    template: CardTemplate,
    timeout: Duration = 1.seconds,
    onTimeout: (noteCount: Int) -> Unit = {},
): Int {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    val unreferencedOrd = generateSequence(0) { it + 1 }.first { "c${it + 1}" !in template.qfmt }
    val templateBytes = BackendUtils.toJsonBytes(template.deepClone().apply { setOrd(unreferencedOrd) })
    val noteIds = notetypes.nids(noteType)
    return try {
        noteIds.count { nid ->
            if (deadline.hasPassedNow()) {
                logger.warn("counting the cards of a new card type timed out")
                onTimeout(noteIds.size)
                return noteIds.size
            }
            !backend
                .renderUncommittedCardLegacy(getNote(nid).toBackendNote(), unreferencedOrd, templateBytes, false, true)
                .isEmpty
        }
    } catch (e: BackendTemplateException) {
        logger.warn("new card type has a template error", e)
        0
    }
}
