// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import android.content.Context
import androidx.core.content.edit
import anki.backend.backendError
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter
import com.ichi2.anki.common.crashreporting.CrashReporter.Companion.FEEDBACK_REPORT_KEY
import com.ichi2.anki.common.crashreporting.CrashReporter.Companion.FEEDBACK_REPORT_NEVER
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import net.ankiweb.rsdroid.exceptions.BackendSyncException.BackendSyncServerMessageException
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class AcraCrashReporterAnalyticsTest {
    private val preferences = SPMockBuilder().createSharedPreferences()
    private val context =
        mock<Context> {
            on { packageName } doReturn "com.ichi2.anki.test"
            on { getSharedPreferences(any(), any()) } doReturn preferences
        }
    private lateinit var previousReporter: CrashReporter
    private var previousSentExceptionReport = false

    @Before
    fun setUp() {
        preferences.edit { putString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_NEVER) }
        previousReporter = CrashReportService.getReporter()
        previousSentExceptionReport = AnkiDroidApp.sentExceptionReportHack
        CrashReportService.setReporter(AcraCrashReporter)
        mockkObject(Analytics)
        every { Analytics.sendAnalyticsException(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(Analytics)
        CrashReportService.setReporter(previousReporter)
        AnkiDroidApp.sentExceptionReportHack = previousSentExceptionReport
    }

    @Test
    fun `an exception carrying a sync server message is not reported`() {
        val withPii = Exception("outer", BackendSyncServerMessageException(backendError {}))

        CrashReportService.sendExceptionReport(withPii, "test", null, false, context)

        verify(exactly = 0) { Analytics.sendAnalyticsException(any(), any()) }
    }

    @Test
    fun `an ordinary exception is still reported`() {
        CrashReportService.sendExceptionReport(IllegalStateException("safe"), "test", null, false, context)

        verify(exactly = 1) { Analytics.sendAnalyticsException(any(), any()) }
    }
}
