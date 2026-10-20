# Compose note editor: demo feedback checklist

Recorded 2026-10-20. **15 action items; 11 resolved.**

This records the tester's demo feedback and the code-based assessments discussed
in chat. Recommendations remain proposals unless an explicit decision is
recorded. The review inspected the Compose editor, its tests and demo script,
and the linked backend's field and MathJax components. The reported video
behavior has not been reproduced on-device for this review, and save latency has
not been measured.

AnkiDroid source references correspond to the pre-rebase revision
`f569a74c4cadc8bf93019b484bf0e026fc300c95`. File paths may move during rebasing.

Check an item when its decision and any agreed implementation or verification
are complete. A proposal can also be resolved by explicitly declining it; record
that decision and its reason. Keep the item numbers stable. Existing capabilities
do not automatically resolve feedback about their presentation or discoverability.

Related checklists:

- [Earlier tester feedback](compose-note-editor-feedback-checklist.md)
- [Toolbar and field-panel feedback](compose-note-editor-toolbar-feedback-checklist.md)

## Feedback, assessments, and completion actions

- [x] **01. Restore the card count in the success snackbar.**

  **Feedback:** The snackbar says “Note added” instead of “1 card added”.

  **Assessment: agree.** The save result already includes `cardCount`, but the
  Compose activity ignores it. A note can generate multiple cards, so the count
  provides useful confirmation.

  **Completion action:** Use the actual number of generated cards and the
  existing localized count message. Verify singular and plural results,
  including a note that generates multiple cards.

  **Implemented 2026-10-20:** The snackbar uses `result.cardCount` with the
  legacy editor's localized card-count message and punctuation handling.
  Removed the unused “Note added” string.

  **Verification:** All 61 existing `EditorViewModelTest` and
  `EditorWebSessionTest` cases pass, including the Add/save flow. Inspected the
  translation's singular and plural branches and the backend count passed
  through `EditorSaveResult.Added`; no device reproduction was performed.

  **Evidence:** [ComposeNoteEditorActivity.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/ComposeNoteEditorActivity.kt),
  `handleSaveResult()`; [EditorState.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorState.kt),
  `EditorSaveResult.Added.cardCount`; [NoteEditorFragment.kt](../AnkiDroid/src/main/java/com/ichi2/anki/NoteEditorFragment.kt),
  `onNoteAdded()` uses `TR.importingCardsAdded(count)`.

- [x] **02. Increase the options menu's screen-edge spacing.**

  **Feedback:** The options dropdown is too close to the screen edge.

  **Assessment: agree.** The menu has no explicit positioning offset, and the
  existing screenshot shows a very narrow right margin.

  **Completion action:** Give the popup a consistent screen inset. Verify its
  placement in LTR, RTL, and narrow windows without clipping menu content.

  **Implemented 2026-10-20:** The options menu uses Material's popup positioning
  with a 16dp minimum surface inset and a width limit for narrow windows. It
  stays below the wrench, clamping within the window instead of flipping across
  the anchor. Keyboard/caret preservation and Back handling are unchanged.

  **Verification:** All 18 existing light/dark menu and interaction checks pass,
  including opening Field options in RTL and in a small window with large text.
  Reviewed the menu screenshot; its edge now aligns with the 16dp page inset.

  **Evidence:** [EditorOptionsMenu.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorOptionsMenu.kt),
  `DropdownMenu`; existing `ComposeNoteEditorScreenshotTest/global_options.png`.

- [x] **03. Use rounded styling for the Toolbar layout icon.**

  **Feedback:** The Toolbar layout icon is not round.

  **Assessment: agree on rounded styling.** The current outlined columns icon
  has square corners. This is visual polish rather than a functional defect.

  **Completion action:** Use a rounded version of the same layout symbol and
  verify that it remains distinct from Manage fields and consistent with nearby
  icons.

  **Scope agreed 2026-10-20:** Prefer rounded styling for all icons added for
  this editor.

  **Implemented 2026-10-20:** Replaced all 17 added editor vectors with Google's
  Material Icons Round or Material Symbols Rounded counterparts, including
  Toolbar layout, alignment, indentation, lists, formatting, rich-text/HTML
  mode, and text direction. Preserved tint, directional mirroring, the custom
  mode-switch spacing, and the independently drawn color indicators. Each
  vector records its source and license.

  **Verification:** All 14 existing light/dark toolbar and alignment checks
  pass. Reviewed a before/after sheet of all 17 icons and the rendered alignment
  dialog.

  **Evidence:** [ic_view_column.xml](../AnkiDroid/src/main/res/drawable/ic_view_column.xml).

