// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.permissions

const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

/**
 * Dummy sentinel "permission" string which represents the permission to send notifications on API <33.
 * On API <33, there is no explicit manifest permission for notifications, but they can be toggled off by the user in system settings.
 * See `legacy_post_notification_permission`
 */
const val LEGACY_POST_NOTIFICATIONS = "NOTIFICATIONS_BEFORE_API_33_DUMMY_SENTINEL"

enum class PermissionSettingsScreen {
    APPLICATION,
    NOTIFICATIONS,
}

/** Selects the system settings screen available for this permission and Android version. */
fun permissionSettingsScreen(
    permission: String?,
    sdkVersion: Int,
): PermissionSettingsScreen =
    when {
        sdkVersion >= 26 &&
            (permission == LEGACY_POST_NOTIFICATIONS || (sdkVersion >= 33 && permission == POST_NOTIFICATIONS)) ->
            PermissionSettingsScreen.NOTIFICATIONS
        else -> PermissionSettingsScreen.APPLICATION
    }
