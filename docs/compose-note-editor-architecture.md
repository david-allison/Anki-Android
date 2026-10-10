# Editor initialization: original 3.4–4.0 s → current release 328 ms cold / 166 ms repeat

The original emulator and current Pixel 9 Pro measurements use different hardware
and methods; this is historical progression, not a controlled speedup ratio.
This pass reanalyzes the retained build's six repeat benchmarks and three
synchronized Android/Chromium traces. **There is no new speedup or APK change.**
The subsequent Pixel release control measured **206 → 166 ms repeat** and
**607 → 328 ms first-process**, without a production source change. The target
remains 100 ms from Activity `onCreate` through draft persistence and initial
focus. The original architecture pass below analyzed debug traces; it does not
establish a release-build lower bound.

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

**Conclusion: reducing actual main-thread work is necessary in the measured debug
configuration. Moving navigation earlier is insufficient. But the evidence does
not yet justify replacing Compose or rewriting the editor.** The previous claim
that only a larger architectural change remains was too strong: the subsequent
emulator control confirms substantial build-configuration overhead, and
there is still a small, concrete duplicate collection-loading path to test.

## Where the repeat time goes

These are contiguous intervals in each opening. Column medians are independent
and must not be added to reconstruct the 204.91 ms median.

| Retained opening | OnCreate → first draw | Draw → navigation | Navigation → fields API | API → fields + draft | Draft → native ready | Total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 64.52 | 101.62 | 116.70 | 38.29 | 29.15 | 350.27 |
| 2 | 46.34 | 21.88 | 99.54 | 37.74 | 25.84 | 231.34 |
| 3 | 47.85 | 15.32 | 90.71 | 33.58 | 14.96 | 202.42 |
| 4 | 47.28 | 15.84 | 88.91 | 38.40 | 16.98 | 207.40 |
| 5 | 42.65 | 11.79 | 91.47 | 31.76 | 18.29 | 195.96 |
| 6 | 46.55 | 17.91 | 87.19 | 27.64 | 19.40 | 198.69 |
| Median | 46.92 | 16.88 | 91.09 | 35.66 | 18.84 | **204.91** |

All values are milliseconds. Opening 1 remains included despite its large
pre-WebView delay. These are fresh seeded Add sessions with two fields, not a
measurement of Edit, recovered drafts, large notes, source-mode startup or tablet
preview. Those paths have functional tests, not equivalent performance coverage.
Visual readiness is a separate callback, approximately 234 ms median in this
batch; neither marker proves physical presentation or a fully shown keyboard.

The critical sequence is:

```text
Activity → first native traversal → construct WebView → navigate
                                                    → parse/evaluate fields bundle
                                                    → translation bridge → fields API
Collection → route check → model load → native controls/attach ────────────┘
fields API + model → mount fields ∥ open IndexedDB → commit draft
                  → persist native discovery pointer → focus → ready
```

The collection and renderer branches overlap. Both still need the app main
thread for callbacks. The diagram describes dependencies, not additive timings.

## The native frame is only part of the main-thread cost

Scheduled CPU from the three synchronized traces, clipped to Activity entry →
native ready:

| Profile | Main-thread CPU | Inside first frame | Inside populated frame | Outside those two frames | Renderer-main CPU |
| --- | ---: | ---: | ---: | ---: | ---: |
| warm1 | 144.64 | 31.18 | 42.43 | 71.03 | 79.00 |
| warm2 | 145.48 | 30.26 | 41.61 | 73.61 | 86.29 |
| warm3 | 126.74 | 26.61 | 37.93 | 62.20 | 84.13 |

Milliseconds of **CPU**, not frame wall duration. The three middle native columns
partition main-thread CPU; renderer CPU overlaps it and must not be added to it.
At these observed costs, main-thread work alone exceeds 100 ms. That supports
reducing work, not just rescheduling it. It is not an unprofiled or release-build
lower bound: tracing, JIT state and runtime conditions affect the measurements.

The 43–47 ms populated frame does not mean we can save 43–47 ms. Its controls,
WebView attachment, semantics, layout and draw are required. It contains only
7–9 ms of toolbar composable-body time and 3–4 ms of metadata-body time; those
exclude subsequent apply/layout work. The rejected vector experiment confirms
that one small toolbar change does not remove the frame.

Nor do we construct the entire screen twice. `Scaffold` and its app bar remain
outside the `state == null` branch in
[NoteEditorScreen](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/NoteEditorScreen.kt).
The loading body is replaced with the populated body. Building disabled metadata
and toolbar placeholders on the first frame would move much of that work earlier,
delaying the WebView start under the current first-traversal gate. It is not
automatically a reduction in total work. The previous background-only first-frame
experiment already failed to establish a worthwhile end-to-end benefit.

The remaining 62–74 ms outside those two frames includes Activity lifecycle,
WebView construction, later frames, bridge callbacks and platform work. Existing
named slices show ContentCapture work too. They do not fully attribute this CPU
to methods. **Do not label all of it Compose overhead or disable platform services
to remove it.** A fresh native stack capture should target these intervals before
choosing a broad native UI rewrite. Old ART profiles are useful leads, not current
Pixel attribution.

## Build configuration control: substantial emulator improvement

