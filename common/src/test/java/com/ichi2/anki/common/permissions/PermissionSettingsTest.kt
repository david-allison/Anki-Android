// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.permissions

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PermissionSettingsTest {
    @Test
    fun `permission and SDK boundaries select the supported settings screen`() {
        val cases =
            listOf(
                Case(null, 25, PermissionSettingsScreen.APPLICATION),
                Case(null, 36, PermissionSettingsScreen.APPLICATION),
                Case("unhandled", 36, PermissionSettingsScreen.APPLICATION),
                Case(POST_NOTIFICATIONS, 32, PermissionSettingsScreen.APPLICATION),
                Case(POST_NOTIFICATIONS, 33, PermissionSettingsScreen.NOTIFICATIONS),
                Case(POST_NOTIFICATIONS, 36, PermissionSettingsScreen.NOTIFICATIONS),
                Case(LEGACY_POST_NOTIFICATIONS, 25, PermissionSettingsScreen.APPLICATION),
                Case(LEGACY_POST_NOTIFICATIONS, 26, PermissionSettingsScreen.NOTIFICATIONS),
                Case(LEGACY_POST_NOTIFICATIONS, 32, PermissionSettingsScreen.NOTIFICATIONS),
                Case(LEGACY_POST_NOTIFICATIONS, 33, PermissionSettingsScreen.NOTIFICATIONS),
            )
        for (case in cases) {
            assertEquals(case.screen, permissionSettingsScreen(case.permission, case.sdkVersion), case.toString())
        }
    }

    private data class Case(
        val permission: String?,
        val sdkVersion: Int,
        val screen: PermissionSettingsScreen,
    )
}
