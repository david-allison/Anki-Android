// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Runtime settings access for reviewer addon scripts.
 *
 * The resolved settings of every enabled reviewer addon are baked into the study page as a
 * bootstrap script (see [bootstrapScript]), so an addon can read its settings synchronously
 * with no round-trip:
 *
 * ```js
 * const { delaySeconds } = ankidroid.addonSettings("my-addon-name");
 * ```
 *
 * Writing a value round-trips to the host through [POST_SET] and updates the baked-in copy:
 *
 * ```js
 * ankidroid.setAddonSetting("my-addon-name", "delaySeconds", 20);
 * ```
 *
 * The addon passes its own npm name, which it knows. Reads and writes are scoped to the
 * calling addon's own settings only.
 */
object AddonSettingsBridge {
    /** `ankidroid/<this>` POST route to persist a single setting value */
    const val POST_SET = "setAddonSetting"

    /**
     * The inline bootstrap script for the study page, or null if no reviewer addon is
     * enabled. Bakes in each enabled addon's resolved settings and exposes the accessors.
     */
    fun bootstrapScript(context: Context): String? {
        val addons = AddonReviewerScripts.enabledReviewerAddons(context)
        if (addons.isEmpty()) return null
        val stateStore = AddonStateStore(context)

        val resolved =
            buildJsonObject {
                for (addon in addons) {
                    put(addon.name, resolveSettingsValues(addon, stateStore.getSettingsValues(addon.name)))
                }
            }

        // language=JS
        return """
            globalThis.ankidroid = globalThis.ankidroid || {};
            (() => {
                const settings = $resolved;
                ankidroid.addonSettings = (name) => ({ ...(settings[name] || {}) });
                ankidroid.setAddonSetting = (name, key, value) => {
                    settings[name] = { ...(settings[name] || {}), [key]: value };
                    fetch("ankidroid/$POST_SET", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({ addon: name, key, value }),
                    }).catch(err => console.log("addon setting write failed:", err));
                };
            })();
            """.trimIndent()
    }

    /**
     * Persists a `{addon, key, value}` write request from [bootstrapScript].
     *
     * The write is scoped to the named addon's own settings; the addon cannot reach another
     * addon's state through this route.
     */
    fun handleSet(
        context: Context,
        bytes: ByteArray,
    ): ByteArray {
        val request = Json.parseToJsonElement(bytes.decodeToString()) as JsonObject
        val addon = request.getValue("addon").jsonPrimitive.content
        val key = request.getValue("key").jsonPrimitive.content
        val value = request.getValue("value")
        AddonStateStore(context).setSettingValue(addon, key, value)
        return byteArrayOf()
    }
}
