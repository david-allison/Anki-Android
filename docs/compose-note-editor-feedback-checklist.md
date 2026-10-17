# Compose note editor: tester feedback checklist

Recorded 2026-10-15. **30 action items; 9 resolved.**

This captures the tester's proposals, their individual evaluations, and the
clarification about replacing the gear menu. Recommendations below are not
accepted design decisions. Items 05, 07, 09, 18, 19, 22, 23, 27 and 30 are implemented; the
other proposals remain open for review.

Check an item when its decision and any agreed implementation or verification
are complete. A proposal can also be resolved by explicitly declining it; record
that decision rather than silently deleting the item. Existing capabilities are
not automatically checked off when the feedback still raises a placement or
usability question. Keep the item numbers stable.

## What the feedback establishes

The tester found the amount of customization overwhelming and did not discover
that the toolbar's three-dot button opens field options until finding the entry
in the gear menu. They liked per-field customization, but raised the effort of
sharing one toolbar across fields and note types, overlap between quick actions
and toolbar buttons, and the distinction between field controls and editing
commands. The claim that most users want one toolbar is a hypothesis, rather
than an established finding about the wider user base.

The strongest recommendations are shared toolbar configuration, clearer overflow
navigation, and better access to field settings. Removing quick actions conflicts
with the explicitly requested workflow of acting on an unfocused field in one
tap. Tabs also need to accommodate the previously investigated 400-field case.

The proposed distinction based on how long an action lasts is imperfect: pinning
persists in the note-type field settings, while formatting buttons can establish
an ongoing editing mode. Scope is a more useful distinction than duration.

## Controls beside fields

- [ ] **02. Use an outlined pin that fills when active.**

  **Assessment: agree.** This conveys state without a large colored background.
  Preserve an accessible touch target and announce the pinned state even if the
  visible icon becomes smaller.

- [ ] **03. Use Anki Desktop's HTML icon and make it bold when active.**

  **Assessment: partly agree.** A recognizable code icon is appropriate, but a
  small stroke-weight change is a weak mode indicator. Compare it with the
  current rich-text/HTML switch before replacing that explicit indication.
  Desktop consistency alone does not establish better phone usability.

- [ ] **04. Hide field icons when unfocused, except an active pin.**

  **Assessment: mostly disagree.** This reduces clutter but hides capabilities
  and makes focus a prerequisite for direct actions. Keeping an active pin
  visible is useful. Preserve the ability to act directly on another field and
  avoid changing header geometry when focus moves.

## Editor menu and toolbar presets

- [ ] **06. Replace the options dropdown with a bottom sheet containing Template editor, Manage fields, Field options, and Manage toolbar presets.**

  **Assessment: reasonable with revisions.** A labeled sheet could improve
  discoverability and reachability. Distinguish changing a note type's field
  structure from configuring one field, and give existing options a destination.
  See the menu mapping below: this replaces the whole top options menu, not just
  its toolbar configuration entry.

- [x] **07. Introduce reusable toolbar presets across fields and note types.**

  **Decision: implemented 2026-10-15.** Unconfigured fields inherit an editable
  shared Default. Named presets are references: edits update every field using
  them across note types. The “This field only” option has been removed. Existing
  field-specific configurations migrate to named presets, reusing matching
  presets and preserving button choices, order, and custom buttons.
  Quick actions and Scroll/Stack/Hidden remain separate. Storage is local in
  SharedPreferences, with a TODO to move it into the Anki collection.
  **Verification:** storage and editor-state tests cover inheritance, existing
  overrides, deletion fallback, custom buttons, stale field targets, and
  preserving note content and the WebView document generation.

- [ ] **08. Put the three toolbar layout choices above the preset list.**

  **Assessment: partly agree.** Clearly state whether Scroll/Stack/Hidden changes
  the current preset or the whole editor. Prefer keeping device presentation
  separate from the command list: a phone and tablet may need different layouts
  for the same buttons.

- [x] **09. Support adding, deleting, editing, and renaming presets, like Manage note types.**

  **Decision: implemented with 07.** Manage toolbar presets opens from the
  wrench menu; Toolbar buttons in field options also opens the preset chooser.
  Presets can be added, duplicated, renamed, edited and deleted. The shared
  Default can be edited or duplicated. Deleting a named preset requires
  confirmation and returns its fields to Default. Editing explains its shared
  scope; cancelling leaves the previous button choices intact.
  **Verification:** interaction tests cover assignment, name validation,
  cancellation, duplication, renaming and confirmed deletion. Light/dark
  screenshots cover management, long field/type/preset names and editing.

