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

Six alternating debug/release pairs on the owned emulator measured **316.05 →
224.96 ms** repeat readiness and **922.47 → 432.14 ms** for the first process
opening. Release uses R8 and is non-debuggable; editor code and packaged assets
are unchanged. Five of six repeat pairs improve, one is 33 ms slower; all first
process pairs improve. These are noisy emulator measurements, not a new Pixel
benchmark or a ratio to apply to its 205 ms result.

Build configuration accounts for substantial cost, so the debug traces do not
establish a production architecture limit. Release still exceeds 100 ms on this
emulator. Temporary timing hooks were removed; the control package was cleaned
up. No phone access or system settings changes, and the collection-loading
experiment remains unimplemented.
[Full control, caveats and artifacts](../AnkiDroid/build/reports/compose-editor/release-control/README.md).

## Architecture pass: reduce work, not just move navigation

Reanalysis of the retained build finds **127–145 ms of app main-thread CPU** in
the synchronized debug traces, including **62–74 ms outside the two large native
frames**. That is not all Compose work, and it is not a release-build lower bound.
The next small candidate is combining the duplicate collection route check and
model load. A broad native rewrite needs better attribution and an optimized
emulator control first. No new APK or speedup in this pass.
[Architecture review, timing breakdown and next experiments](compose-note-editor-architecture.md).

The pass also corrected two trace selections that picked the initial blank
document commit. Readiness and the Safe Browsing A/B result are unchanged.

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

## Current profile: native editor construction still costs 41–48 ms

Fresh traces of the static-loading build put metadata composition at **2.9–3.5 ms**,
toolbar composition at **6.7–8.2 ms**, and Compose applyChanges at **8.6–10.2 ms**
inside the populated frame. These nested phases are not additive savings estimates.
The profile motivated the deferred-loading experiment above. Its result shows why
a first blank frame is not the success metric: it appeared earlier without a
convincing gain in usable fields. [Component analysis and timeline](compose-note-editor-profile.md).

## Static startup loading: retained; repeat readiness 221 → 201 ms

Replacing the two startup spinners with localized “Processing…” text improved
repeat readiness **220.95 → 200.72 ms** (about **20 ms / 9%**) in four alternating
pairs. Visual readiness improved **250.12 → 230.85 ms**; cold readiness improved
**648.70 → 598.34 ms**. Save/busy spinners and all readiness/recovery gates remain.
Three repeat pairs improved by 12–33 ms; one was 3 ms slower. This is a modest
sample, and repeat opening remains about **101 ms above the 100 ms target**.
The preceding 638/221 ms baseline came from a different batch.

First native draw was nearly unchanged (52 → 50 ms). Recorded window frames fell
55 → 48.5, but median late-frame count stayed at 3 and maximum frame-duration
medians increased; this does not establish a general jank improvement. Six editor
and tablet tests plus formatting passed. The clean debug candidate is selected
and installed on the phone, with all temporary instrumentation removed.
[Full comparison, caveats, and artifacts](../AnkiDroid/build/reports/compose-editor/static-loading/README.md).

## Fresh repeat-opening trace: native layout and animation

The three synchronized Android/Chromium traces identify a **43–48 ms frame**
that builds the populated native screen, followed by **11–16 frames** before
readiness. Compose animation slices within those later frames total **7–14 ms**;
the loading indicator animates throughout startup. Frame totals include required
work and are not a forecast of removable latency. Field mounting and opening
draft storage already overlap.

These traces preceded the static-loading experiment above. They describe the
animated baseline; their profiled timings are not clean benchmark results.
[Full analysis](compose-note-editor-profile.md) and
[interactive timeline](../AnkiDroid/build/reports/compose-editor/repeat-critical-path/timeline.html).

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


## Latest optimization: publish readiness after persistence and initial focus

**Paired readiness: 714 → 677 ms cold; 246 → 221 ms repeat. Visual callback:
744 → 732 ms cold; 264 → 247 ms repeat.**

The pointer diagnostics separate IO dispatch, lock acquisition, preference commit,
and return to main. An observed **104 ms** pointer call contains only **4 ms** of
commit work, followed by **99 ms waiting to resume on the main thread**. In a
separate cold trace, a 25 ms Compose frame occupies the return interval. The
unchanged-pointer second synchronization correctly skips the write, but still
incurs a dispatcher round trip. No storage or dispatcher change is needed here.

