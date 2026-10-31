// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.utils.insert
import com.ichi2.anki.libanki.utils.remove
import org.slf4j.LoggerFactory

/** Tracks template identities while editing a note type, independently of platform persistence. */
open class CardTemplateEditorModel(
    val notetype: NotetypeJson,
) {
    /** Database identity at each editor position; null for an unsaved addition. */
    protected var originalTemplateOrds: ArrayList<CardOrdinal?> = notetype.templates.mapTo(ArrayList()) { it.ord }
    protected var deletedTemplateOrds = ArrayList<CardOrdinal>()

    fun getTemplate(ord: Int): CardTemplate {
        logger.debug("getTemplate() on ordinal {}", ord)
        return notetype.templates[ord]
    }

    val templateCount: Int
        get() = notetype.templates.length()

    val noteTypeId: NoteTypeId
        get() = notetype.id

    var css: String
        get() = notetype.css
        set(value) {
            notetype.css = value
        }

    fun updateTemplate(
        ordinal: Int,
        template: CardTemplate,
    ) {
        notetype.templates[ordinal] = template
    }

    fun addNewTemplate() {
        logger.debug("addNewTemplate()")
        originalTemplateOrds.add(null)
    }

    fun removeTemplate(ord: Int) {
        logger.debug("removeTemplate() on ordinal {}", ord)
        originalTemplateOrds.removeAt(ord)?.let { deletedTemplateOrds.add(it) }
    }

    /** Move a template while retaining its database identity for saving, previews and deletion checks. */
    fun repositionTemplate(
        from: CardOrdinal,
        to: CardOrdinal,
    ) {
        require(from in 0 until templateCount && to in 0 until templateCount)
        if (from == to) return
        val template = getTemplate(from)
        notetype.templates.remove(template)
        notetype.templates.insert(to, template)
        originalTemplateOrds.add(to, originalTemplateOrds.removeAt(from))
        Notetypes._updateTemplOrds(notetype)
    }

    /** Returns a copy with database ordinals, preserving the editor if backend validation fails. */
    fun notetypeForSaving(): NotetypeJson =
        notetype.deepClone().also { updated ->
            for ((i, template) in updated.templates.withIndex()) {
                template.setOrd(originalTemplateOrds[i])
            }
        }

    /** Database ordinals of pending deletions, including the proposed editor position. */
    fun getDeleteDbOrds(ord: Int): IntArray = (deletedTemplateOrds + listOfNotNull(getOriginalOrdinal(ord))).toIntArray()

    /** Database ordinal for an editor position, or null for an unsaved addition. */
    fun getOriginalOrdinal(ordinal: Int): CardOrdinal? = originalTemplateOrds[ordinal]

    private companion object {
        val logger = LoggerFactory.getLogger(CardTemplateEditorModel::class.java)
    }
}
