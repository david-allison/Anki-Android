# Note editor demo script

**Recorded length: 2 minutes 18 seconds.** The timestamps below follow the final
passing take, rounded to seconds. Readiness waits vary slightly between takes;
preserve readable result holds without slowing individual gestures or typing.

Silent, real-time screen recording for YouTube and Reddit. Follow the requested
style: just a decisive path through the app, with touch indicators. No narration,
music, captions, title cards, zoom effects, or outro. The field names and the
visible results explain the features. Do not speed up the footage.

## Prepare before recording

Use an isolated emulator and a disposable demo collection. Create the note types,
notes, presets, and custom button before recording. Configure the keyboard and
show-touches setting on that emulator before recording; this script requires no
changes to the connected phone.

Start inside a normal **Japanese vocabulary** note: Front **Memorization**, Back
empty, both 20 px. Enter **あんき → 暗記** in Back using the Japanese kana keypad.
All demo fields retain the note type's default font size, including the language examples.

The **Demo** deck contains four prepared notes, each generating one card. Show the
note-type column in the browser and sort by creation order so edits do not reorder
the examples. Use a compact portrait layout, English labels, light theme, and
normal animation speed. Keyboard preparation happens before recording. The
recorded order is vocabulary, field styles, languages, then drawing.

| Note type | Prepared note and fields | What it demonstrates |
| --- | --- | --- |
| Japanese vocabulary | `Front`: **Memorization**, English keyboard hint; `Back`: empty, Japanese hint. Both fields 20 px. | A familiar Front/Back editor; Japanese kana input **あんき → 暗記**. |
| Languages | `Topic`: **Love**, collapsed; `Chinese`, `Traditional Chinese`, and `Japanese`: empty, default font size, no descriptions. Topic is the browser sort field. | Three distinct keyboard languages: Simplified Chinese, Traditional Chinese, and Japanese, switching automatically with the selected field. |
| Field styles | `Text`: **A star worth remembering.**, default font size, description **Write a memorable sentence.**; `HTML`: **A memorable example.** stored as `<b>A memorable example.</b>`, starts in HTML mode; `Details`: **One picture can be a useful memory cue.**, starts collapsed; `Arabic`: **الحب**, right-to-left, field toolbar disabled. Text is the browser sort field. | Field settings, different toolbars, source mode, a collapsed field, and RTL text. |
| Drawing | `Prompt`: **Draw the Anki star**, pinned; `Sketch`: empty, description **Draw something memorable.**, Drawing already enabled as a quick action. Sketch's field toolbar is disabled. | One-tap drawing on a field that is not selected, followed by the finished card. |

The Drawing card front shows Prompt. Its answer shows Prompt and Sketch. The
drawing itself is a slightly tilted card outline containing a blue five-point
star, resembling the AnkiDroid logo. Prepare a gray outline pen and a blue star
pen before recording, with the gray pen selected. Keep the review card styling
simple so the finished drawing is the focal point.

### Toolbar fixture

- **Default:** enough familiar buttons for Stack to visibly form two rows on the
  chosen emulator. Assign it to Field styles → Text initially.
- **Writing:** Bold, Italic, and Edit HTML active. Put the prepared custom button
  first in Available, followed by Underline and the remaining buttons. This keeps
  the demonstrated drag on one screen.
- **Source:** only Edit HTML active. Assign it to Field styles → HTML. The Arabic
  and Sketch fields hide their toolbars independently of the global layout.
- One prepared custom button, labelled **★**, with Before text
  `<span style="color: #0288d1;">★ ` and After text `</span>`. It exists in the
  library but is initially inactive in Writing. Applying it to **star** should
  visibly produce a blue **★ star** within the sentence.
- Field styles → Text initially has Pin field and Attach multimedia content as
  quick actions. Drawing must be visible in Available without a long scroll.

### Keyboard fixture: verify before recording

Assign Chinese the `zh-Hans-CN` hint, Traditional Chinese `zh-Hant-TW`, and Japanese `ja`.
Use a real keyboard that honors these hints, with all required languages already
installed. Rehearse each conversion on the exact emulator image; the tested
keyboard version and layouts are listed below.

