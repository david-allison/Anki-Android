// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import com.ichi2.anki.AnkiDroidApp

/** Initializes UI dependencies without automatic process diagnostics and file maintenance. */
class RobolectricTestApplication : AnkiDroidApp() {
    override fun logStartupDebugInfo() = Unit

    override fun startProcessMaintenance() = Unit
}
