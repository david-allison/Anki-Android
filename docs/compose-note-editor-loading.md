# Compose note editor loading: before and after

**Browser field loading: 80 → 63 ms (20% faster). Android startup: no consistent
improvement established yet.**

Latest comparison: the previous inline-shell/scoped-CSS implementation versus
the standalone fields entry with fewer startup bridge calls.

| Measurement | Before | After |
|---|---:|---:|
| Initial asset requests, browser | 52 | 8 |
| Initial JavaScript | 607 KB | 401 KB |
| Fields loaded, browser median | 79.5 ms | 63.2 ms |
| Fields loaded, browser median at 6× CPU throttle | 456.1 ms | 356.6 ms |
| Native session ready, cold Android process | 1,180–2,056 ms | 1,464–1,532 ms |
| Native session ready, repeat Android opening | 500–1,058 ms | 601–936 ms |

Browser timings start at navigation and use five fresh processes per case.
Android ranges contain two emulator samples each and start at Activity entry:
the new build won the first pair and lost the second. These are readiness
milestones, not tap-to-visible timings. **The 100 ms Android target is not yet
demonstrated; the Pixel has not been measured.** Details and earlier iterations
follow below.

## Investigation context

Recorded 2026-10-08; investigation continued 2026-10-09. The target is roughly **100 ms to usable fields**, with a smooth
keyboard transition. The current measurements do not establish an architectural
lower bound or show that prewarming is necessary. In particular, **500 ms before
DOMContentLoaded is not evidence of 500 ms spent constructing the DOM**.

The user observed first-opening lag on a Pixel 9 Pro. All measurements below were
taken on an emulator or desktop Chromium; the phone was not used.

## Android measurements

Environment: `emulator-5582`, ARM64, Android 14/API 34, WebView 154.0.8037.106.
The APK was `playDebug`, with code coverage enabled and no R8 optimization.
LeakCanary disabled itself under JUnit. The debug results below predate the
optimized measurements described later in this document.

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

## Baseline: what the HTML waited for

Before inlining the shell and scoping CSS, generated HTML was 2,900 bytes before
native injection. Its order was:

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

That root CSS has no runtime `@import` or `@font-face` chain; its URLs are inline
SVG data. The large CSS files and early blocking asset requests were concrete
investigation targets, rather than evidence that HTML must be slow.

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

## Optimized Android follow-up: check the renderer first

The first R8 `playBenchmark` measurements used an isolated
`com.ichi2.anki.composeperf` installation, without coverage or instrumentation.
Temporary native/browser markers measured Activity entry, WebView construction,
asset interception/reads, field loading, draft storage, and bridge replies.
The APK used default sideload compilation; no AOT compilation or profile-install
command was run.

**The emulator was using software graphics.** Its effective launch log selected
`lavapipe` for Vulkan and `swangle` for GLES, with the SwiftShader adapter. The
same frame records show little recorded UI work but long rendering queues. These
results identify investigation targets; they do not characterize a Pixel's GPU
or establish that native composition is the bottleneck.

In the first fresh Add, actual field loading took 44 ms. By contrast, there was a
458 ms gap between the HTML stream reaching EOF and interception of the tiny
shell stylesheet. Reading that stylesheet's stream took less than 1 ms. The
first-ever IndexedDB open took 367 ms. These are distinct delays, not DOM parsing.

Later fresh Add runs with an existing database reached native session readiness
in 2,233–2,903 ms from Activity entry. Keyboard loops recorded a 113 ms median
frame, while measured JavaScript and layout work across those loops totalled only
about 5 ms. Rendering must be remeasured with hardware acceleration before using
these numbers to justify further app changes.

The visual-state callback records that submitted WebView content is ready for a
subsequent draw; it does not timestamp the first presented fields. A subsequent
natural draw may not occur if the screen has already settled. The probe never
forces a repaint to manufacture that milestone.
[Optimized software-rendering evidence](../AnkiDroid/build/reports/compose-editor/performance-baseline-staged/).

## Reducing blocking requests and styles

The implemented changes inline the trusted 104-byte CSS and small bridge script in the
HTML response, preserving the nonce policy and their position before Svelte
starts. The files remain separate source assets. This removes two parser-blocking
intercepted requests. The native screen also uses window width directly for its
tablet threshold, removing a layout subcomposition used only to obtain that width.

