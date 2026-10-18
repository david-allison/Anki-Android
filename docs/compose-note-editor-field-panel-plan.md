# Field settings panel: navigation and customization

Proposed 2026-10-18. This is a design and implementation checklist; the field
swipes and embedded customization screens are not implemented yet.

## Interaction

- [ ] Swipe horizontally to configure the previous or next field, keeping the
  current section: Field options, Customize toolbar, or Quick actions.
- [ ] Keep the field name visible in the panel header, with previous/next buttons
  and a position such as **2 of 12**. The buttons provide discovery and access
  without requiring a swipe. Stop at the first/last field rather than wrapping.
- [ ] Give the panel its own selected field. Navigating settings must not move
  the WebView caret, focus another input, open the keyboard, or initialize an
  offscreen rich-text editor. Opening a panel starts on the active field.
- [ ] Preserve each main-menu entry as a shortcut into the corresponding section
  of the same panel. Avoid swipeable category tabs: horizontal swipes change
  fields, not categories.

## Panel content

- [ ] Keep the existing field-options grid and its immediate setting changes.
- [ ] Move Customize toolbar into the panel: Show toolbar, preset selection,
  Active/Available buttons, and the fixed Custom buttons footer. Continue to
  explain that editing a shared preset affects every field using it.
- [ ] Move Quick actions into the panel with its existing Active/Available list
  and ordering controls.
- [ ] Keep the header and action buttons visible while lists scroll. Allow a
  taller panel for the two lists, bounded by the available area above the
  keyboard. The editor and its footer must not resize when the panel opens.
- [ ] Vertical scrolling and drag-to-reorder must not trigger field navigation.
  Suspend horizontal paging while a button is being reordered.

## Changes awaiting Save

Customize toolbar and Quick actions currently keep changes pending until the
tick is pressed. Preserve that behavior when adding field navigation:

- [ ] Keep pending edits when swiping away and back. Closing with unsaved edits
  offers the existing discard confirmation.
- [ ] The tick applies the pending changes for the fields visited in that panel;
  do not require repeated saves simply to move between fields.
- [ ] Hold shared preset edits once, separately from each field's preset
  assignment and toolbar visibility. Separate full copies of the preset list
  per field could overwrite another field's newer edits when saved.
- [ ] Apply those changes through one coordinated save operation and validate
  each captured field target. A note-type/document change must not redirect a
  pending change to a different field at the same index.

## Small commits and verification

1. [ ] Add field navigation to the existing options panel. Cover first/last and
   single-field behavior, RTL, accessible previous/next actions, stable keyboard
   and caret, and correct targets for pin/language/font-size/description.
2. [ ] Extract reusable toolbar and quick-action content without changing their
   existing dialog behavior. Preserve reorder, disabled-state and discard tests.
3. [ ] Add pending state across fields and coordinated saving. Test edits to two
   fields sharing a preset, different presets, deletion fallback, cancellation,
   recreation, and stale targets.
4. [ ] Embed the two customization screens in the panel. Add light/dark
   screenshots for each section, keyboard open/closed, long field names, RTL,
   large text, and tablet layout; verify vertical drag versus horizontal swipe.
5. [ ] Check the 400-field case and first editor frame. Create the panel and only
   nearby pages on demand; introduce no editor-startup work or WebView reload.