For Chinese, use the **Simplified Chinese Pinyin** keyboard: `ai` → **爱**
(U+7231). For Traditional Chinese, switch to the **Traditional Chinese Pinyin**
keyboard: `ai` → **愛** (U+611B). For Japanese, switch to the **12-key kana**
keyboard, tap **あ**, then flick its key left for **い**, and select **愛**.
Enter each two-character reading in a few hundred milliseconds. Wait for the
candidate to become available and select it immediately. Do not add delays between
keystrokes or around candidate selection. Keep the combined **爱 / 愛 / 愛** view
for 2 seconds. Do not impose a multi-second minimum on each keyboard scene or
take screenshots while typing.

The point is to show three distinct keyboard language configurations, including
the two Chinese script variants. Preserve each language's natural conversion;
do not configure both Chinese fields to use the same language or inject text.

## Recorded path

The time window includes navigation, animation, and its stated pause. A “hold”
starts only after the result is fully rendered. Use the visible back arrows to
leave screens, and the current Save action where changes need saving; the app
currently represents some Save actions with a tick.

| Time | Action | What the viewer should have time to see |
| --- | --- | --- |
| **0:00–0:01** | Start inside **Japanese vocabulary**, with Front **Memorization** and Back empty. | Normal 20 px fields; hold for 1 second. |
| **0:01–0:04** | Tap Back. On the Japanese keypad, enter **あんき** and choose **暗記**. | Tap あ, flick わ upward for ん, flick か left for き. No deliberate typing or candidate delay. |
| **0:04–0:05** | Hide the keyboard. | Let the committed **Memorization / 暗記** entry register while closing the keyboard. |
| **0:05–0:06** | Save and return to the prepared Demo deck. | A normal completed note before customization. |
| **0:06–0:09** | Open **A star worth remembering.**; tap Text. | Text, HTML source, and collapsed Details are already configured. |
| **0:09–0:16** | Open **Editor options → Field options**. | Hold all nine options for 2 seconds: Pin, HTML, Collapsed, RTL, Font, Keyboard, Description, Customize toolbar, Quick actions. |
| **0:16–0:21** | Open Font, show the default size, then Cancel. | The size control is visible; the field keeps its default size. |
| **0:21–0:25** | Open Description, show the prepared sentence, Cancel. | Hold the existing description and explanation for 1.5 seconds. |
| **0:25–0:28** | Enable Pin field; use the field counter to choose **HTML**. | The pin becomes active and the named picker changes field focus. |
| **0:28–0:34** | Show Start in HTML mode, close the panel, tap **Edit rich text**. | Source becomes bold text; hold for 1.5 seconds. Its Source toolbar differs from Text's. |
| **0:34–0:40** | Expand Details; show and tap Arabic; return to Text. | An expanded field, right-aligned text, and a field with its toolbar disabled. |
| **0:40–0:46** | Open **Toolbar layout**, show **Scroll / Stack / Hidden**, choose **Stack**. | Hold choices for 1.5 seconds, then show the two-row toolbar. Manage fields and Card types are visible menu entries. |
| **0:46–0:53** | Open **Customize toolbar**, choose **Writing**; briefly open its **…** menu. | The heading, Show toolbar, preset choice, Add preset, and Clone preset. |
| **0:53–0:57** | Drag **★** from Available into Active, then into first place. | Two 350 ms drags with 200 ms after each drop. |
| **0:57–1:05** | Open **Custom buttons → ★**. Cancel the unchanged definition, return, Save. | Hold the label, Before text and After text for 2 seconds. |
| **1:05–1:12** | Open **Quick actions**; drag Drawing from Available to Active; return. | The one-tap explanation and cross-group drag. Changes save immediately. |
| **1:12–1:19** | Long-press **star**, hold the native menu, tap toolbar **★**. | One selection menu, held for 1 second; then the blue **★ star** result for 2 seconds. |
| **1:19–1:21** | Save the customized note. | Return to the browser. |
| **1:21–1:23** | Open **Love**. | A separate prepared example with three language-specific fields at the default size. |
| **1:23–1:24** | Tap Chinese, type **ai**, choose **爱**. | Simplified Chinese Pinyin; enter the reading in a few hundred milliseconds. |
| **1:24–1:26** | Tap Traditional Chinese, type **ai**, choose **愛**. | Traditional Chinese Pinyin switches automatically; no artificial candidate delay. |
| **1:26–1:28** | Tap Japanese, enter **あい**, choose **愛**. | The visibly Japanese 12-key kana layout; tap あ and flick left for い. |
| **1:28–1:32** | Hide the keyboard and frame all three language fields. | Hold **爱 / 愛 / 愛** for 2 seconds. |
| **1:32–1:40** | Open **Field options → Keyboard**, show the language picker, return unchanged. | Hold the picker for 1.5 seconds and show Japanese on the tile. |
| **1:40–1:45** | Use the named picker to choose Chinese, then swipe to Traditional Chinese. | **100 ms sideways gesture**, with no extra hold after the new field receives focus. Close the panel. |
| **1:45–1:48** | Save the language example. | Persist all three natural script variants. |
| **1:48–1:50** | Open **Draw the Anki star**, leaving Prompt selected. | Prompt and the empty Sketch field. |
| **1:50–1:53** | Tap **Drawing: Sketch** without selecting Sketch. | The quick action opens drawing directly for another field. |
| **1:53–2:03** | Draw a tilted gray card outline; choose blue and draw a five-point star inside it. | 2.5 seconds for the card, 4.5 seconds for the star, then a 1.5-second hold. Trace the star's outer outline. |
| **2:03–2:09** | Save drawing, confirm **Done** on Add image. | Hold the inserted drawing for 2 seconds. Prompt remains unchanged. |
| **2:09–2:16** | Open Preview and show the answer. | Hold the finished card with its drawing for 3 seconds. |
| **2:16–2:18** | Return and Save; stop on the browser. | A completed note with no outro. |

