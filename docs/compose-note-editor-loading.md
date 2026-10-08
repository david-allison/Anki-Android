# Compose note editor loading: measurements and next experiments

Recorded 2026-10-08. The target is roughly **100 ms to usable fields**, with a smooth
keyboard transition. The current measurements do not establish an architectural
lower bound or show that prewarming is necessary. In particular, **500 ms before
DOMContentLoaded is not evidence of 500 ms spent constructing the DOM**.

The user observed first-opening lag on a Pixel 9 Pro. All measurements below were
taken on an emulator or desktop Chromium; the phone was not used.

## Android measurements

Environment: `emulator-5582`, ARM64, Android 14/API 34, WebView 154.0.8037.106.
The APK was `playDebug`, with code coverage enabled and no R8 optimization.
LeakCanary disabled itself under JUnit. No optimized Android timings exist yet.

The probe opens a synthetic Basic note, observes editable field DOM, and requests
a snapshot. It then types into the note, closes, and reopens the editor in the same
process, restoring that draft. The second opening is not another empty-note load.
Application/provider setup has already occurred under instrumentation; “first
opening” does not mean a cold launch of the entire app.

| Run | First opening observed ready | Reopening observed ready |
|---|---:|---:|
| Before, pair A | 3,982 ms | 2,349 ms |
| Before, pair B | 3,396 ms | 2,268 ms |
| After, pair A | 2,941 ms | 1,815 ms |
| After, pair B | 3,173 ms | 1,456 ms |

**These are coarse observation times, not a breakdown of rendering work.**
`ActivityScenario.launch()` waits for UI idleness, and subsequent checks poll.
Its return time must not be labelled “native screen construction time”. Likewise,
observing a WebView after that wait does not time its constructor. The measurements
do not identify the exact first displayed frame or include a separate input-readiness test.

Navigation Timing gives a more precise, narrower view. These are timestamps since
navigation began, not durations that can be added to the table above:

| First-opening run | HTML response complete | DOM interactive | DOMContentLoaded | HTML complete → DCL |
|---|---:|---:|---:|---:|
| Before, pair B | 122.1 ms | 866.7 ms | 866.8 ms | 744.7 ms |
| After, pair A | 175.0 ms | 649.0 ms | 649.7 ms | 474.7 ms |
| After, pair B | 41.7 ms | 952.7 ms | 952.8 ms | 911.1 ms |

The delay is predominantly **before parsing completes**, rather than between
`domInteractive` and dispatch of `DOMContentLoaded`. This does not tell us whether
the parser was running, waiting for an asset, or unable to get CPU time.

Individual bundled resources also have unexpectedly large elapsed durations:

| Resource | After A, first / reopen | After B, first / reopen |
|---|---:|---:|
| `editor.css` — only 104 bytes | 56.2 / 35.3 ms | 451.7 / 3.0 ms |
| `editor.js` | 55.7 / 39.5 ms | 450.4 / 2.8 ms |
| Root stylesheet | 163.8 / 158.4 ms | 88.6 / 94.4 ms |

These are local, intercepted requests, with no DNS or connection setup. They are
**not measured disk-read or JavaScript execution times**. Existing resource records
omitted start timestamps, so they cannot reconstruct the waterfall. Requests can
overlap; adding their durations would be wrong.

## What the HTML actually waits for

The generated HTML is 2,900 bytes before native injection. Its order is:

| Order | Resource | Size, uncompressed | Relationship to DCL |
|---|---|---:|---|
| 1 | Injected `editor.css` | 104 B | Can hold the following classic script |
| 2 | Injected `editor.js` | About 16 KB | Parser-blocking fetch and execution |
| 3 | 20 script preload links | 222,406 B total | Fetches; no direct DCL dependency, but possible contention |
| 4 | Root stylesheet | 147,657 B | Can hold the following inline classic script |
| 5 | Inline bootstrap | Included in HTML | Starts dynamic imports, then returns |
| Later | Field component stylesheet | 115,022 B | Requested during dynamic editor initialization |

The inline bootstrap calls:

```js
Promise.all([import(start), import(app)]).then(([kit, app]) => kit.start(app, element));
```