The original phone benchmark used `playDebug`, `isDebuggable = true`, coverage off and
LeakCanary off. Its local Gradle override does not enable R8. The repository's
`benchmark` build inherits release optimizations and sets `isDebuggable = false`.
Android explicitly recommends an R8-optimized release build for assessing Compose
performance because debug builds impose overhead.
[Official Compose performance guidance](https://developer.android.com/develop/ui/compose/performance#properly-configure).

That does not invalidate the controlled debug A/B improvements or change the
100 ms UX target. It prevents interpreting 205 ms as the architecture's
production ceiling. An optimized control on the owned emulator can separate these
questions without installing a non-debug build on the phone. It must use the same
editor flag, synthetic collection, features and readiness endpoint; an emulator
result cannot replace the Pixel headline. The subsequent six-pair control is now
complete: repeat readiness improves **316.05 → 224.96 ms**, with one slower pair;
first-process readiness improves **922.47 → 432.14 ms**, with all six pairs faster.
This measures the complete debug/release configuration difference, not R8 alone.
The later Pixel release comparison above establishes a 166 ms repeat baseline;
the emulator result must not be used to estimate phone latency.

## Concrete opportunities, in order

### 1. Combine route resolution and initial model loading

[ComposeNoteEditorActivity.onCollectionLoaded](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/ComposeNoteEditorActivity.kt)
first awaits `withCol { requiresLegacyEditor(arguments) }`, resumes on the UI
thread, then calls `model.load(arguments)`, which enters `withCol` again.

For Add, both paths read defaults and the note type. For Edit, both fetch the
card/note/type. Have one collection operation return either a legacy destination
or the initial editor data, then publish state once on the main thread. This
removes a duplicate read and an intervening main-thread resumption. Keep the
existing Image Occlusion routing, retained-ViewModel behavior, draft identity and
error handling. Do not publish a partial model just to satisfy the UI sooner.

**This is the next small code experiment I would choose.** It needs no UI change,
cache, new thread, or persistence-policy change. Savings are unknown: current
markers do not isolate the route check or worker completion, and model data is
already available before the fields API in these runs. A benefit would come from
earlier useful overlap or less work, not removing the whole measured model span.
Instrument route/read completion and UI resumption separately; reject the change
as a performance optimization if the end-to-end result is flat.

### 2. Attribute the remaining native CPU, then simplify only the expensive structure

Capture current native stacks for the two frames **and the intervals outside
them**, with an unprofiled comparison kept separate. Use an optimized emulator
control before interpreting general Material/Compose costs as architecture bugs.
If container/subcomposition overhead is substantial, a smaller Compose layout
with equivalent app bar, insets, snackbar, semantics and tablet placement is a
reasonable prototype. Merely replacing `Scaffold`, annotating state as stable or
moving controls into the first frame has no established savings here.

A native rewrite needs materially more benefit than the rejected ~8 ms vector
candidate, while preserving the first-frame controls, touch targets, RTL,
night mode, large fonts, keyboard layout and tablet preview. There is no evidence
supporting a promise that it will get us to 100 ms.

### 3. Reduce field mounting work if native reductions prove insufficient

The API → document interval is still approximately 28–38 ms across the six repeat runs. The
backend `FieldsOnly.load()` constructs Anki's field components, waits for their
elements, then the host records normalized initial HTML and commits the draft.
IndexedDB opening already overlaps this; do not count that time twice.

The backend's `RichTextStyles.svelte` still inserts three shadow-root stylesheet
links during mounting. Captures show those requests taking roughly 5–10 ms, but
`stylesDidLoad` awaits the mutable user style/rule, **not all three link loads**.
Eliminating those requests therefore does not imply a 5–10 ms ready-time saving.
Any inline/shared-style experiment must measure rendered fields as well as API
readiness and preserve mutable per-field font/direction/color styles. Profile
field construction first; do not replace Anki's editing behavior with a cheaper
contenteditable implementation.

## Changes that do not currently justify another experiment

- Earlier navigation alone: already tested; it moved work without improving
  repeat readiness and delayed the first draw.
- Inline translations alone: already tested; the bridge disappeared but the
  final readiness median did not improve. A shorter native blocking frame may
  change that interaction later; the old result is not a proof of permanent zero
  value, but there is no reason to repeat the same experiment now.
- Another toolbar icon cache: the completed six-pair trial did not justify its
  maintenance cost.
- Declaring readiness before draft persistence or focus: changes the endpoint
  and failure behavior. It does not satisfy the existing target.
- Combining focus with initial loading: the native recovery-pointer commit
  currently sits between them. A single JS command cannot simply remove that
  dependency without a recovery/input-ordering change.
- Retaining or prewarming a WebView between screen openings: may avoid genuine
  initialization, but adds Activity/context, collection, theme and session
  lifetime issues. It remains a later option, not the recommendation of this pass.

## Measurement correction and verification

The old Safe Browsing handoff analyzer selected the first document commit after
the browser sent the navigation. In warm2/warm3 that was the initial blank
document. The corrected selector requires the document commit to be inside
`RenderFrameImpl::CommitNavigation` on the same renderer thread.

| Capture | Previous response → commit | Corrected response → commit |
| --- | ---: | ---: |
| warm1 | 47.46 ms | 47.46 ms |
| warm2 | 10.01 ms | 13.26 ms |
| warm3 | 10.44 ms | 15.88 ms |

The regression check failed against the old report, then passed after rerunning
the corrected analysis. All three captures still have zero Safe Browsing check or
deferral events. **The measured 205 ms readiness and the retained Safe Browsing
A/B improvement are unchanged.** Loading/profile documentation and the local
Safe Browsing report now use the corrected numbers.

[Reproducible calculations](../AnkiDroid/build/reports/compose-editor/architecture-pass/analyze.py),
[results](../AnkiDroid/build/reports/compose-editor/architecture-pass/analysis.json),
[navigation regression check](../AnkiDroid/build/reports/compose-editor/architecture-pass/verify_navigation.py),
[benchmark inputs](../AnkiDroid/build/reports/compose-editor/native-frame/README.md),
[trace inputs](../AnkiDroid/build/reports/compose-editor/safe-browsing/profile/paired).

No production source, backend source, APK, phone state or emulator state changed
in this pass. No GitHub writes or commits were made.
