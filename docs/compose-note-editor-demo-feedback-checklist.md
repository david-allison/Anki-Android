# Compose note editor: demo feedback checklist

Recorded 2026-10-20. **15 action items; 1 resolved.**

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

- [ ] **02. Increase the options menu's screen-edge spacing.**

  **Feedback:** The options dropdown is too close to the screen edge.

  **Assessment: agree.** The menu has no explicit positioning offset, and the
  existing screenshot shows a very narrow right margin.

  **Completion action:** Give the popup a consistent screen inset. Verify its
  placement in LTR, RTL, and narrow windows without clipping menu content.

  **Evidence:** [EditorOptionsMenu.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorOptionsMenu.kt),
  `DropdownMenu`; existing `ComposeNoteEditorScreenshotTest/global_options.png`.

- [ ] **03. Use rounded styling for the Toolbar layout icon.**

  **Feedback:** The Toolbar layout icon is not round.

  **Assessment: agree on rounded styling.** The current outlined columns icon
  has square corners. This is visual polish rather than a functional defect.

  **Completion action:** Use a rounded version of the same layout symbol and
  verify that it remains distinct from Manage fields and consistent with nearby
  icons.

  **Evidence:** [ic_view_column.xml](../AnkiDroid/src/main/res/drawable/ic_view_column.xml).

- [ ] **04. Use a close button for the main Field options panel.**

  **Feedback:** The bottom panel's back button would be clearer as an “×”.

  **Assessment: agree for the main panel.** One back arrow currently performs
  two different actions: dismissing Field options or returning from a nested
  section.

  **Completion action:** Use “×” with an appropriate accessible label when
  dismissing the main panel. Retain Back when returning from Customize toolbar
  or Quick actions. Preserve existing dismissal and pending-change behavior.

  **Evidence:** [EditorFieldPanel.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorFieldPanel.kt),
  the navigation button branches on `pending`.

- [ ] **05. Rename the current Font control to Font size.**

  **Feedback:** Earlier screenshots offered font and font size. If changing
  the font is unavailable, the control should say “Font size”.

  **Assessment: agree; rename it now.** The dialog only edits size and preserves
  the existing font name. It explicitly contains a TODO to restore font
  selection with verified WebView fonts and a Custom option.

  **Completion action:** Rename the field option and dialog title consistently
  to “Font size”, including any relevant help references. Preserve the stored
  font name when changing size. Treat restoring a usable font selector as a
  separate enhancement.

  **Evidence:** [EditorFieldFontDialog.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorFieldFontDialog.kt),
  `onSave(field.font, size.toInt())`; [EditorFieldOption.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorFieldOption.kt),
  `FONT`; [EditorFieldFontDialogTest.kt](../AnkiDroid/src/test/java/com/ichi2/anki/noteeditor/compose/EditorFieldFontDialogTest.kt).

- [ ] **06. Clarify the font-size help text without omitting typed answers.**

  **Feedback:** “Used when editing and typing answers” is confusing; the tester
  suggests “Used for text in the field”.

  **Assessment: agree with rewriting; disagree with the exact replacement.**
  Typed answers also read this field's configured font and size, so the proposed
  replacement omits a real effect.

  **Completion action:** Use wording such as **“Text size when editing this
  field and typing its answer during review.”** Check that related help text
  consistently describes the setting's scope.

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

- [ ] **08. Investigate fields briefly disappearing before expanding Notes.**

  **Feedback:** The fields vanished briefly before the Notes field expanded.

  **Assessment: agree this needs investigation; the cause is unconfirmed.**
  Expanding a field only toggles and mounts that field. Panel transitions
  separately clip the entire WebView document, making dismissal and clipping
  worth investigating. The inspected code does not establish the cause of the
  particular disappearance.

  **Completion action:** Reproduce the panel-close/Notes-expand sequence and
  inspect the transient frames, clipping bounds, and document reloads. Fix the
  demonstrated cause and verify that field content remains visible throughout
  the transition.

  **Evidence:** [editor.js](../AnkiDroid/src/main/assets/note-editor/editor.js),
  `clipFieldOptions()` and `setFieldOptionsVisible()`;
  [WebEditorView.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/web/WebEditorView.kt),
  `setFieldOptionsTop()`; backend `FieldsOnly.svelte`, `toggleCollapsed()`
  (see backend source locations below).

