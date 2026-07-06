# Cross-platform addon UI — plan

> **⚠ This is a design/planning doc, not a spec.** It captures the state of the
> addons effort as of 2026-07 and a proposed direction for addon settings UIs.
> Cross-check issue/PR links before relying on details. Companion docs:
> [prior-art.md](prior-art.md) — how Obsidian, Joplin, Logseq, VS Code,
> WebExtensions and Anki desktop solve this, with implementation details;
> [addon-survey.md](addon-survey.md) — the top AnkiWeb addons, how they build
> their UI and hook into the app today, and whether they'd port to this model.

## Goal

Addons need user-facing configuration. The UI for it should be written once in
HTML/CSS/JS/TS and work across the Anki ecosystem (AnkiDroid, Anki desktop,
AnkiMobile), rather than each platform hand-rolling native preference screens.

From the originating discussion (Brayan & David):

> It should be on HTML/CSS/JS/TS to be cross-platform. We should provide some
> standard widgets in the addons settings API, such as switches, dropdowns,
> sliders, number selectors, etc — something similar to what Obsidian does.

This doc covers the **settings/UI layer**: how an addon declares its settings,
how they are rendered and persisted, and how an addon can ship custom UI. The
addon *runtime* (what an addon can do, where its code executes) and
*distribution* (how users find and install addons) are adjacent tracks — they
are summarized where they constrain the UI design, and tracked in their own
issues.

## Where things stand

### History in this repo

