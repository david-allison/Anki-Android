# Note editor demo script

**Target length: about 2 minutes 25 seconds.** Allow up to 2:45 after rehearsal if
keyboard conversion or a dense settings screen needs more time. These are
planning timestamps, not measured timings: preserve the readable pauses before
trying to hit the target.

Silent, real-time screen recording for YouTube and Reddit. Follow the requested
style: just a decisive path through the app, with touch indicators. No narration,
music, captions, title cards, zoom effects, or outro. The field names and the
visible results explain the features. Do not speed up the footage.

## Prepare before recording

Use an isolated emulator and a disposable demo collection. Create the note types,
notes, presets, and custom button before recording. Configure the keyboard and
show-touches setting on that emulator before recording; this script requires no
changes to the connected phone.

Start in the card browser, filtered to a **Demo** deck containing exactly these
three notes, in this order. Each note type generates one card. Show the note-type
column and sort by creation order so editing text does not reorder the examples.
Use a compact portrait layout, English app labels, light theme, and the normal animation speed.
The first frame already shows the three examples; no setup or loading footage.

| Note type | Prepared note and fields | What it demonstrates |
| --- | --- | --- |
| Languages | `Topic`: **Love**, collapsed; `Chinese`, `Traditional Chinese`, and `Japanese`: empty, 36 px, no descriptions. Topic is the browser sort field. | Three language hints, real keyboard conversion, and the same character in three fields. |
| Field styles | `Text`: **A star worth remembering.**, 24 px, description **Write a memorable sentence.**; `HTML`: **A memorable example.** stored as `<b>A memorable example.</b>`, starts in HTML mode; `Details`: **One picture can be a useful memory cue.**, starts collapsed; `Arabic`: **الحب**, right-to-left, field toolbar disabled. Text is the browser sort field. | Field settings, different toolbars, source mode, a collapsed field, and RTL text. |
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

Assign Chinese the `zh` hint, Traditional Chinese `zh-Hant-TW`, and Japanese `ja`.
Use a real keyboard that honors these hints, with all required languages already
installed. Rehearse each conversion on the exact emulator image and record its
keyboard version and chosen layouts with the future test fixture.

For the two Chinese fields, type `ai` using the configured Pinyin layouts and
select **愛**. For Japanese, type `ai` using a Romaji layout, obtain **あい**, and
select **愛**. Allow the candidate strip to settle before tapping the candidate.

**The required character is 愛 (U+611B) in all three fields.** Simplified **爱**
(U+7231) is not an acceptable substitute. The generic Chinese fixture must use a
Chinese input configuration that actually offers 愛; verify this before fixing
the test's key sequence. Do not label it “Simplified Chinese,” assume the default
candidate is correct, or silently paste/inject the character in the recording.
If this configuration cannot produce the required candidate, resolve the fixture
before recording rather than disguising the result.

## Recorded path

The time window includes navigation, animation, and its stated pause. A “hold”
starts only after the result is fully rendered. Use the visible back arrows to
leave screens, and the current Save action where changes need saving; the app
currently represents some Save actions with a tick.

