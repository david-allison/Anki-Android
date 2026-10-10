# Compose note editor: original 3.4–4.0 s → best 115 ms repeat / 206 ms first

**Best measured prototype · Pixel 9 Pro · release build · FAB-menu preloading**

| Editor readiness | Before preload | Best measured |
| --- | ---: | ---: |
| Repeat opening, from Activity creation | 157 ms | **115 ms** |
| First opening, from Activity creation | 305 ms | **206 ms** |
| Repeat opening, from selecting Add | 179 ms | **139 ms** |
| First opening, from selecting Add | 331 ms | **231 ms** |

**100 ms target: 15 ms remaining on the Activity-based repeat measure.**
Six alternating pairs; every pair improved for both first and repeat openings.
Readiness includes draft persistence, the native recovery pointer and autofocus.

The original 3.4–4.0 s came from an instrumented emulator; the headline results are
Pixel release medians. This is the full historical progression across different
hardware and methods, not a controlled speedup ratio. First opening means the
first editor in a fresh app process, timed after app launch.

**Touch-down follow-up:** ACTION_DOWN preparation reduces the median slowest
menu frame **46 → 14 ms first / 16 → 10 ms repeat** with normal presses.
It does not establish a new best loading time. Very short presses can still
queue behind construction: individual stress samples took **79 / 49 ms** from
release to menu draw. [Measurements and caveats](../AnkiDroid/build/reports/compose-editor/touch-preload-phone/README.md).
These are archived prototypes; the retained clean baseline remains
166 ms repeat / 328 ms first on its earlier shared-text control.

[Full preload measurements and prototype](../AnkiDroid/build/reports/compose-editor/fab-preload/README.md)
· [AndroidX WebKit investigation](compose-note-editor-webview-preload.md).

## Loading UI removed: no consistent additional speed gain

Startup Processing text and the initial tablet preview spinner are removed.
The preview retains its pane width while initializing. Errors and explicit
operation feedback remain available; initialization and readiness are unchanged.

Six alternating Pixel release pairs (24 openings), using the same ACTION_DOWN
preload in both arms, give these Add-to-usable-fields medians:

| Opening | With loading UI | Without loading UI |
| --- | ---: | ---: |
| First | 266 ms | 243 ms |
| Repeat | 146 ms | 163 ms |

First improves in four pairs; repeat improves in only two. There is **no
consistent overall speed gain**. Activity-based medians are 243 → 216 ms first
and 123 → 140 ms repeat. Keep the historical 115 / 206 ms best labelled as an
earlier result; the UI removal is not a new timing record. All field/style and
prerender checks passed, plus typing/deletion in both first-pair repeats.

The change is retained in source and a clean isolated release build, without the
archived preload/profiling hooks. Both release builds and lint passed.
[Protocol, exact diff, artifacts and results](../AnkiDroid/build/reports/compose-editor/loading-ui/README.md).

## FAB-menu preload candidate: 179 → 139 ms from Add selection

The corrected trigger is **opening the FAB menu**, before selecting Add. Six
alternating Pixel release pairs improve repeat Add-to-ready **179.28 → 138.79 ms**
and first readiness **330.60 → 231.38 ms**; all six pairs improve for both.
All 12 prerenders activate. Activity-based repeat readiness is **156.59 → 114.99 ms**,
about **15 ms above the 100 ms target**. Actual candidate menu dwell is 256–481 ms.

There is an animation tradeoff: median slowest menu frame **8 → 22 ms repeat**,
**9 → 50 ms first**, although the initial menu draw remains prompt. Native typing
and cancellation checks pass. The prototype is archived for refinement and the
clean baseline restored; these are candidate figures, not a new retained build.
The earlier late-trigger test below did not answer the intended preload question.
[Measurements, frame costs and exact source](../AnkiDroid/build/reports/compose-editor/fab-preload/README.md).

## Earlier final-Add preload: wrong trigger for the intended speculation

Six alternating Pixel release pairs confirmed full-page prerender activation in
all 12 attempts. Repeat **Add-to-ready was 172 → 176 ms**, with three pairs faster
and three slower; first-opening readiness was **322 → 326 ms**. First native draw
was later: **40 → 74 ms repeat**, **67 → 134 ms first**. Activity-only readiness
would misleadingly suggest a 27 ms saving because preparation moved before it.
Renderer-only warm-up also failed to justify retention (**179 → 177 ms repeat**).

Both temporary experiments were removed and the clean isolated release restored.
These use actual DeckPicker Add with empty fields, unlike the shared-text release
control below; compare values within each paired experiment. The later plus-menu experiment above tests the intended trigger. No system
settings or app-startup changes.
[API review and measured results](compose-note-editor-webview-preload.md).

## Pixel release control: repeat 206 → 166 ms

Six alternating pairs on the Pixel 9 Pro measured **206.01 → 166.05 ms repeat
readiness** and **606.73 → 328.01 ms first-process readiness**, debug → R8 release.
All six pairs improved for both endpoints. Repeat readiness improves **40 ms / 19%**,
leaving **66 ms** to the 100 ms target. The new debug result reproduces the previous
~205 ms; there was no screen regression. Repeat visual callbacks were **237 → 184 ms**.