- [x] **04. Use a close button for the main Field options panel.**

  **Feedback:** The bottom panel's back button would be clearer as an “×”.

  **Assessment: agree for the main panel.** One back arrow currently performs
  two different actions: dismissing Field options or returning from a nested
  section.

  **Completion action:** Use “×” with an appropriate accessible label when
  dismissing the main panel. Retain Back when returning from Customize toolbar
  or Quick actions. Preserve existing dismissal and pending-change behavior.

  **Implemented 2026-10-20:** The main panel uses a rounded “×” with the
  translated accessible label “Close”. Nested sections retain their Back arrow
  and return-to-Field-options action. Updated the existing screenshot and
  device/demo selectors to follow the new accessible label.

  **Verification:** All 14 selected light/dark panel checks pass, including
  closing without the keyboard, pending-edit discard confirmation, nested
  navigation, RTL, and large text. Reviewed the close-button screenshot; the
  Android device-test sources also compile.

  **Evidence:** [EditorFieldPanel.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorFieldPanel.kt),
  the navigation button branches on `pending`.

- [x] **05. Rename the current Font control to Font size.**

  **Feedback:** Earlier screenshots offered font and font size. If changing
  the font is unavailable, the control should say “Font size”.

  **Assessment: agree; rename it now.** The dialog only edits size and preserves
  the existing font name. It explicitly contains a TODO to restore font
  selection with verified WebView fonts and a Custom option.

  **Completion action:** Rename the field option and dialog title consistently
  to “Font size”, including any relevant help references. Preserve the stored
  font name when changing size. Treat restoring a usable font selector as a
  separate enhancement.

  **Implemented 2026-10-20:** The field option, help catalogue, and dialog title
  reuse the existing translated “Font size” label. Removed the unused “Font”
  string and updated the existing test/demo selectors. Added an explicit TODO
  to introduce font selection in a future enhancement, with verified WebView
  fonts and a Custom option.

  **Verification:** All three existing font-dialog tests pass, covering size
  validation, preserving the configured font, and cancellation. All six
  selected light/dark dialog and field-options layout checks pass, including
  large text on a small screen. Reviewed the dialog screenshot; the updated
  device-test sources compile.

  **Evidence:** [EditorFieldFontDialog.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorFieldFontDialog.kt),
  `onSave(field.font, size.toInt())`; [EditorFieldOption.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorFieldOption.kt),
  `FONT`; [EditorFieldFontDialogTest.kt](../AnkiDroid/src/test/java/com/ichi2/anki/noteeditor/compose/EditorFieldFontDialogTest.kt).

- [x] **06. Clarify the font-size help text without omitting typed answers.**

  **Feedback:** “Used when editing and typing answers” is confusing; the tester
  suggests “Used for text in the field”.

  **Assessment: agree with rewriting; disagree with the exact replacement.**
  Typed answers also read this field's configured font and size, so the proposed
  replacement omits a real effect.

  **Implemented 2026-10-20:** Applied the approved wording: **“Used only when
  editing and typing answers.”** The font-size dialog and field-options help
  catalogue share the same string, keeping both descriptions consistent.

  **Verification:** All four existing light/dark font-size dialog and
  field-options help checks pass.

  **Evidence:** [compose_note_editor.xml](../AnkiDroid/src/main/res/values/compose_note_editor.xml),
  `compose_editor_field_font_help`; [TypeAnswer.kt](../AnkiDroid/src/main/java/com/ichi2/anki/cardviewer/TypeAnswer.kt)
  reads `fld.font` and `fld.fontSize`.

