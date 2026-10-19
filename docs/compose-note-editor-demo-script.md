# Note editor demo script

Silent, real-time app walkthrough for YouTube and Reddit, with visible touches.
No narration, music, captions, title cards, zoom effects, or outro. Move decisively,
pause for dense settings and meaningful results, and do not speed up the footage.

**Length: 1:57.** Timestamps below are measured from the passing full take.

## Prepare before recording

Use the isolated emulator and disposable Demo collection. All fields retain their
default font size. Prepare these note types, the two example notes, toolbar
presets, custom button, drawing pens, and the available Japanese tag off camera.

| Note type | Fields and prepared content | Purpose |
| --- | --- | --- |
| English/Japanese | **English**, keyboard language **Default**; **Japanese**, keyboard language **Japanese**. Initially no note. | Create **memorization / 暗記** on camera, add the **Japanese** tag, save immediately, and return to the Deck Picker. |
| Field styles | **Text**: A star worth remembering., description Write a memorable sentence.; **HTML**: `<b>A memorable example.</b>`, initially source mode; **Details**: One picture can be a useful memory cue., initially collapsed; **Arabic**: الحب, RTL, field toolbar disabled. All keyboard hints remain Default. | Field settings, toolbar layouts, presets, custom buttons, and Quick actions. |
| App icons | **Logo**: empty, Drawing quick action enabled, field toolbar disabled; **App**: AnkiDroid. Both keyboard hints remain Default. | Draw the question in the first field; reveal the app name from the second field as the answer. |

The first frame is the **Deck Picker**. The opening creates a real new note,
rather than editing a prepared vocabulary card. After saving, leave the empty
next-note form and return to the Deck Picker before opening the prepared examples
in the card browser. The browser shows note type names so the initially empty
Logo example can be opened as **App icons**.

There is no separate Chinese/Traditional/Japanese section. The opening itself
demonstrates switching from the user's default keyboard to Japanese.

### Prepared toolbar and drawing controls

- **Default**: enough familiar buttons for Stack to form two rows.
- **Writing**: Bold, Italic, and Edit HTML active; the prepared **★** custom button
  is first in Available.
- **Source**: only Edit HTML active; assigned to the HTML field.
- **★** inserts Before text `<span style="color: #0288d1;">★ ` and After text
  `</span>`. Applying it to the selected word **star** produces blue **★ star**.
- Text initially has Pin and Attach media quick actions; Drawing is available
  for the demonstrated cross-group drag.
- Prepare gray 7 px and blue 12 px drawing pens, with gray selected.
- Draw a slightly tilted card and an uneven blue five-point star. Use separate
  card strokes, small gaps/overlaps, mild wobble, unequal star points, and slower
  turns. Keep it recognizably hand-drawn.

### Keyboard preparation

Use Gboard with English (US) QWERTY as its default and Japanese **12-key kana**
with **Flick only** enabled. The English field has no explicit language hint;
the Japanese field requests `ja`. Do not use Japanese QWERTY.

Cache key locations before recording. Type **memorization** rapidly on the real
English keyboard. For **あんき → 暗記**, tap あ, flick わ upward for ん, then flick
か left for き. Select the real conversion candidate as soon as it appears and
tap Save immediately afterward. Do not add a result hold or dismiss the keyboard
before saving. Add the Japanese tag before entering the fields so tagging does
not interrupt that final save.

## Recorded path

| Time | Action | What the viewer should see |
| --- | --- | --- |
| 00:00–00:11 | Deck Picker → Add → English/Japanese; add Japanese tag; enter both fields; Save → Deck Picker. | A real new tagged note, default field sizes, fast typing and immediate save. |
| 00:11–00:46 | Open the prepared Field styles note from the browser. | All field options, font setting without changing its default, Description, Pin, source/rich text, collapsed and RTL fields. |
| 00:46–01:02 | Show toolbar layouts, select Writing, drag ★ into Active and reorder it. | Brisk 350 ms drags with 200 ms after each drop. |
| 01:02–01:17 | Show the ★ definition and Quick actions; drag Drawing into Active. | Readable settings and the one-tap explanation. |
| 01:17–01:26 | Long-press star, hold the native menu, tap ★, then Save. | One selection menu for 1 second, then blue ★ star for 2 seconds. |
| 01:26–01:32 | Open App icons; keep App selected and tap Drawing beside Logo. | A quick action targeting an unselected first field. |
| 01:32–01:48 | Draw the card and star, Save, confirm Done. | An imperfect hand-drawn logo inserted into Logo; App remains AnkiDroid. |
| 01:48–01:57 | Preview the drawing as the question; show the answer. Return and Save. | The logo first, then AnkiDroid as the answer. Hold the finished card for 3 seconds. |

Keep meaningful result holds separate from readiness waits. Do not add uniform
delays between keystrokes or after candidate selection. Ordinary navigation should
continue once ready. The source/rich-text result, custom button, and finished card
need time to register.

The test must assert field names and Default language on English, both saved
vocabulary values, the Japanese tag, saved toolbar order, the wrapped selection,
drawing in Logo, unchanged App, a loaded question image with no app name, and
AnkiDroid appearing only after Show answer.

## Running the instrumented recording

The test is `ComposeNoteEditorDemoTest.recordEditorWalkthrough`. It requires a
disposable emulator and the isolated `.composefieldsprobe` package. It creates
fresh synthetic data and restores app preferences afterward. It refuses to record
on a physical device or a normal AnkiDroid build.

Normal instrumented runs skip it unless `-e editorDemo true` is supplied.
`BuildConfig.CI == true` always skips it, including with explicit opt-in.
The guard runs before setup or settings changes. The CI gate has been verified
locally with a CI-flagged probe returning JUnit skipped status `-4`.

The rehearsed emulator runs Android 14 at 720 × 1600, density 280, with hardware
graphics (`-gpu host -feature -Vulkan`) and 4 GB RAM. The rehearsed Gboard version is
`18.4.1.985164140-release-arm64-v8a`. Emulator preparation does not change the
connected phone's settings or live AnkiDroid data.

Build and install the isolated app and tests:

```sh
./gradlew -I tools/editor-demo.gradle \
  :AnkiDroid:assemblePlayDebug :AnkiDroid:assemblePlayDebugAndroidTest
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

For a section rehearsal, add `-e demoSection introduction`, `styles`, or `drawing`.
The default `all` records one continuous path. Each run replaces the emulator's
artifact directory. Preserve a successful take before running another.

The test writes `take.mp4`, scene timings in `timeline.json`, the outcome in
`result.json`, and timestamped input in `touches.json`. `frames.json` marks useful
moments for screenshots extracted after recording. Failure screenshots and UI
hierarchies are diagnostic artifacts, not part of the delivered video.

Android's native touch sprite does not show UiAutomation-injected events. Render
small touch dots from the actual input coordinates and elapsed-clock timestamps:

```sh
python3 tools/render-editor-demo.py /tmp/anki-editor-demo
```

This requires `ffmpeg` and `ffprobe`, with no additional Python packages. Android's
Winscope metadata aligns input with video. `take.mp4` stays untouched; `demo.mp4`
is the delivery copy. Its 60 fps container repeats static frames as needed,
preserving real time without concealing or repairing rendering stalls. Original
emulator touch/debug settings are restored even after a failed take.
