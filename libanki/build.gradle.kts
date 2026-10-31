import com.ichi2.anki.gradle.addAnkiBackendDependencies

plugins {
    id("ankidroid.jvm.library")
    `java-test-fixtures`
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // Project dependencies
    implementation(project(":common"))

    // Backend libraries
    addAnkiBackendDependencies(project)

    // JVM dependencies
    implementation(libs.slf4j.api)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Annotations and Android-compatible JSON types
    implementation(libs.androidx.annotation)
    compileOnly(libs.json)

    // test dependencies
    testImplementation(testFixtures(project(":common")))
    testImplementation(libs.hamcrest)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    // Preserve Android JSON coercion and null behavior without Robolectric.
    testCompileOnly(libs.json)
    testRuntimeOnly(libs.android.json)

    // testFixtures dependencies
    testFixturesImplementation(project(":common"))
    testFixturesImplementation(libs.slf4j.api)
    testFixturesImplementation(libs.junit.vintage.engine)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(libs.kotlinx.coroutines.test)
    testFixturesCompileOnly(libs.json)
}
