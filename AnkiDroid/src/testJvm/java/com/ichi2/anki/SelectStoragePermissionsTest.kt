// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.annotation.SuppressLint
import android.os.Build
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.junit.Test
import kotlin.test.assertTrue

/** Storage permission policy without an Android runtime. */
class SelectStoragePermissionsTest {
    @Test
    fun startupBeforeQ() {
        val expectedPermissions =
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )

        // force a safe startup before Q
        assertThat(
            selectStoragePermissions(canManageExternalStorage = false, sdkVersion = BEFORE_Q).permissions.asIterable(),
            contains(*expectedPermissions),
        )
        assertThat(
            selectStoragePermissions(canManageExternalStorage = true, sdkVersion = BEFORE_Q).permissions.asIterable(),
            contains(*expectedPermissions),
        )
    }

    @Test
    fun startupQ() {
        assertThat(selectStoragePermissions(canManageExternalStorage = false, sdkVersion = Q), equalTo(StoragePermissionSet.LEGACY_ACCESS))
        assertThat(selectStoragePermissions(canManageExternalStorage = true, sdkVersion = Q), equalTo(StoragePermissionSet.LEGACY_ACCESS))
    }

    @SuppressLint("InlinedApi")
    @Test
    fun `Android 11 - After upgrade from AnkiDroid 2 15 (with MANAGE_EXTERNAL_STORAGE)`() {
        // after an upgrade, all we need is READ/WRITE. Once we reinstall, we need MANAGE_EXTERNAL_STORAGE
        val expectedPermissions =
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )

        selectStoragePermissions(
            canManageExternalStorage = true,
            currentFolderIsAccessibleAndLegacy = true,
            sdkVersion = R_OR_AFTER,
        ).let {
            assertThat(
                it.permissions.asIterable(),
                contains(*expectedPermissions),
            )
        }
    }

    @SuppressLint("InlinedApi")
    @Test
    fun `Android 11 - After reinstall (with MANAGE_EXTERNAL_STORAGE)`() {
        val permissions =
            selectStoragePermissions(
                canManageExternalStorage = true,
                currentFolderIsAccessibleAndLegacy = false,
                sdkVersion = R_OR_AFTER,
            )

        assertTrue(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE in permissions.permissions)
    }

    @Test
    fun startupAfterQWithoutManageExternalStorage() {
        assertThat(
            selectStoragePermissions(canManageExternalStorage = false, sdkVersion = R_OR_AFTER),
            equalTo(StoragePermissionSet.APP_PRIVATE),
        )
    }

    /**
     * Helper for [com.ichi2.anki.selectStoragePermissions], making `currentFolderIsAccessibleAndLegacy` optional
     */
    private fun selectStoragePermissions(
        canManageExternalStorage: Boolean,
        currentFolderIsAccessibleAndLegacy: Boolean = false,
        sdkVersion: Int,
    ): StoragePermissionSet =
        com.ichi2.anki.selectStoragePermissions(
            canManageExternalStorage = canManageExternalStorage,
            currentFolderIsAccessibleAndLegacy = currentFolderIsAccessibleAndLegacy,
            sdkVersion = sdkVersion,
        )

    companion object {
        const val BEFORE_Q = Build.VERSION_CODES.Q - 1
        const val Q = Build.VERSION_CODES.Q
        const val R_OR_AFTER = Build.VERSION_CODES.R
    }
}
