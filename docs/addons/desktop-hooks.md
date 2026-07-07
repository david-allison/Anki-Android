# Anki desktop hooks → AnkiDroid addon events

> A catalogue of Anki desktop's addon hook points and how each maps onto AnkiDroid's
> sandboxed addon model ([AddonPageHost](README.md)). Verified against `main` (2026-07):
> **167 hooks total — 150 GUI (`qt/aqt/gui_hooks.py`, from `qt/tools/genhooks_gui.py`) +
> 17 collection-level (`pylib/anki/hooks.py`, from `pylib/tools/genhooks.py`); 28 are
> filters.** This covers the load-bearing ones; the full generated list is in those files.

## The distinction that decides AnkiDroid support — three kinds, not two

Desktop's own classification (`pylib/tools/hookslib.py`) is binary: a hook is a **filter**
if it declares a return type (the fire loop does `arg = filter(arg, …)`), else a plain
**hook**. But that binary hides a third case that matters to us:

1. **Pure event** — a notification with no return and no mutable arg to change (e.g.
   `reviewer_did_show_question(card)`, `profile_did_open()`). → **Supportable**: maps to
   `ankidroid.onEvent(type, cb)`.
2. **Mutating hook** — a *plain* hook (no return) that nonetheless modifies host state by
   mutating a passed mutable object (a `list`, `dict`, `CellRow`, `TemplateRenderOutput`,
   `OverviewContent`). Desktop calls these plain hooks, but they *shape host output*.
   Examples: `deck_browser_will_render_content(_, content)`, `editor_did_init_buttons(buttons, _)`,
   `browser_did_fetch_columns(columns)`, `card_did_render(output, _)`.
3. **Filter** — returns a modified value (`card_will_show(text,…) -> str`,
   `field_filter(...) -> str`, `reviewer_will_answer_card(...) -> tuple`).

**Both (2) and (3) are un-supportable by a sandboxed addon** for the same reason: they sit in
the host's *synchronous render/compute path*, receiving and shaping a host object before it's
used. A sandboxed iframe can't return a value into that path, and our security direction is
explicitly *against* addon code running there. So AnkiDroid support splits by *what a hook
does*, not by desktop's filter/hook label:

- **✅/◐ pure events** — fire to `ankidroid.onEvent`. (✅ wired · ◐ mappable, not yet wired)
- **▲ output-shaping** (filters + mutating hooks) — must be **reframed as declarative
  contributions** (like `menus`), or left unsupported.
- **✕** — desktop-only Qt concept, no AnkiDroid surface.

---

## Reviewer (22 hooks)

Pure events — mappable to `onEvent`:

| hook | args | AnkiDroid |
|---|---|---|
| `reviewer_did_show_question` | (card) | ✅ `onEvent("question")` |
| `reviewer_did_show_answer` | (card) | ✅ `onEvent("answer")` |
| `reviewer_did_answer_card` | (reviewer, card, ease) | ✅ `onEvent("answered", {rating})` |
| `reviewer_did_init` | (reviewer) | ✅ implicit (bootstrap on page load) |
| `reviewer_will_end` | () | ◐ mappable |
| `reviewer_will_suspend_card` / `_note` | (id) | ◐ mappable |
| `reviewer_will_bury_card` / `_note` | (id) | ◐ mappable |
| `reviewer_will_play_question_sounds` / `_answer_sounds` | (card, tags) | ◐ mappable |

Output-shaping (▲ — reframe, not directly supportable):

| hook | kind | why ▲ |
|---|---|---|
| `card_will_show(text, card, kind) -> str` | **filter** | rewrites card HTML — the biggest one; needs a card-render text-filter API (`{{addon:Field}}`) |
| `reviewer_will_answer_card(...) -> tuple[bool, ease]` | **filter** | veto/remap ease → native answer-bar filter |
| `reviewer_will_init_answer_buttons(...) -> tuple` | **filter** | native answer bar |
| `reviewer_will_compare_answer -> tuple` / `reviewer_will_render_compared_answer -> str` | **filter** | type-answer comparison |
| `reviewer_will_show_context_menu(reviewer, menu)` | **mutating hook** (adds `QMenu` items) | → declarative context-menu contributions |

