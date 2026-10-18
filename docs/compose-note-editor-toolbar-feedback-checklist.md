# Compose note editor: toolbar and field-panel feedback

Recorded 2026-10-18. **12 action items; 0 resolved.**

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

- [ ] **03. Remove “Add a hint for this field” from the Description row.**

  **Assessment: agree.** The visible **Description** label is sufficient, with
  the tooltip explaining its purpose.

  **Proposed action:** remove the empty-state helper from the settings row.
  Continue displaying an existing description and retain the tooltip. Preserve
  the actual description hint inside empty editor fields.

- [ ] **04. Match the Customize toolbar title hierarchy to the template editor.**

  **Assessment: strongly agree.** The template editor uses the screen name as
  its title and the note-type name as a subtitle. Customize toolbar currently
  emphasizes the field name over the screen name; a field called “Back” is
  especially ambiguous because it also sounds like navigation.

  **Proposed action:** make **Customize toolbar** the primary title and the
  field name a smaller subtitle. Check long names, large text, and space for
  the save action.

- [ ] **05. Apply the same title treatment to Quick actions.**

  **Assessment: agree.** These two configuration screens should clearly belong
  together.

  **Proposed action:** make **Quick actions** the primary title and the field
  name a smaller subtitle, using the same spacing and typography as item 04.
  Preserve field switching only on the main panel.

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

- [ ] **08. Give the Active area slightly rounded corners.**

  **Assessment: agree, subtly.** Round the outer group while keeping its rows
  visually connected. Preserve the darker active background and clear drag
  handles requested earlier.

  **Proposed action:** apply consistent group corners in Customize toolbar and
  Quick actions. Check empty and populated groups, dragging between Available
  and Active, and clearance for the last row while scrolled to the bottom.

- [ ] **09. Align the design of the related configuration screens.**

  **Assessment: agree with the direction.** The study-screen toolbar, browser
  columns, and editor configuration should share title hierarchy, row spacing,
  drag handles, selection styling, and navigation conventions. Saving behavior
  may differ where the operation warrants it.

  **Proposed action:** review the screens together and identify shared visual
  and interaction conventions. Treat broader alignment as a separate task;
  record justified differences before deciding whether to share components.

- [ ] **10. Investigate inconsistent status bars.**

  **Assessment: agree this deserves investigation.** Background color and icon
  contrast should look intentional across the affected screens. The tester's
  comment does not establish which screens or conditions cause the problem.

  **Proposed action:** identify the affected screens, capture their appearance,
  and check status-bar background, icon contrast, and top spacing in light and
  dark themes before choosing a fix.

- [ ] **11. Replace “Duplicate” with the existing “Clone preset” string.**

  **Assessment: agree for consistency.** “Duplicate” is slightly clearer in
  isolation, but reusing AnkiDroid's established **Clone preset** wording and
  translations is preferable here.

  **Proposed action:** reuse the existing translated string for the preset menu
  action. Preserve the cloning behavior and pending preset selection.

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