- [ ] **07. Evaluate more editing space for short notes.**

  **Feedback:** Deck and note type appear permanently visible; most users may
  prefer more room for editing.

  **Assessment: partly disagree with the premise; agree with the space concern.**
  Both selectors already collapse after deliberate scrolling through fields.
  Short notes may not produce enough scrolling to trigger this. The claim about
  most users remains a hypothesis.

  **Completion action:** Evaluate a compact header or an explicit collapse
  control, especially with short notes and the keyboard open. Record the chosen
  behavior and verify that deck and note type remain easy to inspect and change.

  **Evidence:** [NoteEditorScreen.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/NoteEditorScreen.kt),
  `EditorCollapsingHeader` wraps `EditorMetadata`;
  [editor.js](../AnkiDroid/src/main/assets/note-editor/editor.js),
  `showHeader()` and the deliberate-scroll handler.

- [x] **08. Investigate fields briefly disappearing before expanding Notes.**

  **Feedback:** The fields vanished briefly before the Notes field expanded.

  **Assessment: agree; reproduced a clipping error during panel dismissal.**
  Once the sheet slides outside its parent, `boundsInWindow()` returns an empty
  rectangle. The panel then reports a top of zero for several frames, telling
  the WebView to clip away every field before clearing the clip on dismissal.

  **Implemented 2026-10-20:** Use the sheet's unclipped `positionInWindow()` and
  wait for its first measured position instead of assuming zero. Full-screen
  configuration pages still deliberately clip the covered fields.

  **Verification:** Extended the existing frame-by-frame panel dismissal test.
  It failed before the fix, recording several zero-height clips at the end of
  dismissal, and passes afterward. All eight selected light/dark panel checks
  pass, including unchanged field bounds and configuration-page navigation.
  This reproduces the panel transition defect; the original video was not
  replayed on-device.

  **Evidence:** [editor.js](../AnkiDroid/src/main/assets/note-editor/editor.js),
  `clipFieldOptions()` and `setFieldOptionsVisible()`;
  [WebEditorView.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/web/WebEditorView.kt),
  `setFieldOptionsTop()`; [NoteEditorScreen.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/NoteEditorScreen.kt),
  the sheet positioning callback; backend `FieldsOnly.svelte`, `toggleCollapsed()`
  (see backend source locations below).

- [x] **09. Reduce excess width for single-symbol custom toolbar buttons.**

  **Feedback:** The small star occupies too much toolbar space.

  **Assessment: agree.** Custom buttons inherit text-button minimum widths in
  the scrolling toolbar and receive two columns in the stacked toolbar,
  regardless of label length.

  **Completion action:** Give single-symbol custom buttons the normal icon-button
  allocation while retaining an adequate touch target. Keep flexible widths for
  longer labels. Verify scrolling and stacked layouts, including emoji labels.

  **Implemented 2026-10-20:** Custom buttons now have a 48 dp minimum width.
  Both layouts use the rendered label width, so stars and joined emoji occupy
  one stacked column while longer labels can use two. Scrolling overflow
  indicators use the same measurements.

  **Verification:** Eight light/dark layout checks and all three scroll-spacing
  tests pass. New layout checks cover a star, a joined emoji, a longer label,
  48 dp touch targets, and single-column spacing in both layouts. Reviewed the
  stacked-toolbar screenshot.

  **Evidence:** [EditorToolbarButtons.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorToolbarButtons.kt),
  custom `TextButton`; [EditorScrollingToolbar.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorScrollingToolbar.kt),
  custom `ToolbarButtonExtent`; [NoteEditorScreen.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/NoteEditorScreen.kt),
  stacked items use their measured width to select one or two columns.

- [ ] **10. Make emoji custom-button labels easier to discover and use.**

  **Feedback:** The tester would prefer emoji icons for custom buttons.

  **Assessment: agree with the preference; the basic capability already exists.**
  The custom-button label accepts unrestricted text, including emoji, and is
  rendered directly. A dedicated picker or clearer presentation would be an
  enhancement.

  **Completion action:** Verify emoji entry and display, decide whether clearer
  guidance or an optional picker is needed, and record that decision. Coordinate
  compact presentation with item 09.

  **Evidence:** [EditorCustomButtonsDialog.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorCustomButtonsDialog.kt),
  label `OutlinedTextField`; [EditorToolbarButtons.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorToolbarButtons.kt),
  `Text(item.button.label)`.

