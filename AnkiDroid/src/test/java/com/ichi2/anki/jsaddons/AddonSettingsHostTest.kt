// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidJsAPIConstants.CURRENT_JS_API_VERSION
import com.ichi2.anki.RobolectricTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AddonSettingsHostTest : RobolectricTest() {
    private fun installConfigurableAddon(name: String = "settings-addon") {
        val manifest = AddonStorage(targetContext).getManifestFile(name)
        manifest.parentFile!!.mkdirs()
        manifest.writeText(
            """
            {
              "name": "$name", "addonTitle": "Settings addon", "version": "1.0.0", "main": "index.js",
              "ankidroidJsApi": "$CURRENT_JS_API_VERSION", "addonType": "reviewer",
              "homepage": "https://example.com", "keywords": ["ankidroid-js-addon"],
              "settings": [ { "type": "number", "key": "n", "title": "N", "default": 7 } ]
            }
            """.trimIndent(),
        )
        File(manifest.parentFile, "index.js").writeText("// js")
    }

    @Test
    fun getReturnsSchemaAndResolvedValuesTest() {
        installConfigurableAddon()

        val response =
            Json.parseToJsonElement(
                AddonSettingsHost
                    .handle(
                        targetContext,
                        AddonSettingsHost.METHOD_GET,
                        """{"addon":"settings-addon"}""".encodeToByteArray(),
                    )!!
                    .decodeToString(),
            ) as JsonObject

        // the schema is returned for the page to render
        assertTrue("schema is included", response.containsKey("schema"))
        // and the resolved value (the default, since nothing stored yet)
        val values = response["values"] as JsonObject
        assertEquals(JsonPrimitive(7), values["n"])
    }

    @Test
    fun setPersistsValuesReadableByGetTest() {
        installConfigurableAddon()

        AddonSettingsHost.handle(
            targetContext,
            AddonSettingsHost.METHOD_SET,
            """{"addon":"settings-addon","values":{"n":42}}""".encodeToByteArray(),
        )

        val values =
            (
                Json.parseToJsonElement(
                    AddonSettingsHost
                        .handle(
                            targetContext,
                            AddonSettingsHost.METHOD_GET,
                            """{"addon":"settings-addon"}""".encodeToByteArray(),
                        )!!
                        .decodeToString(),
                ) as JsonObject
            )["values"] as JsonObject
        assertEquals(JsonPrimitive(42), values["n"])
    }

    @Test
    fun unknownMethodReturnsNullTest() {
        assertEquals(null, AddonSettingsHost.handle(targetContext, "somethingElse", "{}".encodeToByteArray()))
    }
}
