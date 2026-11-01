// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.content.Context
import io.mockk.every
import kotlin.reflect.KClass

/** Keep the SDK's return type when a JVM mocking framework records an erased service lookup. */
fun <T : Any> Context.mockSystemService(
    type: KClass<T>,
    service: T,
) {
    every { this@mockSystemService.hint(type).getSystemService(type.java) } returns service
}
