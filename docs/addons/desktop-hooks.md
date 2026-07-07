# Anki desktop hooks → AnkiDroid addon events

> A catalogue of Anki desktop's addon hook points (`gui_hooks` in `qt/aqt/gui_hooks.py`,
> generated from `qt/tools/genhooks_gui.py`; plus the collection-level hooks in
> `pylib/anki/hooks.py`), and how each maps onto AnkiDroid's sandboxed addon model
> ([AddonPageHost](README.md)). Desktop has ~200 hooks; this covers the load-bearing ones.
>
> **The one distinction that matters:** a hook is either a plain **event** (a notification —
> "this happened") or a **filter** (it receives a value and returns a possibly-modified one —
> "shape this before it's used"). Events map cleanly onto our `ankidroid.onEvent(type, cb)`.
> Filters do **not** — a sandboxed addon can't synchronously return a value into the host's
> render path, and our security direction is *against* letting addon code sit inside host
> rendering. Filters are therefore either reframed as declarative contributions (like
> `menus`) or left unsupported, noted per-hook below.

## Legend

- **event** — a notification; maps to `ankidroid.onEvent(type, cb)`.
- **filter** — returns a modified value; not directly supportable (see above).
- Status: **✅ implemented** · **◐ mappable** (event we could fire, not yet wired) ·
  **▲ filter** (needs a declarative reframing) · **✕ no AnkiDroid surface** (desktop-only
  Qt/webview concept).

---

## Reviewer

| desktop hook | kind | AnkiDroid |
|---|---|---|
| `reviewer_did_show_question(card)` | event | ✅ `onEvent("question")` |
| `reviewer_did_show_answer(card)` | event | ✅ `onEvent("answer")` |
| `reviewer_did_answer_card(reviewer, card, ease)` | event | ✅ `onEvent("answered", {rating})` |
| `reviewer_will_answer_card(ea, reviewer, card)` | filter (may veto/remap ease) | ▲ needs a native answer-button/ease filter (AnkiDroid answer bar is native) |
| `reviewer_will_init_answer_buttons(buttons, ...)` | filter | ▲ native answer bar |
| `reviewer_will_end()` | event | ◐ mappable — fire on session finish |
| `reviewer_did_init(reviewer)` | event | ✅ implicit (bootstrap runs on page load) |
| `reviewer_will_show_context_menu(reviewer, menu)` | filter (adds items) | ▲ reframe as declarative context-menu contributions |
| `card_will_show(text, card, kind)` | **filter** (rewrites card HTML) | ▲ the big one — template/field filters; needs a card-render text-filter API |
| `state_did_change(new, old)` | event | ◐ mappable — screen/state change event |
| `state_shortcuts_will_change(state, shortcuts)` | filter | ▲ reframe as declarative shortcut contributions |

## Editor / note editor

| desktop hook | kind | AnkiDroid |
|---|---|---|
| `editor_did_init(editor)` | event | ◐ once the editor is a shared web page |
| `editor_did_init_buttons(buttons, editor)` | filter (adds toolbar buttons) | ▲ reframe as declarative editor-button contributions |
| `editor_did_load_note(editor)` | event | ◐ mappable |
| `editor_did_focus_field(note, idx)` / `editor_did_unfocus_field` | event | ◐ mappable |
| `editor_will_munge_html(txt, editor)` | filter | ▲ needs a field-html filter |
| `editor_did_fire_typing_timer(note)` | event | ◐ mappable |

AnkiDroid's editor is native today, so none is wired; these become tractable when the editor
moves to the shared web layer (upstream, in progress).

## Browser

| desktop hook | kind | AnkiDroid |
|---|---|---|
| `browser_will_show(browser)` | event | ◐ (native browser) |
| `browser_menus_did_init(browser)` | filter (adds menu items) | ▲ → declarative `menus` on a `browser` screen (the pattern already exists) |
| `browser_did_fetch_columns(columns)` | filter | ▲ declarative column contributions + a stats/query API |
| `browser_did_fetch_row(item, ..., row)` | filter | ▲ as above |
| `browser_will_search(context)` / `browser_did_search(context)` | filter/event | ◐/▲ |
| `browser_will_show_context_menu(browser, menu)` | filter | ▲ declarative context menu |
| `browser_sidebar_will_show_context_menu(...)` | filter | ✕ native sidebar model |

## Deck browser / overview / main window

| desktop hook | kind | AnkiDroid |
|---|---|---|
| `deck_browser_will_render_content(dbrowser, content)` | **filter** (injects HTML) | ▲ deck-list is native; reframe as a deck-list panel/row contribution |
| `overview_will_render_content(overview, content)` | filter | ▲ as above |
| `top_toolbar_did_init_links(links, toolbar)` | filter | ▲ declarative toolbar contributions |
| `main_window_did_init()` | event | ✅ implicit (app start) |
| `collection_did_load(col)` | event | ◐ mappable (profile/collection lifecycle) |
| `profile_did_open()` / `profile_will_close()` | event | ◐ mappable (AnkiDroid has profiles) |
| `theme_did_change()` | event | ◐ mappable |
| `deck_browser_will_show_options_menu(menu, did)` | filter | ▲ declarative per-deck menu |

## Collection-level (pylib, non-GUI)

| desktop hook | kind | AnkiDroid |
|---|---|---|
| `card_did_render(output, ctx)` | filter | ▲ card render filter (see `card_will_show`) |
| `note_will_be_added(col, note, deck_id)` | event/filter | ◐ (needs collection write events) |
| `notes_will_be_deleted(col, ids)` | event | ◐ |
| `sync_will_start()` / `sync_did_finish()` | event | ◐ mappable — several top addons need these |
| `schema_will_change()` | filter (may veto) | ✕ |
| `field_filter(field_text, field_name, filter_name, ctx)` | **filter** (`{{my-filter:Field}}`) | ▲ the template-filter mechanism many addons use; needs a registered field-filter API |

---

## What this means for AnkiDroid

**Events are easy and we should keep adding them.** `question`/`answer`/`answered` are wired;
`reviewerWillEnd`, `stateChanged`, `sync` start/finish, `profile` open/close, and collection
mutation events are all mappable with the same `AddonPageHost.fireEventScript` +
`ankidroid.onEvent` mechanism, fired from the relevant Kotlin lifecycle point. These are the
cheap, high-value wins.

**Filters are the hard half, and they're most of what desktop addons actually use**
([addon-survey.md](addon-survey.md) found desktop addons are *predominantly filters over host
rendering*). A sandboxed addon can't sit in the host's synchronous render path. The path
forward for each filter family is a **declarative reframing**, which we've already started:
- menu/context-menu/toolbar filters → declarative `menus`-style contributions (done for
  deck-picker menus; the pattern generalises).
- answer-button / ease filters → a native answer-bar customisation surface.
- `card_will_show` / `field_filter` / `card_did_render` → a registered **card-render text
  filter** (`{{addon:Field}}`), the single highest-value missing piece; it's a text-in →
  text-out transform we *could* run in a sandboxed context because it doesn't touch live DOM.
- browser columns → declarative column registration + the collection query API (which now
  exists — `AddonCollectionApi`).

**Desktop-only concepts** (Qt sidebar models, `schema_will_change` veto, raw webview
injection) have no AnkiDroid surface and shouldn't get one.

The through-line matches dae's stated direction and this repo's model: **events flow freely;
anything that shapes host output becomes a declaration the host renders, not addon code in the
render path.**
