# AnkiWeb addon survey — would real addons work in the proposed model?

> Companion to [README.md](README.md) (the plan) and [prior-art.md](prior-art.md).
> Question under test: the plan proposes declarative settings + reviewer JS
> injection + sandboxed iframe panels + a permissioned JS API. Do *real* addons —
> with actual UI, not just settings — fit that model, and what do they need from
> the host? Surveyed 2026-07 from live AnkiWeb data and addon source code.

## Method

- **Ranking**: AnkiWeb's own catalog via its `svc/shared/list-addons` API
  (3,088 addons total), using the site's default sort (Wilson score lower bound
  on thumbs up/down). AnkiWeb no longer publishes download counts, so raw
  thumbs-up volume was used as a second popularity proxy.
- **Analysis**: ~24 of the top addons were analyzed from their actual GitHub
  source (hook registrations, monkey-patch targets, injected JS, bridge
  messages), not from descriptions.

## The top of AnkiWeb today

Top of the default (rating) sort, with category — full analysis for **bold**
entries below:

| # | Addon | Category |
|---|---|---|
| 1 | Ankimon (Pokemon gamification) | reviewer-ui |
| 2 | **Review Heatmap** | stats |
| 3 | extended editor for field (TinyMCE) | editor |
| 4 | SynapsePro (whole-UI overhaul) | main-window |
| 5 | Onigiri (whole-UI overhaul) | main-window |
| 6 | AnkiCollab | system-integration |
| 7 | Button Colours (Good, Again) | reviewer-ui |
| 8 | **Batch Editing** | browser |
| 10 | **Advanced Review Bottom Bar** | reviewer-ui |
| 11 | **FSRS Helper** | scheduler |
| 12 | Anki Killstreaks | reviewer-ui |
| 13 | **Mini Format Pack** | editor |
| 14 | **Add Table** | editor |
| 21 | **Pass/Fail 2** | reviewer-ui |
| 22 | **Anki Simulator** | stats |
| 25 | **Search Stats Extended** | stats |
| 26 | **Speed Focus Mode** | reviewer-ui |
| 35 | **AwesomeTTS** | media/tts |
| 37 | **Image Occlusion Enhanced** | editor |
| 39 | **More Overview Stats** | stats |

High-vote veterans outside the rating top-40: **HyperTTS**, **AnkiConnect**,
**Custom Background Image and Gear Icon**, **AnkiHub**, **Advanced Browser**,
**Contanki** (gamepad), **Frozen Fields** (legacy), **Edit Field During Review
(Cloze)**, **Symbols As You Type**, **Customize Keyboard Shortcuts**,
Pop-up Dictionary (legacy), plus analyzed representatives: **BetterSearch**,
**Special Fields**, **SIAC** (PDF reader in Add dialog), **Straight Reward**,
**AJT Flexible Grading**, **Life Drain**, **Progress Bar**, **Enhance Main
Window**, **Fastbar**, **Colorful Tags**, **Clickable Tags**, long-term-backup
addons, Migaku (legacy).

Ecosystem observations relevant to the plan:

- **Reviewer UI + gamification dominate the ratings top-20** — exactly the
  category a reviewer-JS addon model serves first.
- Several all-time favourites are dead or absorbed: Frozen Fields (native since
  2.1.45), Image Occlusion (native since 23.10), Night Mode (native), Pop-up
  Dictionary (legacy). Many top slots are **maintained forks** of abandoned
  addons. Churn is structural — caused by addons depending on private
  internals (see below).