These times start at editor Activity `onCreate`, not the Add tap or app launch.
Readiness still includes the draft checkpoint, native recovery pointer and initial
focus. Same phone, synthetic collection, production source and 196 identical
editor/backend assets; this measures the complete build-configuration difference.
All 24 openings are retained, including a 1,008 ms first-process debug outlier.
The clean release passed a synthetic-field/Save-enabled smoke check and remains
installed as `com.ichi2.anki.composeperfreleasecontrol`. No system settings or
`com.ichi2.anki` were touched. Temporary diagnostic hooks are absent from that APK.
[Results, controls and APK](../AnkiDroid/build/reports/compose-editor/release-phone/README.md).

## Earlier release control: emulator repeat 316 → 225 ms

The same editor in an R8-optimized, non-debuggable release build measured
**224.96 ms repeat / 432.14 ms first-process readiness**, versus **316.05 / 922.47 ms**
in debug on the same emulator. Six alternating pairs, 24 valid openings; all
196 editor/backend assets match. Repeat release is faster in five pairs and
33 ms slower in one. All samples are retained.

This confirms a large build-configuration effect, not a new source optimization.
At this stage the Pixel's 583/205 ms debug benchmark was unchanged; the later
Pixel release comparison is above. Current debug stack costs are insufficient grounds for a native
UI rewrite. The control used identical temporary milestones, with CDP validation
after the timed endpoint, and no CPU profiler or forced compilation.
[Full results and limits](../AnkiDroid/build/reports/compose-editor/release-control/README.md).

## Architecture pass: native CPU attribution and remaining opportunities

The retained traces contain **127–145 ms of main-thread CPU** before ready,
including **62–74 ms outside the first and populated frames**. Scheduling alone
cannot explain away that work, but debug overhead has not been separated from
production cost. The review identifies duplicate collection route/model reads
as the next small experiment and defines the evidence needed before a UI rewrite.
It also corrects the blank-document commit selection in two handoff traces;
benchmark readiness is unchanged.
[Full architecture pass and reproducible calculations](compose-note-editor-architecture.md).

## Toolbar vector experiment rejected: 205 → 197 ms repeat, inconsistent

Completed **six alternating Pixel pairs (24 openings)**. Reusing toolbar vectors
measured **204.91 → 197.32 ms** repeat session readiness and **234.14 → 226.65 ms**
visual readiness. The final two repeat pairs were −2.20 and +2.54 ms, effectively
flat. WebView API readiness stayed **152.95 → 153.22 ms**; cold session readiness
was **582.56 → 589.48 ms**. The small, inconsistent benefit does not justify
113 lines of duplicated icon geometry. The candidate is archived and removed.

The native populated frame remains **43–47 ms**, spread across composition,
applying changes, measurement, layout and drawing. The emulator's rendering
failures recovered after restarting its host with unchanged flags and userdata;
their underlying cause remains unresolved. Candidate and retained-build checks,
including the optional screenshot failure, are recorded in the report.

The selected APK is unchanged. **583/205 ms cold/repeat** is its latest
remeasurement; the previous **597/200 ms** came from another batch.
[Full results, caveats, candidate patch and clean restoration](../AnkiDroid/build/reports/compose-editor/native-frame/README.md).

## Exact-host Safe Browsing exception retained: 216 → 200 ms repeat

Four alternating pairs on the Pixel 9 Pro measured **216.29 → 200.37 ms** repeat
session readiness and **248.72 → 225.09 ms** visual readiness. Cold session
readiness was **622.30 → 597.47 ms**. Repeat paired deltas were −33.60, +0.77,
−11.85 and −15.88 ms: a modest improvement, with one essentially flat pair.
Registration is included: median **0.61 ms cold / 0.34 ms repeat**.

The exact-host `.appassets.androidplatform.net` allowlist removes the observed
Safe Browsing navigation hold: all three candidate traces have **zero check or
deferral events**, versus 17–68 ms holds in the earlier baseline traces. The
HTML response → renderer commit gap is now **47.46, 13.26 and 15.88 ms**; the
remaining long handoff overlaps native UI work. The traces establish the
mechanism, while the paired benchmark measures the end-to-end benefit.

The API is **app-wide**, so this exact local host also receives the exception in
other WebViews in this APK. External hosts/subdomains retain checks. HTTPS/GET
request interception, CSP, bridge origins and draft storage are unchanged;
unsupported/rejected registration falls back to normal navigation. No prewarming
or phone settings changes. Eleven emulator editor/boundary/tablet tests and
formatting passed; the final API-gated boundary test also passed separately.

**Reminder: add an issue for the measured local-asset Safe Browsing startup cost.**
Nothing has been posted. Repeat startup remains about **100 ms above the target**.

[Comparison, caveats, official API docs and artifacts](../AnkiDroid/build/reports/compose-editor/safe-browsing/README.md)
· [Original navigation hypothesis and traces](../AnkiDroid/build/reports/compose-editor/navigation-handoff/README.md).

## Deferred loading screen: rejected; repeat readiness 212 → 207 ms

Tested a background-only initial frame, building the full editor once note data
arrived and showing a spinner only if loading persisted for 300 ms. Across four
alternating pairs, repeat readiness was **211.66 → 206.93 ms**, and visual readiness
**243.01 → 238.84 ms**. One pair regressed 11 ms. Cold readiness was **583.27 →
571.15 ms**, but cold visual readiness changed only **616.77 → 615.76 ms**.

The candidate's first draw was earlier, but contained less UI: no title/navigation
controls. The small, uncertain gain in usable fields did not justify that tradeoff.
**Reverted; retained code and clean APK are unchanged.** Six candidate editor/tablet
tests and formatting passed. The latest retained-build measurement is **583/212 ms**;
the previous **598/201 ms** is another batch of the same code, not a regression.
[Full comparison and restoration](../AnkiDroid/build/reports/compose-editor/deferred-loading/README.md).

