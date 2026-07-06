// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Host-owned state of installed addons: one JSON object per addon, keyed by the addon's
 * immutable npm name, in a dedicated per-profile SharedPreferences file.
 *
 * `enabled` is one key of the object; future per-addon facts (granted permissions,
 * settings values) become further keys of the same object. State is read and written as a
 * [JsonObject] rather than a typed model so that **keys written by a newer AnkiDroid
 * survive writes from an older one**.
 *
 * Addons default to disabled: absent state is valid state, so extending the state never
 * needs a migration.
 */
class AddonStateStore(
    context: Context,
) {
    // ProfileContextWrapper prefixes named SharedPreferences files per profile,
    // so addon state is isolated between profiles
    private val prefs = context.getSharedPreferences("addons", Context.MODE_PRIVATE)

    private fun getState(addonName: String): JsonObject {
        val stored = prefs.getString(addonName, null) ?: return JsonObject(emptyMap())
        return try {
            Json.parseToJsonElement(stored) as? JsonObject ?: JsonObject(emptyMap())
        } catch (_: SerializationException) {
            // corrupt state is treated as absent: the addon reverts to disabled
            JsonObject(emptyMap())
        }
    }

    private fun setState(
        addonName: String,
        state: JsonObject,
    ) = prefs.edit { putString(addonName, state.toString()) }

    fun isEnabled(addonName: String): Boolean = (getState(addonName)[KEY_ENABLED] as? JsonPrimitive)?.booleanOrNull ?: false

    fun setEnabled(
        addonName: String,
        enabled: Boolean,
    ) = setState(addonName, JsonObject(getState(addonName) + (KEY_ENABLED to JsonPrimitive(enabled))))

    /** The stored settings values of [addonName]; defaults are resolved by the caller */
    fun getSettingsValues(addonName: String): JsonObject = (getState(addonName)[KEY_SETTINGS] as? JsonObject) ?: JsonObject(emptyMap())

    /**
     * Merges [values] into the stored settings of [addonName]: existing keys not present
     * in [values] are preserved, including keys this AnkiDroid does not know about
     */
    fun setSettingsValues(
        addonName: String,
        values: JsonObject,
    ) {
        val state = getState(addonName)
        val merged = JsonObject(getSettingsValues(addonName) + values)
        setState(addonName, JsonObject(state + (KEY_SETTINGS to merged)))
    }

    fun setSettingValue(
        addonName: String,
        key: String,
        value: JsonElement,
    ) = setSettingsValues(addonName, JsonObject(mapOf(key to value)))

    /**
     * Replaces the settings of [addonName] wholesale with [values], for the raw JSON editor
     * where the edited text is the source of truth. Non-settings state (e.g. `enabled`) is
     * preserved.
     */
    fun replaceSettingsValues(
        addonName: String,
        values: JsonObject,
    ) = setState(addonName, JsonObject(getState(addonName) + (KEY_SETTINGS to values)))

    /** The permission ids granted to [addonName] (see [AddonPermission.id]) */
    fun getGrantedPermissions(addonName: String): Set<String> =
        (getState(addonName)[KEY_GRANTED] as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .toSet()

    fun isGranted(
        addonName: String,
        permission: AddonPermission,
    ): Boolean = permission.id in getGrantedPermissions(addonName)

    /** Replaces the granted permission set of [addonName] wholesale */
    fun setGrantedPermissions(
        addonName: String,
        granted: Set<String>,
    ) = setState(addonName, JsonObject(getState(addonName) + (KEY_GRANTED to JsonArray(granted.map { JsonPrimitive(it) }))))

    fun grant(
        addonName: String,
        permission: AddonPermission,
    ) = setGrantedPermissions(addonName, getGrantedPermissions(addonName) + permission.id)

    fun revoke(
        addonName: String,
        permission: AddonPermission,
    ) = setGrantedPermissions(addonName, getGrantedPermissions(addonName) - permission.id)

    /** Forgets all state of [addonName]: for use when the addon is uninstalled */
    fun remove(addonName: String) = prefs.edit { remove(addonName) }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_GRANTED = "granted"
    }
}
