// SPDX-License-Identifier: GPL-3.0-or-later

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.WriteProperties
import com.ichi2.anki.gradle.StoreRobolectricSdk
import com.ichi2.anki.gradle.libsVersionFor
import com.ichi2.anki.gradle.configureRobolectricNativeCopies
import javax.inject.Inject

interface RobolectricArchiveServices {
    @get:Inject
    val archives: ArchiveOperations
}

configureRobolectricNativeCopies()

/*
 * Downloads android-all-instrumented dependencies into Gradle's cache for offline Robolectric.
 * Writes a properties file pointing to the cached jars, so worktrees do not need their own copies.
 *
 * Keep this as a convention plugin: applying a separate .gradle.kts script makes Android lint's
 * K2 build-script analyzer crash with `findFirCompiledSymbol`.
 *
 * Once applied to your gradle project, can be executed with ./gradlew robolectricSdkDownload
 */

// The general idea of this was borrowed from https://gist.github.com/xian/05c4f27da6d4156b9827842217c2cd5c
// I then modified it heavily to allow easier addition of new SDK versions
// The full implementation is from https://gist.github.com/simtel12/13ff3e57c37e78e468502b51ebb0f4f2

// List from: https://github.com/robolectric/robolectric/blob/master/robolectric/src/main/java/org/robolectric/plugins/DefaultSdkProvider.java
// This list will need to be updated for new Android SDK versions that come out.
// Note: the PREINSTRUMENTED_VERSION constant is what you put on the end of the artifact

// Only the versions currently used in AnkiDroid Robolectric tests are active, the rest are commented out
// When updating Robolectric, match these versions to DefaultSdkProvider and PREINSTRUMENTED_VERSION.
// Run `./gradlew jacocoUnitTestReport` to check that all SDKs used by the tests are included.
val robolectricAndroidSdkVersions =
    listOf(
//        mapOf("androidVersion" to "6.0.1_r3", "frameworkSdkBuildVersion" to "r1"),
        mapOf("sdk" to "24", "androidVersion" to "7.0.0_r1", "frameworkSdkBuildVersion" to "r1"),
        mapOf("sdk" to "25", "androidVersion" to "7.1.0_r7", "frameworkSdkBuildVersion" to "r1"),
//        mapOf("androidVersion" to "8.0.0_r4", "frameworkSdkBuildVersion" to "r1"),
        mapOf("sdk" to "27", "androidVersion" to "8.1.0", "frameworkSdkBuildVersion" to "4611349"),
        mapOf("sdk" to "28", "androidVersion" to "9", "frameworkSdkBuildVersion" to "4913185-2"),
        mapOf("sdk" to "29", "androidVersion" to "10", "frameworkSdkBuildVersion" to "5803371"),
        mapOf("sdk" to "30", "androidVersion" to "11", "frameworkSdkBuildVersion" to "6757853"),
        mapOf("sdk" to "31", "androidVersion" to "12", "frameworkSdkBuildVersion" to "7732740"),
        mapOf("sdk" to "32", "androidVersion" to "12.1", "frameworkSdkBuildVersion" to "8229987"),
        mapOf("sdk" to "33", "androidVersion" to "13", "frameworkSdkBuildVersion" to "9030017"),
        mapOf("sdk" to "34", "androidVersion" to "14", "frameworkSdkBuildVersion" to "10818077"),
        mapOf("sdk" to "35", "androidVersion" to "15", "frameworkSdkBuildVersion" to "13954326"),
        // TODO: check TarArchiveInputStream.forEachEntry when this is updated
        mapOf("sdk" to "36", "androidVersion" to "16", "frameworkSdkBuildVersion" to "13921718"),
        mapOf("sdk" to "37", "androidVersion" to "17", "frameworkSdkBuildVersion" to "15733970"),
    )

val robolectricSdkDownload =
    tasks.register<WriteProperties>("robolectricSdkDownload") {
        group = "Dependencies"
        description = "Downloads Robolectric SDKs and writes their cached paths for offline tests"
        destinationFile = layout.buildDirectory.file("robolectric-deps.properties")
    }