## Current native layout profile: populated frame remains 41–48 ms

Three fresh repeat traces of the retained static-loading build isolate component
composition. The populated native frame still takes **41–48 ms**: metadata body
execution is **2.9–3.5 ms**, toolbar **6.7–8.2 ms**, and field-container **0.5–0.7 ms**.
Compose applyChanges separately takes **8.6–10.2 ms**, followed by layout/drawing.
These spans are nested within the frame and must not all be added together.
The AndroidView factory itself is about **0.01 ms** because it returns an existing
WebView; construction and deferred attachment/layout are not included in that
factory span.

No later Recomposer:animation slices at least 0.4 ms were observed before readiness,
but **8–17 further native frames** remain. Profiler readiness varies **249–372 ms**;
this is not a replacement for the controlled **201 ms** benchmark. The profiles
also show variable navigation-to-shell delays, so the native frame does not explain
every millisecond. The subsequent deferred-loading experiment above skipped the
temporary loading scaffold but produced only a small, uncertain gain in usable
fields. Its earlier background-only first frame was not itself a speedup.

[Interactive component timeline](../AnkiDroid/build/reports/compose-editor/native-layout-profile/timeline.html)
and [capture details](../AnkiDroid/build/reports/compose-editor/native-layout-profile/README.md).

## Static loading result: repeat readiness 221 → 201 ms

The trace suggested testing the startup spinner. A localized static indication
improved median repeat readiness **220.95 → 200.72 ms** and visual readiness
**250.12 → 230.85 ms** across four alternating pairs; three repeat pairs improved,
one was 3.26 ms slower. Cold readiness was **648.70 → 598.34 ms**. Retained after
six emulator editor/tablet tests and formatting checks. No readiness, persistence,
focus, or busy-operation behavior changed. The clean debug APK is installed.

Recorded frame counts fell, but late-frame counts did not; maximum frame-duration
medians rose. This is a startup-latency result, not a general jank improvement.
The target still requires about **101 ms** more improvement on repeat openings.
[Full data, pair deltas, and limitations](../AnkiDroid/build/reports/compose-editor/static-loading/README.md).

## Repeat-opening critical path: synchronized Android and Chromium traces

Three fresh repeat openings capture Android scheduling/Compose slices and
Chromium navigation, script execution, rendering, and IndexedDB on the same
opening. One process warmup precedes them; app startup is excluded. These traces
precede the static-loading change above and describe the animated baseline. Its
then-current clean benchmark was **638/221 ms cold/repeat**; these profiled
observations do not replace controlled benchmark timings.

| Profiled repeat | First native frame | Populated native frame | Later frames before ready | Compose animation slices | Session ready |
| --- | ---: | ---: | ---: | ---: | ---: |
| warm1 | 42.7 ms | 48.1 ms | 16 | 14.0 ms | 277.1 ms |
| warm2 | 32.0 ms | 43.0 ms | 11 | 6.7 ms | 221.6 ms |
| warm3 | 41.2 ms | 46.2 ms | 14 | 8.5 ms | 255.7 ms |

The repeatable native cost is building the populated screen: a **43–48 ms frame**
after the initial **32–43 ms frame**. Current `NoteEditorScreen` first builds an
app bar and loading body, then replaces that body with metadata, fields, and the
toolbar when model state arrives. The second frame overlaps page navigation;
starting navigation earlier did not remove it in the previous experiment.
These frame durations include required layout, so none is an estimate of a
wholly removable delay.

The UI then executes **11–16 further frames** before readiness. Compose animation
slices at least 0.4 ms account for **6.7–14.0 ms** within those frames; the
indeterminate loading indicator remains active throughout startup. The later
frames total 29.9–54.6 ms, but include required WebView/layout work and cannot all
be attributed to the spinner. Animation work and frame totals are nested, not
additive. The subsequent static-loading experiment above removed the two startup
animations while preserving Save/busy behavior and recovery gates. No subsequent
full trace was captured, so the measured latency gain cannot be equated directly
with the animation CPU slices in these baseline profiles.

The renderer subsequently mounts fields in **21.5–29.8 ms**, including a
**16.7–19.8 ms JavaScript task**. IndexedDB open takes **20.0–23.1 ms** and overlaps
field mounting. Initial draft commit takes **2.1–4.0 ms**. Native recovery-pointer
persistence and focus complete afterward; session readiness remains after both.
Translation request/response takes only **2.7–5.4 ms in these traces**, reinforcing
that it is not a consistent explanation for the total repeat-opening delay.
Do not add these overlapping spans to calculate startup time.

The traces localize costs but do not yet attribute the whole navigation-to-shell
wait to one component. In particular, the renderer starts page parsing after the
populated native frame in all three captures; correlation alone does not prove
that removing that frame would recover the full interval. Static loading recovered
about 20 ms of median repeat latency. Reducing transient loading-layout work and
understanding the native/renderer navigation handoff remain candidates for another
controlled comparison; their potential gains are not yet measured.

Native boot/monotonic clock samples align Chromium timestamps. Clock-offset
spread is at most 1.1 microseconds; three independent navigation milestones agree
within **0.1 ms**. Performance marks omitted from Chromium's export are positioned
using its verified navigation origin and the page's recorded performance timeline.
Scheduling CPU on different threads overlaps and must not be added as elapsed time.

