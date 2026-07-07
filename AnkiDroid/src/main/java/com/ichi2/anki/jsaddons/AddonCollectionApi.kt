// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import android.content.Context
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.jsaddons.AddonPermission.Scoped.Access
import com.ichi2.anki.jsaddons.AddonPermission.Scoped.Entity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import timber.log.Timber

/**
 * The collection API exposed to sandboxed addons, gated by the scoped [AddonPermission]s.
 *
 * Every method maps to exactly one capability ([requiredPermission]); [handle] enforces the
 * grant **server-side** — the trusted page relay forwards the *authenticated* addon name
 * (from its window→name map), and this re-checks the grant before touching the collection, so
 * neither the sandboxed iframe nor a compromised relay can call an ungranted capability.
 *
 * > **Card-impersonation caveat (WIP).** Card template JS runs in the same page scope as the
 * > relay, so a malicious card could invoke the bridge naming an addon that *has* been
 * > granted a capability. This is the unresolved "cards are not a security boundary" problem
 * > from [#20695](https://github.com/ankidroid/Anki-Android/issues/20695); it applies equally
 * > to the existing JS API. Documented, not solved here.
 *
 * The wire format is a JSON envelope: `{ "ok": true, "value": <result> }` on success,
 * `{ "ok": false, "error": "<message>" }` otherwise. The client shim resolves/rejects a
 * Promise from it. Methods are added in droppable per-capability commits.
 */
object AddonCollectionApi {
    private val json = Json { ignoreUnknownKeys = true }

    /** The capability a method requires, or null if the method is unknown. */
    fun requiredPermission(method: String): AddonPermission? =
        when (method) {
            "decks.all", "decks.current" -> AddonPermission.Scoped(Entity.DECKS, Access.READ)
            "decks.add" -> AddonPermission.Scoped(Entity.DECKS, Access.WRITE)
            "notes.find", "notes.info" -> AddonPermission.Scoped(Entity.NOTES, Access.READ)
            "notes.addTags", "notes.removeTags" -> AddonPermission.Scoped(Entity.NOTES, Access.WRITE)
            "cards.find", "cards.info" -> AddonPermission.Scoped(Entity.CARDS, Access.READ)
            "cards.suspend", "cards.unsuspend", "cards.setFlag" -> AddonPermission.Scoped(Entity.CARDS, Access.WRITE)
            else -> null
        }

    /**
     * Runs [method] for [addon] if it holds the required capability; returns the JSON envelope.
     * Never throws — failures become `{ ok: false, error }`.
     */
    suspend fun handle(
        context: Context,
        addon: String,
        method: String,
        argsJson: String,
    ): String {
        val required = requiredPermission(method) ?: return error("unknown collection method: $method")
        if (!AddonStateStore(context).isGranted(addon, required)) {
            return error("addon '$addon' lacks the '${required.id}' permission")
        }
        val args =
            try {
                json.parseToJsonElement(argsJson) as? JsonObject ?: JsonObject(emptyMap())
            } catch (_: Exception) {
                JsonObject(emptyMap())
            }
        return try {
            success(dispatch(method, args))
        } catch (e: Exception) {
            Timber.w(e, "Addon collection call '%s' failed", method)
            error(e.localizedMessage ?: "collection error")
        }
    }

    private suspend fun dispatch(
        method: String,
        @Suppress("UNUSED_PARAMETER") args: JsonObject,
    ): JsonElement =
        when (method) {
            "decks.all" ->
                withCol { decks.allNamesAndIds(skipEmptyDefault = false, includeFiltered = true) }
                    .let { decks ->
                        buildJsonArray {
                            for (deck in decks) {
                                add(
                                    buildJsonObject {
                                        put("id", deck.id)
                                        put("name", deck.name)
                                    },
                                )
                            }
                        }
                    }
            "decks.current" ->
                withCol { decks.current() }.let { deck ->
                    buildJsonObject {
                        put("id", deck.getLong("id"))
                        put("name", deck.getString("name"))
                    }
                }
            "decks.add" ->
                withCol { decks.addNormalDeckWithName(args.str("name")) }.let { op ->
                    buildJsonObject { put("id", op.id) }
                }
            "notes.find" ->
                withCol { findNotes(args.str("query")) }.let { ids ->
                    buildJsonArray { for (id in ids) add(kotlinx.serialization.json.JsonPrimitive(id)) }
                }
            "notes.info" ->
                withCol { getNote(args.str("noteId").toLong()) }.let { note ->
                    buildJsonObject {
                        put("noteId", note.id)
                        put("fields", buildJsonArray { for (f in note.fields) add(kotlinx.serialization.json.JsonPrimitive(f)) })
                        put("tags", buildJsonArray { for (t in note.tags) add(kotlinx.serialization.json.JsonPrimitive(t)) })
                    }
                }
            "notes.addTags" ->
                withCol { tags.bulkAdd(args.longs("noteIds"), args.strings("tags").joinToString(" ")) }
                    .let { buildJsonObject { put("count", it.count) } }
            "notes.removeTags" ->
                withCol { tags.bulkRemove(args.longs("noteIds"), args.strings("tags").joinToString(" ")) }
                    .let { buildJsonObject { put("count", it.count) } }
            "cards.find" ->
                withCol { findCards(args.str("query")) }.let { ids ->
                    buildJsonArray { for (id in ids) add(kotlinx.serialization.json.JsonPrimitive(id)) }
                }
            "cards.info" ->
                withCol { getCard(args.str("cardId").toLong()) }.let { card ->
                    buildJsonObject {
                        put("cardId", card.id)
                        put("noteId", card.nid)
                        put("deckId", card.did)
                        put("queue", card.queue.code)
                    }
                }
            "cards.suspend" ->
                withCol { sched.suspendCards(args.longs("cardIds")) }.let { buildJsonObject { put("count", it.count) } }
            "cards.unsuspend" ->
                withCol { sched.unsuspendCards(args.longs("cardIds")) }.let { buildJsonObject { put("ok", true) } }
            "cards.setFlag" ->
                withCol { setUserFlagForCards(args.longs("cardIds"), args.int("flag")) }
                    .let { buildJsonObject { put("count", it.count) } }
            else -> throw IllegalArgumentException("unhandled method: $method")
        }

    private fun JsonObject.str(key: String): String =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: throw IllegalArgumentException("missing string argument '$key'")

    private fun JsonObject.longs(key: String): List<Long> =
        (this[key] as? JsonArray)?.map { it.jsonPrimitive.long }
            ?: throw IllegalArgumentException("missing id-array argument '$key'")

    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray)?.map { it.jsonPrimitive.content }
            ?: throw IllegalArgumentException("missing string-array argument '$key'")

    private fun JsonObject.int(key: String): Int =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
            ?: throw IllegalArgumentException("missing int argument '$key'")

    private fun success(value: JsonElement): String =
        buildJsonObject {
            put("ok", true)
            put("value", value)
        }.toString()

    private fun error(message: String): String =
        buildJsonObject {
            put("ok", false)
            put("error", message)
        }.toString()
}