## Editor / note editor (19 hooks)

Native on AnkiDroid today, so none is wired; tractable once the editor is a shared web page.
- **events (◐):** `editor_did_init`, `editor_did_load_note`, `editor_did_focus_field`,
  `editor_did_fire_typing_timer`, `editor_did_update_tags`, `editor_did_paste`.
- **mutating hooks (▲):** `editor_did_init_buttons(buttons, _)` (add toolbar buttons),
  `editor_did_init_shortcuts` — → declarative editor-button / shortcut contributions.
- **filters (▲):** `editor_will_load_note -> str`, `editor_did_unfocus_field -> bool`,
  `editor_will_munge_html -> str`, `editor_will_process_mime -> QMimeData`.

## Browser (14 hooks)

Native browser on AnkiDroid.
- **events (◐):** `browser_will_show`, `browser_did_change_row`, `browser_did_search`.
- **mutating hooks (▲):** `browser_menus_did_init` / `browser_will_show_context_menu`
  (→ declarative `menus` on a `browser` screen — the pattern exists),
  `browser_did_fetch_columns(columns)` / `browser_did_fetch_row(..., row)`
  (→ declarative column registration + the collection query API, which now exists),
  `browser_will_search(context)`.
- **filters (▲):** `browser_will_build_tree -> bool`, `default_search -> str`.
- **✕:** `browser_sidebar_will_show_context_menu` (native sidebar model).

## Deck browser / overview / main window / state / profile / sync (48 hooks)

Pure events — the cheap, high-value wins (◐ unless noted):

| hook | AnkiDroid |
|---|---|
| `main_window_did_init` | ✅ implicit (app start) |
| `profile_did_open` / `profile_will_close` | ◐ (AnkiDroid has profiles) |
| `collection_did_load` | ◐ |
| `state_will_change` / `state_did_change` | ◐ screen/state change |
| `state_did_undo` | ◐ (Life-Drain-class addons need undo) |
| `operation_did_execute(changes, handler)` | ◐ generic "collection changed" |
| `theme_did_change` | ◐ |
| `day_did_change` | ◐ |
| `sync_will_start` / `sync_did_finish` | ◐ (FSRS-Helper-class addons need these) |
| `media_sync_did_start_or_stop` | ◐ |
| `backup_did_complete` | ◐ |
| `overview_did_refresh`, `deck_browser_did_render` | ◐ |
| `deck_browser_will_show_options_menu(menu, did)` | ▲ → declarative per-deck menu |

Output-shaping (▲):
- `deck_browser_will_render_content(_, content)` / `overview_will_render_content(_, content)`
  — **mutating hooks** (inject deck-list / overview HTML) → deck-list panel/row contributions.
- `overview_will_render_bottom -> Callable` — **filter** (add overview bottom-bar buttons).
- `top_toolbar_did_init_links(links, _)` — **mutating hook** → declarative toolbar links.
- `state_shortcuts_will_change(state, shortcuts)` — **mutating hook** → declarative shortcuts.
- `webview_will_set_content(web_content, _)` — **mutating hook**, the #1 desktop pattern
  (inject CSS/JS into any webview) → this is exactly what `AddonPageHost` replaces with
  sandboxed injection.
- `webview_did_receive_js_message -> tuple` — **filter** (`pycmd`) → our postMessage relay.
- `style_did_init -> str`, `main_window_should_require_reset -> bool` — filters.

## Card layout / add cards / stats / deck options / fields / models / import-export / addons / sound (47 hooks)