[Interactive aligned timeline](../AnkiDroid/build/reports/compose-editor/repeat-critical-path/timeline.html) ·
[Raw traces, analysis scripts, and clean-APK restoration](../AnkiDroid/build/reports/compose-editor/repeat-critical-path/README.md).

## Inline translations experiment: rejected; repeat readiness 221 → 220 ms

Served the backend's editing/keyboard translation response with the initial HTML
instead of requesting it over the native bridge during Svelte startup. Exact
request bytes selected the inline response; different module requests retained
the bridge. Unbound pages still waited for collection binding. No cross-session
cache, backend bundle change, prewarming, or readiness-policy change was made.

Four complete reversing-order pairs (16 openings) on the same Pixel 9 Pro:

| Metric | Bridge (retained) | Inline translations |
| --- | ---: | ---: |
| Repeat session ready | 220.82 | 220.12 |
| Repeat visual callback | 246.82 | 247.22 |
| Repeat first native draw | 48.25 | 49.28 |
| Cold session ready | 638.07 | 644.69 |
| Cold visual callback | 682.47 | 691.22 |
| Cold first native draw | 208.34 | 210.10 |

All values are milliseconds from editor Activity `onCreate`. Both variants used
the same timing hooks without a CPU/GPU profiler. Session readiness still
includes recovery persistence and autofocus; a visual callback is not physical
screen presentation. App-startup optimization remains out of scope.

**Not retained.** Paired repeat differences (candidate minus baseline) are
+5.53, +1.17, −39.97, and +7.11 ms. Three of four pairs are slower, and repeat
readiness/visual medians are essentially unchanged. Cold paired differences also
change sign. There is no demonstrated progress toward the 100 ms screen target.

The mechanism did work: all eight timed candidate openings contained the inline
response and made zero translation bridge requests. In repeat runs the baseline
bridge round trip took a median 14.2 ms; shell-to-fields-API time fell from
57.8 to 39.1 ms. HTML grew by 7,618 bytes and its response preparation changed
from 1.75 to 2.09 ms. Native API-ready medians were 172.2 versus 163.3 ms, but
that did not translate into an end-to-end gain. Independent medians are not
additive, and these captures alone do not establish which scheduling interaction
absorbed the improvement. The next investigation needs a current repeat-opening
trace across native layout, renderer work, and field/draft completion.

Eight candidate checks passed: inline response and fallback, unbound startup,
Add/Edit, recovery, Back, recreation, and tablet preview. Formatting passed.
The earlier interrupted batch is excluded in full: the editor lost foreground
before a guarded screenshot/cleanup, so the harness stopped. A read-only check
later found the debug editor foreground again; the complete comparison was
restarted with the same guards and passed. No phone settings changed.

The previous source and clean debug APK are restored. The latest baseline
remeasurement is **638/221 ms cold/repeat**; the preceding 697/232 ms values came
from another batch of the same retained implementation, not a code change.

[Experimental patches, complete captures, tests, and clean restoration](../AnkiDroid/build/reports/compose-editor/inline-translations/README.md).

## Early navigation experiment: rejected; repeat readiness 232 → 233 ms

Removed the deliberate first-frame wait in `ComposeNoteEditorActivity`, starting
WebView construction and navigation during `onCreate`. The candidate kept the
same fields assets, collection-binding gate, draft persistence, and autofocus.
Four reversing-order pairs produced 16 openings on the same Pixel 9 Pro, with
identical timing hooks and no CPU/GPU profiler during measurement.

| Metric | Wait for first frame (retained) | Start during onCreate |
| --- | ---: | ---: |
| Repeat session ready | 232.00 | 232.50 |
| Repeat visual callback | 261.51 | 266.16 |
| Repeat first native draw | 51.30 | 69.46 |
| Cold session ready | 697.21 | 655.79 |
| Cold visual callback | 738.32 | 714.01 |
| Cold first native draw | 223.02 | 285.64 |
| Repeat late-frame share (%) | 7.55 | 6.77 |
| Cold late-frame share (%) | 11.24 | 14.38 |

All durations are milliseconds from Activity `onCreate`, except frame shares.
Readiness still includes the initial draft, recovery pointer, and autofocus;
visual callbacks do not measure physical screen presentation.

**The change is not retained.** Repeat readiness medians are essentially equal,
and the first native draw is 18 ms later. Individual paired repeat differences
(candidate minus baseline) are −74.29, −20.57, −7.61, and +21.55 ms: three pairs
improve, but the gain is inconsistent, and this does not establish progress
from approximately 250 ms toward 100 ms. Cold readiness is lower in three pairs,
but the first native draw is 63 ms later and cold late-frame share increases.
The initial favorable pair alone would have overstated the benefit.

Navigation starts much earlier (repeat median **70 → 19 ms**), but navigation to
the fields API lengthens (**103 → 149 ms**). Fields API readiness consequently
barely moves (**171 → 167 ms**). The translation request/response span increases
from **36 → 72 ms**; this includes dispatch and scheduling, not just translation
computation. Earlier navigation overlaps native work but does not remove that
work. A new trace would be needed to attribute all the added waiting precisely.

The next screen-level investigation should address the native layout and page
bootstrap interaction, especially the translation handshake, rather than moving
navigation earlier again. App-startup locale/TTS changes remain deferred.

