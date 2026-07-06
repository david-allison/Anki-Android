// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Mani <infinyte01@gmail.com>

package com.ichi2.anki.jsaddons

import android.content.Context
import com.ichi2.anki.BackupManager
import com.ichi2.anki.CollectionHelper
import timber.log.Timber
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
    private val context: Context,
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
    fun getManifestFile(addonName: String): File = manifestIn(getAddonDir(addonName))

    /** The manifest inside [addonDir]: npm tarballs nest their content under `package/` */
    private fun manifestIn(addonDir: File): File = File(addonDir, "package/package.json")

    /**
     * Every addon in the addons directory, whether its manifest is valid or not.
     *
     * Plain and hidden files in the addons directory are ignored: staging directories are
     * hidden, and a future AnkiDroid may keep bookkeeping files next to the addons
     */
    fun getInstalledAddons(): List<InstalledAddon> =
        getAddonsDir()
            .listFiles { file -> file.isDirectory && !file.isHidden }
            .orEmpty()
            .map { dir -> InstalledAddon(dir.name, getAddonModelFromJson(manifestIn(dir))) }

    /** Removes the addon directory of [addonName] and everything in it */
    fun deleteAddon(addonName: String): Boolean = BackupManager.removeDir(getAddonDir(addonName))

    /**
     * Resolves a `/_addons/<name>/<path>` URL path to a file inside that addon's
     * `package/` directory, for serving addon files to a WebView.
     *
     * @return the file, or null if [path] does not resolve to an existing file inside the
     *   addons directory: a path traversal guard, as [path] comes from the WebView
     */
    fun resolveWebExport(path: String): File? {
        val relative = path.removePrefix(WEB_EXPORTS_PREFIX)
        if (relative == path) return null // not under the addons prefix
        val addonName = relative.substringBefore('/', missingDelimiterValue = "")
        val fileInPackage = relative.substringAfter('/', missingDelimiterValue = "")
        if (addonName.isEmpty() || fileInPackage.isEmpty()) return null

        val file = File(File(getAddonDir(addonName), "package"), fileInPackage)
        if (!file.canonicalPath.startsWith(addonsDir.canonicalPath + File.separator)) return null
        if (!file.isFile) return null
        return file
    }

    /**
     * Installs an addon from an npm `.tgz` tarball.
     *
     * The install is atomic: the tarball is extracted into a hidden staging directory and
     * only moved into place after its manifest validates, so a corrupt tarball can never
     * leave a broken addon behind. An existing installation of the same addon is replaced.
     *
     * @return [AddonValidationResult.Valid] with the installed addon's model, or
     *   [AddonValidationResult.Invalid] with the reasons the tarball was rejected
     */
    fun installFromTarball(tarball: File): AddonValidationResult {
        val stagingDir = File(getAddonsDir(), ".staging-${System.currentTimeMillis()}")
        try {
            try {
                TgzPackageExtract(context).extractTarGzipToAddonFolder(tarball, stagingDir)
            } catch (e: Exception) {
                Timber.w(e, "Addon extraction failed")
                return AddonValidationResult.Invalid(listOf("Unable to extract addon: $e"))
            }

            val result = getAddonModelFromJson(manifestIn(stagingDir))
            if (result !is AddonValidationResult.Valid) return result

            val targetDir = getAddonDir(result.addonModel.name)
            if (targetDir.exists() && !targetDir.deleteRecursively()) {
                return AddonValidationResult.Invalid(listOf("Unable to replace the existing addon in $targetDir"))
            }
            if (!stagingDir.renameTo(targetDir)) {
                return AddonValidationResult.Invalid(listOf("Unable to move the addon into $targetDir"))
            }
            return result
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    companion object {
        /** URL path prefix under which addon files are served to WebViews (mirrors desktop's `/_addons/`) */
        const val WEB_EXPORTS_PREFIX = "/_addons/"
    }
}

/** An entry of the addons directory: its [directoryName] and the outcome of validating its manifest */
data class InstalledAddon(
    val directoryName: String,
    val result: AddonValidationResult,
)
