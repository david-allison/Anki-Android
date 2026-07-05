// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Mani <infinyte01@gmail.com>

package com.ichi2.anki.jsaddons

import android.content.Context
import com.ichi2.anki.BackupManager
import com.ichi2.anki.CollectionHelper
import java.io.File

/**
 * Access to the addons directory of the current profile.
 *
 * All knowledge of the on-disk addon layout lives in this class: nothing else may
 * construct addon paths, so the layout can evolve behind this one seam.
 *
 * The layout, per addon (npm tarballs nest their content under `package/`):
 *
 * ```
 * AnkiDroid/
 *   - addons/
 *       - some-addon/
 *           - package/
 *               - package.json
 *               - index.js
 * ```
 */
class AddonStorage(
    context: Context,
) {
    private val addonsDir = File(CollectionHelper.getCurrentAnkiDroidDirectory(context), "addons")

    /** The addons directory of the current profile, created if it does not exist */
    fun getAddonsDir(): File {
        if (!addonsDir.exists()) {
            addonsDir.mkdirs()
        }
        return addonsDir
    }

    /** The directory of a single addon, e.g. `AnkiDroid/addons/some-addon/` */
    fun getAddonDir(addonName: String): File = File(getAddonsDir(), addonName)

    /** The manifest of a single addon, e.g. `AnkiDroid/addons/some-addon/package/package.json` */
    fun getManifestFile(addonName: String): File = File(getAddonDir(addonName), "package/package.json")

    /**
     * Every addon in the addons directory, whether its manifest is valid or not.
     *
     * Plain files in the addons directory are ignored: a future AnkiDroid may keep
     * bookkeeping files next to the addon directories
     */
    fun getInstalledAddons(): List<InstalledAddon> =
        getAddonsDir()
            .listFiles { file -> file.isDirectory }
            .orEmpty()
            .map { dir -> InstalledAddon(dir.name, getAddonModelFromJson(getManifestFile(dir.name))) }

    /** Removes the addon directory of [addonName] and everything in it */
    fun deleteAddon(addonName: String): Boolean = BackupManager.removeDir(getAddonDir(addonName))
}

/** An entry of the addons directory: its [directoryName] and the outcome of validating its manifest */
data class InstalledAddon(
    val directoryName: String,
    val result: AddonValidationResult,
)
