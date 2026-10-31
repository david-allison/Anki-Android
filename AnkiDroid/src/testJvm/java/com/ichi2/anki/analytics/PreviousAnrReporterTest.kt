// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import com.ichi2.anki.exception.AnrReportException
import org.junit.Test
import java.io.IOException
import java.io.Reader
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviousAnrReporterTest {
    private val state = TestAnrState()
    private var exits = emptyList<PreviousAnrExit>()
    private var updatedAt = 0L
    private val reports = mutableListOf<Throwable>()
    private val reportMetadata = mutableListOf<String?>()

    @Test
    fun `latest main process ANR is reported on first launch and only once`() {
        val old = exit(150)
        val latest = exit(200)
        val crash = exit(250, isAnr = false)
        val sender = exit(280, process = "${PACKAGE_NAME}:acra")
        exits = listOf(sender, old, crash, latest)

        report()
        report()

        assertEquals(1, reports.size)
        val exception = assertIs<AnrReportException>(reports.single())
        assertEquals("Previous ANR: process exited at 1970-01-01T00:00:00.200Z.", reportMetadata.single())
        assertTrue(exception.message!!.contains("Other report metadata describes this reporting launch"))
        assertContentEquals(MAIN_STACK_TRACE, exception.stackTrace)
        assertEquals(0, old.traceReads)
        assertEquals(0, crash.traceReads)
        assertEquals(0, sender.traceReads)
    }

    @Test
    fun `reports from before an app update are skipped`() {
        updatedAt = 250
        exits = listOf(exit(200))
        report()
        assertTrue(reports.isEmpty())
    }

    @Test
    fun `undecodable traces produce a compact diagnostic once without the dump or current stack`() {
        val invalidTraces =
            listOf(
                "",
                "Future ANR format with private diagnostic data",
                "\"main\" prio=5 tid=1 Waiting\n  frame com.example.NewFormat::blockingCall",
                "x".repeat(256 * 1024 + 1),
            )
        for ((index, trace) in invalidTraces.withIndex()) {
            val undecodable = exit(200L + index * 100, trace = trace)
            exits = listOf(undecodable)
            report()
            report()
            assertEquals(1, undecodable.traceReads)
        }

        assertEquals(invalidTraces.size, reports.size)
        for (report in reports) {
            val exception = assertIs<AnrReportException>(report)
            assertEquals(
                "Previous ANR: unable to decode main-thread trace.\nOther report metadata describes this reporting launch.",
                exception.message,
            )
            assertTrue(exception.stackTrace.isEmpty())
            assertNull(exception.cause)
            assertTrue(exception.stackTraceToString().length < 256)
        }
        assertContentEquals(
            listOf(200, 300, 400, 500).map { "Previous ANR: process exited at 1970-01-01T00:00:00.${it}Z." },
            reportMetadata,
        )
    }

    @Test
    fun `missing and unreadable traces do not produce reports`() {
        exits = listOf(exit(200, trace = null))
        report()
        val unreadable = exit(600)
        unreadable.traceFailure = IOException("unavailable")
        exits = listOf(unreadable)
        assertFailsWith<IOException> { report() }
        report()
        assertEquals(1, unreadable.traceReads)
        assertTrue(reports.isEmpty())
    }

    @Test
    fun `an exit exposed after an empty scan is still reported`() {
        exits = emptyList()
        report()
        exits = listOf(exit(200))
        report()
        assertEquals(1, reports.size)
    }

    @Test
    fun `a failed processed marker prevents reading or reporting the trace`() {
        state.canCommit = false
        val latest = exit(200)
        exits = listOf(latest)

        report()

        assertEquals(0, latest.traceReads)
        assertTrue(reports.isEmpty())
        assertEquals(0L, state.lastProcessedTimestamp)
    }

    private fun report() =
        processPreviousAnr(state, PACKAGE_NAME, updatedAt, { exits }) { report, metadata ->
            reports.add(report)
            reportMetadata.add(metadata)
        }

    private fun exit(
        timestamp: Long,
        isAnr: Boolean = true,
        process: String = PACKAGE_NAME,
        trace: String? = TRACE,
    ) = TestExit(timestamp, isAnr, process, trace)
}

private class TestAnrState : PreviousAnrReportState {
    override var lastProcessedTimestamp = 0L
    var canCommit = true

    override fun markProcessed(timestamp: Long): Boolean {
        if (!canCommit) return false
        lastProcessedTimestamp = timestamp
        return true
    }
}

private class TestExit(
    override val timestamp: Long,
    override val isAnr: Boolean,
    override val processName: String,
    private val trace: String?,
) : PreviousAnrExit {
    var traceReads = 0
    var traceFailure: IOException? = null

    override fun openTrace(): Reader? {
        traceReads++
        traceFailure?.let { throw it }
        return trace?.reader()
    }
}

private const val PACKAGE_NAME = "com.ichi2.anki"

private val MAIN_STACK_TRACE =
    arrayOf(
        StackTraceElement("java.util.concurrent.CountDownLatch", "await", "CountDownLatch.java", 230),
        StackTraceElement("com.example.AnrActivity", "blockMainThread", "AnrActivity.kt", 58),
    )

private val TRACE =
    """
    Subject: Input dispatching timed out

    "main" prio=5 tid=1 Waiting
      at java.util.concurrent.CountDownLatch.await(CountDownLatch.java:230)
      at com.example.AnrActivity.blockMainThread(AnrActivity.kt:58)

    "worker" prio=5 tid=2 Runnable
      at com.example.Worker.run(Worker.kt:10)
    """.trimIndent()