The candidate passes six existing Add/Edit, recovery, Back, recreation, and
live tablet-preview tests plus formatting checks. Phone and tablet screenshots
were inspected. Production source and the installed debug APK are restored to
the previous implementation; the experimental patch and both APKs remain local.
No phone settings changed. Previous 657/241 ms figures came from another batch
of the retained implementation; this batch remeasures it at 697/232 ms, not a
new regression or optimization.

[Patch, raw captures, build/test logs, and restoration receipt](../AnkiDroid/build/reports/compose-editor/early-navigation/README.md).

## Deferred: redundant locale and speech discovery work

Investigation only; no new implementation or device benchmark. The latest
transparent-background runs still put the first native draw at **221 ms cold /
52 ms repeat**, and WebView construction starts at **239 / 61 ms**. These are
milestones from Activity `onCreate`, not independent durations to add together.

Two concrete sources of unnecessary startup work precede a broader layout change:

1. `i18n/LocaleCodes.kt` eagerly builds `twoLetterSystemLocaleMapping` by enumerating
   every system locale and its ISO codes. Its only consumer is the
   `isRobolectric` branch of `normalize()`. The production ICU branch does not
   use it. Make the test fallback initialize only when that branch needs it.
   The earlier ART cold profile attributes **44.7 ms CPU** to
   `LocaleCodesKt.<clinit>`, including 36.3 ms in locale enumeration. ART profiling
   adds overhead; these are attribution clues, not a forecast of saved latency.
2. `TtsVoices.loadVoices()` reads default-engine languages, closes the connection,
   then reconnects to that engine while scanning all engines. The local Android
   36 SDK implementation confirms that `getAvailableLanguages()` already fetches
   the entire voice list via `service.getVoices()`. Fetch once, derive languages
   from the same voices, and reuse the default-engine result. Preserve early
   publication of default languages, other-engine discovery, error handling,
   and connection cleanup. Check fallback-engine identity before excluding a
   package from subsequent discovery.

In the existing `draft-pointer-profile/candidate-traces/trace-cold.pftrace`,
the first native frame takes 177.5 ms, with 161.1 ms scheduled main-thread CPU.
A worker associated with speech discovery uses 133.6 ms CPU during the same
interval; the older ART stacks identify voice deserialization and locale setup.
This establishes overlapping work, not the amount by which it delays the UI.
The trace predates the background/shadow changes; those TTS and locale paths
remain in the current source. Do not add parallel worker time to frame time.

Next experiment: benchmark the locale-table change alone, then voice-result
reuse, with reversing-order cold/repeat runs and the same readiness/visual/frame
metrics. Also verify locale normalization and multi-engine TTS behaviour. Both
changes remove work without postponing speech initialization. Expected benefit
is primarily cold startup; no measured end-to-end saving is available yet.

If these are small, investigate first-frame composition next. The existing trace
contains 48.3 ms in Compose view initialization, 30.0 ms in initial composition,
and 51.6 ms in measurement (including another 22.0 ms of composition). Nested
durations must not be added. This identifies where to profile further; it does
not prove that replacing `Scaffold` will save those durations.

## Transparent outer background and native theme labels

The area around and between fields now shows the native Compose background.
Field surfaces stay opaque. Labels and their icons use the app theme foreground,
including dark and black themes; sticky label backgrounds match the native
surface to keep scrolling content from showing through. Theme colours are
injected before the fields mount, rather than relying on the system dark-mode
setting or recolouring note HTML.

A same-Pixel comparison of a theme-matched **opaque WebView** versus a
**transparent WebView** produced these medians:

| Metric | Opaque | Transparent (selected) |
| --- | ---: | ---: |
| Cold session readiness | 672.14 ms | 656.69 ms |
| Repeat session readiness | 236.80 ms | 240.74 ms |
| Cold visual callback | 730.95 ms | 716.02 ms |
| Repeat visual callback | 272.52 ms | 269.74 ms |
| Cold late-frame share | 10.58% | 10.48% |
| Repeat late-frame share | 6.58% | 5.61% |

Four reversing-order pairs, 16 openings. Both variants have identical packaged
assets and timing hooks; the backdrop setter chooses the native colour or
transparent. No GPU/ART/V8 profiler runs during the benchmark. Timings start at
Activity `onCreate`, not the tap or process start.

Transparent cold readiness is lower in all four pairs, but the differences range
from 10 to 167 ms. Earlier stages vary too (fields API: 498 vs 459 ms; first
native draw: 225 vs 221 ms). Repeat differences change sign. These data support
keeping transparency for its appearance without a demonstrated overall speed
penalty; they do not establish a general rendering speedup or typing-latency gain.
The previous 613/231 ms result is from a different batch, not this comparison's
baseline. Neither comparing those batches nor the original emulator timings
isolates the cost of the background change. The 100 ms target remains unmet.

Three theme tests first failed against the previous grey background, then passed
with the new CSS; the final tests also verify readable labels/icons, opaque field
surfaces, and preserved explicit HTML colours. The selected clean APK passes six
additional Add/Edit/recovery/tablet checks. Light/dark/black and tablet screenshots
were inspected; formatting checks pass. No phone settings changed.

[Raw measurements, variants, screenshots, and validation](../AnkiDroid/build/reports/compose-editor/background/README.md).


## Field shadows: less cold-start graphics work

**Same-Pixel paired readiness: 723 → 613 ms cold;
236 → 231 ms repeat.** Six reversing-order pairs
(24 openings), using identical timing hooks and no GPU/ART/V8 profiler during
measurement. Time starts at Activity `onCreate`, not the tap or process launch.
Cold readiness and visual callback improve in all six pairs; the median paired
reductions are 81.51 ms and 76.98 ms respectively. Repeat readiness improves in
four of six pairs.

