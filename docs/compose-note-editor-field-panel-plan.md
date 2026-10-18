# Field settings panel: navigation and customization

Implemented 2026-10-18. Field options, Customize toolbar and Quick actions now
share the editor’s bottom panel, with independent field navigation.

## Interaction

- [x] Swipe horizontally to configure the previous or next field, keeping the
  current section: Field options, Customize toolbar, or Quick actions.
- [x] Keep the field name visible in the panel header, with previous/next buttons
  and a position such as **2 of 12**. The buttons provide discovery and access
  without requiring a swipe. Stop at the first/last field rather than wrapping.
- [x] Give the panel its own selected field. Navigating settings must not move
  the WebView caret, focus another input, open the keyboard, or initialize an
  offscreen rich-text editor. Opening a panel starts on the active field.
- [x] Keep a single Field options entry in the wrench menu. Customize toolbar
  and Quick actions belong inside that panel. Avoid swipeable category tabs:
  horizontal swipes change fields, not categories.
- [x] Make Customize toolbar and Quick actions accessible from Field options
  itself. Open them within the same panel; the back arrow and Android Back
  return to Field options, retaining the displayed field and pending changes.

## Panel content

- [x] Keep the existing field-options grid and its immediate setting changes.
- [x] Move Customize toolbar into the panel: Show toolbar, preset selection,
  Active/Available buttons, and the fixed Custom buttons footer. Continue to
  explain that editing a shared preset affects every field using it.
- [x] Move Quick actions into the panel with its existing Active/Available list
  and ordering controls.
- [x] Keep the header and action buttons visible while lists scroll. Allow a
  taller panel for the two lists, bounded by the available area above the
  keyboard. The editor and its footer must not resize when the panel opens.
- [x] Vertical scrolling and drag-to-reorder must not trigger field navigation.
  Suspend horizontal paging while a button is being reordered.

## Changes awaiting Save

Customize toolbar and Quick actions currently keep changes pending until the
tick is pressed. Preserve that behavior when adding field navigation:

- [x] Keep pending edits when swiping away and back. Closing with unsaved edits
  offers the existing discard confirmation.
- [x] The tick applies the pending changes for the fields visited in that panel;
  do not require repeated saves simply to move between fields.
- [x] Keep the tick available after returning to Field options with pending
  toolbar or quick-action edits. The close button still offers discard confirmation.
- [x] Hold shared preset edits once, separately from each field's preset
  assignment and toolbar visibility. Separate full copies of the preset list
  per field could overwrite another field's newer edits when saved.
- [x] Apply those changes through one coordinated save operation and validate
  each captured field target. A note-type/document change must not redirect a
  pending change to a different field at the same index.

## Small commits and verification

1. [x] Add field navigation to the existing options panel. Cover first/last and
   single-field behavior, RTL, accessible previous/next actions, stable keyboard
   and caret. Setting callbacks use the displayed field; the description test
   explicitly verifies the target after swiping away from the focused field.
2. [x] Extract reusable toolbar and quick-action content without changing their
   existing dialog behavior. Preserve reorder, disabled-state and discard tests.
3. [x] Add pending state across fields and coordinated saving. Test edits to two
   fields sharing a preset, different presets, deletion fallback, cancellation,
   recreation, and stale targets.
4. [x] Embed the two customization screens in the panel. Add light/dark
   screenshots for each section, keyboard open/closed, long field names, RTL,
   large text, and tablet layout; verify vertical drag versus horizontal swipe.
5. [x] Check the 400-field case: the panel is absent before opening, and only
   nearby pages are composed. Field navigation does not reload the WebView.
6. [ ] Remeasure first-editor-frame timing on the Pixel. No panel content or
   per-field draft is created until a panel opens; timing has not been remeasured.

## Verification notes

- ViewModel tests cover a coordinated multi-field save, shared preset edits,
  parcel recreation, deleted-preset fallback and stale targets.
- Screenshot tests cover each panel, navigation boundaries, RTL, a 400-field
  note type, long names, tablets, the keyboard, pending edits, discard and Save.
  They also check that the editor retains its dimensions and focused field.
- A scrolled Customize toolbar screenshot matches the Pixel with the keyboard
  closed, preset controls offscreen, and Custom buttons fixed at the bottom.
- A regression test opens both customization sections from Field options for
  a field other than the focused field, returns using both back controls, and
  saves the accumulated changes together.
- Native emulator testing verified horizontal swipes through the lists, a
  diagonal reorder drag without paging, pending order retained after navigation,
  and Save restoring the original caret with the keyboard still open.
- Native list rows have separate accessibility roots. Offscreen pages suppress
  their controls and section headings so TalkBack sees only the current field.
- At 200% text size on a small screen, the redundant section label is omitted
  and Custom buttons scrolls with the controls, leaving room for a complete
  preset selector above the keyboard.

Robolectric returns empty insets when newly attached native lists request them.
The screenshot fixture retains its latest injected keyboard and system-bar
insets across these requests; otherwise it shows controls under the simulated
keyboard and shifts the editor into the status bar. This is test-only behavior.

Robolectric also needs an explicit draw after a section transition: its native
RecyclerView header otherwise retains a pending Compose layout, preventing the
screenshot runner from becoming idle. This draw is confined to the test helper.