// Each sandbox copies these resources into its own native runtime directory. Keep the exact
// bundled bytes on a directory classpath entry to avoid repeatedly decompressing their jars.
val nativeRuntimeResourceIncludes = listOf("fonts/**", "icu/**", "hyphen-data/**", "native/**")
val robolectricNativeResourceCache =
    tasks.register<Sync>("robolectricNativeResourceCache") {
        group = "Dependencies"
        description = "Extracts Robolectric's bundled native resources for reuse by test sandboxes"
        into(layout.buildDirectory.dir("robolectric-native-resources"))
        inputs.property("nativeRuntimeResourceIncludes", nativeRuntimeResourceIncludes)
        // SDK-specific ICU paths must remain distinct; never replace one SDK's data with another's.
        duplicatesStrategy = DuplicatesStrategy.FAIL
    }

configurations.matching { it.name == "playDebugUnitTestRuntimeClasspath" }.all {
    val nativeRuntime =
        incoming.artifactView {
            componentFilter {
                it is ModuleComponentIdentifier && it.group == "org.robolectric" && it.module.startsWith("nativeruntime-dist")
            }
        }.files
    val backendRuntime =
        incoming.artifactView {
            componentFilter {
                it is ModuleComponentIdentifier && it.group == "io.github.david-allison" && it.module == "anki-android-backend-testing"
            }
        }.files
    // Capture the service rather than the script's Project in the lazy archive provider.
    val archives = objects.newInstance<RobolectricArchiveServices>().archives
    robolectricNativeResourceCache.configure {
        from(nativeRuntime.elements.map { artifacts -> archives.zipTree(artifacts.single().asFile) }) {
            include(nativeRuntimeResourceIncludes)
        }
        // The loader still checksums and loads its normal private/cached copy. Avoid inflating
        // these published resources again in every worker. Local backend JARs keep their own
        // resource lookup; Sync removes the published files when that artifact is absent.
        val backendLibraries = listOf("librsdroid.dylib", "librsdroid.so", "rsdroid.dll")
        inputs.property("backendNativeLibraryResourceIncludes", backendLibraries)
        from(backendRuntime.elements.map { artifacts -> artifacts.map { archives.zipTree(it.asFile) } }) {
            include(backendLibraries)
        }
    }
}

// Use separate configurations so Gradle retains every SDK version instead of selecting the newest.
robolectricAndroidSdkVersions.forEach { robolectricSdkVersion ->
    // the final part of this `-i<number>` comes from PREINSTRUMENTED_VERSION in upstream DefaultSdkProvider
    val version = "${robolectricSdkVersion["androidVersion"]}-robolectric-${robolectricSdkVersion["frameworkSdkBuildVersion"]}-i7"

    // Creating a configuration with a dependency allows Gradle to manage the actual resolution of
    // the jar file
    val sdkConfig = configurations.create(version)
    dependencies.add(version, "org.robolectric:android-all-instrumented:$version")

    // Android 15+ obtains ICU data from its SDK jar instead of nativeruntime-dist.
    if (robolectricSdkVersion.getValue("androidVersion").substringBefore('.').toInt() >= 15) {
        robolectricNativeResourceCache.configure {
            val archives = objects.newInstance<RobolectricArchiveServices>().archives
            from(sdkConfig.elements.map { artifacts -> archives.zipTree(artifacts.single().asFile) }) {
                include("icu/**")
            }
        }
    }

    val downloadedJar = layout.file(sdkConfig.elements.map { it.single().asFile })
    // Most tests use targetSdk. Prepare only that SDK to keep the extra disk space bounded.
    // Preserve android-all's filename because Robolectric also uses it to locate native resources.
    val sdkJar =
        if (robolectricSdkVersion.getValue("sdk") == libsVersionFor("targetSdk")) {
            tasks.register<StoreRobolectricSdk>("storeRobolectricDefaultSdk") {
                group = "Dependencies"
                description = "Prepares uncompressed SDK entries for reuse by test sandboxes"
                sourceJar.set(downloadedJar)
                outputJar.set(layout.buildDirectory.file("robolectric-sdks/android-all-instrumented-$version.jar"))
            }.flatMap { it.outputJar }
        } else {
            downloadedJar
        }

    robolectricSdkDownload.configure {
        inputs.file(sdkJar).withPropertyName(version)
        property(
            "org.robolectric:android-all-instrumented:$version",
            sdkJar.map { it.asFile.absolutePath },
        )
    }
}
