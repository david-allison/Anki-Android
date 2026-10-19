# Note editor demo script

Silent, real-time app walkthrough for YouTube and Reddit, with visible touches.
No narration, music, captions, title cards, zoom effects, or outro. Move decisively,
pause for dense settings and meaningful results, and do not speed up the footage.

**Length: 1:41.** Timestamps below are measured from the passing full take.

## Prepare before recording

Use the isolated emulator and disposable Demo collection. All fields retain their
default font size. Prepare these note types, the two example notes, toolbar
presets, custom button, drawing pens, and the available Japanese tag off camera.

| Note type | Fields and prepared content | Purpose |
| --- | --- | --- |
| English/Japanese | **English**, keyboard language **Default**; **Japanese**, keyboard language **Japanese**. Initially no note. | Create **memorization / 暗記** on camera, add the **Japanese** tag, show the completed note, save, and return to the Deck Picker. |
| Astronomy | **Question**: Which star is closest to Earth?, description Ask one clear question.; **Answer**: `<b>The Sun</b>`, initially normal mode; **Notes**: Sunlight takes about 8 minutes to reach Earth., initially collapsed; **Arabic answer**: الشمس, RTL, field toolbar disabled. All keyboard hints remain Default. | Toggle the Answer into HTML and back, then show field settings, toolbar layouts, presets, custom buttons, and Quick actions. |
| App icons | **Logo**: empty, Drawing quick action enabled, field toolbar disabled; **App**: AnkiDroid. Both keyboard hints remain Default. | Draw the question in the first field; reveal the app name from the second field as the answer. |

The first frame is the **Deck Picker**. The opening creates a real new note,
rather than editing a prepared vocabulary card. After saving, leave the empty
next-note form and return to the Deck Picker before opening the prepared examples
in the card browser. The browser shows note type names so the initially empty
Logo example can be opened as **App icons**.

There is no separate Chinese/Traditional/Japanese section. The opening itself
demonstrates switching from the user's default keyboard to Japanese.

### Prepared toolbar and drawing controls

- **Default**: enough familiar buttons for Stack to form two rows; the prepared
  **★** custom button is first in Available.
- **Writing**: ★, Bold, Italic, and Edit HTML active, with ★ first.
- **Highlight** is a second custom button (`<mark>` / `</mark>`), left inactive
  near the bottom of Available so the scroll has a clear destination.
- **Source**: only Edit HTML active; assigned to the Answer field, which starts in normal mode.
- **★** inserts Before text `<span style="color: #0288d1;">★ ` and After text
  `</span>`. Applying it to the selected word **star** produces blue **★ star**.
- Question initially has Pin and Attach media quick actions; Drawing is available
  for the demonstrated cross-group drag.
- Prepare gray 7 px and blue 12 px drawing pens, with gray selected.
- Draw a slightly tilted card and an uneven blue five-point star. Use separate
  card strokes, small gaps/overlaps, mild wobble, unequal star points, and slower
  turns. Keep it recognizably hand-drawn.

### Keyboard preparation

Use Gboard with English (US) QWERTY as its default and Japanese **12-key kana**
with **Flick only** enabled. The English field has no explicit language hint;
the Japanese field requests `ja`. Do not use Japanese QWERTY.

Cache key and Save/Back button locations before recording. After the Add screen
is ready, allow 200 ms before selecting the first field. Type **memorization**
rapidly on the real English keyboard. For **あんき → 暗記**, tap あ, flick わ upward for ん, then flick
か left for き. Select the real conversion candidate as soon as it appears,
dismiss the keyboard, then add the Japanese tag. Show the completed note for
750 ms after confirming the tag, then save. Keep this order:
**memorization → 暗記 → Japanese tag → completed note → Save**.

## Recorded path

| Time | Action | What the viewer should see |
| --- | --- | --- |
| 00:00–00:12 | Deck Picker → Add → English/Japanese; enter memorization, then 暗記; add Japanese tag; Save → Deck Picker. | Fast typing, then a 750 ms view of the completed tagged note before saving. |
| 00:12–00:39 | Open Astronomy. Use the Answer toolbar toggle: normal → HTML → normal. Then show field options. | Real field names and study content; demonstrate HTML once through the toggle. Show Font, Description, Pin, collapsed Notes, and the RTL Arabic answer without changing the HTML field setting. |
| 00:39–00:54 | Show toolbar layouts, select Writing, drag ★ into Active and reorder it. | Brisk 350 ms drags with 200 ms after each drop. |
| 00:54–01:08 | Show the ★ definition and Quick actions; drag Drawing into Active. | Readable settings and the one-tap explanation. |
| 01:08–01:14 | Long-press star, hold the native menu, tap ★, then Save. | One selection menu for 1 second, then blue ★ star for 2 seconds. |
| 01:14–01:17 | Open App icons; keep App selected and tap Drawing beside Logo. | A quick action targeting an unselected first field. |
| 01:17–01:31 | Draw the card and star, Save, confirm Done. | An imperfect hand-drawn logo inserted into Logo; App remains AnkiDroid. |
| 01:31–01:41 | Preview the drawing as the question; show the answer. Return and Save. | The logo question for 2 seconds, then the AnkiDroid answer for 4 seconds before returning and saving. |

Use explicit holds for readable settings and completed results. Do not pad scenes
to minimum lengths or add uniform delays between keystrokes. Ordinary navigation
continues once ready. Let the completed vocabulary note, source/rich-text result,
custom button, and finished logo card register before moving on.

Use the field-name picker to select **Arabic answer** with the keyboard closed.
After closing field options, expand **Notes** and hold its revealed content for
1.5 seconds. Expansion should only reveal the content; it should neither select
Notes nor open the keyboard. The RTL answer and its hidden toolbar remain visible
during this hold. Then select Question to continue with toolbar customization.

Pause briefly before opening **Customize toolbar**, then let the screen settle
for 1 second. Edit **Default** first: drag ★ from Available into Active, then above
Bold. Verify the actual row order and show the result for 750 ms before switching
to **Writing**. Show its overflow menu, then scroll to the inactive **Highlight**
custom button and hold for 1 second before opening **Custom buttons**. Saving must
preserve the changes to Default as well as selecting Writing for the field.

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
