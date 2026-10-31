// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.permissions

import kotlin.reflect.KMutableProperty0

/** Android's rationale check only describes repeat requests, so the first request is always allowed. */
fun canRequestPermission(
    requested: KMutableProperty0<Boolean>,
    shouldShowRationale: () -> Boolean,
): Boolean = !requested.get() || shouldShowRationale()

/** Records requests before launching the system dialog, or opens settings after permanent denial. */
fun requestPermissionOrOpenSettings(
    requested: KMutableProperty0<Boolean>,
    shouldShowRationale: () -> Boolean,
    request: () -> Unit,
    openSettings: () -> Unit,
) {
    if (canRequestPermission(requested, shouldShowRationale)) {
        requested.set(true)
        request()
    } else {
        openSettings()
    }
}

enum class NotificationPermissionPrompt {
    RUNTIME_PERMISSION,
    LEGACY_SETTINGS,
}

/**
 * Shows a notification prompt when it can help. Legacy settings are suggested once; runtime
 * permission requests may be repeated until the system stops allowing them.
 *
 * @return Whether a prompt was shown. A failed launch does not record the legacy prompt as shown.
 */
fun showNotificationPermissionPromptIfNeeded(
    notificationsEnabled: Boolean,
    supportsRuntimePermission: Boolean,
    canRequestPermission: () -> Boolean,
    legacyPromptShown: KMutableProperty0<Boolean>,
    showPrompt: (NotificationPermissionPrompt) -> Unit,
): Boolean {
    if (notificationsEnabled) return false
    if (supportsRuntimePermission) {
        if (!canRequestPermission()) return false
        showPrompt(NotificationPermissionPrompt.RUNTIME_PERMISSION)
    } else {
        if (legacyPromptShown.get()) return false
        showPrompt(NotificationPermissionPrompt.LEGACY_SETTINGS)
        legacyPromptShown.set(true)
    }
    return true
}
