// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import org.acra.ReportField
import org.acra.builder.ReportBuilder
import org.acra.collector.ReflectionCollector
import org.acra.config.CoreConfiguration
import org.acra.data.CrashReportData
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.json.JSONObject
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class AcraBuildConfigTest {
    @Test
    fun `only reviewed build metadata is collected`() {
        val config = mock<CoreConfiguration> { on { buildConfigClass } doReturn BuildConfig::class.java }
        val report = CrashReportData()

        ReflectionCollector().collect(ReportField.BUILD_CONFIG, mock(), config, ReportBuilder(), report)

        val buildConfig = report[ReportField.BUILD_CONFIG.name] as JSONObject
        assertThat(
            "Review new BuildConfig fields for credentials and personal data before adding them here",
            buildConfig.keys().asSequence().toList(),
            containsInAnyOrder(
                "ACRA_URL",
                "APPLICATION_ID",
                "BACKEND_VERSION",
                "BUILD_TIME",
                "BUILD_TYPE",
                "CI",
                "DEBUG",
                "ENABLE_LEAK_CANARY",
                "FLAVOR",
                "GIT_COMMIT_HASH",
                "SHOW_DONATE_LINKS",
                "VERSION_CODE",
                "VERSION_NAME",
            ),
        )
    }
}