- [ ] **09. Reduce excess width for single-symbol custom toolbar buttons.**

  **Feedback:** The small star occupies too much toolbar space.

  **Assessment: agree.** Custom buttons inherit text-button minimum widths in
  the scrolling toolbar and receive two columns in the stacked toolbar,
  regardless of label length.

  **Completion action:** Give single-symbol custom buttons the normal icon-button
  allocation while retaining an adequate touch target. Keep flexible widths for
  longer labels. Verify scrolling and stacked layouts, including emoji labels.

  **Evidence:** [EditorToolbarButtons.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorToolbarButtons.kt),
  custom `TextButton`; [EditorScrollingToolbar.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorScrollingToolbar.kt),
  custom `ToolbarButtonExtent`; [NoteEditorScreen.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/NoteEditorScreen.kt),
  custom items receive `columnWidth * 2`.

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

- [ ] **11. Prevent a visible field reset when saving an existing note.**

  **Feedback:** Saving the star note collapsed Notes and changed the toolbar.

  **Assessment: agree; the code suggests a plausible cause.** The demo edits
  an existing note. Saving an edit increments the document generation, and the
  state observer synchronizes document changes. This can rebuild the fields
  and reapply their collapsed defaults before the activity closes. Notes is
  configured as collapsed by default in the demo.

  **Completion action:** Confirm the save-time reload and avoid unnecessary
  visible document reconstruction when the completed edit is closing. Verify
  that the expanded field and toolbar do not reset before dismissal. Keep
  “Start collapsed” behavior for a genuinely new Add document separate.

  **Evidence:** [EditorViewModel.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorViewModel.kt),
  the edit branch of `saveSnapshot()`; [EditorWebSession.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorWebSession.kt),
  the state collector and `synchronize()`; [EditorDemoFixture.kt](../AnkiDroid/src/androidTest/java/com/ichi2/anki/noteeditor/compose/EditorDemoFixture.kt),
  Astronomy's Notes default; [ComposeNoteEditorDemoTest.kt](../AnkiDroid/src/androidTest/java/com/ichi2/anki/noteeditor/compose/ComposeNoteEditorDemoTest.kt),
  “Save field styles”.

- [ ] **12. Hide the editing toolbar when there is genuinely no editing target.**

  **Feedback:** The toolbar shown when no field is selected should be hidden.

  **Assessment: agree for a settled unfocused state.** Visibility falls back
  to the first field, while toolbar contents fall back to a fresh default
  configuration. This explains the unrelated toolbar.

  **Completion action:** Hide the toolbar when there is no editing target.
  Preserve the target during interactions with editor controls, and distinguish
  a genuine unfocused state from the transient reset addressed by item 13.
  Verify that toolbar visibility and contents use the same target.

  **Evidence:** [EditorState.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/EditorState.kt),
  `showsToolbar(activeField)` uses `activeField ?: 0`;
  [NoteEditorScreen.kt](../AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/compose/NoteEditorScreen.kt)
  falls back to `EditorFieldToolbar()` when `target` is null.

- [ ] **13. Eliminate the temporary toolbar during Add transitions.**

  **Feedback:** Adding a note switches from the first field's toolbar to a
  temporary toolbar and then back.

  **Assessment: strongly agree.** Document rendering clears the active field,
  and the activity restores focus after synchronization. This exposes the
  fallback described in item 12.

  **Completion action:** Publish the next document's toolbar and focus together.
  Verify that repeated Add operations show neither an unrelated toolbar nor
  a hide/show layout jump. Include first-field presets, hidden toolbars, and
  collapsed-field defaults.

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

- [ ] **15. Replace the broken-image MathJax loading state.**

  **Feedback:** Inserting MathJax briefly shows a broken-image icon and
  “Mathjax”.

  **Assessment: strongly agree; the code provides a concrete explanation.**
  The MathJax component creates its image before asynchronous conversion
  finishes. Its initially undefined result is encoded into an SVG data URL,
  and the image has `alt="Mathjax"`. That can produce the reported placeholder.

  **Completion action:** Show a valid pending representation until the
  converted SVG is ready. Verify first insertion with an uncached renderer and
  subsequent edits, preserving the equation's selection and caret behavior.

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
