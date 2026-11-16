# Card Browser

`:feature:browse` owns the Card Browser UI, search, columns, selection, saved searches,
and browser appearance editor. It depends on the shared modules and never on `:AnkiDroid`.

The application supplies `BrowserIntegration` at startup for navigation to other screens
and dialogs. `BrowserActivityHost` connects `CardBrowserScreen` to the application's
navigation drawer and embedded Note Editor while preserving the existing activity name,
intents, and deep links.

Run the independent browser tests with `./gradlew :feature:browse:testDebugUnitTest`.
Tests that require the application host remain in `:AnkiDroid`.
