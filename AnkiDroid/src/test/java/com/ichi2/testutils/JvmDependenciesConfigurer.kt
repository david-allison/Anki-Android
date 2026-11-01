// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import anki.generic.Empty
import org.robolectric.config.AndroidConfigurer
import org.robolectric.internal.bytecode.InstrumentationConfiguration
import org.robolectric.internal.bytecode.Interceptors
import org.robolectric.internal.bytecode.ShadowProviders
import java.io.File
import java.util.jar.JarFile
import javax.inject.Inject

/** Reuse JVM dependencies while keeping Android types and app state in their SDK sandboxes. */
class JvmDependenciesConfigurer
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
            // Generated wire messages are immutable JVM values. Share their protobuf schemas too,
            // while leaving translations (and their Robolectric shadows) sandbox-specific.
            builder.doNotAcquirePackage("com.google.protobuf.")
            wireMessageClasses.forEach(builder::doNotAcquireClass)
            // Mockito accepts the sandbox's Class objects; its JVM engine need not be reloaded.
            builder.doNotAcquirePackage("org.mockito.")
            builder.doNotAcquirePackage("io.mockk.")
        }

        companion object {
            private val wireMessageClasses: List<String> =
                File(
                    requireNotNull(
                        Empty::class.java.protectionDomain
                            ?.codeSource
                            ?.location,
                    ).toURI(),
                ).let { source ->
                    val entries =
                        if (source.isDirectory) {
                            File(
                                source,
                                "anki",
                            ).walkTopDown().filter { it.isFile }.map { it.relativeTo(source).invariantSeparatorsPath }.toList()
                        } else {
                            JarFile(source).use { jar ->
                                jar
                                    .entries()
                                    .asSequence()
                                    .map { it.name }
                                    .toList()
                            }
                        }
                    entries
                        .filter { it.startsWith("anki/") && it.endsWith(".class") }
                        .filterNot { it.startsWith("anki/i18n/GeneratedTranslations") || it.startsWith("anki/i18n/PreviewTranslations") }
                        .map { it.removeSuffix(".class").replace('/', '.') }
                }
        }
    }