The rebuilt HTML is 2,828 bytes before injection, retains 20 preloads totalling
222,607 bytes, and has no root stylesheet link.

The backend now supplies a small field-specific stylesheet. Other pages await
their existing full stylesheet in the root route loader. Shared upstream field
components, rich-text shadow styles, CodeMirror, and MathJax remain unchanged.

Paired browser measurements alternate five fresh Chromium processes per variant
and CPU setting; timings in the table are medians. These measure the CSS change; they **do not** include the native
inline-shell change.

| Measurement | Before scoped CSS | After scoped CSS |
|---|---:|---:|
| Requested CSS | 265,655 B | 25,733 B |
| Total route assets | 872,200 B | 632,479 B |
| DCL, no throttle | 14.3 ms | 7.7 ms |
| DCL, 6× CPU throttle | 99.9 ms | 48.5 ms |
| Fields loaded since navigation, no throttle | 85.8 ms | 85.7 ms |
| Fields loaded since navigation, 6× throttle | 516.0 ms | 511.3 ms |

This is a substantial reduction in CSS and parser blocking, but **not a measured
end-to-end browser loading improvement**. Twelve browser tests pass, including
source mode, RTL, collapsed fields, scroll shadows, and navigation to another
page while its stylesheet is delayed. Twenty-three computed-style selections
match, and light/dark rich/source screenshots are byte-identical before/after.
[CSS evidence and reproduction details](../build/reports/compose-editor/performance-css/README.md).

Repeating the unchanged optimized Android APK with host GPU rendering (Apple M1
Max/Metal, Vulkan disabled) reduced the keyboard sample's median/p95 frame times
from 113/250 ms to 40/81 ms. Startup was still highly variable: cold fresh Add
3,182/5,162 ms; repeat fresh Add 2,546/721 ms to native readiness. These are
**environment comparisons, not gains from the code changes**. The first run
immediately after emulator boot timed out and was excluded explicitly.
[Hardware baseline evidence](../AnkiDroid/build/reports/compose-editor/performance-baseline-hardware/).

## Combined changes on the hardware-rendered emulator

Before/after diagnostic `playDebug` APKs use the same isolated package, with
coverage and LeakCanary disabled. They were alternated on the same emulator with
host GPU rendering and an initialized draft database. Each valid sample opened
a fresh seeded Add, rather than restoring a draft. All timings below are in ms.
DCL/API readiness are relative to WebView navigation; native session readiness
is relative to Activity `onCreate`, not the user's tap or a presented frame.

| Opening | DCL before → after | API ready before → after | Native ready before → after |
|---|---:|---:|---:|
| Comparable cold process | 429 → 203–245 | 568 → 536–553 | 1,319 → 1,221–1,248 |
| Same-process repeat | 167 → 90–117 | 267 → 242–247 | 444 → 509–677 |

Each row shows one comparable baseline sample and two updated samples, not
population statistics. Seven valid samples are preserved in the evidence. The
additional first baseline cold sample took 9,308 ms, including 3,459 ms before
navigation began; it is recorded separately rather than attributing that whole
stall to HTML. One attempted warm baseline reused the existing Activity and is
invalid. One updated warm capture required reattaching DevTools to the current
WebView; its existing timeline/native log were recovered without relaunching.

**The HTML loading stage improved; consistent overall startup improvement has
not been established.** The warm result in particular does not justify a claim
that the UX is now faster. These emulator samples also do not establish phone
performance or attainment of the 100 ms target.

The final clean debug APK then passed an Add smoke test: two real Anki fields,
expected seeded text, and no temporary `editor:*` timing marks.
[Paired timelines, reproduction scripts, and final smoke evidence](../AnkiDroid/build/reports/compose-editor/performance-emulator-debug-paired/).

## Validation and phone handoff after the CSS changes

The combined native/bootstrap and backend-style changes pass:

- 8 WebView instrumentation tests, including source mode, Japanese IME,
  unchanged HTML, draft recovery, and script isolation.
- 5 editor Activity tests on the phone-sized emulator.
- 1 tablet preview test; initial front, updated front, and updated answer are
  correct in actual-display screenshots with the keyboard visible.
- 12 backend browser tests and a full Svelte type check with no errors/warnings.

[Android test evidence](../AnkiDroid/build/reports/compose-editor/loading-optimization-validation/).
Temporary timing code has been removed from production source; diagnostic APKs
and capture scripts remain in ignored report directories.

