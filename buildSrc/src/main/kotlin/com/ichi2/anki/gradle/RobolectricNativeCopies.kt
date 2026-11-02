// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.testing.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Only the app test runtime opts in; production artifacts and other dependencies are untouched. */
fun Project.configureRobolectricNativeCopies() {
    dependencies.registerTransform(NativeCopyTransform::class.java) {
        from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
        to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "ankidroid-native-file-copies")
    }
    configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
        val variant = name.removeSuffix("UnitTestRuntimeClasspath").replaceFirstChar(Char::uppercaseChar)
        val testTaskName = "test${variant}UnitTest"
        val optimized = incoming.artifactView {
            componentFilter {
                it is ModuleComponentIdentifier && it.group == "org.robolectric" && it.module == "nativeruntime"
            }
            attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "ankidroid-native-file-copies")
        }.files
        // Register after AGP has supplied its task classpath, so its initialization cannot
        // replace this artifact view. JVM-only groups derive their filtered classpath from it.
        afterEvaluate {
            tasks.withType(Test::class.java).configureEach {
                if (name == testTaskName) {
                    val original = classpath
                    val runtimeName = "nativeruntime-${libsVersionFor("robolectric")}.jar"
                    classpath = files(optimized, original.filter { it.name != runtimeName && it.name != "jetified-$runtimeName" })
                }
            }
        }
    }
}

/**
 * Robolectric 4.17's private copy methods always stream through Guava's small buffer. Route only
 * their source/sink factories to a JVM helper, which uses Files.copy for fresh cached file copies.
 * Keep the loader, private extraction directories, JNI registration and every resource unchanged.
 */
@CacheableTransform
abstract class NativeCopyTransform : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    @get:Classpath
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        val destination = outputs.file(input.name)
        ZipFile(input).use { source ->
            val entries = source.entries().asSequence().toList()
            check(entries.none { it.name.matches(Regex("META-INF/[^/]+\\.(SF|RSA|DSA|EC)", RegexOption.IGNORE_CASE)) }) {
                "Native resource optimization requires an unsigned Robolectric jar"
            }
            val loaderName = "org/robolectric/nativeruntime/DefaultNativeRuntimeLoader.class"
            check(source.getEntry(loaderName) != null) { "Robolectric native loader changed; review the resource copy transform" }
            ZipOutputStream(destination.outputStream().buffered()).use { output ->
                for (entry in entries) {
                    output.putNextEntry(ZipEntry(entry.name).apply { time = 0 })
                    source.getInputStream(entry).use { stream ->
                        if (entry.name == loaderName) {
                            output.write(rewriteCopyFactories(stream.readBytes()))
                        } else {
                            stream.copyTo(output)
                        }
                    }
                    output.closeEntry()
                }
            }
        }
    }

    private fun rewriteCopyFactories(bytes: ByteArray): ByteArray {
        val reader = ClassReader(bytes)
        val writer = ClassWriter(reader, 0)
        var sources = 0
        var sinks = 0
        reader.accept(object : ClassVisitor(Opcodes.ASM9, writer) {
            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?,
            ): MethodVisitor =
                object : MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    override fun visitMethodInsn(
                        opcode: Int,
                        owner: String,
                        method: String,
                        descriptor: String,
                        isInterface: Boolean,
                    ) {
                        val replacement = when {
                            opcode == Opcodes.INVOKESTATIC && owner == "com/google/common/io/Resources" &&
                                method == "asByteSource" && descriptor == "(Ljava/net/URL;)Lcom/google/common/io/ByteSource;" -> {
                                sources++
                                true
                            }
                            opcode == Opcodes.INVOKESTATIC && owner == "com/google/common/io/Files" &&
                                method == "asByteSink" && descriptor == "(Ljava/io/File;[Lcom/google/common/io/FileWriteMode;)Lcom/google/common/io/ByteSink;" -> {
                                sinks++
                                true
                            }
                            else -> false
                        }
                        super.visitMethodInsn(
                            opcode,
                            if (replacement) "com/ichi2/testutils/nativefiles/NativeResourceFiles" else owner,
                            method,
                            descriptor,
                            isInterface,
                        )
                    }
                }
        }, 0)
        // One source/sink pair each for ICU, fonts, hyphenation data and the native library.
        check(sources == 4 && sinks == 4) {
            "Robolectric native copy sites changed ($sources sources, $sinks sinks); review the resource copy transform"
        }
        return writer.toByteArray()
    }
}
