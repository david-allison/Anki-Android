# Compose note editor: toolbar and field-panel feedback

Recorded 2026-10-18. **12 action items; 6 resolved.**

This records the tester's latest feedback and the assessments discussed in chat.
Recommendations remain proposals unless an explicit decision is recorded.
Removing the note-type name in item 02 is agreed; implementation is pending.

Check an item when its decision and any agreed implementation or verification
are complete. A proposal can also be resolved by explicitly declining it; record
that decision. Keep the item numbers stable. The
[earlier feedback checklist](compose-note-editor-feedback-checklist.md) remains
separate.

## Feedback and recommendations

- [ ] **01. Improve the Toolbar layout icon.**

  **Assessment: agree.** The current icon and the adjacent Fields icon both
  resemble lists. Of the suggested Material icons, prefer **View Column**.
  **View Week** risks suggesting a calendar; **Width Normal** suggests resizing.

  **Proposed action:** compare View Column with its neighboring menu icons at
  their actual display size, then replace the current icon if the distinction
  is clear. Preserve the visible label and accessible meaning.

- [ ] **02. Remove the note-type name below the field name in the main panel.**

  **Decision: remove the note-type name, agreed 2026-10-18.** Implementation is
  pending. Repeating the note-type name adds visual competition
  beside the field-position control. The field name and navigation are the
  useful information in this panel. Customize toolbar and Quick actions already
  omit the note-type name; this proposal concerns the main Field options panel.

  **Proposed layout:** use a full-width navigation row below the field name,
  with the previous arrow on the left, the tappable position (for example,
  **1 of 2**) centered, and the next arrow on the right. This develops the user's
  suggestion to make the navigation full width; the layout itself remains a
  proposal. Preserve the field-name picker, swipe/button animations, and
  accessible arrow targets. Check first/last-field states, long field names,
  large text, RTL, and the single-field case.

- [x] **03. Remove “Add a hint for this field” from the Description row.**

  **Assessment: agree.** The visible **Description** label is sufficient, with
  the tooltip explaining its purpose.

  **Implemented 2026-10-18:** removed the empty-state helper and its unused
  string. An empty description now shows just the Description label; existing
  descriptions remain visible. The tooltip and description hints inside empty
  editor fields are preserved.

  **Verification:** 10 existing light/dark checks pass, covering empty and
  populated settings layouts, description editing, clearing, cancellation, and
  the help catalogue. The isolated probe APK builds successfully.

- [x] **04. Match the Customize toolbar title hierarchy to the template editor.**

  **Assessment: strongly agree.** The template editor uses the screen name as
  its title and the note-type name as a subtitle. Customize toolbar currently
  emphasizes the field name over the screen name; a field called “Back” is
  especially ambiguous because it also sounds like navigation.

  **Implemented 2026-10-18:** **Customize toolbar** is the primary heading and
  the field name is a smaller subtitle. Long screen titles can wrap while field
  names ellipsize; the save action remains accessible.

  **Verification:** six existing light/dark checks pass for the normal layout,
  200% text with a long field name, and pending shared-preset edits. Reviewed the
  generated normal and large-text screenshots; the isolated probe APK builds.

- [x] **05. Apply the same title treatment to Quick actions.**

  **Assessment: agree.** These two configuration screens should clearly belong
  together.

  **Implemented 2026-10-18:** **Quick actions** is the primary heading and the
  field name is a smaller subtitle, sharing item 04's spacing and typography.
  Field switching remains on the main panel and quick-action edits still save
  immediately.

  **Verification:** six existing light/dark checks pass for configuration,
  immediate saving across fields, and reordering without changing the selected
  field. Reviewed the generated screenshot; the isolated probe APK builds.

