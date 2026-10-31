// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import com.ichi2.utils.ImportUtils.FileImporter.CacheFileResult
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.InputStream

/** Distinguishes unavailable sources from failures while reading or closing an opened source. */
internal fun cacheImportSource(
    tempPath: String,
    openSource: () -> InputStream?,
    copy: (InputStream, String) -> Unit,
): CacheFileResult =
    try {
        openSource()?.let { input ->
            copyImportSource(input, tempPath, copy)
        } ?: run {
            Timber.w("Content provider crashed")
            CacheFileResult.ContentProviderCrashed
        }
    } catch (e: FileNotFoundException) {
        // The selected file may have been deleted since the provider returned its URI.
        Timber.w(e, "Import source file is unavailable")
        CacheFileResult.SourceFileUnavailable(e)
    } catch (e: IllegalStateException) {
        if (e.message?.contains("pending/trashed") == true) {
            Timber.w(e, "Import source file is pending or trashed")
            CacheFileResult.SourceFileUnavailable(e)
        } else {
            Timber.w("Could not open import source")
            CacheFileResult.Error(e)
        }
    } catch (e: Exception) {
        Timber.w("Could not open import source")
        CacheFileResult.Error(e)
    }

/** Copy and close failures are reportable even when the source was opened successfully. */
private fun copyImportSource(
    input: InputStream,
    tempPath: String,
    copy: (InputStream, String) -> Unit,
): CacheFileResult =
    try {
        input.use { copy(it, tempPath) }
        CacheFileResult.Success(tempPath)
    } catch (e: Exception) {
        Timber.w("Could not copy file to %s", tempPath)
        CacheFileResult.Error(e)
    }
