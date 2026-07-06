/*
 * Copyright (c) 2021 Mani infinyte01@gmail.com
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

package com.ichi2.anki.jsaddons

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidJsAPIConstants.CURRENT_JS_API_VERSION
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.jsaddons.AddonsConst.ANKIDROID_JS_ADDON_KEYWORDS
import com.ichi2.anki.jsaddons.AddonsConst.REVIEWER_ADDON
import com.ichi2.utils.FileOperation
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.StringEndsWith.endsWith
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class AddonModelTest : RobolectricTest() {
    private lateinit var validNpmPackageJson: File
    private lateinit var notValidNpmPackageJson: File
    private lateinit var addonsPackageListTestJson: String

    @Before
    override fun setUp() {
        super.setUp()

        validNpmPackageJson = File(FileOperation.getFileResource("valid-ankidroid-js-addon-test.json"))
        notValidNpmPackageJson = File(FileOperation.getFileResource("not-valid-ankidroid-js-addon-test.json"))
        addonsPackageListTestJson = FileOperation.getFileResource("test-js-addon.json")
    }

    @Test
    @Throws(IOException::class)
    fun isValidAnkiDroidAddonTest() {
        // test addon is valid or not, for a valid addon the result is Valid
        val result = getAddonModelFromJson(validNpmPackageJson)
        val addon = assertIs<AddonValidationResult.Valid>(result, "package.json contains required fields").addonModel

        // needs to test these fields
        assertEquals(addon.name, "valid-ankidroid-js-addon-test")
        assertEquals(addon.addonTitle, "Valid AnkiDroid JS Addon")
        assertEquals(addon.version, "1.0.0")
        assertEquals(addon.ankidroidJsApi, "0.0.3")
        assertEquals(addon.addonType, "reviewer")
        assertEquals(addon.icon, "") // reviewer icon is empty

        val expected: List<String> = listOf("ankidroid-js-addon")
        assertEquals(addon.keywords, expected)
    }

    @Test
    @Throws(IOException::class)
    fun notValidAnkiDroidAddonTest() {
        // test addon is valid or not, for a not valid addon the result is Invalid
        val result = getAddonModelFromJson(notValidNpmPackageJson)
        // assert that the package.json was not mapped to an addon model
        val errors = assertIs<AddonValidationResult.Invalid>(result, "package.json not contains required fields").errors
        // assert that error list contains error when the package.json not mapped to AddonModel
        assertFalse(errors.isEmpty())
    }

    @Test
    fun unreadableManifestReturnsErrorTest() {
        // a missing/unreadable package.json must be reported as Invalid, not thrown:
        // one corrupt addon directory must not abort listing the other addons
        val result = getAddonModelFromJson(File("/does/not/exist/package.json"))
        val errors = assertIs<AddonValidationResult.Invalid>(result, "model is not built from an unreadable manifest").errors
        assertFalse("unreadable manifest is reported as an error", errors.isEmpty())
    }

    @Test
    fun getAddonModelListFromJsonTest() {
        val url = File(addonsPackageListTestJson).toURI().toURL()
        val result = getAddonModelListFromJson(url)

        // first addon name and tgz download url
        val addon1 = result.first[0]
        assertEquals(addon1.name, "ankidroid-js-addon-progress-bar")
        assertThat(addon1.dist!!.tarball, endsWith(".tgz"))

        // second addon name and tgz download url
        val addon2 = result.first[1]
        assertEquals(addon2.name, "valid-ankidroid-js-addon-test")
        assertThat(addon2.dist!!.tarball, endsWith(".tgz"))
    }

    /**
     * A valid manifest, with individual fields overridable so each test can knock one out
     */
    private fun addonData(
        name: String? = "valid-ankidroid-js-addon-test",
        addonTitle: String? = "Valid AnkiDroid JS Addon",
        icon: String? = "",
        version: String? = "1.0.0",
        description: String? = "A test addon",
        main: String? = "index.js",
        ankidroidJsApi: String? = CURRENT_JS_API_VERSION,
        addonType: String? = REVIEWER_ADDON,
        keywords: List<String>? = listOf(ANKIDROID_JS_ADDON_KEYWORDS),
        author: Map<String, String>? = mapOf("name" to "AnkiDroid"),
        license: String? = "MIT",
        homepage: String? = "https://example.com",
        dist: DistInfo? = DistInfo("https://example.com/addon.tgz"),
        settings: List<AddonSettingDefinition>? = null,
    ): AddonData =
        AddonData(
            name,
            addonTitle,
            icon,
            version,
            description,
            main,
            ankidroidJsApi,
            addonType,
            keywords,
            author,
            license,
            homepage,
            dist,
            settings,
        )

    @Test // the validator must report errors, never throw
    fun missingNameReturnsErrorTest() {
        val result = getAddonModelFromAddonData(addonData(name = null))
        val errors = assertIs<AddonValidationResult.Invalid>(result, "model is not built from an invalid manifest").errors
        assertFalse("missing 'name' is reported as an error", errors.isEmpty())
    }

    @Test // the validator must report errors, never throw
    fun missingKeywordsReturnsErrorTest() {
        val result = getAddonModelFromAddonData(addonData(keywords = null))
        val errors = assertIs<AddonValidationResult.Invalid>(result, "model is not built from an invalid manifest").errors
        assertFalse("missing 'keywords' is reported as an error", errors.isEmpty())
    }

    @Test // 'version' is required during model construction, so it must be validated
    fun missingVersionReturnsErrorTest() {
        val result = getAddonModelFromAddonData(addonData(version = null))
        val errors = assertIs<AddonValidationResult.Invalid>(result, "model is not built from an invalid manifest").errors
        assertFalse("missing 'version' is reported as an error", errors.isEmpty())
    }

    @Test // exercises the checks after the required-fields guard: their errors must be returned
    fun invalidPackageNameReturnsErrorTest() {
        val result = getAddonModelFromAddonData(addonData(name = "NOT!a!valid!npm!name"))
        val errors = assertIs<AddonValidationResult.Invalid>(result, "model is not built from an invalid manifest").errors
        assertFalse("invalid 'name' is reported as an error", errors.isEmpty())
    }

    @Test // a future addonType (e.g. 'background') must be listed as unsupported, not rejected
    fun unknownAddonTypeIsValidTest() {
        val result = getAddonModelFromAddonData(addonData(addonType = "background"))
        assertIs<AddonValidationResult.Valid>(result, "an unknown addonType does not invalidate the addon")
    }

    @Test
    fun olderApiVersionRequiresAddonUpdateTest() {
        val result = getAddonModelFromAddonData(addonData(ankidroidJsApi = "0.0.2"))
        val errors = assertIs<AddonValidationResult.Invalid>(result, "an api version below the minimum is invalid").errors
        assertTrue("error asks for an addon update: $errors", errors.any { it.contains("the addon needs updating") })
    }

    @Test
    fun newerApiVersionRequiresAnkiDroidUpdateTest() {
        val result = getAddonModelFromAddonData(addonData(ankidroidJsApi = "9.9.9"))
        val errors = assertIs<AddonValidationResult.Invalid>(result, "an api version above the current one is invalid").errors
        assertTrue("error asks for an AnkiDroid update: $errors", errors.any { it.contains("AnkiDroid needs updating") })
    }

    @Test
    fun garbageApiVersionReturnsErrorTest() {
        val result = getAddonModelFromAddonData(addonData(ankidroidJsApi = "not-a-version"))
        assertIs<AddonValidationResult.Invalid>(result, "an unparseable js api version invalidates the addon")
    }

    @Test
    fun missingDistIsValidTest() {
        // 'dist' is metadata added by the npm registry API; the package.json inside a
        // tarball does not contain it, so a locally installed addon must still validate
        val result = getAddonModelFromAddonData(addonData(dist = null))
        assertIs<AddonValidationResult.Valid>(result, "model is built from a manifest without 'dist'")
    }

    @Test
    fun validSettingsSchemaIsCarriedOnTheModelTest() {
        val schema =
            listOf(
                AddonSettingDefinition(type = "heading", title = "Appearance"),
                AddonSettingDefinition(type = "toggle", key = "enabled", title = "Show bar"),
                AddonSettingDefinition(
                    type = "enum",
                    key = "position",
                    title = "Position",
                    choices = listOf(AddonSettingChoice("top", "Top"), AddonSettingChoice("bottom", "Bottom")),
                ),
                AddonSettingDefinition(type = "number", key = "height", title = "Height", min = 1.0, max = 24.0),
            )

        val result = getAddonModelFromAddonData(addonData(settings = schema))

        val model = assertIs<AddonValidationResult.Valid>(result, "a valid settings schema validates").addonModel
        assertEquals(schema, model.settings)
    }

    @Test
    fun settingsSchemaWithDuplicateKeysReturnsErrorTest() {
        val schema =
            listOf(
                AddonSettingDefinition(type = "toggle", key = "same", title = "A"),
                AddonSettingDefinition(type = "text", key = "same", title = "B"),
            )

        val result = getAddonModelFromAddonData(addonData(settings = schema))

        val errors = assertIs<AddonValidationResult.Invalid>(result, "duplicate keys are structural breakage").errors
        assertFalse(errors.isEmpty())
    }

    @Test
    fun settingsSchemaWithMissingKeyReturnsErrorTest() {
        val schema = listOf(AddonSettingDefinition(type = "toggle", title = "No key"))

        val result = getAddonModelFromAddonData(addonData(settings = schema))

        assertIs<AddonValidationResult.Invalid>(result, "a value-bearing setting without a key is invalid")
    }

    @Test // a future AnkiDroid may define new setting types; they must not invalidate the addon
    fun unknownSettingTypeIsValidTest() {
        val schema = listOf(AddonSettingDefinition(type = "hologram", key = "x", title = "From the future"))

        val result = getAddonModelFromAddonData(addonData(settings = schema))

        assertIs<AddonValidationResult.Valid>(result, "an unknown setting type does not invalidate the addon")
    }

    @Test
    fun resolveSettingsValuesOverlaysDefaultsTest() {
        val schema =
            listOf(
                AddonSettingDefinition(type = "number", key = "delaySeconds", title = "Delay", default = JsonPrimitive(10)),
                AddonSettingDefinition(type = "toggle", key = "vibrate", title = "Vibrate", default = JsonPrimitive(false)),
                AddonSettingDefinition(type = "text", key = "noDefault", title = "No default"),
            )
        val model = assertIs<AddonValidationResult.Valid>(getAddonModelFromAddonData(addonData(settings = schema))).addonModel

        val resolved = resolveSettingsValues(model, JsonObject(mapOf("vibrate" to JsonPrimitive(true))))

        assertEquals(JsonPrimitive(10), resolved["delaySeconds"]) // default
        assertEquals(JsonPrimitive(true), resolved["vibrate"]) // stored value wins
        assertEquals(null, resolved["noDefault"]) // absent everywhere
    }

    @Test
    fun missingOptionalMetadataIsValidTest() {
        // description/author/license are commonly absent from real package.json files
        val result = getAddonModelFromAddonData(addonData(description = null, author = null, license = null))
        val addon = assertIs<AddonValidationResult.Valid>(result, "model is built from a manifest without optional metadata").addonModel
        assertEquals("", addon.description)
        assertEquals(emptyMap<String, String>(), addon.author)
        assertEquals("", addon.license)
    }
}
