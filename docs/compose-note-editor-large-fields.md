# 400-field initialization: 627 ms → 77 ms

Lazy field initialization reduces the 400-field browser median by **88%**. Arthur
Milchior's actual 206-field note improves from **253 ms to 63 ms**. The editor
retains every field's data, while initially constructing only inputs near the
viewport. A permanent Android regression passed on the Pixel 9 Pro: edit distant
fields, background, recreate, and save all 400 fields without losing or normalizing
untouched content.

These timings cover **field initialization and the first durable draft write** in
desktop Chromium, after the fields API is ready. They exclude navigation, WebView
startup and native Compose work, and do not establish a 100 ms Android opening time.

## Final before/after comparison

Five fresh contexts per document and build, alternating original/current order,
with the same packaged native entry, Android JavaScript shell, translation data,
411 × 700 CSS-pixel viewport and no CPU throttling. Values below are medians.

| Document | Original load + draft | Current load + draft | Initial rich inputs | Original → current draft restore |
| --- | ---: | ---: | ---: | ---: |
| 2 short fields | 18.8 ms | 21.3 ms | 2 → 2 | 5.8 → 6.3 ms |
| 100 short fields | 122.2 ms | 50.3 ms | 100 → 18 | 98.6 → 23.0 ms |
| 400 short fields | 627.1 ms | 77.2 ms | 400 → 18 | 591.6 → 40.0 ms |
| 400 fields, all except the first collapsed | 581.4 ms | 46.3 ms | 400 → 1 | 566.5 → 28.1 ms |
| Arthur's 206 fields, longest note by HTML character count | 252.8 ms | 63.1 ms | 206 → 18 | 217.3 → 29.1 ms |

All 50 contexts passed typing, exact untouched-field HTML and complete draft-reload
checks, with no JavaScript errors. The largest page task in each 400-field probe
fell from a median **501 ms to 51 ms**: three current runs recorded a 51–52 ms task,
and two recorded no task of at least 50 ms. Other current cases recorded none.
These browser tasks are not Android dropped-frame measurements.

The two-field case has a measured 2.5 ms initialization overhead. This trades a
small fixed cost for avoiding hundreds of unnecessary editing components. Scrolling
still mounts new inputs, and retaining visited inputs means memory can grow with
the number of visited fields. This change does not implement full virtualization.

## Arthur's report and its existing fix

