/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.ui.windows.permissions

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import com.ichi2.anki.R
import com.ichi2.anki.databinding.AllPermissionsExplanationFragmentBinding
import com.ichi2.utils.Permissions
import timber.log.Timber

/**
 * Permissions explanation screen that appears when the user clicks on the extra info buttons next to the permissions
 * AnkiDroid requests in the OS settings screen. Explains the permissions AnkiDroid requests and provides switches for
 * toggling them on or off.
 *
 * See [the docs](https://developer.android.com/training/permissions/explaining-access#privacy-dashboard).
 */
@RequiresApi(Build.VERSION_CODES.S)
class AllPermissionsExplanationFragment : PermissionsFragment() {
    /**
     * Attempts to open the dialog for granting permissions. Falls back to opening the OS settings if the dialog fails to
     * show up or if the permissions are rejected by the user. The dialog may fail to show up if the user has previously denied the
     * permissions multiple times, if the user selects "don't ask again" on the permissions dialog, etc.
     */
    private val permissionRequestLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { requestedPermissions ->
            Timber.i("Permission result: $requestedPermissions")
            if (!requestedPermissions.all { it.value }) {
                showToastAndOpenAppSettingsScreen(R.string.manually_grant_permissions)
            }
        }

    /**
     * Activity launcher for the external storage management permission.
     */
    private val accessAllFilesLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = AllPermissionsExplanationFragmentBinding
        .inflate(inflater, container, false)
        .also { viewBinding ->
            val shouldRequestExternalStorage = Permissions.canManageExternalStorage(requireContext())
            if (shouldRequestExternalStorage) {
                viewBinding.manageExternalStoragePermissionItem.apply {
                    isVisible = true
                    requestExternalStorageOnClick(accessAllFilesLauncher)
                }
            }
            viewBinding.headingRequiredPermissions.isVisible = shouldRequestExternalStorage

            Permissions.postNotification?.let {
                viewBinding.postNotificationPermissionItem.apply {
                    isVisible = true
                    offerToGrantOrRevokeOnClick(permissionRequestLauncher, arrayOf(it))
                }
            }

            viewBinding.recordAudioPermissionItem.apply {
                isVisible = true
                offerToGrantOrRevokeOnClick(
                    permissionRequestLauncher,
                    arrayOf(Permissions.recordAudioPermission),
                )
            }
        }.root
}