The user subsequently permitted the Pixel **only with a debug APK**, and forbade
phone system-settings changes. Baseline and updated `playDebug` APKs were built
for the isolated package `com.ichi2.anki.composeperfphone`, labelled “AnkiDroid
Editor Perf”. APK manifests verify `debuggable=true`; coverage and LeakCanary
are disabled consistently in both builds through an archived local Gradle init
script. The existing AnkiDroid package and collection are outside this probe.

USB still reports the physical device as unauthorized, so **no phone APK has
been installed or measured**, and no phone system settings have been changed.
Prepared phone scripts contain no `settings put`, `setprop`, `wm`, keyboard/IME
configuration, or global log clearing. A before/after phone comparison remains
pending USB authorization. Do not present the CSS byte reduction, browser DCL
improvement, or emulator GPU change as a measured phone startup speedup or as
meeting the 100 ms target.
[Phone baseline artifacts](../AnkiDroid/build/reports/compose-editor/performance-phone-baseline/)
and [updated diagnostic APK](../AnkiDroid/build/reports/compose-editor/performance-phone-after/).

## Standalone fields entry and fewer startup bridge calls

The next change mounts the **same upstream Anki fields page** through a 19-line
entry script, without starting the SvelteKit router. A separate Vite build can
also remove generated translation/backend exports used only by other pages.
Native metadata, saving, preview, and recovery ownership remain unchanged.
The backend capability marker adds `entryPoint: "editor-fields.html"`; Android
defaults to `index.html` for older compatible backend bundles.

Five fresh browser processes per variant and CPU setting, alternating variants,
produce these medians. They isolate the backend entry change and do not include
the concurrent Android bridge changes:

| Measurement | Previous route | Standalone fields |
|---|---:|---:|
| Initial asset requests | 52 | 8 |
| Initial JavaScript | 606,746 B | 401,076 B |
| Total initial assets | 632,479 B | 426,806 B |
| API ready, no throttle | 61.1 ms | 42.6 ms |
| Fields loaded since navigation, no throttle | 79.5 ms | 63.2 ms |
| API ready, 6× CPU throttle | 314.1 ms | 194.1 ms |
| Fields loaded since navigation, 6× throttle | 456.1 ms | 356.6 ms |

Complete field loading improves about **20–22% in this browser comparison**.
The gain comes from bootstrap: field-load API duration itself did not improve
(16.2 → 17.7 ms unthrottled; 135.2 → 164.6 ms at 6×). These are navigation/API
milestones, not the first presented frame or Android input readiness.

DCL is no longer a comparable milestone: the previous inline bootstrap starts
dynamic imports that DCL does not await; the new module script executes before
DCL. DCL therefore moves later (7.4 → 26.5 ms unthrottled) even while fields load
sooner. Optimizing this DCL number would be misleading.

The separate build adds approximately **934 KiB of compressed AAR assets**, mostly
duplicate lazy MathJax and CodeMirror chunks retained for compatibility with other
SvelteKit pages. Those engines still do not load for plain fields. Sharing their
build outputs is a possible packaging improvement, not a startup requirement.

The production build and Svelte typecheck pass. The shared browser suite passes
23 tests with one intentional skip for navigation owned by the native host.
All 23 computed-style selections match; four light/dark × rich/source screenshot
pairs are byte-identical.
[Standalone source, browser evidence, and packaging breakdown](../build/reports/compose-editor/performance-standalone/README.md).

Android also removes two unnecessary startup bridge calls:

- A newly allocated draft UUID skips its first recovery lookup. The decision is
  consumed before any web request, so a canceled attachment or a saved-instance
  ID still requires recovery on the next attachment.
- Loading fields and creating their draft use one bridge request, which completes
  only after the IndexedDB transaction commits. The native recovery pointer still
  commits before autofocus. Restored drafts retain their original baseline and
  native metadata.

Validation for this iteration passes: 29 focused draft-ID/ViewModel unit tests,
9 WebView instrumentation tests, 5 editor Activity tests on the phone-sized
emulator, and the tablet preview test. Actual-display screenshots confirm rich
text, source HTML, and initial/updated front/answer previews with the keyboard
open. Temporary timing code has been removed from production source.
[Android tests, screenshots, diagnostic APK, and timing captures](../AnkiDroid/build/reports/compose-editor/performance-standalone-android/).