| Median | Before | Without field shadows |
| --- | ---: | ---: |
| Cold session ready | 723.49 ms | 612.83 ms |
| Repeat session ready | 235.52 ms | 231.35 ms |
| Cold visual callback | 770.63 ms | 667.49 ms |
| Repeat visual callback | 264.24 ms | 257.79 ms |
| Cold fields API → ready | 212.97 ms | 157.07 ms |
| Cold late-frame share | 10.91% | 11.53% |
| Cold late-frame count | 9.0 | 9.0 |
| Cold frame count | 87.0 | 82.0 |

GPU tracing identified Vulkan pipeline creation inside the long cold WebView
raster flush. In separate A–B–B–A profiled captures, removing the desktop editor's
three decorative field shadows lowers the longest cold draw from **106–107 ms
to 76–82 ms**, and total pipeline creation from **100–102 ms to 50–62 ms**.
Repeat captures create no new pipelines; little repeat-start improvement is
expected. The three-line Android CSS change retains rounded corners, borders,
focus/error outlines, and HTML content styling.

Some benchmark variation occurs before field loading: cold fields-API readiness
is 505.37 → 470.87 ms. The entire end-to-end difference
cannot be attributed solely to the removed shadows. The reduced graphics work
is independently reproduced, but late-frame counts/shares do not establish a
smoothness improvement. Frame histories cover each fresh Activity through the
capture's post-readiness wait; sample counts vary. Visual callbacks are not
physical presentation or typing-latency measurements.

Six clean-APK Add/Edit/recovery/tablet tests pass, and source-editing/tablet
screenshots were inspected. All tracing hooks were removed. Phone system
settings were unchanged. [Raw results, profiles, patch, and validation](../AnkiDroid/build/reports/compose-editor/render-wait/README.md).

## Previous investigation: draft-pointer wait and readiness ordering

**Paired session readiness: 714/246 → 677/221 ms cold/repeat. Visual callback:
744/264 → 732/247 ms.** [Detailed report and raw phase records](../AnkiDroid/build/reports/compose-editor/draft-pointer-profile/README.md).

Seven timestamp/CPU/thread-ID samples separate dispatch, IO start, draft lock,
commit start/end, IO end, and main resumption. Logging happens after the measured
interval. Eight baseline openings reproduce a 104 ms pointer call: **4 ms commit,
99 ms return-to-main wait**. A separate cold Perfetto trace shows a 25 ms Compose
frame occupying that return interval. The slow span is not a 100 ms disk write.

Native readiness now becomes true after pointer persistence and initial autofocus,
so enabling the controls does not schedule a competing redraw before those steps.
In six reversing-order pairs (24 openings), median return-to-main falls from
27.5 → 9.1 ms cold and 9.6 → 0.4 ms repeat. Actual commit medians stay similar.
Readiness improves in every pair; visual callback improves in five of six for
each state. Some work moves later: visual gains are 12/17 ms versus session gains
of 36/25 ms. Pre-change-stage timing also varies, so the entire startup difference
must not be attributed to the readiness change.

Long cold stalls remain (114 ms candidate maximum). The additional candidate
Perfetto pair sees a 26.7 ms cold return interval overlapping a 27.9 ms frame,
with 18.8 ms in `postAndWait`, rather than the earlier 20.6 ms recomposition.
Its repeat return interval is 5.7 ms, overlapping a 6.7 ms frame. These traces
illustrate remaining draw/render synchronization; they do not locate every
unprofiled 100 ms stall. No preference format/durability, thread, or platform
service changes were made. Current cold late-frame share is higher, so this
iteration is not a demonstrated smoothness improvement.

Regression test first fails against early readiness, then passes; 34 unit and
six clean-APK editor/tablet tests pass. The Pixel has the clean debug APK with
no diagnostic hooks or system-settings changes. All raw traces remain local.

## Previous scheduling investigation: 756/246 → 702/234 ms

[Four new native/scheduler timelines](../AnkiDroid/build/reports/compose-editor/startup-scheduling/timeline.html) ·
[Sixteen paired openings and validation](../AnkiDroid/build/reports/compose-editor/startup-scheduling/README.md).

The draw callback → WebView gap is mostly native UI work. In the baseline cold
trace, a second Compose frame takes 54 ms and content capture another 17 ms.
The main thread runs for 76 of the 93 ms gap. Repeat has the same pattern: a
35 ms frame and 9 ms content capture within a 59 ms gap.

A one-shot front-of-queue callback starts WebView immediately after the first
traversal returns. The new paired comparison reduces median draw → constructor
from 86 → 15 ms cold and 53 → 10 ms repeat. Complete session readiness improves
by 54/12 ms, with three of four cold pairs and all four repeat pairs faster.
Native first-draw medians are 207 → 204 ms cold and 48 → 52 ms repeat. Cold
late-frame share is essentially unchanged; repeat falls from 7.14% → 4.63%.

The initial draft-pointer span also needs care: the baseline main thread is
running for 29 of 30 ms cold and 14 of 15 ms repeat, largely in a Compose frame.
However, the candidate cold trace has a 118 ms pointer span including 90 ms with
the main thread sleeping. These observations **do not establish that all pointer
latency is UI work, or identify the remaining storage/IO-dispatch delay**.
Do not drop persistence guarantees or attribute all remaining time to disk.

