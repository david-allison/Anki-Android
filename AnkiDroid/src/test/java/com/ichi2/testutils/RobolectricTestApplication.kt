// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.content.Context
import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter
import com.ichi2.anki.common.preferences.sharedPrefs
import org.acra.ACRA

/** UI fixture with logging crash reports; dedicated tests exercise process diagnostics and maintenance. */
class RobolectricTestApplication : AnkiDroidApp() {
    private var previousExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private var installedExceptionHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        super.onCreate()
        installedExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    }

    override fun setupCrashReporting() {
        sharedPrefs().edit {
            putString(CrashReporter.FEEDBACK_REPORT_KEY, CrashReporter.FEEDBACK_REPORT_NEVER)
            putBoolean(ACRA.PREF_DISABLE_ACRA, true)
        }
        CrashReportService.resetForTesting()
        val loggingReporter = CrashReportService.getReporter()
        CrashReportService.setReporter(
            object : CrashReporter by loggingReporter {
                override fun isEnabled(
                    context: Context,
                    defaultValue: Boolean,
                ) = false
            },
        )
    }

    override fun logStartupDebugInfo() = Unit

    override fun startProcessMaintenance() = Unit

    override fun setupTextToSpeech() = Unit

    override fun onTerminate() {
        try {
            super.onTerminate()
        } finally {
            if (Thread.getDefaultUncaughtExceptionHandler() === installedExceptionHandler) {
                Thread.setDefaultUncaughtExceptionHandler(previousExceptionHandler)
            }
        }
    }
}