## Pacing and emulator test

The recording test is manual-only. Normal instrumented runs skip it unless
`-e editorDemo true` is supplied. Builds with `BuildConfig.CI == true` always skip
it, even with that argument. The guard runs before demo setup, touch-setting
changes, or recording. This was verified locally against a CI-flagged probe:
JUnit returned the skipped status (`-4`) with “Demo recording is disabled in CI.”

- Use one continuous, real-time path. Wait for ordinary navigation to complete;
  dense menus need 1–2 seconds to read. Avoid a uniform delay after every tap.
  Do not double-tap, jiggle the scroll position, or move a pointer
  around a control to explain it.
- Keep touch indicators visible throughout, including drawing. Disable the
  coordinate/debug pointer trail. Long presses should clear the platform's
  threshold once; drags should visibly travel between their source and destination.
- The visible holds on **爱 / 愛 / 愛**, **★ star**, and the **finished
  drawing/card** are protected. If rehearsal overruns, first trim repeated
  navigation and the preset-menu detour. Do not compress the IME conversion,
  drawing gesture, or result holds. The final take fits in 2:18; allow longer readiness waits on slower hosts.
- The script shows all nine Field options, all three layout choices, the toolbar
  configuration controls, presets, Custom buttons, and Quick actions. Manage
  fields and Card types are visible as menu entries; their separate editors are
  outside this short feature demo. This is not an exhaustive tour of every
  built-in formatting button or destructive preset action.
- Implement fixture setup separately from the recorded test. Restore the same
  clean fixture before every take, including note content, toolbar assignments,
  custom-button membership, field defaults, keyboard state, and drawing pen.
- During recording, navigate through real UI actions. Use semantic selectors and
  resource IDs; derive keyboard taps and drawing coordinates from the rehearsed
  layout and canvas bounds. Do not use hidden state changes, clipboard insertion,
  direct DOM edits, or a pre-rendered image to simulate any demonstrated action.
- Wait for the destination UI, input connection, candidate list, drag completion,
  and drawing insertion before beginning the next action. Add presentation holds
  separately from readiness waits. If the app misses a wait, fail the take rather
  than continue tapping against the wrong screen.
- Rehearse the text-selection menu → custom-button path on the exact WebView and
  keyboard versions. The menu must not cover ★, and tapping ★ must wrap the
  selected word. Resolve this before recording, not with an off-camera selection.