All four Perfetto traces have zero reported error-severity statistics. They use
scheduler, native frame/Compose slices, and FrameTimeline; there are no ART or V8
sampled stacks. The four captures are separate runs. The paired performance
comparison uses only the existing milestone diagnostics, not the profiler.
All traces and the viewer stay local. The older flame graphs below remain archived
and should not be mistaken for profiles of the current implementation.

## Previous comparison: 715/254 → 712/237 ms

The three profile-guided changes are implemented. The new paired comparison shows
**no consistent cold improvement**, about **16.5 ms faster repeat opening**, and a
lower observed late-frame share. The initial 50–100 ms cold-saving estimate was too
optimistic; explicit commit did not reliably remove the cold draft gap.

[Implementation results and validation](compose-note-editor-loading.md) ·
[All 32 openings, patches, and clean APK](../AnkiDroid/build/reports/compose-editor/three-optimizations/README.md).

The flame graphs below are the **archived pre-change investigation**, based on the
older 681/249 ms timing series. They have not been relabeled as post-change profiles.
The sections below retain the original hypotheses and their profiling evidence;
consult the implementation results above for which gains were actually observed.

## Original profiling investigation

The clean Pixel baseline remains **681 ms cold / 249 ms repeat**, against a **100 ms**
usable-field target. This investigation adds profiles and identifies experiments;
it does not establish a new speed improvement. Preloading and moving more work
onto background threads remain last options.

[Open the interactive flame graphs](../AnkiDroid/build/reports/compose-editor/full-profile/flamegraph.html).
They work offline, with profile/thread selection, search, click-to-zoom, self-weight
tables, and SVG export. [Raw captures and reproduction scripts](../AnkiDroid/build/reports/compose-editor/full-profile/README.md)
are local artifacts. [Speedscope export](../AnkiDroid/build/reports/compose-editor/full-profile/flames.speedscope.json)
is also available; do not upload these artifacts to a remote service.

## What the profiles suggested

### 1. Investigate the draft transaction's commit delay first

The Chromium cold capture separates database work from the surrounding delay:

| Event, relative to transaction creation | Cold | Repeat |
|---|---:|---:|
| Backend `PutOperation` finishes | 1.019 ms | 0.840 ms |
| Renderer handles the request result | 2.499 ms | 2.059 ms |
| Backend starts pending commit | 88.645 ms | 2.164 ms |
| Pending commit work, elapsed duration | 0.166 ms | 0.270 ms |
| Renderer receives transaction completion | 89.199 ms | 3.755 ms |

The cold delay is predominantly **before commit starts**, not time spent writing
in the measured `PutOperation` or commit slices. This supports investigating
transaction scheduling rather than assuming a slow disk. It does not identify
which queue, process scheduling decision, or startup condition caused the gap.
The earlier six clean combined cold runs also had roughly 90 ms in the broader
`createDraft` phase, but that phase includes snapshotting and is not an isolated
transaction measurement.