- [ ] **06. Replace save ticks with “Save”, except in the note editor itself.**

  **Assessment: agree where changes require saving.** A tick can mean Done,
  Apply, or Confirm. **Save** explicitly names the action. Descriptive action
  labels also follow [Material's button guidance][material-buttons].

  **Proposed action:** replace the save ticks on Customize toolbar and Custom
  buttons with visible **Save** labels. Keep the note editor's tick. Preserve
  pending changes, discard confirmation, accessibility, and existing save
  behavior; do not add a save requirement to immediately applied settings.

- [ ] **07. Decide whether Quick actions should have a Save button.**

  **Assessment: prefer keeping immediate saving.** Quick actions currently
  saves each change immediately, following the user's earlier preference. These
  changes are straightforward and reversible. A Save button would imply that
  changes remain pending.

  **Recommended decision:** retain immediate saving and Back navigation. If
  explicit saving is chosen instead, introduce actual pending changes and
  discard handling together with the button. Do not add a cosmetic Save button
  that merely closes a screen whose changes have already been saved.

- [x] **08. Give the Active area slightly rounded corners.**

  **Assessment: agree, subtly.** Round the outer group while keeping its rows
  visually connected. Preserve the darker active background and clear drag
  handles requested earlier.

  **Implemented 2026-10-18:** the shared configuration list rounds only the
  Active group's outer corners by 8 dp. Middle rows remain connected; the
  corners update as items move between groups. Colors and drag targets are
  preserved.

  **Verification:** 10 light/dark checks pass for populated and empty groups,
  reordering, and scrolling to the last row. Both Pixel drag tests pass,
  including movement between empty groups and scrolling at both list edges.

- [ ] **09. Align the design of the related configuration screens.**

  **Assessment: agree with the direction.** The study-screen toolbar, browser
  columns, and editor configuration should share title hierarchy, row spacing,
  drag handles, selection styling, and navigation conventions. Saving behavior
  may differ where the operation warrants it.

  **Proposed action:** review the screens together and identify shared visual
  and interaction conventions. Treat broader alignment as a separate task;
  record justified differences before deciding whether to share components.

- [x] **10. Investigate inconsistent status bars.**

  **Finding and fix, 2026-10-18:** the fullscreen configuration dialogs did not
  explicitly match system-bar icon contrast to their surface. The Pixel capture
  showed the clock and ordinary icons disappearing against Custom buttons'
  white background. Customize toolbar, Quick actions, and Custom buttons now
  select status/navigation icon appearance from their own surface brightness.

  **Verification:** six existing light/dark layout checks pass. A Pixel
  regression checks both system-bar appearance flags on the main editor and all
  three fullscreen screens. Settled Pixel screenshots confirm visible icons;
  optional screenshot capture waits for SystemUI's separate icon transition.

- [x] **11. Replace “Duplicate” with the existing “Clone preset” string.**

  **Assessment: agree for consistency.** “Duplicate” is slightly clearer in
  isolation, but reusing AnkiDroid's established **Clone preset** wording and
  translations is preferable here.

  **Implemented 2026-10-18:** the preset menu reuses Anki's translated clone
  action with the app's sentence-case treatment: **Clone preset** in English.
  Removed the redundant Duplicate string.

  **Verification:** all 13 preset-dialog tests pass, including cloning an
  independent preset. The isolated probe APK builds successfully.

- [ ] **12. Make interaction above the open bottom panel predictable.**

  **Assessment: valid criticism; an interaction decision is needed.** The
  current implementation disables Save, preview, and the deck/note-type
  selectors while field taps can dismiss the panel and pass through. This can
  make the area above the panel feel inconsistently available.

  **Proposed direction:** an outside tap dismisses the panel; the next tap
  interacts with the editor. A light dimming layer would communicate that
  boundary. Field switching within the main panel would continue to update
  focus and scroll position without opening the keyboard.

  **Tradeoff:** returning to editing takes an extra tap. This is a proposal,
  not an approved change to the current pass-through behavior. Preserve discard
  confirmation for unsaved changes. If adopted, verify touch behavior, Android
  Back, keyboard handling, and accessibility so covered controls cannot still
  be activated through another input method.

[material-buttons]: https://github.com/material-components/material-web/blob/main/docs/components/button.md#usage
