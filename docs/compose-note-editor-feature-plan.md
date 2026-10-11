# Compose note editor feature completion plan

The screenshot's three global preferences and formatting command families are
implemented in the native Compose UI, using the backend fields-only API. Fields…
and Cards… reuse existing native management screens. The editor remains behind
the development flag; this document records coverage and follow-up work.

## Prerequisite before adding features

The local backend now implements [lazy field initialization](compose-note-editor-large-fields.md).
All field data remains available for complete snapshots and recovery. Inputs mount
near the viewport or when explicitly targeted, and visited inputs remain alive.
The final browser comparison improves 400-field initialization and draft creation
from **627 ms to 77 ms**; the 400-field recovery/save regression passes on the
Pixel 9 Pro. The linked report contains the measurement boundaries and caveats.

Two follow-ups remain before treating large-note UX as fully covered: a manual
TalkBack pass, and a separate tablet-preview check with Arthur's 420 templates.
Keyboard access is covered by browser tests. Investigation also reproduced an
existing undo problem after switching fields in the unchanged backend bundle:
rich-text blur replaces DOM nodes, and source blur calls CodeMirror `setValue()`
even when the content has not changed. Lazy mounting preserves component identity
but does not repair those existing undo semantics. Track that separately from
adding toolbar commands.

## What is present and what is missing