Mostly desktop-dialog-specific. Notable:
- **events (◐):** `add_cards_did_add_note`, `add_cards_did_change_deck`,
  `deck_options_did_load` (the Svelte deck-options screen — AnkiDroid renders it too),
  `deck_conf_did_load_config` / `will_save_config`, `av_player_did_begin_playing` /
  `did_end_playing`.
- **filters (▲):** `add_cards_will_add_note -> str?` (reject a note), `models_did_init_buttons`,
  `exporter_will_export -> ExportOptions`, `addon_config_editor_will_update_json -> str`.

## Collection-level (pylib, 17 hooks) — fire headless too (AnkiConnect, sync)

| hook | kind | AnkiDroid |
|---|---|---|
| `note_will_be_added(col, note, deck_id)` | event | ◐ (needs collection-write events) |
| `notes_will_be_deleted(col, ids)` | event | ◐ |
| `note_will_flush(note)` / `card_will_flush(card)` | event | ◐ pre-DB-write |
| `card_did_render(output, ctx)` | **mutating hook** (rewrites Q/A) | ▲ card-render filter |
| `field_filter(text, name, filter, ctx) -> str` | **filter** | ▲ custom `{{filter:Field}}` — high value, and a pure text→text transform we *could* run sandboxed |
| `schema_will_change -> bool` | filter (veto) | ✕ |
| `media_file_filter -> str` | filter | ▲ |

---

## Most-used desktop hooks (informed estimate, not a measured ranking)

The docs single out four as canonical: `webview_will_set_content`,
`webview_did_receive_js_message`, `reviewer_did_show_question`, `card_will_show`. The rest of
the top ~20 (from ecosystem patterns, not a statistic): `reviewer_did_show_answer`,
`card_did_render`, `editor_did_init_buttons`, `browser_will_show_context_menu`,
`browser_menus_did_init`, `browser_did_fetch_row`/`_columns`, `profile_did_open`,
`main_window_did_init`, `deck_browser_will_render_content`, `overview_will_render_content`,
`top_toolbar_did_init_links`, `state_did_change`, `editor_did_load_note`, `field_filter`,
`reviewer_did_answer_card`. **Telling fact:** of that top-20, the four the docs name plus
most of the list are output-shaping (webview injection, render filters, toolbar/menu/column
mutation) — i.e. the majority of real desktop addon usage is exactly the ▲ category that a
sandboxed model can't host directly.

## What this means for AnkiDroid

**Events flow freely — keep wiring them.** `question`/`answer`/`answered` are done;
`reviewerWillEnd`, `sync_will_start/did_finish`, `profile_did_open/will_close`,
`state_did_change`, `state_did_undo`, `operation_did_execute`, and the collection mutation
events (`note_will_be_added`, `notes_will_be_deleted`) are all pure events, mappable with the
same `AddonPageHost.fireEventScript` + `ankidroid.onEvent` mechanism fired from the matching
Kotlin lifecycle point. Cheap, high value, no security cost.

**Output-shaping is the hard majority.** Both true filters *and* mutating hooks sit in the
host render path; a sandboxed addon can't. Each family gets a **declarative reframing**, which
this repo has already started:
- webview injection (`webview_will_set_content`) → sandboxed `AddonPageHost` injection (done).
- menu/toolbar/context-menu mutation → declarative `menus`-style contributions (done for the
  deck picker; generalises to browser/toolbar/context menus).
- `card_will_show` / `field_filter` / `card_did_render` → a registered **card-render text
  filter** (`{{addon:Field}}`): the single highest-value missing piece, and uniquely
  sandbox-friendly because it's a pure text→text transform, not live-DOM mutation.
- browser columns → declarative column registration + `AddonCollectionApi` (the query half
  now exists).
- answer-button / ease filters → a native answer-bar customisation surface.

The through-line matches dae's stated direction and this repo's model: **events flow freely;
anything that shapes host output becomes a declaration the host renders, never addon code in
the render path.**
