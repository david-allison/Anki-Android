// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import com.ichi2.anki.jsaddons.AddonsConst.REVIEWER_ADDON
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Guards the sample addons in `tools/sample-addons` against drifting out of validity */
class SampleAddonsTest {
    // unit tests run with the module directory as the working directory
    private val sampleAddonsDir = File("../tools/sample-addons")

    @Test
    fun sampleAddonsAreValidTest() {
        assertTrue(sampleAddonsDir.isDirectory, "sample addons exist at ${sampleAddonsDir.canonicalPath}")
        val sampleDirs =
            sampleAddonsDir
                .listFiles { file -> file.isDirectory && file.name != "out" }
                .orEmpty()
                .sortedBy { it.name }
        assertEquals(6, sampleDirs.size)

        for (dir in sampleDirs) {
            val result = getAddonModelFromJson(File(dir, "package/package.json"))
            val model = assertIs<AddonValidationResult.Valid>(result, "'${dir.name}' has a valid manifest").addonModel
            assertEquals(dir.name, model.name, "directory name matches the addon name")
            assertEquals(REVIEWER_ADDON, model.addonType, "sample addons are reviewer addons")
            assertTrue(File(dir, "package/${model.main}").isFile, "'${dir.name}' ships its main script")
        }
    }
}
