// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.utils.append
import com.ichi2.anki.libanki.utils.insert
import com.ichi2.anki.libanki.utils.remove
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

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

    /** Append an unsaved template and track its identity in the same operation. */
    fun addNewTemplate(template: CardTemplate) {
        notetype.templates.append(template)
        originalTemplateOrds.add(null)
        Notetypes._updateTemplOrds(notetype)
    }

    /** Delete an editor position, retaining its original database ordinal for later validation. */
    fun removeTemplate(ord: Int) {
        val template = getTemplate(ord)
        originalTemplateOrds.removeAt(ord)?.let { deletedTemplateOrds.add(it) }
        notetype.templates.remove(template)
        Notetypes._updateTemplOrds(notetype)
    }

    /** Copy the selected template, choosing an unused localized name and reversing a single card type. */
    fun buildNewTemplate(
        sourceOrdinal: CardOrdinal,
        cardName: (Int) -> String,
    ): CardTemplate {
        val templates = notetype.templates
        var nextNumber = templateCount + 1
        var name = cardName(nextNumber)
        while (templates.any { it.name == name }) name = cardName(++nextNumber)
        val source = getTemplate(sourceOrdinal)
        return Notetypes.newTemplate(name).apply {
            qfmt = source.qfmt
            afmt = source.afmt
            if (templateCount == 1) {
                val oldFront = qfmt
                val answer = Pattern.compile("(?s)(.+)<hr id=answer>(.+)").matcher(afmt)
                qfmt = if (answer.find()) answer.group(2)!!.trim() else afmt.replace("{{FrontSide}}", "")
                afmt = "{{FrontSide}}\n\n<hr id=answer>\n\n$oldFront"
            }
            setOrd(templates.last().ord + 1)
        }
    }

    /** Unsaved additions cannot orphan existing notes; all queued original deletions must be checked together. */
    fun deletionWouldOrphanNote(
        collection: Collection,
        position: CardOrdinal,
    ): Boolean =
        getOriginalOrdinal(position) != null &&
            collection.notetypes.getCardIdsForNoteType(noteTypeId, getDeleteDbOrds(position)) == null

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