The session previously published readiness immediately after the web draft
committed, before recording its native discovery pointer and requesting initial
focus. That enabled the native controls and scheduled a redraw ahead of those
callbacks. It now publishes readiness after persistence and initial focus finish.
The existing autofocus condition and recovery guarantees are retained. The loading
indicator stays present until those steps finish; some drawing therefore moves
later, which is why the visual callback is reported alongside session readiness.

| Median, same Pixel | Before | Current |
|---|---:|---:|
| Session ready, cold | 714 ms | 677 ms |
| Session ready, repeat | 246 ms | 221 ms |
| Visual-state callback, cold | 744 ms | 732 ms |
| Visual-state callback, repeat | 264 ms | 247 ms |
| Pointer return to main, cold | 27.5 ms | 9.1 ms |
| Pointer return to main, repeat | 9.6 ms | 0.4 ms |
| Actual pointer commit, cold | 2.64 ms | 2.42 ms |
| Actual pointer commit, repeat | 4.73 ms | 4.47 ms |

Six pairs with reversing order, **24 valid openings**, identical assets and
milestone diagnostics, no active system/ART/V8 profiler during comparison.
Readiness improves in all six pairs for both process states; visual callback
improves in five of six each. The separate diagnostic investigation adds eight
ordinary openings and four profiled openings. These are new paired results,
not a subtraction from the previous series' 702/234 ms medians.

Not all the whole-startup difference belongs to this change: API readiness also
varies before the changed code executes. The affected document-response → session
interval falls from 53.6 → 29.4 ms cold and 29.8 → 16.7 ms repeat. The
session → visual callback interval grows from 29.1 → 41.5 ms cold and
17.9 → 27.2 ms repeat. **Visual improvement is smaller than session improvement.**
These callbacks do not establish physical presentation or completion of the
keyboard animation. The 100 ms target remains unmet.

Cold frame smoothness does not improve: observed late-frame share rises
10.02% → 12.51%, with median late-frame count 9 → 9.5 and fewer observed frames.
Repeat share changes 5.36% → 4.68%, inconsistently across pairs. Long cold return
stalls still occur (candidate maximum 114 ms); the change removes one source of
contention, not every frame/queue interaction.

The new test first reproduces premature readiness, then passes while independently
holding persistence and focus. All **34** draft/ViewModel/session unit tests and
**six** clean-APK editor/tablet checks pass. Rich/source and tablet screenshots
were inspected; main/test formatting and clean builds pass. The clean debug APK
is installed on the Pixel with profiling hooks removed. No phone settings changed;
the owned emulator's temporary tablet geometry was restored.

[Complete phase timings, paired results, tradeoffs, source patches, and validation](../AnkiDroid/build/reports/compose-editor/draft-pointer-profile/README.md).

## Previous optimization: start WebView after the first traversal

**Same-Pixel paired medians: 756 → 702 ms cold; 246 → 234 ms repeat.**

A current-build Perfetto trace identifies real work behind the apparent gap after
`activity.firstDraw`: the callback fires at the start of drawing, followed by the
rest of that traversal, another Compose frame (54 ms), and content capture
(17 ms). The main thread is running for 76 of the 93 ms gap in this cold capture;
this is primarily queued UI work, not an idle scheduler or WebView construction.

The one-shot callback now uses `view.handler.postAtFrontOfQueue` from the existing
pre-draw listener. It runs **after the current traversal returns**, before the
queued metadata frame. Page loading can overlap subsequent native UI work.
The Activity lifetime guard remains. No preloading, worker-thread changes,
recovery changes, or disabled platform services are involved.

| Pixel 9 Pro median | Previous scheduling | Current scheduling |
|---|---:|---:|
| Session ready, cold | 756 ms | 702 ms |
| Session ready, repeat | 246 ms | 234 ms |
| Visual-state callback, cold | 781 ms | 730 ms |
| Visual-state callback, repeat | 256 ms | 249 ms |
| First native draw callback, cold | 207 ms | 204 ms |
| First native draw callback, repeat | 48 ms | 52 ms |
| Draw callback → WebView construction, cold | 85.6 ms | 14.6 ms |
| Draw callback → WebView construction, repeat | 52.9 ms | 9.5 ms |
| Observed late-frame share, cold | 9.64% | 9.78% |
| Observed late-frame share, repeat | 7.14% | 4.63% |

