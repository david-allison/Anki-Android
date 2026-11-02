plugins {
    id("ankidroid.jvm.library")
}

val gradleTestMaxParallelForks = rootProject.extra["gradleTestMaxParallelForks"] as Int

tasks.withType<Test>().configureEach {
    // Every detector test uses JUnit 4, including the lint infrastructure's TestCase base.
    useJUnit()
    // Each worker starts its own Kotlin/UAST environment. Share that startup across classes
    // while retaining two independent workers for the detector suites.
    maxParallelForks = minOf(2, gradleTestMaxParallelForks)
}

dependencies {
    compileOnly(libs.android.lint.api)
    compileOnly(libs.android.lint)
    testImplementation(libs.hamcrest)
    testImplementation(libs.hamcrest.library)
    testImplementation(libs.junit4)
    testImplementation(libs.android.lint.api)
    testImplementation(libs.android.lint)
    testImplementation(libs.android.lint.tests)
}