The parser does **not** await that promise or the complete Svelte/field bootstrap.
Module work can compete for the renderer thread, but is not itself a prerequisite
for DCL here. Classic scripts can wait for preceding stylesheets.
[DOMContentLoaded semantics](https://developer.mozilla.org/en-US/docs/Web/API/Document/DOMContentLoaded_event).

The root CSS has no runtime `@import` or `@font-face` chain; its URLs are inline
SVG data. The large CSS files and early blocking asset requests are concrete
investigation targets, rather than a reason to assume HTML must be slow.

## Work already reduced

MathJax and CodeMirror now load when needed. Plain fields request only editing and
keyboard translations, instead of all translations followed by a second request.
Native controls no longer recompose for every field revision, and caret-only
events no longer rewrite unchanged drafts.

Controlled browser results: medians of five fresh Chromium processes per case,
using English translation fixtures. These are **not Android or Pixel timings**.

| Measurement | Before | After |
|---|---:|---:|
| Initial JavaScript | 3,129,182 B | 606,545 B |
| Initial route resources | 3,403,063 B | 872,200 B |
| API ready, no CPU throttle | 117 ms | 54 ms |
| Actual field-loading call, no throttle | 16.6 ms | 15.7 ms |
| Fields loaded, no throttle, from navigation | 138 ms | 73 ms |
| API ready, 6× CPU throttle | 686 ms | 293 ms |
| Actual field-loading call, 6× throttle | 143 ms | 128 ms |
| Fields loaded, 6× throttle, from navigation | 818 ms | 422 ms |

Medians of stages do not necessarily sum to the median of the whole run. The
unthrottled results particularly caution against calling a 100 ms target impossible.

Keyboard animation remains unresolved. Most updated debug/emulator samples
improved from roughly 77–81 ms median frames to 65 ms, but one was 105 ms.
Those are still poor frame times. Large native traversal intervals were recorded;
without a call-stack trace, they cannot all be attributed to Compose. During the
earlier keyboard cycles, measured JavaScript work was only about 2 ms total.

## Tiny capture script

Open an empty editor normally on the disposable emulator. In that WebView's
DevTools console, run this after it loads; `copy` is a DevTools console helper:

```js
copy(JSON.stringify({
  timeOrigin: performance.timeOrigin,
  observedAt: performance.now(),
  navigation: performance.getEntriesByType("navigation").map(e => e.toJSON()),
  resources: performance.getEntriesByType("resource").map(e => e.toJSON()),
  marks: performance.getEntriesByType("mark").map(e => e.toJSON()),
}, null, 2));
```

Save the output with the APK revision, build type, WebView version, and whether
this was the first opening in the process. Preserve full entries, especially
`startTime`, `requestStart`, `responseStart`, and `responseEnd`.
Reading this data does not reload or edit the note. Reloading an already attached
editor would measure page bootstrap alone and would not rerun native note binding.
The script captures elapsed milestones; it does not provide CPU attribution.

## Optimize one piece at a time

1. **Explain the slow tiny asset requests.** Capture the full waterfall and native
   interception entry/return plus stream first-read/EOF for `editor.css`,
   `editor.js`, and the root CSS. Measure stream reads too: `AssetManager.open()`
   alone is insufficient. Align browser/native clocks before subtracting times.
2. **Test the shell independently.** Start with a tiny static local page, then add
   the shell CSS, bridge script, root stylesheet, and Svelte bootstrap individually.
   Keep the same WebView and asset-serving mechanism. Compare first and repeated
   navigation separately. This isolates the HTML path from Compose/note setup.
3. **Remove avoidable blocking requests.** First try inlining the 104-byte CSS;
   separately test inlining the small bridge script. Preserve bridge/fetch-shim
   installation before Svelte starts. Measure each change before combining them.
4. **Reduce field-page CSS and preload scope.** Measure CSS coverage and determine
   which of the global stylesheet and 20 preloads the field-only page needs.
   Check rich editing, source mode, MathJax, themes and viewport sizing afterward.
5. **Measure the remaining Android stages in an optimized build.** Record Activity
   entry, collection/note loading, WebView constructor, page/API ready, IndexedDB
   restore, field mounting, initial checkpoint, first displayed fields, and IME
   request/animation. Separate overlapping stages and test-observation overhead.
6. **Only then assess lifecycle changes.** Empty-page prewarming or reuse may help,
   but introduces ownership/memory costs. It is not yet a measured requirement.

If timing gaps remain inside the renderer, capture a trace before navigation with
`devtools.timeline,v8,blink.user_timing,loading,toplevel`. Inspect parsing,
stylesheet processing, script compilation/execution and scheduling separately.

## Local evidence

- [Android baseline output and reproduction probe](../AnkiDroid/build/reports/compose-editor/performance-baseline/)
- [Android updated output and summaries](../AnkiDroid/build/reports/compose-editor/performance-after/)
- [Browser baseline](../AnkiDroid/build/reports/compose-editor/performance-after/anki-fields-boot-baseline.json)
- [Browser updated](../AnkiDroid/build/reports/compose-editor/performance-after/anki-fields-boot-lazy.json)

These artifact directories are ignored build output. Temporary native/JavaScript
timing patches are archived there, not enabled in production source. The optimized
tracing attempt hit lint checks on diagnostic logging; it produced no optimized
measurement. The browser regression suite passes nine tests; the updated debug
WebView suite passes eight, including source editing/IME and draft persistence.
