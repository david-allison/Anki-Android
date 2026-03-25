/*
 * Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.tags

import anki.collection.OpChanges
import anki.collection.OpChangesWithCount
import com.ichi2.anki.libanki.Tags
import com.ichi2.utils.TagsUtil
import timber.log.Timber

/**
 * A tag name, e.g. "science::biology".
 *
 * Tag names should not be logged using `Timber.i` or above to avoid leaking PII to crash reports.
 */
@JvmInline
value class TagName(
    val value: String,
) {
    /**
     * All ancestor tag names, e.g. for "a::b::c" returns ["a", "a::b"].
     * @see TagsUtil.getTagAncestors
     */
    val ancestors: Set<TagName>
        get() = TagsUtil.getTagAncestors(value).mapTo(mutableSetOf()) { TagName(it) }

    /**
     * Whether any tag in [tags] is a descendant of this tag
     *
     * O(n)
     */
    fun hasDescendantIn(tags: Set<TagName>): Boolean = tags.any { it.value.startsWith("$value::") }

    override fun toString(): String = value
}

// Extension methods serve two purposes: removing `.value` from the call site
// and ensuring that collection operation lambdas return `OpChanges`
// Having Timber.d as a final call means the Lambda returns `Unit`, which is converted to `Any`
// which causes opChanges { } to crash, as it expects an `OpChanges` return type

/** @see Tags.setCollapsed */
fun Tags.setCollapsed(
    tag: TagName,
    collapsed: Boolean,
): OpChanges {
    Timber.i("Setting tag collapsed=%b", collapsed)
    return setCollapsed(tag.value, collapsed).also {
        Timber.d("Set tag collapsed=%b: %s", collapsed, tag)
    }
}

/** @see Tags.remove */
fun Tags.remove(tag: TagName): OpChangesWithCount {
    Timber.i("Deleting tag")
    return remove(tag.value).also {
        Timber.d("Deleted tag: %s", tag)
    }
}

/** @see Tags.remove */
fun Tags.remove(tags: Set<TagName>): OpChangesWithCount {
    Timber.i("Deleting %d tags", tags.size)
    return remove(tags.joinToString(" ") { it.value })
}

/** @see Tags.rename */
fun Tags.rename(
    old: TagName,
    new: TagName,
): OpChangesWithCount {
    Timber.i("Renaming tag")
    return rename(old.value, new.value).also {
        Timber.d("Renamed tag: %s to %s", old, new)
    }
}
