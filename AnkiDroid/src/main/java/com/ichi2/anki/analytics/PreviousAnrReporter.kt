// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.utils.ext.requireSystemService
import com.ichi2.anki.exception.AnrReportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.Reader

/**
 * Launches reporting of a previous run's ANR in the background during main-process startup on Android 11+.
 * Logs the reporting duration using the standard startup timing.
 */
internal fun AnkiDroidApp.launchPreviousAnrReporting() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    applicationScope.launch(Dispatchers.IO) {
        try {
            setup("reportPreviousAnr") {
                reportPreviousAnr()
            }
        } catch (_: Exception) {
            // setup() already logged the failure; this diagnostic must not interrupt startup.
        }
    }
}

/**
 * Reports the latest main-process exit caused by an ANR as an [AnrReportException].
 *
 * Uses the captured main-thread stack; other report metadata describes this reporting launch.
 *
 * ANRs that recover before the process exits for another reason are not included.
 */
@RequiresApi(Build.VERSION_CODES.R)
@WorkerThread
context(context: AnkiDroidApp)
internal fun reportPreviousAnr() {
    // Android's exit history is shared across profiles, so the processed marker must be too.
    val state = SharedPreferencesAnrReportState(context.baseContext)

    // ApplicationExitInfo has no app version. Do not attribute an older build's ANR to this one.
    @Suppress("DEPRECATION")
    val updatedAt = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    val activityManager = context.requireSystemService<ActivityManager>()
    processPreviousAnr(
        state,
        context.packageName,
        updatedAt,
        exits = {
            activityManager.getHistoricalProcessExitReasons(null, 0, 0).map(::AndroidAnrExit)
        },
    ) { report, metadata ->
        CrashReportService.sendExceptionReport(
            report,
            origin = "PreviousAnrReporter",
            additionalInfo = metadata,
            onlyIfSilent = true,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private class AndroidAnrExit(
    private val exit: ApplicationExitInfo,
) : PreviousAnrExit {
    override val timestamp get() = exit.timestamp
    override val processName get() = exit.processName
    override val isAnr get() = exit.reason == ApplicationExitInfo.REASON_ANR

    override fun openTrace(): Reader? = exit.traceInputStream?.reader()
}
