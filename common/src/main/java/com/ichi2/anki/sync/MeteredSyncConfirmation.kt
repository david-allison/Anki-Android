// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

/** Applies per-attempt permission and only asks for confirmation when the saved policy blocks sync. */
fun confirmMeteredSync(
    skipPrompt: Boolean,
    shouldBlock: () -> Boolean,
    onConfirmationRequired: () -> Unit,
    onConfirm: (MeteredSyncPermission) -> Unit,
) {
    when {
        skipPrompt -> onConfirm(MeteredSyncPermission.ALLOW_METERED_SYNC_THIS_TIME)
        !shouldBlock() -> onConfirm(MeteredSyncPermission.USE_PREFERENCES)
        else -> onConfirmationRequired()
    }
}