The [desktop manual](https://docs.ankiweb.net/manual/editing#editing-features)
describes the pictured controls. The implementation comparison below comes from
the native toolbar and the backend's fields-only entry point; see the source map.

| Desktop control | Current Compose editor | Remaining difference |
| --- | --- | --- |
| Bold, italic, underline | Rich text and HTML source, with selected toolbar states for rich text. | Mixed selections follow Chromium’s formatting command state. |
| Superscript and subscript | Native toggle buttons; rich text and source wrapping. | — |
| Text colour and highlighting | Native palette, recent colours and hex input; captured selection. | No desktop dropdown layout. |
| Remove formatting | Native checklist for selected text: bold, italic, underline, superscript, subscript, text colour and highlight colour. Choices persist; one Undo restores the edit. | Rich text only; the browser can normalize HTML in the edited field. |
| Bulleted and numbered lists | Native toggle buttons in rich text. | Disabled in HTML source. |
| Alignment and indentation | Native alignment dialog; indentation operates on list items. | Disabled in HTML source. |
| Attachments | Image, camera, audio file, video file and recording via media menu. | — |
| Record audio | Direct microphone shortcut and media-menu entry. | — |
| Equation menu | Native formula dialog: inline/display/chemistry MathJax and three LaTeX wrappers. Tapping rendered maths edits it. | LaTeX wrappers require desktop rendering; no on-device LaTeX compiler. |
| MathJax Preview | Persisted global option; preserves content, source/collapse state and focus when changed. | Changing it rebuilds inputs and resets their undo histories. |
| Shrink Images | Persisted global option; constrains editor display to 250 × 125 CSS pixels. | Does not resize media files or review cards. |
| Auto-close HTML tags | Persisted global option, including source inputs opened later. | Typing assistance only. |
| Fields… | Existing field editor; unsaved HTML follows field identities on return. Deleted-field text remains recoverable until explicitly discarded. | Manual large-note accessibility pass remains. |
| Cards… | Existing template editor; refreshes the tablet preview on return. | Its own preview uses its existing note/template behavior. |
| Collapse field | Field label toggles collapse. | — |
| Sticky pin | Field-options pin retains content after adding a note. | Configurable actions beside fields are deferred. |
| HTML source button | Per field and native toolbar, with live rich-text view and selected toolbar state. | — |

The toolbar also retains cloze insertion and undo/redo. Rich formatting indicators
read the current selection without moving focus or editing HTML. Source mode
clears rich indicators because those buttons insert HTML wrappers there. Checked
states are exposed to accessibility services. Browser tests cover caret/mixed
selections and field/source changes; an emulator test exercises the native bridge
and commands, and light/dark Roborazzi captures the selected buttons.

The fixed **Field options** button sits at the logical end of the toolbar, outside
the scrolling buttons. It opens a standard Material bottom sheet above the
keyboard, hiding formatting controls while open. The sheet shares the editor's
window so the keyboard stays open. The header shows just the field name and note
type, with help and collapse buttons; a subtle horizontal divider separates it
from the controls. Below it, two equal-width sections separated by a vertical
divider contain pin (first) and language controls, both with long-press tooltips.
The help button explains both options without closing the keyboard: pinning keeps
the field's contents after adding a note, while the language hint applies to both
editing and typing answers. It also explains that these settings apply to the named
field on all notes using this note type. The help popup points to the info button,
uses the same pin and language icons, and dismisses on another info tap or a tap
outside; it has no Close button. Its content uses 24 dp padding and 24 dp between
sections, with 8 dp between each heading and description. Icons sit beside the
text to keep the sheet compact; the pin's selected background shows its state.
The sheet has no drag handle or swipe gestures.

`ComposeNoteEditorScreenshotTest` captures the open options, help, and both
long-press tooltips above a simulated keyboard, plus dismissal after hiding the
keyboard and pressing Back. The field area clips the hidden sheet so its header
cannot peek into the navigation-bar inset. A Pixel instrumentation test also
checks the actual two-Back sequence.

Long-name cases cover the deck selector, note type selector, both selectors
together, and the field-options header and help. Selector names truncate with an
ellipsis; the sheet and help wrap the full field and note type names, increasing
their height. The tests use the shared Roborazzi theme/device parameters; light
and dark phone references are recorded locally under
`AnkiDroid/build/outputs/roborazzi/ComposeNoteEditorScreenshotTest/`.

Tapping the language opens the existing locale picker, where **Default** clears an
explicit hint; cancelling preserves it. Tapping the current or another field
closes the panel and restores formatting controls. Programmatic focus restoration
leaves it open. The picker captures its target when opened and retains it through
focus changes and activity recreation. Both settings persist on the note type's field without
rebuilding its inputs. Configurable actions beside individual fields and
field-specific toolbar buttons remain future work.

### Gboard language picker regression (2026-10-11)

Long-pressing Gboard's spacebar can briefly open its language picker and then
close both the picker and keyboard. This reproduces outside AnkiDroid in a
standalone WebView containing a plain `<textarea>`, with JavaScript disabled.
The field's Chinese language hint is not required for the failure.

The Pixel 9 Pro reproduced it with Android 17, WebView **155.0.8059.30** and
Gboard **18.4.1.985164140-release-arm64-v8a**. Copying those runtime APK binaries
to an Android 16 emulator reproduced it there too. No phone settings or regular
AnkiDroid data were changed. All subsequent version changes were on the emulator.

Keeping Gboard 18.4.1 and the editor APK unchanged:

| Emulator case | Result |
| --- | --- |
| Editor, WebView 133.0.6943.137 | Picker stays open: empty field, Chinese text, unfinished Pinyin, HTML source. |
| Editor, WebView 153.0.8010.36 | Same four cases pass. |
| Editor, WebView 155.0.8059.30 | Picker and keyboard close. |
| Native `EditText` in the same activity, Chinese hint | Picker stays open. |
| Stock WebView outside Compose, plain textarea, WebView 155 | Picker and keyboard close, with or without a language hint. |
| Standalone app, plain textarea, WebView 153 | Picker stays open. |
| Standalone app, plain textarea, WebView 155 | Same failure, with or without a language hint. |
| Standalone app, WebView 155, bypass focus-loss callback | Picker stays open. Diagnostic only. |

The mechanism matches Chromium's
[window-focus change](https://github.com/chromium/chromium/commit/f04844f7e01375c586b4ea223e6aa133026cd805):
losing window focus now calls `resetAndHideKeyboard()`. Opening Gboard's picker
takes window focus. The standalone trace records the focus loss, a keyboard-hide
request **3 ms later**, and a null input connection. The picker then closes.
The reset is present in the tested 155 source and absent in the tested 153 source.
Bypassing that callback in the disposable probe prevents the failure; this is
strong causal evidence, although intermediate Chromium builds have not been bisected.

Do not ship a blanket suppression of `onWindowFocusChanged(false)` as the fix:
it also suppresses legitimate provider handling when dialogs or other windows
take focus. Production editor code remains unchanged. The appropriate next step
is an upstream Chromium report with this reproduction, followed by verification
of its fix. Nothing has been published remotely.

The [standalone reproduction](../tools/webview-keyboard-probe/src/local/webviewkeyboardprobe/MainActivity.java)
has no Anki or Compose dependencies, JavaScript, network permission, or collection
access. Build with JDK, Android SDK platform 36, build-tools 35.0.0 and an existing
Android debug keystore:

```sh
bash tools/webview-keyboard-probe/build.sh
adb -s emulator-5554 install -r tools/webview-keyboard-probe/build/keyboard-probe.apk
adb -s emulator-5554 shell am start -S -n local.webviewkeyboardprobe/.MainActivity
```

Set `ANDROID_SDK_ROOT` or `ANDROID_HOME` before building. With English and Chinese
Pinyin already configured in Gboard, tap the textarea, then long-press spacebar.
Expected: the language picker remains available. On the affected runtime, it
briefly appears and disappears along with the keyboard. The optional intent
extra `--ez hint true` supplies `EditorInfo.hintLocales = zh`.
The diagnostic control `--ez skipFocusLoss true` skips the provider callback;
it is deliberately confined to this probe. `KeyboardProbe` logcat entries record
provider version, window focus and connection availability without recording text.
Local screenshots, logs and the exploratory editor-test patch are retained in
`AnkiDroid/build/reports/compose-editor/keyboard-language/`. The editor's four-case
checks passed on 133 and 153, and failed on 155 before any production change.
The exploratory tests are not added to the normal suite while the provider bug
remains unresolved. The standalone probe and unchanged debug app both build.

## Three settings in the screenshot

### Shrink Images

Desktop's option constrains images **inside the editor**. It does not compress the
media file or change its size on review cards. `ImageOverlay.svelte` installs the
root `shrink-image` class and the CSS size variables; desktop supplies limits of
250 × 125 CSS pixels. The shared field stylesheet applies these constraints while
preserving aspect ratio.

The fields-only page sets these variables directly, without loading the desktop
image overlay. The native option changes display only; saved HTML and media bytes
remain unchanged. Image resizing and floating controls are separate work.

### MathJax Preview

`mathjaxConfig.enabled` already defaults to `true`. The backend has an existing
browser test for rendered equations and canonical MathJax delimiters in snapshots.
This is field-local rendering, independent of the tablet card preview.

The desktop overlay is not mounted. Instead, the fields API reports equation
activation and a captured selection to a native formula dialog. Insertion and
replacement use backend conversion helpers and canonical delimiters in snapshots.
Formula code remains lazy. Cancel leaves content unchanged; stale targets are
rejected, and clearing an existing equation removes only that equation.

Changing this option remounts editing inputs. The adapter restores content and
focus, reacquires input handles and composition listeners, and invalidates old
selection bookmarks. **Undo histories reset** when this option changes.

### Auto-close HTML tags

`PlainTextInput.svelte` connects the preference to CodeMirror's `autoCloseTags`
option. CodeMirror stays lazy while source mode is closed. This changes typing
assistance, not saved HTML repair.

All three options use the existing collection keys `shrinkEditorImages`,
`renderMathjax`, and `closeHTMLTags`, defaulting to true. Native initialization
reads them alongside note data and sends them in the initial fields configuration;
there is no additional startup RPC. Changing a preference does not save the note.

## Implementation and validation

Changes are committed locally in separate steps: performance prerequisites,
backend preferences, backend commands, native preferences, native controls, and
native field/template navigation. The capability marker is now version 4; Android
rejects an older bundle rather than silently ignoring commands.

The backend browser suite covers both entry points, including settings, source
wrapping, captured/stale targets, rich formatting, equation insertion/editing,
undo and large notes. The current run has **103 passing tests and one intentional
standalone-page navigation skip**. Svelte checking reports no errors or warnings.
The full backend `just check` remains blocked by missing local Sass/ESLint
executables in `out/node_modules/.bin`; the latest attempt stops at Sass.

All 30 editor state tests pass, including field identity mapping, deleted text
recovery and template-only reloads. Emulator regressions cover global preferences,
colour/equation targeting,
field/template navigation, formatting indicators and selective erasing. The
selective-erasing case covers Cancel, restored choices and targets after recreation,
focus changes and one-step Undo. Two session unit tests check readiness and avoid
redundant settings updates that invalidate restored command targets. Roborazzi
has 22 passing light/dark cases covering field options, help, both tooltips,
keyboard/Back dismissal, long names, the global menu and selected toolbar states.
The colour/equation flow passes emulator instrumentation, with screenshots inspected. An attempted Robolectric dialog
capture gets stuck in window relayout in this environment; that failing test is
not committed.

Opening Fields… or Cards… checkpoints the draft first. Return reloads the note
type; field changes map values and dirty baselines by stable field identity, while
template-only changes refresh preview without replacing fields. Deleted text is
kept in the draft with Copy, Discard, and Keep draft and close actions. Saving is
blocked until the deleted text is explicitly resolved. Recovery also handles a
process death while field management is open.

## Selective erasing and equation undo

With text selected, tapping the eraser opens a native checklist. Cancel leaves the
draft unchanged. The checked formats survive dialog recreation and are remembered
after applying. The captured destination still goes through the existing session,
generation, revision and bookmark checks. With only a caret, the eraser retains
the browser's existing remove-format behavior.

The backend lazily loads the eraser helper. It applies Anki's range-splitting
engine to an inert copy of the active field, retains unselected formats and
attributes, and inserts the result as one browser edit. Undo/Redo restore that
operation. Links, images, list/paragraph structure and equations have browser
coverage. Equation rendering is omitted from the copied HTML and equations count
as single units when restoring the selection. Chromium may normalize markup in
the edited field, and its rendered media/equations are recreated; other fields
are untouched. Stylesheet-provided formatting is outside this operation.

Testing also reproduced an existing equation redo defect using the original
insertion command: replace a field containing an equation, Undo, then Redo could
leave both the old and replacement equations. A separate backend fix preserves
the intact MathJax frame/component when Undo reconnects it. Regression cases cover
replacement, Undo and Redo in both entry points.

## Native UI and performance constraints

The formatting toolbar scrolls horizontally; field options stay fixed at its end.
Colours, selective removal, alignment and equations open native dialogs. Editor
options remain separate from changes to note content. The toolbar reports the
active field, selection/composition, source mode and active rich-text formats.
Bold/italic/underline, superscript/subscript, lists and source use accessible
checked states.

Reuse the existing session, generation, revision and bookmark checks for delayed
actions. A picker must apply to its captured target or report that it became stale.
Continue blocking formatting during composition; no new global freeze or save
coordination mechanism is needed.

Preferences arrive with initial note data. Do not import the desktop
`NoteEditor`, toolbar, profile/RPC wiring or overlays just to obtain command logic.
Keep pickers and equation editors out of the opening path. For settings that
remount fields, explicitly test focus and input listeners after the change.

Measure blank-note opening separately from opening a note containing maths or large
images. Compare first and repeat Add-to-interactive latency and dropped frames
against the current retained build, using the same build type and interaction.
Do not use the best archived preload prototype as the retained baseline. Re-run
the opening benchmark after settings integration and after the formatting group;
avoid a performance claim until measured.

## Separate parity work

The existing Android toolbar also provides headings, font size, horizontal rules
and custom toolbar actions. These are outside the screenshot but should receive
a separate migration audit before making Compose the default. Likewise, assess a
phone preview entry point separately from the existing tablet side-by-side view.
Neither requires copying the desktop toolbar layout.

## Source map

Native implementation:

- [Toolbar, media choices and field options](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/NoteEditorScreen.kt)
- [Fields document and supported actions](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/web/WebEditorDocument.kt)
- [Native command targeting and screen navigation](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/ComposeNoteEditorActivity.kt)
- [Existing Android toolbar features](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/Toolbar.kt)

Local backend sources, including our fields-only adaptation:

- [FieldsOnly and its command dispatch](/Users/davidallison/Work/Anki-Android-Backend-compose-editor/anki/ts/routes/editor-fields/FieldsOnly.svelte)
- [Fields API contract](/Users/davidallison/Work/Anki-Android-Backend-compose-editor/anki/ts/routes/editor-fields/types.ts)
- [Desktop options and preference keys](/Users/davidallison/Work/Anki-Android-Backend-compose-editor/anki/ts/routes/editor/editor-toolbar/OptionsButton.svelte)
- [Image size initialization](/Users/davidallison/Work/Anki-Android-Backend-compose-editor/anki/ts/routes/editor/image-overlay/ImageOverlay.svelte)
- [Shared image CSS](/Users/davidallison/Work/Anki-Android-Backend-compose-editor/anki/ts/lib/editable/editable-base.scss)
- [HTML auto-close configuration](/Users/davidallison/Work/Anki-Android-Backend-compose-editor/anki/ts/routes/editor/plain-text-input/PlainTextInput.svelte)
- [MathJax defaults and conversion](/Users/davidallison/Work/Anki-Android-Backend-compose-editor/anki/ts/lib/editable/mathjax-element.svelte.ts)
- [MathJax preview input remounting](/Users/davidallison/Work/Anki-Android-Backend-compose-editor/anki/ts/routes/editor/EditorField.svelte)
- [Desktop equation interaction](/Users/davidallison/Work/Anki-Android-Backend-compose-editor/anki/ts/routes/editor/mathjax-overlay/MathjaxOverlay.svelte)
- [Desktop selective formatting removal](/Users/davidallison/Work/Anki-Android-Backend-compose-editor/anki/ts/routes/editor/editor-toolbar/RemoveFormatButton.svelte)
- [Desktop list and alignment commands](/Users/davidallison/Work/Anki-Android-Backend-compose-editor/anki/ts/routes/editor/editor-toolbar/BlockButtons.svelte)
- [Existing fields browser tests](/Users/davidallison/Work/Anki-Android-Backend-compose-editor/anki/ts/tests/e2e/editor-fields.spec.ts)