- Assert the language results U+7231 / U+611B / U+611B, committed IME composition, saved toolbar
  order, the wrapped word, the drawing's destination field, and the saved notes.
  Preserve the actual end-to-end recording; keep test logs and debug UI out of it.

## Running the instrumented recording

The test is `ComposeNoteEditorDemoTest.recordEditorWalkthrough`. It requires a
disposable emulator and the isolated `.composefieldsprobe` package. It creates a
fresh synthetic collection before each take and restores the app preferences
afterward. It refuses to record on a physical device or a normal AnkiDroid build.

The rehearsed emulator runs Android 14 at 720 × 1600, density 280, with hardware
graphics (`-gpu host -feature -Vulkan`) and 4 GB RAM. Software graphics produced
choppy footage during movement. The keyboard is Gboard
`18.4.1.985164140-release-arm64-v8a`, with these layouts enabled:

- English (US): QWERTY.
- Chinese (Simplified): Pinyin only.
- Chinese (Traditional): Pinyin only; Simplified Chinese output disabled.
- Japanese: 12 keys only, with **Flick only** enabled. No QWERTY layout.

Each field requests its own keyboard language. The test taps the real `a` and
`i` keys on the Chinese keyboards and uses kana taps/flicks for Japanese. It
selects the visible **爱** or **愛** candidate and verifies the committed result.
It does not paste the result or manually switch keyboards on camera.

Build the isolated app and test APK locally:

```sh
./gradlew -I tools/editor-demo.gradle \
  :AnkiDroid:assemblePlayDebug :AnkiDroid:assemblePlayDebugAndroidTest
```

Install both APKs on the prepared emulator, then run the recording. Replace the
serial with that emulator's serial; the commands below use the rehearsal device.

```sh
adb -s emulator-5586 install -r -t \
  AnkiDroid/build/outputs/apk/play/debug/AnkiDroid-play-arm64-v8a-debug.apk
adb -s emulator-5586 install -r -t \
  AnkiDroid/build/outputs/apk/androidTest/play/debug/AnkiDroid-play-debug-androidTest.apk
adb -s emulator-5586 shell am instrument -w -r \
  -e class com.ichi2.anki.noteeditor.compose.ComposeNoteEditorDemoTest \
  -e editorDemo true \
  com.ichi2.anki.composefieldsprobe.tests/com.ichi2.testutils.NewCollectionPathTestRunner
adb -s emulator-5586 pull \
  /sdcard/Android/data/com.ichi2.anki.composefieldsprobe/files/editor-demo \
  /tmp/anki-editor-demo
```

For a section rehearsal, add `-e demoSection introduction`, `languages`, `styles`,
or `drawing`.
The default `all` records the continuous path. Each run replaces `take.mp4` and
writes scene timings to `timeline.json` and the outcome to `result.json`.
`frames.json` marks useful moments for extracting screenshots from the video
afterward; taking screenshots during the walkthrough would slow it down.
Failure screenshots, UI hierarchies, and recorder output are retained for diagnosis.

The recorder restores the emulator's original touch/debug settings even after a
failed take. Android's native touch sprite does not show these injected events,
so the test also records their coordinates and elapsed-clock timestamps in
`touches.json`. Render small translucent touch dots, including over the keyboard:

```sh
python3 tools/render-editor-demo.py /tmp/anki-editor-demo
```

This requires `ffmpeg` and `ffprobe`, with no additional Python packages. It reads
Android's Winscope timestamp metadata to align the dots to the captured frames.
`take.mp4` remains the original recording; `demo.mp4` is the delivery copy with
visible touches. Its 60 fps container repeats static screen frames as needed;
this preserves real time and does not repair dropped frames or speed up footage.
No captions, narration, or other decoration are added.

**Status:** the complete walkthrough passes, including the normal Front/Back
opening, language conversions, 100 ms sideways gesture, saved toolbar order,
custom-button selection, and a preview assertion that the drawing actually loaded.
CI exclusion and the ordinary non-opt-in skip are verified. The touch-marker
export has been checked visually, including the keyboard, drawing, and finished
card. The raw final take captured roughly 60 fps during the card outline and
59 fps during the star; these measurements exclude static holds.