- [#7959](https://github.com/ankidroid/Anki-Android/issues/7959) (2020) — the
  JS Addons tracking issue. krmanik's design: addons are npm packages tagged
  `ankidroid-js-addon`, downloaded as `.tgz` from the npm registry, extracted to
  the collection's `addons/` folder, with `addonType: reviewer | note-editor`
  deciding where their `index.js` is injected. Mike Hardy's framing from 2020
  still applies: *"if we do them well [they] could be something that goes out
  for the whole ecosystem… every convention we adopt may have tremendous impact
  that lasts for the next decade."*
- **Merged and dormant** — `com.ichi2.anki.jsaddons`: manifest mapping/validation
  (`AddonData`/`AddonModel`, npm `package.json` shape), `NpmUtils.validateName`,
  `TgzPackageExtract` (zip-slip-safe, disk-space-guarded extraction). Nothing
  references this package from app code today.
- **Never landed** — [#10985](https://github.com/ankidroid/Anki-Android/pull/10985)
  (addons browser: list/enable/disable/delete, 2022),
  [#11090](https://github.com/ankidroid/Anki-Android/pull/11090) (`AddonStorage`).
  A 2024 revival branch (`list-addons-from-dir`, commit `61feaa073e`) has a more
  modern `AddonsBrowserActivity` + details dialog, profile-aware, behind dev
  options.
- [#20695](https://github.com/ankidroid/Anki-Android/issues/20695) (2026) — the
  active design discussion for the new JS API: developer contract (update URL
  rather than email) and permission scopes. Working position: **card templates
  cannot be sandboxed meaningfully; addons are the security boundary**, with
  per-capability `read|write` permissions and separately-granted dangerous
  permissions.
- JS API today: `AnkiDroidJsAPI` (old reviewer, `AbstractFlashcardViewer`) and the
  `/jsapi/<method>` POST route in `pages/PostRequestHandler.kt` (new screens),
  gated by an `ApiContract` (`version` + `developer` fields).

### Infrastructure we already share with desktop/iOS

This is the decisive fact for the UI design — **the cross-platform web layer
already exists and is in production on all three clients**:

- Anki's `ts/` SvelteKit app builds to a static bundle that AnkiDroid ships as
  APK assets (via the Anki-Android-Backend AAR) and renders in WebViews:
  deck options, statistics, card info, change-notetype, import screens, congrats
  (`pages/PageFragment.kt`, allowlist in `pages/PageWebViewClient.kt`).
- Serving model: GETs are intercepted in-process (`shouldInterceptRequest` →
  APK assets); RPC is POST-only to a localhost NanoHTTPD (`pages/AnkiServer.kt`:
  `/_anki/<method>` protobuf calls, `/ankidroid/` UI methods, `/jsapi/`).
- `ts/lib/components` is a maintained, themed (day/night), localized widget
  library: `SwitchRow`, `SpinBoxRow`/`SpinBoxFloatRow`, `EnumSelectorRow`,
  `Select`, `CheckBox`, `TitledContainer`, `Modal`, `Collapsible`, … — i.e. the
  "switches, dropdowns, number selectors" already exist. (A slider does not; see
  open questions.)
- Desktop's deck-options page already exposes a JS addon API
  (`$deckOptions.then(...)`, `addHtmlAddon`/`addSvelteAddon`, `auxData()` for
  synced per-preset addon data) and exports a curated widget set
  (`TitledContainer, SpinBoxRow, SpinBoxFloatRow, EnumSelectorRow, SwitchRow`).
  `require("anki/...")` + the bundled Svelte runtime are exposed to addon code.
  **AnkiDroid can't use any of this yet** — we don't serve addon files
  (`/_addons/` equivalent) or fire an equivalent of `deck_options_did_load`.
- Upstream direction (see [prior-art.md](prior-art.md) § Anki ecosystem): dae
  wants iframe isolation for third-party code on Svelte screens
  ([ankitects/anki#3833](https://github.com/ankitects/anki/issues/3833)); a JS
  hooks API for Svelte screens is proposed
  ([ankitects/anki#4529](https://github.com/ankitects/anki/issues/4529)); AnkiHub
  now stewards desktop and has pledged "add-ons working across mobile platforms".

## Design

### Principles (justified in [prior-art.md](prior-art.md) § Lessons)

1. **Declarative-first.** Settings are a schema the host renders — not addon
   code drawing widgets. Obsidian retrofitted exactly this in 1.13 (for settings
   search, validation, mobile rendering) after a decade of imperative plugins;
   VS Code/Joplin/Logseq were declarative from the start. A schema also means
   the settings UI works *without executing addon code* — important given
   addons-as-security-boundary.
2. **Reuse Anki's widget library; don't invent one.** The Obsidian-style widget
   set is delivered by rendering with `ts/lib/components` in a shared Svelte
   page. (VS Code's standalone webview toolkit died of unfunded maintenance;
   Anki's components survive because the app is built from them.)
3. **One page, all platforms** — the settings renderer is a SvelteKit route in
   the shared bundle, like deck-options. Platforms contribute only a thin host
   (fragment/dialog + persistence bridge).
4. **Custom UI is the escape hatch, sandboxed.** Addons needing more than the
   schema render their own HTML **in an iframe** with a postMessage RPC —
   Joplin-mobile/Logseq/VS Code pattern, and dae's stated direction. Never
   same-origin with the app page.
5. **Host-owned, namespaced persistence** with explicit scopes (synced vs
   local). Values persist; schemas are supplied by the installed addon version —
   no host-side schema migrations.

### Addon manifest + settings schema (v1 sketch)

The existing npm `package.json` shape ([#7959](https://github.com/ankidroid/Anki-Android/issues/7959))
gains a `settings` array. Types deliberately mirror what
`ts/lib/components` can render today; presentation hints follow Logseq's
`inputAs` trick rather than new types.

```jsonc
{
  "name": "ankidroid-js-addon-progress-bar",
  "addonTitle": "Progress Bar",
  "version": "1.2.0",
  "addonType": "reviewer",
  "ankidroidJsApi": "0.0.3",
  "homepage": "https://github.com/...",         // + updateUrl per #20695
  "permissions": ["cards:read"],                 // #20695 capability scopes
  "settings": [
    { "type": "heading", "title": "Appearance" },
    { "key": "enabled",   "type": "toggle", "title": "Show progress bar", "default": true },
    { "key": "position",  "type": "enum",   "title": "Position", "default": "top",
      "choices": [ { "value": "top", "label": "Top" }, { "value": "bottom", "label": "Bottom" } ] },
    { "key": "height",    "type": "number", "title": "Bar height", "default": 4,
      "min": 1, "max": 24, "step": 1, "inputAs": "slider" },
    { "key": "goodColor", "type": "text",   "title": "Colour", "inputAs": "color", "default": "#4CAF50" },
    { "key": "scope",     "type": "number", "title": "Cards per session", "default": 100,
      "min": 1, "max": 9999 }                    // no inputAs → SpinBoxRow
  ]
}
```

| Schema type (+hint) | Rendered by | Notes |
|---|---|---|
| `toggle` | `SwitchRow` | |
| `enum` | `EnumSelectorRow` / `Select` | radio presentation possible later (Logseq `enumPicker`) |
| `number` | `SpinBoxRow` / `SpinBoxFloatRow` | `inputAs: "slider"` → slider (component to be added upstream) |
| `text` | text input row | `inputAs: "color" \| "date"` → native input types |
| `textarea` | textarea row | |
| `heading` | `SettingTitle` / `TitledContainer` section | grouping |
| `action` | `LabelButton` | fires an event to the addon (needs runtime), e.g. "Clear cache" |

Common fields: `key`, `title`, `description` (markdown, localizable), `default`,
`visibleIf`/`disabled` (later), `validate` via `min`/`max`/`pattern`. Titles and
descriptions are addon-supplied strings; addon i18n is out of scope for v1
(addons may localize before declaring — open question below).

Anything the schema can't express falls back to (a) a raw JSON editor — exactly
Anki desktop's current behaviour, so it is never a regression — or (b) the
sandboxed custom panel, below.

### Rendering: a shared `addon-settings` page

New SvelteKit route in the shared bundle (upstream `ts/routes/addon-settings/`),
rendered like deck-options on every platform:

```
AnkiDroid AddonsBrowser ──"Configure"──► PageFragment(addon-settings/<addonId>)
                                              │  GET assets: APK bundle (existing)
                                              │  POST /ankidroid/getAddonSettings ► { schema, values }
                                              │  POST /ankidroid/setAddonSettings ◄ { key: value, ... }
                                              └  #night flag → existing theme handling
```

- The page fetches `{schema, values}` from the host, renders rows with
  `ts/lib/components`, and writes values back through one debounced POST method.
  Save/discard semantics can copy deck-options (`deckOptionsPendingChanges`,
  which AnkiDroid's back-handling already understands — `pages/DeckOptions.kt`).
- Host method names are platform-neutral on purpose: desktop implements the same
  two methods against its `meta.json` store; AnkiMobile likewise. The page is
  identical everywhere — that is the whole point.
- Until the route exists upstream, AnkiDroid can prototype: the web assets ship
  from **Anki-Android-Backend** (our own packaging repo), which can carry the
  route as a patch while the upstream PR is in review. Pure-AnkiDroid fallback
  (local asset page without anki components) is possible but loses the shared
  look — patch-via-backend is preferred.

### Custom UI escape hatch (phase 3)

For addons that outgrow the schema (e.g. an interactive preview):

- Manifest declares `settingsPage: "settings.html"`; the host renders it inside
  the addon-settings page in a **sandboxed `<iframe>`** (separate origin — serve
  addon files from a distinct localhost port or origin-isolating scheme, cf.
  Logseq's `lsp://`; never `file://`).
- Bridge: postMessage RPC with path-based calls and callback IDs — Joplin's
  chained `RemoteMessenger` (React Native ↔ WebView ↔ iframe) is the reference
  for our Kotlin ↔ page ↔ iframe chain. Exposed surface: `getSettings`,
  `setSettings`, theme tokens, and the JS API subset the addon's permissions
  allow.
- Widgets inside the iframe: `require("anki/components")`-style runtime exports
  (already exists upstream for deck-options addons), or plain HTML + published
  CSS variables for theme parity.
- This requires serving addon-local files at all — the AnkiDroid equivalent of
  desktop's `setWebExports` + `/_addons/<id>/...` (extend
  `shouldInterceptRequest`, keep `AnkiServer` POST-only).

### Beyond settings: what real addons need ([addon-survey.md](addon-survey.md))

A survey of the top AnkiWeb addons against this model shows the settings +
iframe primitives cover the "pure web presentation" tier well (heatmaps,
progress bars, tooltips, gamification, stats graphs, editor buttons), but the
most popular category — review-UI tweaks — consists of **filters over
host-rendered UI**, not standalone panels. Roughly two-thirds of the top
addons port, *if* the runtime API grows, in priority order:

1. **Filters**: answer-button list (labels/colors/visibility), ease remap,
   pre-answer scheduling-state mutation (the v3 custom-scheduling channel
   already proves this cross-platform), card-render/field text filters. On
   AnkiDroid the answer bar is native — these are native implementations
   exposed to addon JS.
2. **Events**: answered-with-ease, undo performed, screen change, session end,
   sync start/finish.
3. **Read/write APIs**: search + note read, revlog (per-card + per-day
   aggregates), batched per-deck counts; note/card writes with host-side
   sanitization and **undo-entry grouping**. AnkiConnect's ~110 actions are
   the reference catalog.
4. **Mount points beyond the reviewer**: deck-list/overview/stats panel slots,
   persistent indicator strip, editor toolbar buttons (semantic commands
   only), menus, browser columns — on AnkiDroid these are native screens, so
   each needs a native slot hosting a WebView, or waits for shared Svelte
   equivalents.
5. **Scoped settings** (per-deck/preset/notetype) and a **synced addon KV
   store**.
6. **Permissioned native capabilities**: network fetch, TTS provider
   registration, audio, gamepad, secret storage.

Out of scope by design (each with an API-shaped replacement): raw SQL,
subprocess, arbitrary filesystem access, synthetic input, inbound sockets, and
whole-UI replacement. AnkiConnect's niche — external apps creating/querying
cards — is already served natively on Android by `CardContentProvider` + the
LGPL `:api` module (per-app `READ_WRITE_DATABASE` runtime permission), so the
addon system doesn't need to solve external integration here; the two systems
should share the permission-model thinking in
[#20695](https://github.com/ankidroid/Anki-Android/issues/20695).

### Execution contexts: where addon code runs (incl. screens with no WebView)

On AnkiDroid the deck picker, browser and editor are native — there is no
WebView to inject into — and several addon archetypes are headless entirely
(backup-on-close, FSRS-Helper-style sync catch-up, Life Drain's cross-screen
state, Contanki's always-on gamepad polling). Three context types cover all
cases:

1. **Page contexts** (ephemeral) — addon JS injected into an existing WebView:
   the study screen, shared Svelte pages, iframe panels. DOM available; dies
   with the screen.
2. **Declarative contributions, no code at all** — menu items, editor buttons,
   shortcuts, columns, panel registrations are manifest declarations rendered
   *natively* by the host. Simple actions (open panel X, run search Y,
   deep-link) execute natively without any JS. Most native-screen interactions
   from [addon-survey.md](addon-survey.md) Tier 2 are declaration + a small
   handler, not resident code.
3. **Background context** (app-scoped, per profile) — one headless JS runtime
   hosting each enabled addon's `background.js` in isolation. This is where
   lifecycle/sync/undo/answered events are delivered, where handlers for
   declarative contributions run, where cross-screen state lives, and what
   native screens talk to over the same RPC used everywhere else.

**Android implementation — decided: a hidden WebView host.** One detached,
app-scoped WebView containing one sandboxed iframe per addon (distinct
origins). This is exactly Joplin's shipped-and-proven Android architecture
(hidden 1×1 WebView, per-plugin iframes, chained postMessage RPC), and it
reuses the same iframe/origin/RPC machinery as our custom settings panels —
one bridge to build. The full web platform comes free: timers, Gamepad API
for Contanki-class addons, Web Audio, fetch under permission policy.

Implementation constraints:

- Lazy-start: create the host only when an enabled addon declares a
  background entry (Joplin does this to protect startup time/memory).
- WebView APIs are main-thread; keep bridge dispatch off the UI thread where
  possible.
- **Never introduce `WebView.pauseTimers()`** anywhere in the app — it pauses
  timers in every WebView process-wide and would silently freeze the host (we
  currently never call it; guard with a lint rule or comment on the host).
- Addons must tolerate process death — contexts are ephemeral, values persist
  (the Joplin re-register-on-every-start principle).

Alternative noted for the future only: `androidx.javascriptengine`
(JavaScriptSandbox) — out-of-process V8 isolates with per-isolate heap
limits. Stronger resource-accounting, but no DOM/timers/fetch (everything
hand-bridged); revisit as a hardening path once the API surface is complete,
not for v1.

Platform analogs: iOS has a first-class no-WebView JS engine (JavaScriptCore
`JSContext`) or a hidden WKWebView; desktop already runs hidden webviews for
this (Contanki) or keeps logic in Python. The spec should define the
*contract* — a headless context with the addon API bridge and **no DOM
guarantee** — and let platforms choose the engine.

Policy: the background context runs only while the app process is alive and
foregrounded — no foreground services in v1; idle addons' iframes can be torn
down and relaunched on demand. Addons with persistent polling (gamepad) keep
theirs alive and should be visible as such in the addons browser.

### Persistence

Host-owned KV, namespaced by addon id, scoped:

| Scope | Backing store | Analog |
|---|---|---|
| `synced` (default) | collection config, key `addon:<id>` (JSON object) | WebExtensions `storage.sync`; deck-options `auxData` precedent shows addon data syncing today |
| `local` | per-profile SharedPreferences / file | WebExtensions `storage.local`; per-device tweaks (e.g. fullscreen) |
| per-deck-preset (later) | deck config `auxData` | already synced + exposed on desktop |

Reads/writes go through the host (which enforces namespace + a size quota —
collection config entries sync, so quotas matter). The dormant
`AddonModel.updatePrefs` StringSet-of-enabled-addons approach is superseded:
enabled/disabled becomes a key in the addon's own store (as Logseq does).

### Permissions & contract

Defined in [#20695](https://github.com/ankidroid/Anki-Android/issues/20695), not
here. Constraints the UI plan inherits:

- Manifest-declared capabilities shown at install time; dangerous ones granted
  individually at runtime (WebExtensions `optional_permissions`-style, requested
  within a user gesture and revocable from the addon browser).
- The settings *schema* renders without executing addon code, so the settings
  screen itself needs no permission grants; `action` buttons and custom panels
  do.
- Contract gains `updateUrl` (self-service fixes) replacing the email-only
  developer field.

### Distribution (adjacent track — summarized)

The UI design is independent of the registry choice; don't block on it. Current
assets: npm keyword + tgz pipeline (built, dormant), AnkiWeb (desktop-only
today), or an Obsidian/Logseq-style curated index + GitHub releases. Ecosystem
decision to make with upstream/AnkiHub. Whatever is chosen: no addon
self-update, semver + `minApiVersion` gating (mikunimaru's versioning guidance in
#7959), and a revocation list (krmanik's `remove.txt` idea, done properly).

## Phased plan

**Phase 0 — alignment (upstream RFC).** Write up the settings schema + shared
`addon-settings` route as an RFC referencing ankitects/anki#3833/#4529; get
dae/AnkiHub/Brayan sign-off on schema v1 and on adding the route (and a slider
component) to `ts/`. This is the highest-leverage, lowest-code step — everything
cross-platform hangs off it. Decide AnkiDroid's runtime v1 scope in
[#20695](https://github.com/ankidroid/Anki-Android/issues/20695) in parallel.

**Phase 1 — addon manager foundation (AnkiDroid, no new UI tech).** Revive the
2024 `list-addons-from-dir` branch: `AddonStorage`
([#11090](https://github.com/ankidroid/Anki-Android/pull/11090)), install from
local file, list/enable/disable/delete browser
([#10985](https://github.com/ankidroid/Anki-Android/pull/10985)), behind a dev
option. Port `jsaddons` prefs to the host-owned store. Exit criteria: an addon
can be installed from disk, toggled, and injected into the new study screen
(`ui/windows/reviewer`) behind the flag.

**Phase 2 — declarative settings (the core of this doc).** Schema parsing +
validation in AnkiDroid; `getAddonSettings`/`setAddonSettings` POST methods; the
shared Svelte `addon-settings` page (upstream PR, carried as an
Anki-Android-Backend patch meanwhile); "Configure" action in the addons browser;
JSON-editor fallback for schema-less addons. Exit criteria: the progress-bar
example above is configurable via generated UI that matches deck-options
styling in day/night themes.

**Phase 3 — sandboxed runtime: custom panels, background context,
permissions.** `/_addons/` static serving; the sandboxed iframe host +
postMessage RPC (one implementation serving both custom panels and the
**hidden-WebView background context** decided above); widget/runtime exports;
permission prompts wired to the JS API. Exit criteria: an addon with
`settingsPage` renders sandboxed and can only call granted APIs, and a
background addon (e.g. a Life-Drain-style state tracker) receives events
across screen changes without any visible WebView.

**Phase 4 — ecosystem rollout.** Desktop implements the two settings host
methods against `meta.json` (giving desktop schema-generated config UI for JS
addons — replacing raw JSON editing); AnkiMobile equivalent; distribution
registry per the ecosystem decision; public docs + template repo
(Obsidian-style sample addon).

Sequencing notes: Phases 1 and 0 run in parallel. Phase 2 depends on both.
Nothing in Phases 1–2 requires the permissions system to be finished — the
schema UI executes no addon code.

## Open questions

1. **Where does the settings page live long-term?** Upstream `ts/routes/`
   (preferred; needs dae/AnkiHub buy-in) vs maintained as an
   Anki-Android-Backend patch (works, but drifts and doesn't help desktop).
2. **Slider component** — add to `ts/lib/components` upstream, or ship
   `inputAs: "slider"` initially rendered as a SpinBox until it exists?
3. **Synced storage backing** — is collection config (`addon:<id>` keys)
   acceptable to upstream for third-party data (size limits, sync conflict
   semantics), or does this need a backend-blessed addon-data table? auxData
   proves the pattern per-preset; a collection-level equivalent needs upstream
   agreement.
4. **Schema in manifest vs runtime registration.** Manifest (VS Code-style) lets
   the host render settings without executing addon code and index them for
   search; runtime registration (Joplin/Logseq) allows dynamic schemas. v1
   proposal: manifest-only, revisit if real addons need dynamism.
5. **Addon string localization** — v1 ships author-supplied strings only; do we
   later accept per-locale schema strings (`title: {en, ja, ...}`)?
6. **Multiprofile interaction** — addons and their `local` settings are
   per-profile today (2024 branch loads per-profile); is that the intended
   model? (`synced` scope follows the collection, so profiles get it for free.)
7. **AnkiMobile participation** — no general JS API there today (only User
   Actions 1–8); the shared page only needs the two POST methods, but someone
   must implement them.
8. **Old reviewer** — do reviewer addons target only the new study screen
   (proposed: yes) or also `AbstractFlashcardViewer`?
9. **Native-surface filters/mounts on AnkiDroid** — the answer bar, deck
   picker, browser and editor are native Android UI. Which addon filter/mount
   points (answer-button filter, deck-list panel slot, editor buttons — see
   [addon-survey.md](addon-survey.md)) do we implement natively in v1, and
   which wait for shared Svelte ports of those screens?
10. **Background entry point shape** — manifest field (`background` entry
    alongside `addonType`), idle-teardown policy, and whether
    persistent-polling addons (gamepad) need a distinct declaration so the
    addons browser can surface their battery cost.

## References

- Issues: [#7959](https://github.com/ankidroid/Anki-Android/issues/7959) (JS
  addons tracking), [#20695](https://github.com/ankidroid/Anki-Android/issues/20695)
  (contract & permissions), [ankitects/anki#3833](https://github.com/ankitects/anki/issues/3833)
  (template components), [ankitects/anki#4529](https://github.com/ankitects/anki/issues/4529)
  (Svelte-screen JS hooks)
- PRs / branches: [#10985](https://github.com/ankidroid/Anki-Android/pull/10985),
  [#11090](https://github.com/ankidroid/Anki-Android/pull/11090),
  `list-addons-from-dir` (`61feaa073e`)
- Code: `AnkiDroid/src/main/java/com/ichi2/anki/jsaddons/`,
  `AnkiDroid/src/main/java/com/ichi2/anki/pages/` (`AnkiServer`, `PageFragment`,
  `PageWebViewClient`, `PostRequestHandler`, `DeckOptions`),
  `AnkiDroidJsAPI.kt`; upstream `ts/lib/components/`, `ts/routes/deck-options/`,
  `ts/lib/tslib/runtime-require.ts`
- [prior-art.md](prior-art.md) — full prior-art survey with sources
- [addon-survey.md](addon-survey.md) — top AnkiWeb addons: UI surfaces,
  integration mechanisms, portability tiers
