// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.utils.FileOperation
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class AddonRegistryTest : RobolectricTest() {
    @Test
    fun fetchesAndParsesTheIndexTest() {
        // the existing index fixture is an array of addon manifests, like a registry index
        val indexUrl = File(FileOperation.getFileResource("test-js-addon.json")).toURI().toURL()

        val result = AddonRegistry(indexUrl).fetchAvailableAddons()

        val addons = assertIs<AddonRegistry.FetchResult.Success>(result).addons
        assertTrue("the index yields installable addons", addons.isNotEmpty())
        assertTrue("each has a tarball to download", addons.all { it.dist?.tarball?.endsWith(".tgz") == true })
    }

    @Test
    fun unreachableIndexFailsGracefullyTest() {
        val result = AddonRegistry(File("/does/not/exist/index.json").toURI().toURL()).fetchAvailableAddons()

        assertIs<AddonRegistry.FetchResult.Failure>(result)
    }
}
