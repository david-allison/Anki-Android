// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class UnitTestCoverageWorkflowTest {
    @Test
    fun `coverage upload requires a unit test report`() {
        val workflow = File("../.github/workflows/tests_unit.yml").readText()
        val steps = workflow.split(Regex("(?m)^      - "))
        val upload = steps.first { "uses: codecov/codecov-action@" in it }
        assertTrue(
            "hashFiles('AnkiDroid/build/reports/jacoco/jacocoUnitTestReport/jacocoUnitTestReport.xml') != ''" in upload,
            "Coverage upload must be skipped when no unit-test report was generated",
        )
    }
}