| Time | Action | What the viewer should have time to see |
| --- | --- | --- |
| **0:00–0:03** | Hold briefly on the three prepared browser entries, then open **Love**. | These are ready-made examples, with different note types. No introductory explanation. |
| **0:03–0:05** | Let the Languages editor settle; tap **Chinese**. | The three named language fields and the keyboard appearing. |
| **0:05–0:11** | Type `ai`; select **愛** from the Chinese candidates. | Keep the candidate strip visible for about 1 second before selection, then the committed character for about 1 second. |
| **0:11–0:17** | Tap **Traditional Chinese**, type `ai`, select **愛**. | The field and keyboard context change without opening keyboard settings. Same candidate/result pauses. |
| **0:17–0:23** | Tap **Japanese**, type `ai` → **あい**, select **愛**. | Let **あい** and the conversion candidate register before committing. |
| **0:23–0:26** | Hide the keyboard; frame the three language fields together. | Hold the three completed **愛** fields for 2 seconds. This is the language section's payoff. |
| **0:26–0:34** | Open **Editor options → Field options → Keyboard** for Japanese. Show the language picker, then return without changing the hint. | About 1.5 seconds on the picker and a clear view of **Japanese** on the Keyboard tile after returning. |
| **0:34–0:39** | Tap the field counter, choose **Chinese** by name, then swipe the main panel to **Traditional Chinese**. Return to the editor. | The named-field picker, the swipe transition, and the two different language labels. Do not switch fields inside a subscreen. |
| **0:39–0:42** | Save the language note and open **A star worth remembering.** from the browser. | A clean transition to the second prepared note type. |
| **0:42–0:45** | Briefly show the styled note: Text, HTML source, and the collapsed Details row. Tap Text. | The different field defaults already work when the note opens. |
| **0:45–0:49** | Open **Editor options → Field options**. | Hold the complete overview for 2 seconds: Pin field, Start in HTML mode, Start collapsed, Right-to-left text, Font, Keyboard, Description, Customize toolbar, and Quick actions. If needed, make one deliberate scroll to expose the lower entries. |
| **0:49–0:54** | Open **Font**, change **24** to **28**, Save. | The size control and the resulting **28 px** summary. No tour of individual keystrokes. |
| **0:54–0:58** | Open **Description**, show the prepared sentence, Cancel. | Hold the existing description and its explanation for about 1.5 seconds. No unnecessary typing. |
| **0:58–1:01** | Turn on **Pin field**, then use the field counter to choose **HTML**. | The pin's active state; the field picker moves directly to the requested field. |
| **1:01–1:07** | Show HTML's enabled **Start in HTML mode** setting, close the panel, then tap its mode switch (**Edit rich text**). | Source becomes formatted text. Hold the rendered bold sentence for 1.5 seconds. Its compact Source toolbar differs from Text's toolbar. |
| **1:07–1:13** | Expand **Details**, scroll just enough to show **Arabic**, and tap Arabic. Return to Text. | The collapsed field opens; Arabic is right-aligned and has no formatting toolbar. Allow about 1 second for each result. |
| **1:13–1:18** | With Text selected, open **Editor options → Toolbar layout**, show **Scroll / Stack / Hidden**, choose **Stack**. | About 1.5 seconds on the choices, then the actual two-row toolbar. The options menu also exposes Manage fields and Card types. |
| **1:18–1:23** | Open **Field options → Customize toolbar**; choose **Writing** from Preset. Briefly open its **…** menu, then dismiss it. | The field-specific heading, Show toolbar, preset choice, and preset actions including Add preset and Clone preset. |
| **1:23–1:28** | Drag **★** from **Available** into **Active**, then move it to the first position. | Two deliberate drags, about 0.8 seconds each, with a short pause after each drop. The destination and new order must be obvious. |
| **1:28–1:36** | Tap the fixed **Custom buttons** row; open **★**. Show its label, Before text, and After text. Cancel the unchanged edit, return to Customize toolbar, then Save. | Hold the button definition for 2 seconds. This explains the upcoming result without creating a button on camera. Keep the prepared library entry unchanged. |
| **1:36–1:43** | Open Text's **Quick actions**; drag **Drawing** from Available into Active. Return through the overview to the editor. | The one-tap explanation, the cross-group drag, and Drawing now beside the Text field. Quick actions save immediately. |
| **1:43–1:50** | Long-press **star** in Text. Hold the native selection menu, then tap the toolbar's **★** button. | Exactly one deliberate text-selection menu demonstration, held for about 1 second. Then hold the blue **★ star** result for 2 seconds. Preserve the selection when tapping the custom button. |
| **1:50–1:54** | Save, return to the browser, and open **Draw the Anki star**. | The third prepared note type, with Prompt and the empty Sketch field. |
| **1:54–1:57** | Keep Prompt selected and tap the **Drawing** quick action beside **Sketch**. | Drawing opens directly for Sketch without first selecting Sketch or opening the attachment sheet. |
| **1:57–2:09** | Draw a slightly tilted gray card outline, select the prepared blue pen, and draw a five-point star inside the card. | About 2.5 seconds for the card and 4–5 seconds for the star. Hold the completed logo-like drawing for 1.5 seconds. Trace the star's outer outline, not a crossing pentagram. |
| **2:09–2:15** | Save the drawing back to Sketch; hide the keyboard if necessary and frame the image. | Hold the inserted star-on-card drawing for 2 seconds. The image belongs to Sketch, while Prompt remains unchanged. |
| **2:15–2:22** | Open card Preview, briefly show the prompt, then show the answer. | Hold the finished card with the drawing for 3 seconds. Finish the demonstration on an outcome, not a settings screen. |
| **2:22–2:25** | Return to the editor and Save. Stop once the browser has settled. | A completed note, with no outro or extra navigation. |

## Pacing and later emulator test

- Use one continuous, real-time path. Ordinary navigation needs roughly
  250–400 ms after settling; dense menus need 1–2 seconds. Avoid a uniform delay
  after every tap. Do not double-tap, jiggle the scroll position, or move a pointer
  around a control to explain it.
- Keep touch indicators visible throughout, including drawing. Disable the
  coordinate/debug pointer trail. Long presses should clear the platform's
  threshold once; drags should visibly travel between their source and destination.
- The visible holds on **three 愛 characters**, **★ star**, and the **finished
  drawing/card** are protected. If rehearsal overruns, first trim repeated
  navigation and the preset-menu detour. Do not compress the IME conversion,
  drawing gesture, or result holds. A clear 2:35 is better than a rushed 2:25.
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
- Assert the three exact U+611B values, committed IME composition, saved toolbar
  order, the wrapped word, the drawing's destination field, and the saved notes.
  Preserve the actual end-to-end recording; keep test logs and debug UI out of it.

**Status:** script and fixture specification only. The timed emulator recording
test and video have not yet been implemented or rehearsed.
