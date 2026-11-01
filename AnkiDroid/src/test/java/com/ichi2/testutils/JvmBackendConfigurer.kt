// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import org.robolectric.config.AndroidConfigurer
import org.robolectric.internal.bytecode.InstrumentationConfiguration
import org.robolectric.internal.bytecode.Interceptors
import org.robolectric.internal.bytecode.ShadowProviders
import javax.inject.Inject

/** Share the JVM-only JNI bridge; collection handles and app state remain in each sandbox. */
class JvmBackendConfigurer
    @Inject
    constructor(
        shadowProviders: ShadowProviders,
    ) : AndroidConfigurer(shadowProviders) {
        override fun configure(
            builder: InstrumentationConfiguration.Builder,
            interceptors: Interceptors,
        ) {
            super.configure(builder, interceptors)
            // The bridge transports only primitives and byte arrays. Its native library must be
            // loaded by the same classloader, rather than copied and loaded again for every SDK.
            builder.doNotAcquireClass("net.ankiweb.rsdroid.NativeMethods")
            builder.doNotAcquirePackage("net.ankiweb.rsdroid.testing.")
        }
    }
