// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement

/**
 * AnkiDroid's implementation of the **platform-neutral addon-settings contract** — the two
 * host methods a shared, cross-platform settings page would call (see
 * `docs/addons/design-notes.md` §3 and [rfc.md](../../../../../../../docs/addons/rfc.md)).
 *
 * The renderer itself (a `ts/routes/addon-settings/` SvelteKit page) is upstream and not in
 * this repo, so this class cannot yet be wired into a live page. But the contract is
 * defined and tested here so the upstream page has an exact, working reference to target,
 * and so desktop/AnkiMobile implement the *same* two methods against their own stores.
 *
 * The contract is JSON (addon settings are JSON, not protobuf):
 *
 * - **getAddonSettings** `{ "addon": "<name>" }` → `{ "schema": [...], "values": {...} }`
 *   where `schema` is the manifest's settings definitions and `values` is the resolved
 *   current values (stored overlaid on defaults). The page renders `schema`, filling from
 *   `values`.
 * - **setAddonSettings** `{ "addon": "<name>", "values": {...} }` → `{}`; persists.
 */
object AddonSettingsHost {
    const val METHOD_GET = "getAddonSettings"
    const val METHOD_SET = "setAddonSettings"

    private val json = Json { ignoreUnknownKeys = true }

    /** Handles [METHOD_GET]/[METHOD_SET], or returns null if [method] is not ours. */
    fun handle(
        context: Context,
        method: String,
        requestBytes: ByteArray,
    ): ByteArray? =
        when (method) {
            METHOD_GET -> getAddonSettings(context, requestBytes)
            METHOD_SET -> setAddonSettings(context, requestBytes)
            else -> null
        }

    private fun addonName(request: JsonObject): String =
        (request["addon"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: throw IllegalArgumentException("request has no 'addon'")

    private fun getAddonSettings(
        context: Context,
        requestBytes: ByteArray,
    ): ByteArray {
        val request = json.parseToJsonElement(requestBytes.decodeToString()) as JsonObject
        val name = addonName(request)
        val model =
            (AddonStorage(context).getInstalledAddons().firstOrNull { it.directoryName == name }?.result as? AddonValidationResult.Valid)
                ?.addonModel
        val schema = model?.settings.orEmpty()
        val values = model?.let { resolveSettingsValues(it, AddonStateStore(context).getSettingsValues(name)) } ?: JsonObject(emptyMap())

        val response =
            buildJsonObject {
                put("schema", json.encodeToJsonElement(ListSerializer(AddonSettingDefinition.serializer()), schema))
                put("values", values)
            }
        return response.toString().encodeToByteArray()
    }

    private fun setAddonSettings(
        context: Context,
        requestBytes: ByteArray,
    ): ByteArray {
        val request = json.parseToJsonElement(requestBytes.decodeToString()) as JsonObject
        val name = addonName(request)
        val values = request["values"] as? JsonObject ?: JsonObject(emptyMap())
        AddonStateStore(context).replaceSettingsValues(name, values)
        return "{}".encodeToByteArray()
    }
}
