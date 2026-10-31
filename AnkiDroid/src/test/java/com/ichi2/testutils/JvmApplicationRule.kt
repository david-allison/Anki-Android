// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.app.Application
import android.content.SharedPreferences
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.common.android.ApplicationContextInitializer
import io.mockk.every
import io.mockk.mockk
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Scope application-context access and named preference files without starting Android. */
class JvmApplicationRule : TestRule {
    val application = mockk<Application>()
    private val preferences = mutableMapOf<String, SharedPreferences>()

    init {
        every { application.getSharedPreferences(any(), any()) } answers {
            preferences.getOrPut(firstArg()) { SPMockBuilder().createSharedPreferences() }
        }
    }

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val previous = ApplicationContextInitializer.instanceOrNull as Application?
                try {
                    ApplicationContextInitializer.setInstance(application)
                    base.evaluate()
                } finally {
                    if (previous == null) {
                        ApplicationContextInitializer.clearForTesting()
                    } else {
                        ApplicationContextInitializer.setInstance(previous)
                    }
                }
            }
        }
}
