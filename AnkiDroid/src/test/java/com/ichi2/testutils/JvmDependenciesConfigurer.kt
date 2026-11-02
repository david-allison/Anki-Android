// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import anki.generic.Empty
import kotlinx.serialization.KSerializer
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
            // Generated contracts, wire messages and preview strings use only JVM values.
            // Stateful backends and the Android translation provider stay sandbox-specific.
            builder.doNotAcquirePackage("com.google.protobuf.")
            generatedBackendClasses.forEach(builder::doNotAcquireClass)
            // Core uses ClassValue/soft-reference serializer caches on the host JVM. JSON's
            // descriptor caches and each app's generated serializers remain SDK-specific.
            serializationCoreClasses.forEach(builder::doNotAcquireClass)
            // Mockito accepts the sandbox's Class objects; its JVM engine need not be reloaded.
            builder.doNotAcquirePackage("org.mockito.")
            builder.doNotAcquirePackage("io.mockk.")
            builder.doNotAcquirePackage("org.jsoup.")
            builder.doNotAcquirePackage("com.google.common.")
        }

        companion object {
            private val generatedBackendClasses: List<String> =
                classesInPackage(Empty::class.java, "anki/")
            private val serializationCoreClasses: List<String> =
                classesInPackage(KSerializer::class.java, "kotlinx/serialization/")

            private fun classesInPackage(
                type: Class<*>,
                packagePath: String,
            ): List<String> =
                File(
                    requireNotNull(
                        type.protectionDomain
                            ?.codeSource
                            ?.location,
                    ).toURI(),
                ).let { source ->
                    val entries =
                        if (source.isDirectory) {
                            File(
                                source,
                                packagePath,
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
                        .filter { it.startsWith(packagePath) && it.endsWith(".class") }
                        .map { it.removeSuffix(".class").replace('/', '.') }
                }
        }
    }