[Issue 21289](https://github.com/ankidroid/Anki-Android/issues/21289) reports a crash
when backgrounding the legacy note editor or opening its previewer with a very
large note type. The exception was `TransactionTooLargeException`: the parcel was
583,180 bytes, including 572,788 bytes of fragment view state. This was a saved
Android view hierarchy problem, rather than a limit on the number of fields in
Anki's collection.

[PR 21295](https://github.com/ankidroid/Anki-Android/pull/21295) was merged on
17 September 2026, closing the issue. It changed `FieldEditLine` to save compact
editable state instead of its whole child hierarchy. That fix is already present
in this branch. Android's [Binder transaction buffer is shared and limited](https://developer.android.com/reference/android/os/TransactionTooLargeException),
so a crash below a nominal 1 MB parcel size is possible. Reducing per-field view
state addresses the reported overhead; it does not make arbitrarily large saved
content safe to put into a Bundle.

The linked `chanson.zip` contains an APKG whose actual Lyrics note type has
**206 fields, 420 templates and 928 notes**. All those notes have 206 fields.
The report says approximately 400 fields, so both the real attachment and a
separate synthetic 400-field document were exercised. The attachment's modern
`collection.anki21b` contains the real data; its `collection.anki2` is a two-field
compatibility message and must not be mistaken for the reproduction.

## Original Android lifecycle and persistence investigation

An isolated debug APK, `com.ichi2.anki.composefieldsprobe`, ran on emulator-5582:
Android 14, Android System WebView 154.0.8037.106. A temporary instrumentation probe
used the production Compose activity, bridge, IndexedDB drafts and collection Save.
The two test cases used separate draft identities.

| Measurement | 2 fields | 400 fields |
| --- | ---: | ---: |
| Serialized activity saved state | 3,460 B | 3,464 B |
| Activity launch through mounted fields and snapshot | 1,595 ms | 2,021 ms |
| Activity recreation through restored fields and snapshot | 400 ms | 1,662 ms |
| 20 sequential native snapshot requests | 28 ms | 62 ms |

Both cases passed:

- Editing the first and last fields, including Unicode and HTML.
- Stopping and resuming the activity, with unsaved content retained.
- Recreating the activity and WebView, with all field values and the dirty state
  restored from the draft.
- Saving the note to the collection and verifying the edited last field.

These are single diagnostic emulator runs, not controlled performance comparisons.
The two-field launch also pays first-use process/WebView costs. The recreated
editor and browser measurements provide stronger evidence of the scaling problem.
An initial probe reused an Add draft across a schema change; recovery correctly
rejected that mismatched draft. The successful probe isolated the draft identities.

The near-constant Bundle size agrees with the implementation: `EditorViewModel`
saves a session identifier, while field contents remain in the WebView/disk draft.
The test does not establish Android process-death recovery, large shared-content
Intent safety, or behavior with megabytes of HTML per field. Those are separate
cases from multiplying short editable fields.

## Original field-initialization investigation

Headless Chromium used the actual packaged fields bundle and the current Android
`editor.js` shell, including draft writes. Only translation delivery and asset
serving were supplied by the local harness. Viewport: 411 × 700 CSS pixels; no CPU
throttling. Each row is the median of three fresh browser contexts. The timer starts
after the fields API is available and ends after field loading and the initial
durable draft write; it excludes page navigation and native Compose work.

| Document | Load and draft | Largest page task during the probe | Draft restoration after reload |
| --- | ---: | ---: | ---: |
| 2 short fields | 18 ms | No task of at least 50 ms | 6 ms |
| 100 short fields | 115 ms | 82 ms | 92 ms |
| 400 short fields | 625 ms | 497 ms | 628 ms |
| 400 fields, all except the first collapsed | 598 ms | 508 ms | 539 ms |
| Arthur's 206 fields, using the longest note by total HTML character count | 238 ms | 172 ms | 200 ms |

An additional 15-run correctness pass verified all 20 typed characters and exact
HTML for every field through page reload and draft restoration, including unchanged
offscreen fields, without JavaScript errors. The synthetic 400-field document
contains only 4.3 KB of field HTML. Large content is therefore not required to
produce this opening pause.

The typing probe, which waits for a snapshot and animation frames after each key,
was approximately 16 ms at both two and 400 fields. It does not demonstrate a typing
regression or measure Android input latency. It also does not simulate rapid typing
with a tablet preview. Twenty edits triggered whole-document reconciliation and
draft writes; these remain scaling costs to watch, but the measured priority is
initialization.

## What caused the work

Before this change, `FieldsOnly.svelte` created an `EditorField`, rich input and
source-pane wrapper for every field in its `{#each}` loop. `load()` then awaited
every rich input's element and installed its attributes and composition listeners.
Collapsing a field hid its contents without avoiding component construction.

CodeMirror itself stays lazy: the 400-field rich-text probes created zero
CodeMirror instances. Reintroducing eager source editors would add further cost.

A separate diagnostic trace of the 400-field mount recorded 804 style update
events, four layout events and a layout tree exceeding 10,000 objects. Style
updates accounted for roughly 75 ms and layout for 36 ms in that traced run.
Tracing increased total duration, and nested timings must not be added as though
they were disjoint. The result supports reducing constructed components, rather
than assuming one expensive paint explains the whole pause.

Disabling the field expand/collapse animation in a browser-only experiment reduced
the 400-field median from 625 to 577 ms; 400 mostly collapsed fields fell from 598
to 551 ms. These were separate small samples, not alternating device benchmarks.
The experiment still left a page task over 400 ms. Animation suppression alone
does not solve the problem, and no such change has been retained.

## Implemented lazy initialization

The fields-only backend now keeps all field stores and metadata, with inexpensive
labels and placeholders for fields that have not been visited, retaining the
collapse and HTML-source controls even when an input is unmounted. An observer mounts
inputs near the scroll viewport with a 256 CSS-pixel buffer. Collapsed fields do
not mount until expanded or explicitly targeted. Initial load awaits the initially
visible inputs; native focus, source mode and restored commands await their own
field. There is no maximum field count.

Once created, inputs remain alive. Snapshots and drafts still include every field,
including exact original HTML for untouched fields that Anki would normalize on
mount. Merely scrolling to a field does not make the note dirty. The normalized
baseline is captured before waiting for CodeMirror: rich-text edits made while the
source engine downloads must be retained. Keyboard expansion transfers focus from
the placeholder label to the real label, so Tab can enter the input.

Observer registration and component-handle bindings deliberately avoid invalidating
the whole Svelte field list. A diagnostic profile exposed this additional cost in
the first lazy implementation; otherwise each new handle repeated work across
hundreds of rows.

This is an initial-mount strategy, not permanently bounded memory. Scrolling through
all fields eventually creates all inputs. Full virtualization would also need a
policy for retaining undo history and selection when inputs are destroyed.

## Regression coverage and remaining caveats

The new Android regression edits fields 0, 199 and 399, visits field 299 without
editing it, and compares every field through background/resume, activity recreation
and collection Save. It verifies that distant inputs remain unmounted and Android
saved state remains below 16 KB. A fixed native input-count limit was replaced with
this check after the tablet viewport correctly exposed more fields; the browser
mount-count regression uses a fixed 411 × 700 viewport.

The final native suite passed **six tests on the Pixel 9 Pro**, including this
400-field regression, Add/sticky fields, Edit/close and draft recovery. Its tablet
test was skipped as expected on a phone. This used Android 17 and WebView
155.0.8059.30, with isolated debug packages `com.ichi2.anki.composefieldsprobe`
and `com.ichi2.anki.composefieldsprobe.tests`. Device settings and `com.ichi2.anki`
were left untouched.

The ordinary two-field tablet-preview regression passed separately with a
1280 × 800 emulator viewport at 160 dpi, including against the final backend
bundle. This does not cover Arthur's 420-template note type. The final test
emulator used API 34 and WebView 113.0.5672.136, so its execution time is not
directly comparable with the original WebView 154 diagnostic run. Android APK
assembly and Kotlin formatting checks passed.

Browser coverage now includes:

- 400 expanded and collapsed fields, scrolling and explicit focus of distant fields.
- Exact untouched HTML, source editing, complete snapshots and restored command targets.
- Retained rich/source component identity, and source undo/redo while focused.
- Input disabling, composition guards and rejection of obsolete rich bookmarks.
- Typing while a newly mounted field's source engine is deliberately held pending.
- Keyboard expansion and focus transfer, plus existing light/dark and RTL behavior.

An existing undo problem was reproduced against the **unchanged** packaged bundle
in both rich and source modes: one Undo after editing, switching to another field
and switching back did not undo the edit. Rich-text blur resubscribes its DOM mirror,
which replaces nodes; source blur resubscribes with `CodeMirror.setValue()` even for
unchanged content. The lazy implementation keeps input identity and introduces no
unmount/remount history reset, but does not fix those existing behaviors. This
needs a separate fix; retaining the component alone does not guarantee correct
cross-field undo.

Manual TalkBack navigation remains unverified. Browser keyboard tests check focus
and reachable controls, not Android screen-reader behavior. The exact-height scroll
layout can also adjust as previously unseen HTML replaces estimated placeholders.

Both editor builds and the Svelte type check pass. The browser suite passed 35 tests
across the SvelteKit and native entries, with the existing native-host navigation
skip. All 81 TypeScript unit tests passed using the installed Node entry point.
The standard `just check`/`just test-ts` recipes were attempted but are blocked by
this checkout's incomplete `out/node_modules`: required `sass` and `protoc-gen-es`
commands are absent there, while the working root `node_modules` links to the
shared dependency installation. Those full recipes must not be described as passed.

Arthur's 420 templates also warrant a separate tablet-preview run. The current
preview constructs the template choices and reparses note-type JSON on each render;
it renders one selected card, not all 420 cards. This investigation measured the
attachment's field content in the browser, not its complete collection/template
preview on Android. Do not infer that the preview scales well from these results.

## Reproduction artifacts and source references

Local artifacts are in
`AnkiDroid/build/reports/compose-editor/large-fields/`: `summary.json`,
`lazy-comparison-summary.json`, raw browser results, the Chromium trace, browser
scripts, original/current packaged assets, the attachment, merged-fix metadata,
the temporary native probe patch, and browser/emulator/Pixel test logs. They are
ignored build outputs and have not been uploaded. The original temporary native
probe was removed after collecting the baseline. A permanent 400-field regression
now accompanies the backend implementation.

- [Legacy compact saved state](../AnkiDroid/src/main/java/com/ichi2/anki/FieldEditLine.kt)
- [Native session identifier and in-memory editor state](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorViewModel.kt)
- [Reconciliation and complete draft records](../AnkiDroid/src/main/assets/note-editor/editor.js)
- [Preview scheduling](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorWebSession.kt)
- [Selected-card preview rendering](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/preview/EditorPreviewInput.kt)
- [Backend lazy field construction and loading](/Users/davidallison/Work/Anki-Android-Backend-compose-editor/anki/ts/routes/editor-fields/FieldsOnly.svelte)
- [Collapsible component lifecycle](/Users/davidallison/Work/Anki-Android-Backend-compose-editor/anki/ts/lib/components/Collapsible.svelte)
