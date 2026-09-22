// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 voczi <dev@voczi.com>

package com.ichi2.anki.pages

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.HamcrestUtils.containsInAnyOrder
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStreamReader
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class PostRequestHandlerTest : RobolectricTest() {
    @Test
    fun `All backend typescript functions should be handled`() {
        assertThat(
            "Mapping exists for every TS backend function call",
            typescriptFunctionsUsedByBackend - unsupportedEditorFunctions,
            // this matcher asserts equality in everything but order, no extras, nothing missing
            containsInAnyOrder((collectionMethods + uiMethods).keys),
        )
    }

    @Test
    fun `saveCustomColours does not throw`() =
        runTest {
            // saveCustomColours is a FrontendService which is not implemented, but should not throw
            assertNotNull(handleCollectionPostRequest("saveCustomColours", byteArrayOf()))
        }

    /**
     * Auto-generated list of all typescript funcs created & packaged during backend build
     */
    private val typescriptFunctionsUsedByBackend: List<String> =
        InputStreamReader(targetContext.assets.open("backend/ts_funcs.txt")).use {
            it.readLines().also { lines ->
                assertThat("Stored Typescript functions", lines, not(empty()))
            }
        }

    // The asset lists imports from every bundled route, including Anki's experimental editor
    // and preferences. Android does not expose those routes. Keep this list explicit so new
    // calls still fail this test; do not expose file/clipboard/frontend APIs just for coverage.
    private val unsupportedEditorFunctions =
        setOf(
            "addMediaFile",
            "addMediaFromPath",
            "addMediaFromUrl",
            "addNote",
            "askUser",
            "closeAddCards",
            "closeEditCurrent",
            "convertPastedImage",
            "decodeIriPaths",
            "defaultDeckForNotetype",
            "defaultsForAdding",
            "encodeIriPaths",
            "extractMediaFiles",
            "getAbsoluteMediaPath",
            "getCard",
            "getClozeFieldOrds",
            "getConfigBool",
            "getConfigJson",
            "getDeck",
            "getMetaJson",
            "getNote",
            "getNotetype",
            "getProfileConfigJson",
            "htmlToTextLine",
            "newNote",
            "noteFieldsCheck",
            "openCardsDialog",
            "openFieldsDialog",
            "openFilePicker",
            "openLink",
            "openMedia",
            "playFile",
            "readClipboard",
            "recordAudio",
            "setConfigJson",
            "setMetaJson",
            "setProfileConfigJson",
            "showInMediaFolder",
            "showMessageBox",
            "updateNotes",
            "updateNotetype",
            "writeClipboard",
        )
}
