// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import anki.collection.OpChanges
import anki.collection.OpChangesWithCount
import com.ichi2.anki.common.utils.ankiCaseFold
import com.ichi2.anki.libanki.Tags
import com.ichi2.anki.libanki.utils.RustCompat
import timber.log.Timber
import java.text.Normalizer

/**
 * A tag name, e.g. "science::biology".
 *
 * Equality, hashing and hierarchy checks mirror backend tag identity, including normalization
 * and the case-folding bug in Anki's pinned unicase version. [value] retains the name's spelling
 * for display and case-only renames; compare it explicitly when checking whether the spelling changed.
 *
 * Tag names should not be logged using `Timber.i` or above to avoid leaking PII to crash reports.
 */
class TagName private constructor(
    val value: String,
) {
    private val identity = normalize(value).ankiCaseFold()

    /** Includes [root] itself; `a::b` is a descendant of `a`, but `ab` is not. */
    fun isSameOrDescendantOf(root: TagName): Boolean = identity == root.identity || identity.startsWith("${root.identity}::")

    /** Ancestor tags, e.g. `TagName.build("a::b::c").ancestors` returns `["a", "a::b"]` */
    val ancestors: List<String>
        get() {
            val parts = value.split("::")
            if (parts.size <= 1) return emptyList()
            return buildList {
                // prefer StringBuilder over joinToString for performance
                val sb = StringBuilder()
                for (i in 0 until parts.size - 1) {
                    if (i > 0) sb.append("::")
                    sb.append(parts[i])
                    add(sb.toString())
                }
            }
        }

    override fun equals(other: Any?): Boolean = other is TagName && identity == other.identity

    override fun hashCode(): Int = identity.hashCode()

    override fun toString(): String = value

    companion object {
        fun build(tagName: String): TagName? {
            if (tagName.isBlank()) return null
            // in upstream, a space is converted to "::" in the UI
            // But "_" is converted to " " in some visual displays
            return TagName(tagName.replace(" ", "_"))
        }

        // Mirror tags/register.rs for identity only. Preserve the supplied spelling
        // and leave mutation validation (e.g. forbidden separators) to the backend.
        private fun normalize(spelling: String): String =
            spelling.split("::").joinToString("::") { component ->
                Normalizer
                    .normalize(component, Normalizer.Form.NFC)
                    .filterNot { it <= '\u001f' || it == '\u007f' || it == '\u3000' }
                    .trim(RustCompat::isWhitespace)
                    .ifEmpty { "blank" }
            }
    }
}

/** Returns this tag's path under [parent], or at the top level when the parent is null. */
internal fun TagName.withParent(parent: TagName?): TagName {
    val leaf = value.substringAfterLast("::")
    return requireNotNull(TagName.build(parent?.let { "${it.value}::$leaf" } ?: leaf))
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