The Android comparison alternates the previous inline-shell/scoped-CSS APK with
the new standalone-entry/startup-call APK twice. Both are isolated debug builds
without coverage or LeakCanary, on the same hardware-rendered emulator. Each
opening has a new Activity and WebView target with verified unique seeded fields;
the driver closes through the native toolbar and confirms detachment before the
next opening. The database already exists; these are fresh notes, not restored
drafts. No builds or other performance captures ran during measurement.

| Opening | API ready since navigation, previous → new | Native session ready since Activity entry, previous → new |
|---|---:|---:|
| Pair 1, cold process | 1,085 → 600 ms | 2,056 → 1,464 ms |
| Pair 1, same-process repeat | 535 → 327 ms | 1,058 → 601 ms |
| Pair 2, cold process | 463 → 551 ms | 1,180 → 1,532 ms |
| Pair 2, same-process repeat | 243 → 345 ms | 500 → 936 ms |

All eight captures are valid. **The second pair reverses the first pair's timing
advantage: a consistent Android startup improvement is not established.** The
new APK consistently requests far fewer resources (9–12 vs 53–54), but time
before navigation and field-loading duration vary substantially. Intercepted
resource durations can overlap and must not be summed as elapsed loading time;
their reported byte counts are zero, so byte comparisons above come from the
controlled browser capture. Session readiness includes native checkpoint
coordination and autofocus, not keyboard-animation completion or a presented
frame. Neither these samples nor the browser gains meet or disprove the 100 ms
Android target. The Pixel remains USB-unauthorized and has not been used.

The final clean debug APK also passes an Add smoke check: visible editable fields
contain the expected seed, the standalone assets load, and diagnostic timing
marks are absent. This checks readiness, not launch latency.
[Clean debug APK](../AnkiDroid/build/reports/compose-editor/final-debug/AnkiDroid-Editor-Perf-debug.apk)
and [final smoke evidence](../AnkiDroid/build/reports/compose-editor/performance-standalone-android/final-smoke/).

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

## Next measurements

The shell requests and oversized CSS have now been removed; full asset timing
and native interception/stream markers have also been captured. Remaining work
should follow measurements rather than assume that DOM construction, Compose,
or recovery is the dominant cost:

1. **Compare on the Pixel after USB authorization.** Use the two isolated debug
   APKs with identical diagnostic/build settings. Separate first-ever database
   creation, a fresh Add after process restart, a repeated Add, and draft recovery.
   Keep phone system settings unchanged.
2. **Attribute renderer scheduling gaps.** The stream reads are short, but gaps
   before interception and native reply delivery can be large on the emulator.
   Capture `devtools.timeline,v8,blink.user_timing,loading,toplevel` before
   navigation if the phone reproduces them. Distinguish execution from waiting.
3. **Investigate the remaining JavaScript only if it dominates.** The standalone
   entry reduces it to about 401 KB. Bootstrap improved in the browser, while
   field loading itself did not; use Android stage timings to choose the next step.
4. **Measure necessary startup storage separately from bridge trips.** The fresh
   lookup skip and combined field load/checkpoint remove two bridge requests.
   They do not avoid opening IndexedDB or committing the recovery record. Measure
   these remaining costs before changing their scheduling or durability. The
   latest candidate spends 11–60 ms opening the existing database after loading
   fields. Opening it concurrently is a possible follow-up; error handling must
   still await field initialization and the durable checkpoint before readiness.
5. **Assess lifecycle changes last.** Prewarming/reuse may help, but adds
   ownership/memory costs. These measurements do not establish it as necessary
   or establish a 100 ms architectural lower bound.

## Local evidence

- [Android baseline output and reproduction probe](../AnkiDroid/build/reports/compose-editor/performance-baseline/)
- [Android updated output and summaries](../AnkiDroid/build/reports/compose-editor/performance-after/)
- [Browser baseline](../AnkiDroid/build/reports/compose-editor/performance-after/anki-fields-boot-baseline.json)
- [Browser updated](../AnkiDroid/build/reports/compose-editor/performance-after/anki-fields-boot-lazy.json)

These artifact directories are ignored build output. Temporary native/JavaScript
timing patches are archived there, not enabled in production source. An earlier
optimized tracing attempt hit lint checks; the subsequent successful capture and
environment findings are recorded above. Validation counts are recorded with
each iteration so that results are not attributed to a later untested change.