- [ ] **10. Include toolbar and quick-action commands in the preset editor, excluding Pin field and Edit HTML.**

  **Assessment: mostly agree with a common command catalogue.** It reduces
  duplicated configuration concepts, but does not remove the usefulness of
  placing commands beside fields. Excluding Pin from content formatting is
  sensible; excluding HTML switching entirely is less compelling. Decide which
  commands are eligible separately from where they may appear.

- [ ] **11. Drag commands into Always show, Menu only, and Disabled sections.**

  **Assessment: good model; revise labels.** Prefer **Toolbar / Menu / Hidden**.
  A scrolling toolbar cannot guarantee that every button is always visible, and
  "Disabled" can mean temporarily unavailable in the current context. Provide
  accessible alternatives to dragging between sections.

- [ ] **12. Make the bottom toolbar's three-dot button open an overflow menu.**

  **Assessment: strongly agree.** This directly addresses the tester's
  expectation. Keep Field options accessible through a clearly named entry or
  another discoverable control. This is separate from replacing the top gear.

- [ ] **13. Add a custom-toolbar-item creation button at the bottom of the preset editor.**

  **Assessment: agree with the capability; reconsider placement.** A button
  below a long command list is easy to miss. Prefer a visible action near the
  top or another persistent entry.

- [ ] **14. Give custom items an edit button, with deletion available through editing.**

  **Assessment: agree.** Editing a definition must be separate from enabling or
  hiding it. Distinguish removing a button from this toolbar from deleting the
  shared definition everywhere, and explain the scope of shared edits.

### What the proposed menu replaces

The options icon is in the top app bar beside Preview and Save. Its gear has
been replaced with a wrench (05). Replacing the dropdown with a bottom sheet
remains a separate proposal (06).

| Proposed destination | Relationship to the current editor |
| --- | --- |
| Template editor | Current Cards entry |
| Manage fields | Current Fields entry |
| Field options | Expanded version of the current field-settings panel |
| Manage toolbar presets | New management screen for shared toolbar configurations |

The bottom toolbar's three-dot control is covered separately by item 12. The
proposal does not specify where the current global Font size option goes.
Custom button creation is later placed in preset editing by items 13–14.

## Field settings

For note-type field settings, make it clear that changes affect that field on
every note using the note type. Reuse Anki's collection properties where they
exist; distinguish these settings from device-local presentation preferences.

- [ ] **15. Make Field options a screen with one tab per field.**

  **Assessment: disagree with tabs as the general solution.** Tabs work for a
  handful of short names but scale poorly to long names and 400 fields. Prefer a
  searchable field selector or list, initially selecting the current field.
  Avoid making common field actions unnecessarily distant.

- [ ] **16. Include a field Description setting.**

  **Assessment: agree.** Particularly useful for unfamiliar or shared note
  types. Keep descriptions optional and avoid permanently expanding every
  field header to show them.

- [ ] **17. Include Keyboard language in field settings.**

  **Assessment: agree; the capability already exists.** The unresolved issue is
  placement. Preserve the explanation that it applies to the field across the
  note type, including typing answers, and keep frequent changes accessible.

