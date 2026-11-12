// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import com.ichi2.anki.libanki.Collection
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

/** A reusable batch of names. Comparisons run in the backend; constructing this does no database work. */
internal class TagNameLookup(
    names: Iterable<TagName>,
) {
    val names = names.distinct()
    private val normalized = this.names.map { TagName.normalize(it.value) }
    private val buckets =
        normalized.indices.groupBy { normalized[it].split("::").size }.mapValues { (_, indices) ->
            indices to buildJsonArray { indices.forEach { add(normalized[it]) } }.toString()
        }

    /** Includes implicit tree parents. Different hierarchy depths can never compare equal. */
    fun matching(
        col: Collection,
        tag: TagName,
    ): List<TagName> {
        val query = TagName.normalize(tag.value)
        val (indices, encoded) = buckets[query.split("::").size] ?: return emptyList()
        return col.db
            .queryLongList(
                "select cast(key as integer) from json_each(?) where value = ? collate unicase",
                encoded,
                query,
            ).map { names[indices[it.toInt()]] }
    }

    /** Resolve each spelling to the first equivalent name, so callers can prefer existing destinations. */
    fun canonicalNames(col: Collection): Map<TagName, TagName> =
        buildMap {
            col.db
                .query(
                    """select cast(key as integer),
                    first_value(cast(key as integer)) over (partition by value collate unicase order by cast(key as integer))
                    from json_each(?)""",
                    buildJsonArray { normalized.forEach { add(it) } }.toString(),
                ).use { cursor ->
                    while (cursor.moveToNext()) put(names[cursor.getInt(0)], names[cursor.getInt(1)])
                }
        }
}

internal fun Collection.sameTag(
    left: TagName,
    right: TagName,
): Boolean = db.queryScalar("select ? = ? collate unicase", TagName.normalize(left.value), TagName.normalize(right.value)) != 0

/** Compare complete components, allowing folds such as Straße/STRASSE to have different lengths. */
internal fun Collection.tagIsInSubtree(
    tag: TagName,
    root: TagName,
): Boolean = tag.prefix(root.value.split("::").size)?.let { sameTag(it, root) } ?: false

internal fun TagName.prefix(depth: Int): TagName? {
    val components = value.split("::")
    return if (components.size < depth) null else TagName.build(components.take(depth).joinToString("::"))
}