- Whole-UI replacements (SynapsePro, Onigiri) rank astonishingly high (#4, #5):
  there is real demand for reskinning Anki wholesale. That is explicitly a
  **non-goal** for a sandboxed cross-platform model.

## How desktop addons actually interface with the app

Ranked by prevalence in the analyzed set:

1. **HTML/CSS/JS injection into screen webviews** — official content hooks
   (`deck_browser_will_render_content`, `overview_will_render_content`,
   `webview_will_set_content`, `card_will_show`) or raw `web.eval`. Dominant
   because desktop's screens are already web pages.
2. **Monkey-patching private methods** wherever no hook exists —
   `Reviewer._bottomHTML`, `Overview._table`, `DeckBrowser._renderDeckTree`,
   `NewDeckStats.refresh`, `Editor.loadNote`, `AnkiQt.backup`,
   `Anki2Importer._importNotes`… Every one is a de-facto missing-API signal,
   and this is the #1 breakage source across Anki releases.
3. **`pycmd` bridge messages** (`webview_did_receive_js_message`) — always
   `prefix:payload` strings, i.e. already message-shaped; some use the async
   reply callback (`pycmd(msg, callback)`), which maps 1:1 to a promise-based
   JS API.
4. **Host-served addon assets** — `setWebExports` → `/_addons/<pkg>/…` URLs
   loadable from any webview.
5. **Subsystem registries** — `av_player.players.append` (TTS providers),
   `aqt.mediasrv.post_handlers` (custom data endpoints for in-page JS),
   `aqt.dialogs.register_dialog`, `setConfigAction`.
6. **Native Qt construction** — menus, dialogs, toolbars, dock widgets,
   QShortcut/QAction surgery, synthetic input events. The only part with no
   direct HTML/JS analogue.
7. **Local servers for external integration** — AnkiConnect's raw socket on
   127.0.0.1:8765 (pumped by a 25 ms QTimer), Migaku's Tornado websocket.

Two instructive extremes:

- **Search Stats Extended** (extends the *new Svelte stats page* with ~40
  graphs) has no sanctioned extension point, so it: wraps `NewDeckStats.refresh`,
  `web.eval`s an entire compiled Svelte app onto `document.body` after a
  configurable `setTimeout` race, monkey-patches `window.fetch` inside the page
  to spy on the native search box, and registers custom `mediasrv`
  post-handlers for revlog data. Everything it needs is API-shaped; nothing it
  does is sanctioned. **This addon is a ready-made spec for "extend shared
  Svelte screens".**
- **Contanki** (gamepad) contains no native input code at all: it runs the
  HTML5 Gamepad API in a hidden webview polling at 20 Hz and bridges to Python.
  The web platform already covers more than expected — what it lacks is a
  *command invocation API* (it calls `reviewer._answerCard(1)` and synthesizes
  Qt mouse events instead).

## Portability verdicts

Tiers, against the model (a) reviewer/page JS injection, (b) permissioned JS
API, (c) sandboxed iframe panels, (d) declarative settings:

### Tier 1 — port nearly as-is (web presentation + data/command APIs)

| Addon | UI today | What it needs |
|---|---|---|
| Custom Background/Gear | CSS + images into every screen | per-screen CSS injection, asset URLs, image picker |
| Clickable Tags | tag pills in card HTML | card-render text filter, "open browser with search" deep-link |
| More Overview Stats | HTML table on overview | overview injection point, per-deck state counts API |
| Speed Focus Mode | countdown button in answer bar area; JS timers | show-answer/answer/bury commands, Q/A-shown events, per-deck settings, audio asset playback |
| Pop-up Dictionary | qTip tooltip in reviewer | async `findNotes`/`getNote` bridge, browser deep-link |
| Anki Simulator | Chart.js dialog | iframe dialog, deck-config + revlog read API, Web Worker |
| Search Stats Extended | Svelte graphs under stats page | stats-page mount point + ready/search-changed events, revlog query API |
| Review Heatmap | d3 heatmap on deck list/stats | panel slots outside reviewer, per-day revlog aggregates, custom search token |
| Batch Editing | modal form over browser selection | menu contribution, iframe form, notes-write API **with undo grouping**, selected-notes context |
| Add Table / Mini Format Pack | editor toolbar buttons | declarative editor buttons + semantic format/insert commands (Mini Format Pack survived every editor rewrite *because* it only calls semantic APIs) |
| Anki Killstreaks / Ankimon-class gamification | medals/sprites during review | answer events + overlay/panel + addon storage |
| Long-term backups | headless | lifecycle events + export API |

### Tier 2 — need filter/mount points over host-rendered UI

These change how the *host's own UI* renders — commands and iframes aren't
enough; the API needs **filters** (return-value hooks), which is also exactly
what Anki's `reviewer_will_init_answer_buttons` / `reviewer_will_answer_card` /
`field_filter` already are on desktop:

| Addon | Filter/mount needed |
|---|---|
| Pass/Fail 2, Button Colours, AJT Flexible Grading (core) | answer-button list filter (labels/colors/visibility), ease remap before answer |
| Straight Reward | pre-answer scheduling-state mutation (v3 `set_scheduling_states` — **this channel already exists cross-platform** for custom scheduling JS) |
| Edit Field During Review | field-boundary markup / template filter (`{{edit:…}}`), `updateNoteField` with host-side sanitization, media-paste API |
| Frozen Fields | per-field decoration slot in editor + sticky flag API (died from owning editor DOM; trivial under a real API) |
| Symbols As You Type | editor field input events + insert-at-cursor + **synced addon KV store** (today: a raw table it creates inside collection.anki2!) |
| Advanced Review Bottom Bar | answer-bar customization surface (partial port at best — it *replaces* the native bar) |
| Life Drain, Progress Bar | cross-screen persistent bar slot, screen-change events, **undo events** (Life Drain hard-depends on `review_did_undo`) |
| BetterSearch | search-input API (text+cursor events, setText) on every search field, tag/deck/notetype lists |
| Advanced Browser (read-only part) | declarative browser column registration + computed stats (its SQL `ORDER BY` injection and internal-field editing must die) |
| Enhance Main Window | deck-list row/column decoration + **batched** per-deck stats API (would also fix its notorious O(#decks) SQL) |

### Tier 3 — need native capability APIs behind permissions

| Addon | Native capabilities required |
|---|---|
| HyperTTS / AwesomeTTS | **TTS provider registration** (voice list + async synthesize for `{{tts}}`), network fetch (binary, custom headers), media write + field update, secret storage, AV-queue playback. Desktop's subprocess/`say`/SAPI paths become "platform TTS" (Android `TextToSpeech`) |
| Contanki | Gamepad API in a persistent hidden context (pure JS!), named command registry (answer/undo/navigate), overlay HUD; its synthetic-mouse mode is dropped by design |
| AnkiHub | authenticated fetch + secret storage, sync lifecycle events (incl. pre-sync ordering), background jobs + progress, menu/editor/reviewer/browser mounts, embedded authenticated web panels |
| FSRS Helper (core) | bulk card query/write (due, `custom_data`) with undo integration, revlog read, sync events, background progress. Its raw SQL (incl. `DELETE FROM revlog`) and private backend flags don't port — and shouldn't |
| Image Occlusion Enhanced | its mask editor is *already an HTML/JS app behind a string bridge* — would need media-write + notetype APIs; moot since 23.10 made IO native (the absorption itself validates the architecture) |

### Tier 4 — host features or non-goals, not sandboxed addons

| Addon | Why |
|---|---|
| AnkiConnect | inbound localhost TCP listener for *other applications* (Yomitan…). Can never live in a sandbox — this is **host-feature territory, and AnkiDroid already ships it**: `CardContentProvider` + the LGPL `:api` client (`AddContentApi`) expose notes, note types/templates/fields, decks, media, and `schedule/` (query due + answer cards) over Android IPC, gated per-app by the `READ_WRITE_DATABASE` runtime permission — the Android analog of AnkiConnect's origin-grant prompt, serving the same niche (dictionary apps → cards). Remaining gaps vs AnkiConnect: no GUI navigation verbs, no import/export/profile actions, and IPC is app-to-app only (a companion *browser-extension* → AnkiDroid flow has no path). Its ~110 JSON actions remain the best available spec for our JS API's collection verbs |
| Customize Keyboard Shortcuts | rebinds native Qt shortcuts by id introspection ("incredibly dubious" per its own author). The fix is a first-class **command/keymap registry**; its config file is already the right schema |
| Special Fields | replaces importer internals; needs an import merge-policy extension point |
| Fastbar, Colorful Tags | native Qt toolbar / browser-sidebar model decoration — needs native contribution points per platform, no web equivalent |
| SIAC (PDF reader in Add dialog) | the *pane* is exactly our target architecture (HTML/JS over a message bridge), but its substrate — arbitrary file reads, own SQLite, subprocess, remote script/iframes, bundled Rust module, and a literal `eval()` bridge — is categorically unsandboxable |
| Migaku (current) | bundles ffmpeg via subprocess — out of scope |
| SynapsePro, Onigiri | whole-UI replacement: explicit non-goal for a cross-platform sandbox |

**Coverage estimate**: of the rating-sorted top ~40 plus the high-vote
veterans, roughly **two-thirds land in Tiers 1–2** (portable with the JS API
plus filter/mount points), the TTS/service cluster needs Tier-3 capability
APIs, and a small but important set (AnkiConnect, keymap, whole-UI reskins)
are host-feature territory.

## What this means for the model

The plan's v1 primitives (settings schema, reviewer JS, iframe panels) cover
Tier 1 well. The survey adds these requirements, in priority order:

1. **Filters, not just commands+events.** Answer-button filter, ease remap,
   pre-answer scheduling-state mutation, card-render/field text filters.
   Desktop addons are predominantly *filters over host rendering*; a
   command-only JS API cannot express the single most popular addon category
   (review-UI tweaks). On AnkiDroid the answer bar is native — these filters
   must be implemented natively and exposed to addon JS.
2. **Events beyond the card lifecycle**: answered-with-ease, **undo performed**,
   screen/state change, review-session end, sync start/finish, profile/collection
   lifecycle.
3. **Read APIs**: search (`findNotes`/`findCards` with Anki query syntax), note
   field read, per-card revlog, per-day revlog aggregates, batched per-deck
   state counts, card stats/memory-state. (AnkiConnect's action catalog +
   Search Stats Extended's three endpoints are the reference spec.)
4. **Write APIs with undo semantics**: note field update (host-sanitized),
   bury/suspend/flag, bulk card writes with undo-entry grouping and progress.
5. **UI mount points beyond the reviewer**: deck-list/overview/stats panel
   slots, a persistent indicator strip, editor toolbar buttons (semantic
   commands only), menu/context-menu contributions, browser columns. On
   AnkiDroid, native screens (deck picker, browser, editor) need either small
   WebView mount points or these surfaces wait for shared Svelte equivalents.
6. **Scoped settings**: per-deck / per-preset / per-notetype — global-only
   declarative settings are insufficient (Speed Focus Mode, Life Drain,
   Straight Reward all scope per deck).
7. **Synced addon KV storage** (ends "create a table inside collection.anki2"
   and private SQLite files) and addon asset serving.
8. **Platform affordances**: toast, audio playback of bundled assets, TTS
   provider registration, gamepad permission, shortcut/gesture registration,
   navigation deep-links ("open browser with this search").
9. **Deliberately excluded, with API-shaped replacements**: raw SQL, subprocess,
   arbitrary filesystem access, synthetic input into native UI, remote script
   injection, eval bridges, inbound sockets (→ host-owned external API — which
   AnkiDroid already provides via `CardContentProvider`/`:api`).

The strongest empirical validation of the whole approach: **addons that called
stable semantic APIs survived every Anki rewrite; addons that owned DOM or
patched private methods died at 2.1.41, 2.1.50, 23.10 — and their replacements
now occupy the top charts as forks.** The sandbox boundary the plan proposes
sits exactly on that line, and the missing-API list above is precisely the set
of things addons currently monkey-patch to get.
