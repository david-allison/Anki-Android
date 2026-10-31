// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import com.ichi2.anki.exception.AnrReportException
import java.io.Reader
import java.time.Instant

/** Metadata stays independent of Android, and only the selected exit opens its trace. */
internal interface PreviousAnrExit {
    val timestamp: Long
    val processName: String
    val isAnr: Boolean

    fun openTrace(): Reader?
}

internal interface PreviousAnrReportState {
    val lastProcessedTimestamp: Long

    fun markProcessed(timestamp: Long): Boolean
}

/** Selects an unprocessed exit from this build before decoding and reporting its bounded trace. */
internal fun processPreviousAnr(
    state: PreviousAnrReportState,
    processName: String,
    updatedAt: Long,
    exits: () -> List<PreviousAnrExit>,
    report: (AnrReportException, String) -> Unit,
) {
    val since = state.lastProcessedTimestamp
    val latest =
        exits()
            .filter {
                it.isAnr && it.processName == processName && it.timestamp > since && it.timestamp >= updatedAt
            }.maxByOrNull { it.timestamp } ?: return

    // Persist before opening a trace so failures cannot cause repeated reports on every launch.
    if (!state.markProcessed(latest.timestamp)) return
    val exception = latest.openTrace()?.use { AnrReportException(it.readAnrMainThreadStackTrace()) } ?: return
    report(exception, "Previous ANR: process exited at ${Instant.ofEpochMilli(latest.timestamp)}.")
}