Sixteen openings, four pairs with reversing order, no active system/ART/V8
profiler during these measurements. All APK assets are byte-identical.
Cold readiness improves in three of four pairs (one is 4 ms slower); repeat
readiness improves in all four. Median gains are **54 ms cold / 12 ms repeat**.
Cold frame behavior does not clearly improve; the repeat first-draw callback is
4 ms later. The current timings come from this new series, not subtraction from
the preceding series' 712/237 ms medians. The 100 ms target remains unmet.

Android cautions that front-of-queue work can disturb message ordering or starve
other work. This is a single bounded startup task, never a recurring callback;
its measured constructor takes about 29 ms cold / 12 ms repeat. The tradeoff is
prioritizing that task over already queued work after the first traversal.
[Handler API documentation](https://developer.android.com/reference/android/os/Handler#postAtFrontOfQueue(java.lang.Runnable)).

Five clean-APK editor tests pass (Add/sticky fields, Edit/close, Back coordination,
Activity recreation, complete draft discovery/recovery), plus the tablet preview
test. Phone-sized and tablet screenshots were inspected. Main Kotlin formatting
and the clean debug build pass. The clean APK is installed on the Pixel; its
synthetic fields/styles load and no diagnostic marks remain. Phone settings were
not changed; only the owned emulator's temporary tablet geometry was changed and
restored.

[Results, exact APKs, source patch, tests, and traces](../AnkiDroid/build/reports/compose-editor/startup-scheduling/README.md) ·
[Offline chronological trace viewer](../AnkiDroid/build/reports/compose-editor/startup-scheduling/timeline.html).

## Previous optimization: three profile-guided changes

**All three changes: 715 → 712 ms cold (no consistent gain), 254 → 237 ms repeat.
Observed late-frame share: 15.6% → 11.8% cold, 10.2% → 6.9% repeat.**

Implemented explicit IndexedDB commit, narrower keyboard-inset layout with toolbar-only
selection-state observation, and batched field styling with direct CSS URL imports.
No preloading, new offloading, durability changes, or platform-service disabling.

| Median on Pixel 9 Pro | Baseline | + explicit commit | + Compose changes | + field styling (all) |
|---|---:|---:|---:|---:|
| Native session ready, cold | 715 ms | 694 ms | 698 ms | 712 ms |
| Native session ready, repeat | 254 ms | 238 ms | 245 ms | 237 ms |
| Visual-state callback, cold | 746 ms | 724 ms | 722 ms | 734 ms |
| Visual-state callback, repeat | 273 ms | 254 ms | 259 ms | 253 ms |
| Initial draft phase, cold | 82.8 ms | 85.5 ms | 101.7 ms | 52.2 ms |
| Initial draft phase, repeat | 5.8 ms | 2.9 ms | 2.5 ms | 3.3 ms |

32 valid openings, four counterbalanced orders, four samples per variant/process
state. All changes together beat baseline in two of four cold pairs and three of
four repeat pairs. **The predicted 50–100 ms cold improvement did not materialize.**
Explicit commit does not reliably eliminate the cold draft delay. The complete
change saves about 16.5 ms (6.5%) on repeat medians; four pairs are still a small sample.

Frame shares are per-run medians of `FrameCompleted > FrameDeadline` among valid
frames in each fresh editor window, through readiness and keyboard settling.
The combined variant has a lower observed share in all four pairs for both
process states. This is evidence of better frame behavior in these captures,
not a measurement of typing latency or physical presentation. Frames with nonzero
flags are excluded; the observation interval ends after a 600 ms settling wait.

Timings start at editor Activity entry and exclude preceding process/IntentHandler
startup. All variants use the same lightweight milestone instrumentation; no ART,
V8, or Chromium profiler is enabled. The final installed debug APK has no timing
hooks. Do not compare the previous 681/249 ms baseline directly with this run's
715/254 ms baseline; the fresh paired comparison is the relevant before/after.
**The 100 ms target is still not met.**

Validation: 33 unit tests; 27 browser tests and one intentional skip; real WebView
coverage; clean-APK Add, Edit, complete recovery, commit fallback, aborted-write
recovery, and tablet preview checks. Phone and tablet keyboard screenshots were
inspected. The full backend `just check` was attempted but could not execute Sass
in the desktop Qt CSS build; the scoped web build and Svelte check passed.

The clean combined debug APK is installed on the Pixel with a synthetic Add note.
No phone system settings or permissions were changed. The owned emulator's
temporary tablet geometry was restored.

[Full results, all failures, source patches, APKs, and test receipts](../AnkiDroid/build/reports/compose-editor/three-optimizations/README.md).

## Previous comparison: JavaScript payload and draft-ID formatting

**Initial JavaScript: 401,076 → 270,790 bytes (−32.5%). Pixel field readiness:
688 → 681 ms cold, 250 → 249 ms repeat. No consistent overall startup gain.**

The latest comparison separates the JavaScript reduction from the formatter fix.
Two named translation imports remove unused generated functions. Native draft-ID
hashing now encodes SHA-256 bytes directly instead of constructing 32 formatters
per hash. Both changes preserve the existing editor behavior and recovery keys;
no preloading or new offloading was introduced.

| Median on Pixel 9 Pro | Baseline | JS only | Both fixes |
|---|---:|---:|---:|
| Initial JavaScript, uncompressed | 401,076 B | 270,790 B | 270,790 B |
| Fields API ready, cold process | 499 ms | 484 ms | 480 ms |
| Fields API ready, repeat opening | 178 ms | 175 ms | 176 ms |
| Native session ready, cold process | 688 ms | 674 ms | 681 ms |
| Native session ready, repeat opening | 250 ms | 250 ms | 249 ms |
| Fields visual-state callback, cold process | 719 ms | 702 ms | 709 ms |
| Fields visual-state callback, repeat opening | 270 ms | 265 ms | 267 ms |

Six cycles cover every order of the three variants, providing **36 valid openings**
and six samples per variant/process state. The combined version improves session
readiness in three of six cold pairs and three of six repeat pairs. An initially
favorable warm result reverses in the three replication cycles, so it must not be
reported as a dependable improvement.

Readiness and visual intervals start at editor Activity entry, excluding preceding
process/IntentHandler startup. These are synthetic seeded Add openings in a debug
APK, not actual Add-button taps. The visual callback does not prove physical display
presentation. **The 100 ms usable-field target is not met.**

Only the isolated debug app and its private collection were used. No phone system
settings or permissions were changed. The clean APK with both fixes is installed
and was left displaying a synthetic Add note; its phone smoke passes.

[All results, failures, replication, and phase breakdown](../AnkiDroid/build/reports/compose-editor/performance-fields-formatters-phone-android/README.md).
[Clean debug APK and source patches](../AnkiDroid/build/reports/compose-editor/final-debug/README.md).
Earlier iterations and their distinct baselines follow below.

## Profile that motivated the three changes (2026-10-09)

Before the latest implementation, we captured cold/repeat ART
sampled stacks, Chromium startup timelines, and source-mapped V8 profiles:
[interactive flame graphs](../AnkiDroid/build/reports/compose-editor/full-profile/flamegraph.html)
and [detailed findings, capture limits, and experiment plan](compose-note-editor-profile.md).

The strongest new lead is the cold draft transaction: its backend `put` finishes
around 1 ms, but pending commit starts around 89 ms; measured commit work itself
is under 1 ms. Test explicit IndexedDB commit before attributing this to disk IO.
This is one profiled pair, not a demonstrated optimization.

Other leads are Compose layout/drawing during the keyboard transition, batching
per-field style setup, replacing three CSS-URL module requests, and conditional
plural-rule fallback. None requires preloading or new offloading. The profiler
adds overhead, so its durations must not replace the clean 681/249 ms baseline.
The original clean debug APK has been restored on the phone.

## Investigation context

Recorded 2026-10-08; investigation continued 2026-10-09. The target is roughly **100 ms to usable fields**, with a smooth
keyboard transition. The current measurements do not establish an architectural
lower bound or show that prewarming is necessary. In particular, **500 ms before
DOMContentLoaded is not evidence of 500 ms spent constructing the DOM**.

The user observed first-opening lag on a Pixel 9 Pro. The original measurements
used an emulator or desktop Chromium. The later Pixel comparison uses only an
isolated debug APK and a fresh app-private collection; phone system settings
remain unchanged.

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
drafts. Agent-launched builds and other performance captures were stopped during
measurement; unrelated host workloads were not monitored in this iteration.

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

That iteration's clean debug APK also passes an Add smoke check: visible editable fields
contain the expected seed, the standalone assets load, and diagnostic timing
marks are absent. This checks readiness, not launch latency.
[Smoke evidence for that iteration](../AnkiDroid/build/reports/compose-editor/performance-standalone-android/final-smoke/).

## Concurrent collection and page startup

The next iteration removes the dependency from the complete native note model
to starting WebView navigation. Activity creation starts collection loading and
the trusted packaged page independently. The page can fetch and initialize its
assets while the collection opens and the note loads.

The WebView initially has no media root. Media requests return an empty 404 and
translation requests suspend until the host binds the opened collection. Binding
is idempotent for the same canonical media directory and rejects a different
directory. Note attachment starts when the WebView joins the native view tree,
so early bootstrap cannot issue autofocus before the view is attached. Activity
destruction also cleans up a WebView that never reached the screen.

Two smaller dependencies are removed:

- Deck and note-type picker lists load only when their dialog opens. They do not
  delay initial fields or replace newer editor metadata when their read finishes.
- The first draft database open starts alongside field rendering. The command
  still waits for rendering and a committed recovery record. An early database
  failure is retained and reported after rendering finishes, preserving the
  ordering of later commands.

Validation passes: 31 focused unit tests, 11 WebView tests, 5 phone-layout Activity
tests, and 1 tablet preview test. New cases cover delayed collection binding,
denied media before binding, rejected collection switching, picker reads during
metadata changes, and queued commands after an early storage failure. The final
attachment adjustment reruns the Activity/tablet tests; the WebView and JavaScript
code are unchanged from the 11 passing WebView tests.
[Concurrent startup evidence](../AnkiDroid/build/reports/compose-editor/performance-concurrent-android/).

The first comparison confirms the intended overlap: navigation begins 67–175 ms
after Activity entry, versus 250–1,147 ms before; database opening overlaps field
rendering by 47–83 ms, versus zero before. Complete attachment still varies:
cold pairs are 2,053 → 1,214 ms and 1,609 → 2,426 ms; repeats are 790 → 827 ms and
655 → 679 ms. These are four samples per variant, not a reliable total speedup.

The slow 2,426 ms candidate has a specific browser wait: its initial 23,765-byte
stylesheet request spans **904 ms**, versus 297 ms in the other cold candidate.
That difference accounts for 606 ms of the additional 1,212 ms. Other increases
occur before that request, during readiness-message delivery, and while awaiting
the native draft pointer. Field loading itself is faster in the slow run. The
trace does not establish whether the stylesheet wait is asset I/O, scheduling,
or WebView prioritization; it must not be described as CSS parsing CPU time.
Only our builds/browser captures were held idle; unrelated IDE/JVM activity was
subsequently observed on the host and was not controlled.

The follow-up embeds the initial stylesheet in the standalone HTML at build time,
removing that request. Only generated `editor-fields.html` changes: JavaScript,
shared components, deferred shadow styles, and CodeMirror styles are unchanged.
Styles with URL-bearing syntax or escaping conservatively keep their external
link to preserve resolution under the native `/media/` base. Current styles have
none, and a browser regression checks that their separate request stays absent.
This moves CSS into the HTML; it does not reduce the bytes of CSS needed.

The CSS change passes 23 browser cases, 10 focused build-helper tests, and a clean
typecheck. All 23 computed-style selections and four screenshot pairs match.
[Inline stylesheet build and visual evidence](../build/reports/compose-editor/performance-inline-fields-css/README.md).

Four further alternating pairs compare all these changes with the previous
standalone entry. The initial stylesheet request is absent in all eight candidate
captures; keyboard visibility after session readiness is verified in all sixteen.
The complete result still does not support an overall speedup:

| Median milestone from Activity entry | Previous standalone entry | Concurrent startup + inline CSS |
|---|---:|---:|
| Cold process: session ready | 1,423 ms | 1,326 ms |
| Repeat opening: session ready | 524 ms | 752 ms |
| Repeat opening: first native draw, range | 74–93 ms | 221–258 ms |

Candidate repeat openings are slower in three of four pairs. Unrelated host
workloads remain present and are recorded before/after each pair; these are not
controlled device benchmarks. Nevertheless, the later first native draw appears
in every candidate repeat, making early WebView construction a concrete suspect.
Starting navigation earlier is not sufficient if it competes with native startup.
The subsequent experiment defers WebView construction until after the first
native draw traversal, retaining independent collection loading and the other
changes. This orders work after drawing; it does not prove physical presentation.
[All sixteen captures and stage breakdowns](../AnkiDroid/build/reports/compose-editor/performance-inline-css-android/).

## Let the native screen draw before constructing WebView

The final scheduling change uses a one-shot pre-draw callback and posts WebView
construction after that traversal. Collection loading starts immediately; page
bootstrap still does not depend on a complete note model. The posted callback
checks that the Activity is neither finishing nor destroyed. Collection binding,
attachment, durable recovery, and autofocus keep the same ordering as above.

A separate comparison holds all other changes constant. Four reversing-order
pairs provide eight targeted repeat openings plus eight archived cold priming
launches. Repeat first-draw ranges are **158–228 → 72–144 ms**; the constructor
starts 44–111 ms after that draw in the candidate. The cold first draw improves
in every pair too. This fixes the native-frame regression from early construction.

It does not establish a complete-startup improvement. Repeat session-readiness
ranges are **440–960 → 435–652 ms**, with medians **492 → 557 ms**. Cold priming
medians are **1,121 → 1,160 ms**. Deferring construction moves navigation later
(repeat median 45 → 207 ms), while navigation-to-API readiness falls 341 → 215 ms
and native model readiness moves 213 → 71 ms. These stage medians must not be
added to derive a total. The tradeoff is an earlier native frame with mixed field
readiness; neither the original early-bootstrap attempt nor this revision has
proved a total startup win.

All sixteen captures have the expected editable seed, zero initial stylesheet
requests, and a visible keyboard after session readiness. Five phone-layout
Activity tests and the separate tablet preview pass again. The unchanged WebView
and storage implementations retain their eleven passing tests; unit and backend
validation are recorded above. Unrelated IDE/emulator host workloads remain
visible in the archived before/after snapshots. No physical phone was connected
for these measurements, and no phone settings were changed.
[Paired results, exact APKs, logs, scripts, and host samples](../AnkiDroid/build/reports/compose-editor/performance-after-frame-android/README.md).

The final debug APK has all temporary instrumentation removed. Its emulator smoke
check confirms editable upstream fields, changed snapshots, automatic keyboard,
standalone module URLs, no initial stylesheet request, and no diagnostic marks.
[Final clean-build smoke evidence](../AnkiDroid/build/reports/compose-editor/performance-after-frame-android/final-smoke/).


## Native HTML preparation: avoid slow string processing

A further trace splits `backendIndexResponse()` into twelve phases, recording both
elapsed time and the servicing thread's CPU time. Timestamps go into arrays; one
JSON record is logged after response construction. Four unchanged-code captures
(two cold/warm sequences) identify actual CPU work rather than inferring it from
resource timing:

| Phase | Thread CPU per response | Elapsed time per response |
|---|---:|---:|
| Escape closing script tags | 24–29 ms | 50–188 ms |
| Replace `<head>` with the native shell | 7–12 ms | 14–64 ms |
| Complete HTML preparation | 38–49 ms | 98–379 ms |

Together, the two string replacements account for **80.2% of measured HTML
preparation CPU**. Regex compilation and script-nonce insertion account for only
1.1%. Much of the complete elapsed interval is off-CPU: the trace does not assign
that time specifically to disk access, locks, or host scheduling.

The diagnostic bridge script is 17,518 characters and has no `</` sequence.
Previously, escaping nevertheless scanned it case-insensitively. The production
fix first checks for `</`; if present, it retains the exact existing escaping
operation. Otherwise it returns the original script. Head insertion now finds the first
`<head>` once and uses `StringBuilder.insert` to copy the strings in bulk. The
generated page contains one head, and the web assets are byte-identical between
comparison APKs.

A plain WebView regression embeds a mixed-case closing tag and a would-be
injected script inside a JavaScript string. An unescaped control must execute
the injected script. With the production helper applied, it requires the existing
string result, execution of the following trusted statement, and no execution of
the injected script. This checks the HTML parser boundary as well as the returned
string, independently of the editor's navigation and CSP restrictions.
[Phase captures and paired comparison evidence](../AnkiDroid/build/reports/compose-editor/performance-html-startup-android/).

Three complete paired comparisons of the script guard plus an intermediate
`replaceFirst` implementation reduce median HTML thread CPU from **56.3 → 19.6 ms
cold** and **47.8 → 18.8 ms warm**. The script escaping phase falls to about 2 ms,
but head insertion still takes roughly 7.5 ms CPU: Kotlin's replacement path
copies the remaining HTML through a per-character `CharSequence` operation.

That explains the final bulk insertion change. Four subsequent phase captures
measure head insertion at **0.084–0.342 ms CPU**, with complete HTML preparation at
**11.5–14.5 ms CPU**. Its regression test compares the packaged document's exact
output with the preceding implementation, preserves later literal `<head>` text,
and leaves a document without a head unchanged.

The paired run had uncontrolled external compilation/IDE load. Its fourth
baseline cold opening crashed in WebView's native GPU thread; one labelled retry
failed allocating an Android graphics context before WebView construction. Both
failures and the unpaired candidate samples are retained. Only our emulator was
restarted, with the same AVD, data, and graphics flags, before the four final phase
captures. Those four complete without crashes and confirm all field styles load
before readiness, but they are **not a paired full-startup speed comparison**.
[Final bulk insertion evidence](../AnkiDroid/build/reports/compose-editor/performance-bulk-html-android/README.md).


## Pixel comparison: the CPU saving is real, whole startup changes little

This earlier sixteen-opening HTML-preparation comparison used the Pixel 9 Pro with Android 17
(API 37), WebView 155.0.8059.30, and the isolated `playDebug` package. Coverage and
LeakCanary were disabled in both APKs. Four pairs reversed variant order; each
installation was followed by one force-stopped cold process and one new editor
in the warm process. The collection and WebView databases already existed after
an excluded setup prime. These are fresh Add openings, not draft recovery timings.

Native HTML preparation thread CPU falls from **18.4 → 3.7 ms cold** and
**17.1 → 1.5 ms warm**, about 80% and 91% respectively.
Complete readiness varies enough that the median reductions of 16 ms cold and
5 ms warm should not be treated as a dependable large UX improvement. Candidate
session readiness ranges are **663–715 ms cold** and **241–269 ms warm**.
Individual cold changes are −93, −28, +1, and +41 ms; repeat changes are −10,
−18, −6, and +0.2 ms. The report retains all successful captures and the excluded
setup failure/retry, with native monotonic intervals rather than host timestamps.

The remaining cold candidate intervals are approximately:

| Interval | Median |
|---|---:|
| Editor Activity entry → navigation | 311 ms |
| Navigation → fields API ready | 174 ms |
| Fields API ready → native session ready | 198 ms |

These separate medians need not sum to median complete readiness. Before
navigation, the native first draw is around 207 ms, followed by an approximately
85 ms gap before the approximately 25 ms WebView constructor. Moving construction
earlier is the already-tested scheduling tradeoff described above, not a newly
established saving.

Within browser initialization, HTML response completion → shell execution spans
61–123 ms cold. The 400,812-byte fields module's response completion → API readiness
spans another 51–69 ms. These intervals include scheduling/runtime work; they do
not establish equivalent amounts of parsing or JavaScript execution.

The cold `createDraft()` phase takes about **93 ms**, versus about 5 ms warm.
That phase includes `reconcile()`/`snapshotDocument()` and the complete checkpoint
transaction. Its next useful measurement is a split between those operations;
calling the entire interval storage I/O would be unsupported. The remaining tiny
field styles finish 94–134 ms before cold session readiness and 32–50 ms before
warm readiness, so their response completion is not the final readiness blocker
in these samples.

Both new HTML regressions passed on the emulator, as did the existing HTML
round-trip case. The final clean debug APK was then installed on the Pixel and
left displaying a fresh synthetic Add note. Its smoke check verifies two editable,
fully styled fields, the private collection path, and absent diagnostic marks/logs.
No phone system settings or permissions were changed; no user collection was opened.
[Full Pixel comparison and clean-build smoke](../AnkiDroid/build/reports/compose-editor/performance-phone-html-android/README.md).


## Remove retained translations and per-byte formatters

Two tooltip components imported the entire generated translation namespace.
Svelte's generated dependency tracking retained that namespace and its unrelated
functions. Named imports preserve the same translation functions and collapse
state dependency while removing **130,286 bytes** from the standalone module:

| Emitted, uncompressed JavaScript | Before | After |
|---|---:|---:|
| Main module | 400,812 B | 270,526 B |
| Initial JavaScript, including three style URL modules | 401,076 B | 270,790 B |

That is a 32.5% payload reduction, not a claim of 32.5% faster startup. Existing
23 browser cases and two added translated-tooltip cases pass. The new regression
covers Arabic, English fallback, RTL, collapse state, platform shortcut labels,
source toggling and preserved snapshots on both editor entrypoints. Typecheck
and scoped formatting pass. Native libraries in the repackaged local backend AAR
remain byte-identical.

The Android change replaces 32 `String.format` calls per SHA-256 digest with direct
lowercase hexadecimal encoding. The seeded Add route used by the phone probe
hashes twice, while ordinary Add hashes once. The key inputs, UTF-8 encoding,
SHA-256 algorithm and stored key format are unchanged. Compatibility tests recover
legacy pointers across all 256 digest byte values and an independent Unicode key.
All 33 draft-ID/editor-state unit tests pass, as do emulator Add, Edit and complete
recovery integration cases. No preloading or new offloading was introduced.

Three separate diagnostic APKs isolate the unchanged baseline, JavaScript reduction
alone, and both fixes. The baseline and JS-only native editor code is byte-identical;
other DEX differences are fully accounted for by build timestamp metadata. JS-only
and combined APKs have byte-identical assets. Full Pixel timing results are retained
[with the three-variant comparison](../AnkiDroid/build/reports/compose-editor/performance-fields-formatters-phone-android/README.md).
[Backend build and browser evidence](../build/reports/compose-editor/performance-extra-js/README.md).


The completed Pixel experiment uses all six variant-order permutations, with a
cold and warm opening for each variant in every cycle. Its headline table is at
the top of this document. Session-readiness changes for both fixes versus baseline,
paired by cycle, are **−69, +8, +3, −73, +25, −31 ms cold**, and **−87, −13, −14,
+5, +21, +7 ms warm**. All captures are retained. The first three cycles suggested
a warm improvement; all three replication cycles move in the opposite direction.
The six-cycle medians therefore provide no consistent overall startup win.

There is narrower evidence of reduced work. With JS only, the warm interval from
module response completion to the translation request improves in five of six
pairs, by a median paired **5.6 ms**. With both fixes versus JS only, model-load
elapsed duration improves in five of six pairs, with median paired differences
of **2.5 ms cold and 1.3 ms warm**. These intervals include scheduling and, for the
native model, collection work/coroutine suspension; they do not isolate JavaScript
or formatter CPU. An exceptionally short cold model interval occurs in both the
baseline and combined versions, disproving attribution of that outlier to the fix.

Before the successful series, the driver rejected Android's temporary
`PackageUpdateActivity` launch response. Read-only follow-up showed that the app
subsequently created a fresh editor. That failure is archived separately. A narrowly
scoped driver correction recognizes only that exact wrapper response while still
requiring the fresh process/Activity, unique synthetic fields, readiness, private
collection, and verified closure. **None of the 36 successful captures needed the
exception**, and none failed. No system screen was interacted with.

The final clean APK SHA-256 is
`988a2bc85b10db219714a679240ff9d09679a9b105d031907751c90f88bbfb65`.
It has all temporary diagnostics removed and all 154 packaged backend assets
verified against build output. Its Pixel smoke confirms two editable, styled
fields in the private synthetic collection, with no diagnostic logs or marks.


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

1. **Split the cold Pixel draft-creation interval.** The fresh Add cold/repeat
   comparison is now complete. Measure reconciliation/snapshot work separately
   from transaction creation/completion before changing durability. First-ever
   setup and draft recovery remain distinct scenarios, not included in its medians.
2. **Attribute renderer scheduling gaps.** The stream reads are short, but gaps
   before interception and native reply delivery can be large on the emulator.
   Capture `devtools.timeline,v8,blink.user_timing,loading,toplevel` before
   navigation if the phone reproduces them. Distinguish execution from waiting.
3. **Attribute the remaining browser work before further changes.** Initial
   JavaScript is now about 271 KB after removing retained translations. The Pixel
   comparison does not establish a consistent whole-startup gain. Separate parsing,
   execution, rendering and scheduling rather than equating fewer bytes with the
   same percentage of saved time.
4. **Measure necessary startup storage separately from bridge trips.** The fresh
   lookup skip and combined field load/checkpoint remove two bridge requests.
   Database opening now starts alongside field rendering; the durable checkpoint
   and native discovery pointer remain awaited. Distinguish any remaining storage
   delay from bridge delivery and renderer scheduling before changing durability.
5. **Keep preloading/reuse as a final option.** It adds ownership/memory costs
   and is outside these two fixes. Continue investigating work on the current
   opening path first. These measurements do not establish preloading as necessary
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