The smallest experiment is to call `transaction.commit()` after enqueueing the
single write in `editor.js::transact()`, with feature detection for supported
WebViews. Keep resolving the promise from `oncomplete` and rejecting on abort;
request success alone is not sufficient for draft recovery. The IndexedDB
specification says explicit commit can begin without waiting for request-result
handlers. That makes it a plausible way to remove a round trip, **not proof that
it removes this particular 86 ms gap**. [IndexedDB transaction lifecycle](https://www.w3.org/TR/IndexedDB/#transaction-lifecycle).

Validate with alternating unchanged/explicit-commit cold openings, then the draft
recovery and write-failure tests. Do not change transaction durability or publish
readiness early to make this result look faster. The current profile has one
cold/repeat pair, so the delay itself needs replication.

### 2. Reduce Compose work during opening and keyboard animation

The native sampled profiles contain Compose frames in **52% of cold main-thread
CPU weight and 85% of repeat main-thread CPU weight**. They include startup,
field readiness, and keyboard settling, so these percentages are not exclusively
pre-readiness work. Layout/draw, `Scaffold`, Material controls, and inset animation
are prominent. Native field hashing and model loading are not prominent in the
repeat samples.

The source gives concrete places to investigate:

- `NoteEditorScreen` applies `imePadding()` to the entire `Scaffold`. Measure which
  children are remeasured and drawn through the keyboard animation. Try keeping
  the top bar and metadata outside the changing inset layout while resizing the
  field area and keeping the toolbar reachable above the keyboard.
- `EditorContent` reads editor, readiness, and toolbar state together. Check actual
  recomposition counts before narrowing those subscriptions to the controls that
  need them. A sampled `Scaffold` frame alone does not prove unnecessary
  recomposition: some of this is required measurement and drawing.
- Investigate Compose's content-capture semantics scans: they account for 19.6%
  of repeat main-thread CPU weight, including `getCurrentSemanticsNodes` and
  appear/disappear event generation. This is part of the 85% above, not an
  additional cost. Check why the tree changes and whether scans repeat; preserve
  accessibility and platform integration rather than disabling services.
- Avoid constructing unnecessary controls for intermediate loading states, and
  verify whether the loading-to-fields transition duplicates expensive layout.

Use the same debug APK configuration and measure frame timing through autofocus;
keep keyboard behavior and accessible controls intact. Do not infer a millisecond
saving from these profile percentages. ART sampling materially slows this build:
its main-thread CPU weights are 1,930 ms cold and 750 ms repeat, far above normal
opening costs. The independent Chromium capture does not enable ART profiling.

### 3. Simplify per-field styling and component initialization

Source-mapped V8 samples identify `RichTextStyles.svelte`, `Collapsible.svelte`,
`RichTextInput.svelte`, and Svelte effect flushing among the startup work.
`RichTextStyles` contributes 2.92 ms cold / 4.33 ms repeat of **self sample weight**;
these small single-run values locate code, rather than establish reliable costs.

`RichTextStyles` currently imports three tiny modules to obtain CSS URLs, mounts a
`CustomStyles` component for each field, then applies four reactive style setters.
Each setter awaits the style rule and rewrites the underlying style element.
Useful bounded experiments are:

- Resolve the three known CSS URLs without three dynamic module requests in the
  fields-only bundle; retain the actual stylesheet loading guarantees.
- Batch initial color/font/size/direction into one style update, preserving later
  reactive updates and upstream add-on expectations.
- Inspect repeated component/effect setup during the first `FieldsOnly.load()`.
  Keep direct HTML editing, selection, hints, RTL, and recovery behavior covered.

These changes may shorten the chain between fields API readiness and rendered
fields. They are unlikely, individually, to explain hundreds of milliseconds.

### 4. Trim remaining initialization, with compatibility checks

`intl-pluralrules` is imported unconditionally by the shared i18n utility and
appears in the V8 startup samples. Test a fields-only conditional fallback where
native `Intl.PluralRules` provides the required locale behavior. Preserve support
for the minimum supported WebView and test Arabic/plural fallback behavior; do
not remove the polyfill across desktop Anki on the basis of this phone.

Markdown helpers also appear in the initial bundle. Trace their actual import
users before deciding whether any are avoidable. Profile data is insufficient to
recommend deleting a feature or rewriting the editor framework.

## Why DOMContentLoaded is not the CPU budget

In the Chromium profiles, navigation-to-DOMContentLoaded is 171.9 ms cold and
75.6 ms repeat. Total measured module-evaluation slices are only **14.72 ms cold /
7.64 ms repeat** (12.86 / 6.00 ms reported thread CPU). Compilation, inline scripts,
resource delivery, layout, and scheduling are separate costs; some compilation is
on background threads and first-capture trace coverage is incomplete before the
renderer starts. These figures do not account for every preceding millisecond.
They do rule out describing the whole interval as JavaScript module evaluation.

The complete Chromium trace retains renderer, browser IO, IndexedDB, compositor,
and task events for inspection. Events overlap; summing arbitrary inclusive
slices would double-count work. No single “CPU versus disk” pie chart is justified.

## Capture coverage and limitations

Recorded on 2026-10-09, Pixel 9 Pro, Android 17/API 37, WebView 155.0.8059.30.
All six openings use `com.ichi2.anki.composeperfphone`, an isolated debug APK with
its own synthetic two-field Add note and private collection. These are seeded
share-to-Add openings, not a physical Add-button tap, Edit, or tablet preview.

| Capture | Coverage | Limitation |
|---|---|---|
| ART cold + repeat | App Java/Kotlin stacks; 1 ms requested sampling; CPU and wall clocks | Significant observer overhead; native callees opaque; includes keyboard settling |
| Chromium cold + repeat | WebView navigation, rendering, scheduling, IndexedDB; 5 s capture | Starts after WebView construction; trace arguments are stripped by WebView |
| V8 cold + repeat | Function samples across module bootstrap, field load, focus; 500 µs requested sampling | Temporary 3 s navigation gate changes contention; sampled elapsed weights are not OS CPU time |

WebView tracing produced `ProfileChunk` events with empty arguments, so they
cannot supply JavaScript stacks. Separate DevTools CPU profiles supply those
stacks. Their navigation gate allows attachment before JavaScript starts; do not
compare their navigation or idle timings to the clean baseline. Idle and
unattributed `(program)` samples have separate views and are explicitly excluded
from the JavaScript-focused graph.

The V8 source map was generated locally with hidden maps. Its 270,526-byte entry
module is byte-for-byte identical to the captured APK's entry module, SHA-256
`176364af97e398d189d8ef432d61fdec376eacf4c8141c290a8040684693b0fa`.
474 cold / 369 repeat call-frame nodes map to original source locations. Each
frame retains the original minified name when the map supplies no source name.

This is broad app/web coverage, **not a complete kernel, native C++/Rust, GPU, or
physical presentation profile**. Thread waits are visible in the ART wall view;
we have not attributed every wait to a kernel scheduling or IO cause.

The ART decoder rejects overflow, partial records, and inconsistent stacks. Both
captures decode without stack mismatches. Three synthetic format tests check
CPU/wall separation and malformed captures. The offline viewer passes browser
checks for rendering, zoom, and absence of network requests; PNG/SVG previews
were inspected. Native traces can also be opened in Android Studio; V8 profiles
in DevTools; Chromium JSON in an offline trace viewer.

The clean debug APK was restored and its two editable, styled fields checked.
No profiling hooks remain in production source. No phone system settings,
permissions, compilation settings, or user collection were changed. All code,
traces, source maps, APKs, and reports remain local.

Tool references: [Android trace generation](https://developer.android.com/studio/profile/generate-trace-logs),
[ART trace format implementation](https://android.googlesource.com/platform/art/+/master/runtime/trace.cc),
[WebView tracing configuration](https://developer.android.com/reference/androidx/webkit/TracingConfig.Builder).