- [x] **11. Prevent a visible field reset when saving an existing note.**

  **Feedback:** Saving the star note collapsed Notes and changed the toolbar.

  **Assessment: agree; saving an edit unnecessarily replaced its document.**
  The generation change triggered a reload, reapplying collapsed defaults
  before the activity closed. Notes is collapsed by default in the demo.

  **Implemented 2026-10-20:** Saving an existing note retains its generation,
  fields and toolbar. The save-before-changing-note-type flow marks the saved
  content clean in place, preserving the DOM, selection and undo history if the
  editor stays open. A new Add still starts a new document with its defaults.

  **Verification:** All 62 model/session tests pass, including a regression
  against save-time reloads. Both Pixel 9 Pro checks pass: saving an existing
  note closes the activity, and accepting a save in place preserves expansion,
  the same DOM node, selection, undo/redo, and the clean baseline after recovery.

  **Evidence:** [EditorViewModel.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorViewModel.kt),
  the edit branch of `saveSnapshot()`; [EditorWebSession.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorWebSession.kt),
  the state collector and `synchronize()`; [EditorDemoFixture.kt](../AnkiDroid/src/androidTest/java/com/ichi2/anki/noteeditor/compose/EditorDemoFixture.kt),
  Astronomy's Notes default; [ComposeNoteEditorDemoTest.kt](../AnkiDroid/src/androidTest/java/com/ichi2/anki/noteeditor/compose/ComposeNoteEditorDemoTest.kt),
  “Save field styles”.

- [ ] **12. Hide the editing toolbar when there is genuinely no editing target.**

  **Feedback:** The toolbar shown when no field is selected should be hidden.

  **Assessment: agree for a settled unfocused state.** The toolbar still uses
  the initial focus target when selection is absent. Item 13 makes its contents
  and visibility consistent during Add; hiding it for a genuinely unfocused
  editor remains a separate decision.

  **Completion action:** Hide the toolbar when there is no editing target.
  Preserve the target during interactions with editor controls, and distinguish
  a genuine unfocused state from the transient reset addressed by item 13.
  Verify that toolbar visibility and contents use the same target.

  **Evidence:** [EditorState.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorState.kt),
  `showsToolbar(activeField)` uses `toolbarFieldIndex(activeField)`;
  [NoteEditorScreen.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/NoteEditorScreen.kt)
  displays the initial focus field's toolbar while the active field is null.

- [x] **13. Eliminate the temporary toolbar during Add transitions.**

  **Feedback:** Adding a note switches from the first field's toolbar to a
  temporary toolbar and then back.

  **Assessment: strongly agree.** Document rendering clears the active field
  before the activity restores focus. Previously, that interval displayed a
  fresh default toolbar instead of the intended field's configuration.

  **Implemented 2026-10-20:** During this interval, toolbar contents, visibility,
  mode and scroll identity all use the same initial focus field. Collapsed
  defaults are skipped, hidden toolbars stay hidden, and an all-collapsed note
  has no fallback toolbar. Formatting stays disabled until selection is ready.

  **Verification:** All 14 light/dark checks pass. Regression checks repeatedly
  clear and restore selection and verify the custom buttons, HTML mode, hidden
  toolbar, and unchanged field bounds throughout the transition.

  **Evidence:** [editor.js](../AnkiDroid/src/main/assets/note-editor/editor.js),
  `render()` clears `activeField`; [EditorWebSession.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorWebSession.kt),
  `synchronize()` publishes toolbar state and readiness;
  [ComposeNoteEditorActivity.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/ComposeNoteEditorActivity.kt),
  `handleSaveResult()` focuses `initialFocusField` after synchronization.

- [ ] **14. Measure and minimize save/add latency and spinner flashes.**

  **Feedback:** The loading indicator raises concern about a delay on every
  save or Add.

  **Assessment: agree with the concern; a regression is not yet demonstrated.**
  Every save immediately enters a busy state, which immediately displays a
  spinner. Adding also rebuilds the field document and persists its draft
  before restoring focus.

  **Completion action:** Measure Save-to-close and Add-to-editable against the
  legacy editor under comparable conditions. Remove demonstrated unnecessary
  work, including the reload investigated in item 11. Consider delayed progress
  feedback for operations that usually finish quickly; verify actual latency
  separately from spinner visibility.

  **Evidence:** [ComposeNoteEditorActivity.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/ComposeNoteEditorActivity.kt),
  `save()` and `setBusy()`; [NoteEditorScreen.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/NoteEditorScreen.kt),
  `EditorFields()`; [EditorWebSession.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorWebSession.kt),
  `synchronize()`; [editor.js](../AnkiDroid/src/main/assets/note-editor/editor.js),
  `loadDocumentAndCreateDraft()`.

