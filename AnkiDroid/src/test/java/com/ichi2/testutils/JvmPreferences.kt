// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.content.res.Resources
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.settings.PrefsRepository
import io.mockk.every
import io.mockk.mockk

/** Isolated preferences with stable resource keys; resource contents are tested with Android. */
internal fun jvmPreferences(): PrefsRepository {
    val resources = mockk<Resources>()
    every { resources.getString(any()) } answers { firstArg<Int>().toString() }
    return PrefsRepository(SPMockBuilder().createSharedPreferences(), resources)
}
