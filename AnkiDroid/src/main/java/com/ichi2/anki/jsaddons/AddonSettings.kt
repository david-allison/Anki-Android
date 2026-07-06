// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * One entry of an addon manifest's `settings` array: the declarative settings schema
 * (see `docs/addons/README.md`).
 *
 * The schema is rendered by the host, so a settings screen works without executing any
 * addon code. Value-bearing types map to standard widgets; `inputAs` is a presentation
 * hint on an existing type, not a new type.
 *
 * Forward compatibility: **unknown [type]s are tolerated** and skipped at render, so an
 * addon declaring a setting type from a newer AnkiDroid still installs and runs here.
 */
@Serializable
data class AddonSettingDefinition(
    /** `heading`, `toggle`, `enum`, `number`, `text`, `textarea` or `action` */
    val type: String? = null,
    /** The key the value is stored under; required for value-bearing types */
    val key: String? = null,
    val title: String? = null,
    /** Addon-supplied description; may contain markdown */
    val description: String? = null,
    val default: JsonElement? = null,
    /** For [TYPE_ENUM] */
    val choices: List<AddonSettingChoice>? = null,
    /** For [TYPE_NUMBER] */
    val min: Double? = null,
    /** For [TYPE_NUMBER] */
    val max: Double? = null,
    /** For [TYPE_NUMBER] */
    val step: Double? = null,
    /** Presentation hint, e.g. `slider` on a number, `color`/`date` on text */
    val inputAs: String? = null,
) {
    companion object {
        const val TYPE_HEADING = "heading"
        const val TYPE_TOGGLE = "toggle"
        const val TYPE_ENUM = "enum"
        const val TYPE_NUMBER = "number"
        const val TYPE_TEXT = "text"
        const val TYPE_TEXTAREA = "textarea"
        const val TYPE_ACTION = "action"

        /** The types which store a value under [key] */
        val VALUE_TYPES = setOf(TYPE_TOGGLE, TYPE_ENUM, TYPE_NUMBER, TYPE_TEXT, TYPE_TEXTAREA)
    }
}

@Serializable
data class AddonSettingChoice(
    val value: String? = null,
    val label: String? = null,
)

/** The effective settings of [addon]: its declared defaults overlaid with the stored [values] */
fun resolveSettingsValues(
    addon: AddonModel,
    values: JsonObject,
): JsonObject {
    val defaults =
        addon.settings
            .mapNotNull { setting -> setting.key?.let { key -> setting.default?.let { default -> key to default } } }
            .toMap()
    return JsonObject(defaults + values)
}

/**
 * Structural validation of a manifest's settings schema.
 *
 * Deliberately minimal: only breakage the host cannot render around is an error.
 * Unknown types are not errors (see [AddonSettingDefinition]).
 *
 * @return the errors; empty for a usable schema
 */
fun validateSettingsSchema(settings: List<AddonSettingDefinition>): List<String> {
    val errors = mutableListOf<String>()
    val seenKeys = mutableSetOf<String>()
    for ((index, setting) in settings.withIndex()) {
        if (setting.type.isNullOrBlank()) {
            errors.add("Invalid settings schema: entry $index has no 'type'")
            continue
        }
        if (setting.type in AddonSettingDefinition.VALUE_TYPES) {
            if (setting.key.isNullOrBlank()) {
                errors.add("Invalid settings schema: '${setting.type}' entry $index has no 'key'")
                continue
            }
            if (!seenKeys.add(setting.key)) {
                errors.add("Invalid settings schema: duplicate key '${setting.key}'")
            }
            if (setting.title.isNullOrBlank()) {
                errors.add("Invalid settings schema: entry '${setting.key}' has no 'title'")
            }
            if (setting.type == AddonSettingDefinition.TYPE_ENUM && setting.choices.isNullOrEmpty()) {
                errors.add("Invalid settings schema: enum '${setting.key}' has no 'choices'")
            }
        }
    }
    return errors
}