- [x] **15. Replace the broken-image MathJax loading state.**

  **Feedback:** Inserting MathJax briefly shows a broken-image icon and
  “Mathjax”.

  **Assessment: strongly agree; a regression in our lazy-loading change.**
  Backend commit `173a30af0` changed the shared component's conversion from
  synchronous to asynchronous. The image still assumes the result is immediately
  available, encoding `undefined` into its SVG data URL while the renderer loads.
  The invalid image then exposes the broken-image icon and `alt="Mathjax"`.
  This has not been reproduced in stock Anki Desktop.

  **Completion action:** Show a valid pending representation until the
  converted SVG is ready. Verify first insertion with an uncached renderer and
  subsequent edits, preserving the equation's selection and caret behavior.

  **Implemented 2026-10-20:** Use the existing empty-equation SVG icon while
  conversion is pending, styled for the current theme and font size. Replace
  its source when conversion finishes while retaining the same selectable image
  element. Expose the pending state through `aria-busy`.
  Local shared-editor commit: `0e6990fca`; backend pointer: `60b828a`.

  **Verification:** All five targeted native-editor browser tests pass,
  including inline and block insertions with the renderer deliberately held
  pending, successful rendering, selection preservation, equation replacement
  with undo, and enabling previews. The desktop browser project's existing
  harness returns 404 for this native route. The broader backend check also
  encounters existing lint and macOS installer failures.

  **Follow-up 2026-10-20:** Opening the equation dialog now starts loading and
  initializing the field WebView's SVG renderer. This includes editing an
  existing equation and restoring an open dialog. The separate dialog-preview
  WebView does not initialize the field renderer. Preloading returns immediately
  to the host command queue, and insertion reuses the module and startup promise.
  Keep the valid placeholder if insertion happens before loading completes.
  Local shared-editor commit: `56a65cd9d`; backend pointer: `67ba1f7`.

  **Follow-up verification:** All eight focused native-editor browser checks
  pass, including plain-editor startup without MathJax, duplicate preload calls,
  unchanged selection/history, editing during loading, and insertion before or
  after loading finishes. A focused check of the production host bridge confirms
  that pending and failed preloads do not block subsequent commands. Extended
  the device integration test to hold preloading pending while the dialog is
  open and verify that a snapshot still completes. The backend, debug app, and
  Android test APK builds pass; the Pixel was disconnected, so that device test
  was not run. The final backend-wide check stops at an existing formatting
  error in `code-mirror.ts`.

  **Evidence:** Backend `Mathjax.svelte`, the `converted` variable,
  asynchronous `render()`, and unconditional `<img>` (see below).
  [ComposeNoteEditorDemoTest.kt](../AnkiDroid/src/androidTest/java/com/ichi2/anki/noteeditor/compose/ComposeNoteEditorDemoTest.kt)
  waits for the equation image's `naturalWidth > 0`; that check establishes
  eventual rendering, not the absence of a broken-image frame.

## Backend source locations

The review used the backend selected by this worktree's `local.properties`:
`/Users/davidallison/Work/Anki-Android-Backend-compose-editor`.
These references point to that local checkout:

- [FieldsOnly.svelte](/Users/davidallison/Work/Anki-Android-Backend-compose-editor/anki/ts/routes/editor-fields/FieldsOnly.svelte):
  `toggleCollapsed()` changes one field; `load()` clears and rebuilds the field
  list using the supplied defaults.
- [Mathjax.svelte](/Users/davidallison/Work/Anki-Android-Backend-compose-editor/anki/ts/lib/editable/Mathjax.svelte):
  asynchronous conversion and the image's pending state.

## Implementation boundaries

Items 11–14 share save, focus, and toolbar transition code, but describe distinct
outcomes to verify. Keep separate fixes in granular commits. Items 08, 11, and 14
need reproduction or measurement before recording their suspected causes or
performance effects as confirmed.
