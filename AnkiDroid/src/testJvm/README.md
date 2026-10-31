# JVM unit tests

Tests in `java/` execute without the Robolectric runtime. Name each test file after its top-level test class; nested test classes and file-local helpers can share that file.

The standard `:AnkiDroid:testPlayDebugUnitTest` command runs both JVM and Android unit tests. Its `--tests` filters apply across both groups, and an unmatched filter fails.

Run only the JVM group with `:AnkiDroid:testPlayDebugJvmUnitTest`. Other debug flavors have corresponding tasks.

Use JUnit 4 in `src/test/java` for tests that need Robolectric, Android lifecycle behavior, or Android resources. The JVM group supports JUnit 4 and Jupiter; put Jupiter tests in `src/testJvm/java`. JVM tests may mock Android types supplied by the SDK stubs.