- [x] **18. Include per-field Font and font size.**

  **Assessment: agree with clear scope.** Separate editing typography from
  device-wide readability settings and card styling. Anki also uses the field's
  configured size for typed answers. Consider font availability and fallback on
  Android. See [Anki's field settings documentation][anki-fields].

  **Implemented:** Font in the existing field options edits the collection's
  field font and size (5–300 pixels). The dialog explains the note-type scope,
  typed answers and unavailable-font fallback. HTML mode retains a monospace
  font and follows the field size. The global editor-size override remains
  device-local, with a “Use field sizes” action to clear it. Live updates preserve
  text and undo history; first load uses the existing document settings without
  an extra typography request.

- [x] **19. Include a reverse-text-direction toggle.**

  **Assessment: agree.** Prefer the clearer label **Right-to-left text**. This
  controls editing direction; card templates still control review appearance.
  See [Anki's field settings documentation][anki-fields].

  **Implemented:** Right-to-left text in field options saves the note type’s
  `rtl` flag. Changes apply to existing fields without reloading or changing
  their HTML, selection or undo history.

- [ ] **20. Include Use HTML editor by default.**

  **Assessment: agree.** Keep the persistent default separate from changing
  modes during the current edit. The Compose editor already reads the backend's
  `plainText` setting; convenient configuration is the missing part of this
  proposal.

- [x] **21. Include Collapse by default.**

  **Implemented:** The field-options switch saves Anki's collection-backed
  `collapsed` default, using its existing translation. It applies when opening
  a note or starting the next Add; changing the default leaves the current
  field expansion and text intact. Automatic focus skips collapsed fields,
  and is omitted if all fields are collapsed. Temporary expansion remains in
  draft recovery without changing the default; validation still reveals a
  collapsed field that needs attention.

- [x] **22. Include a per-field Show toolbar toggle.**

  **Assessment: conditional agreement.** Useful for fields that rarely need
  formatting, but it overlaps with a global Hidden layout. Define inheritance,
  precedence, and how users restore a hidden toolbar before adding another
  control.

  **Implemented:** Show toolbar is saved per field alongside its preset
  assignment. The global Hidden layout takes precedence. Field options remain
  available from the wrench menu, so a hidden toolbar can be restored.

- [x] **23. Include a toolbar-preset dropdown, hidden when the toolbar is off.**

  **Assessment: agree if presets are adopted.** Include **Use default** so fields
  can inherit future changes. Retain the selection when hiding the toolbar and
  restoring it. Selecting a shared preset should have clear shared-edit
  semantics, rather than silently copying its current contents.

  **Implemented:** Field options include a shared-preset dropdown with Use
  default. The dropdown is hidden when this field’s toolbar or the global
  layout is hidden; hiding retains the chosen preset. Migrated field-specific
  configurations appear as named presets in the same dropdown.

## Icons, attachments, and MathJax

- [x] **24. Make black, filled icons less distracting.**

  **Decision: implemented 2026-10-17.** The remaining unselected toolbar,
  navigation, configuration-list, help, and alignment icons now use the theme's
  `colorControlNormal`, matching the wrench and field quick actions. Keep the
  existing icon shapes, selected formatting colours, colour indicators, and
  Save emphasis. Disabled buttons retain Material's disabled styling, and
  E-ink retains its intentionally black controls. Screenshot regressions cover
  the toolbar and alignment tint in light and dark themes.

- [x] **27. Remove the decorative icon above the MathJax dialog title.**

  **Decision: implemented 2026-10-15.** Removed the top decoration, which
  duplicated the title's meaning and consumed vertical space. The MathJax
  toolbar action and the title's question-mark help button remain.
  **Verification:** all eight light/dark `EquationDialogScreenshotTest` cases
  pass, covering add, edit, empty preview, format selection, and the manual link.

- [ ] **28. Shorten the title to Add MathJax.**

  **Assessment: disagree with the exact wording.** The dialog also edits
  existing equations. The current **MathJax equation** names the object and is
  accurate in both modes.

- [x] **30. Prioritize usability with long MathJax equations.**

  **Assessment: strongly agree.** The preview currently has a fixed 112 dp
  height. Existing tests cover basic rendering and editing, but do not establish
  usability for wide formulas, multiline expressions, and matrices. Evaluate
  these with the keyboard open, large text, and landscape orientation. Consider
  a larger or expandable editing surface; do not shrink equations until they
  become unreadable.

  **Implemented:** The equation dialog always opens at full size,
  retaining its text, format and renderer. The source uses a monospace font,
  disables autocorrection and shows up to 12 lines. The larger
  preview pans wide equations and tall matrices at their original readable size.
  Confirmation and cancellation remain outside the scrolling content. These
  changes are only created when the equation dialog opens.

  **Verification:** 16 light/dark Roborazzi cases pass, including long single-line
  and multiline source, 1.5× text and a short window. Six emulator WebView tests
  pass, including touch panning to the last term/row, renderer reuse and applying
  the edited text. Roborazzi covers native layout; rendered equation
  captures come from the emulator. The input test IME has no visible keyboard.

## Dependencies and review order

- Decide shared defaults and preset inheritance (07) before committing to the
  management model in 08–11, 13–14, and 23.
- Overflow discoverability (12) can improve independently of preset storage.
- Removing quick actions or focus-independent controls (01, 04) requires an
  explicit product decision about losing the existing one-tap workflow.
- Evaluate settings scope and large-note navigation before replacing the current
  field-settings panel (15–23).
- Treat the MathJax help, decoration, title, preview contrast, and long-equation
  layout as separate decisions (26–30). Long-equation usability has greater
  functional value than the cosmetic changes.

[anki-fields]: https://docs.ankiweb.net/manual/editing#customizing-fields
[photo-picker]: https://developer.android.com/training/data-storage/shared/photo-picker
[material-dialogs]: https://m2.material.io/go/design-dialogs/
