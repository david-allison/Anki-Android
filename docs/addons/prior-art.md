# Addon settings UIs — prior art

> Companion to [README.md](README.md) (the addon UI plan). This surveys how other
> open-source ecosystems let addons declare/render settings UIs, with implementation
> details relevant to a cross-platform (desktop + mobile WebView) HTML/CSS/JS/TS
> addon system for Anki. Researched 2026-07; links may drift.

The systems fall on a spectrum:

```
declarative schema, host renders UI                      addon ships its own HTML
◄──────────────────────────────────────────────────────────────────────────────►
VS Code        Joplin        Logseq        Obsidian (1.13+ │ classic)   WebExtensions
```

## Comparison at a glance

| | Settings declaration | Widget set | Custom-UI escape hatch | Persistence | Sandboxing | Permissions | Distribution | Mobile |
|---|---|---|---|---|---|---|---|---|
| **VS Code** | JSON Schema in `package.json` (`contributes.configuration`) | Host-rendered: checkbox, dropdown (enum), number/string inputs, list/grid editors | Webview: CSP-locked iframe, postMessage only | Host-owned `settings.json`, scoped (user/workspace/language) | Extension host has **no DOM access**; webviews isolated | None (trust + marketplace scanning) | Marketplace | VS Code web only |
| **Joplin** | `SettingItem` records registered at plugin startup | Host-rendered per platform: React DOM on desktop, React Native on mobile | Panels/dialogs = real webviews with `postMessage` bridge | Host-owned, namespaced `plugin-<id>.<key>` in the app's own settings model | Desktop: hidden BrowserWindow (has Node!); mobile: **iframe per plugin in one hidden WebView** | None (manifest `platforms` gating only) | npm crawler → curated repo of `.jpl` tarballs | **Yes — same bundle runs on Android/iOS** |
| **Logseq** | `SettingSchemaDesc[]` via `useSettingsSchema()` | Host-rendered: input, toggle, select/radio/checkbox, textarea, color, range, date | `provideUI`/`showSettingsUI`; iframe plugins | Host-owned JSON file per plugin id | Sandboxed iframe, custom `lsp://` scheme for origin isolation | `effect` flag = same-origin escalation (stricter review) | PR-curated marketplace → GitHub release zips | No (desktop + web) |
| **Obsidian** | Classic: imperative `Setting` builder. 1.13+: declarative `getSettingDefinitions()` | `addToggle/addDropdown/addSlider/addText/addTextArea/addColorPicker/addSearch/...` | Full DOM access (no sandbox at all) | Plugin-owned `data.json` via `loadData()/saveData()` | **None** — "Obsidian cannot reliably restrict plugins"; Restricted Mode default-off switch | None (policies + review + scanning) | PR-curated registry → GitHub releases | **Yes — Capacitor WebView, `isDesktopOnly` opt-out** |
| **WebExtensions** | None — addon ships `options_ui` HTML page | None (BYO; `browser_style` standardization attempt was removed) | The options page *is* custom UI (iframe in about:addons) | `browser.storage.sync/local` KV (+quotas, `onChanged`) | Browser process/origin model | **Yes** — install-time manifest + runtime `permissions.request()` | Store review + signing | Firefox Android |
| **Anki desktop today** | None — `config.json` defaults + `config.md` docs | None — raw JSON editor dialog (or hand-rolled Qt via `setConfigAction`) | Full Python/Qt access; deck-options Svelte page has a JS injection API | `meta.json` per addon, merged over `config.json` | None | None | AnkiWeb `.ankiaddon` | No |

---

## VS Code — the declarative end

- Extensions declare settings as JSON Schema under `contributes.configuration` in
  `package.json`: `type`, `default`, `description`/`markdownDescription`, `enum` +
  `enumDescriptions`/`enumItemLabels`, `minimum`/`maximum`, `pattern` +
  `patternErrorMessage`, `order`, `editPresentation: "multilineText"`,
  `deprecationMessage`, plus scopes (`application`/`machine`/`window`/`resource`/
  `language-overridable`).
- The host renders the entire settings editor: boolean → checkbox, string+enum →
  dropdown, number/string → inputs, simple arrays → editable lists, flat objects →
  key/value grids. **Coverage is bounded**: nested objects/arrays/null fall back to
  an "Edit in settings.json" link. Search/filter comes for free because the schema
  is introspectable.
- Custom UI exists only behind a hard boundary: webviews are isolated contexts with
  scripts disabled by default, a recommended CSP (`default-src 'none'; ...`),
  `localResourceRoots` allowlists, `asWebviewUri()` resource mapping, and
  postMessage as the sole channel (`acquireVsCodeApi()`); state is lost on
  backgrounding unless `getState`/`setState` (or expensive `retainContextWhenHidden`)
  is used. Extension code itself runs in a DOM-less extension host process,
  explicitly so extensions cannot break UI performance or block core UI evolution.
- Theming for webviews: body classes `vscode-light`/`vscode-dark` + CSS variables
  (`--vscode-editor-foreground` etc.).
- Cautionary tale: the official **webview-ui-toolkit** (web components matching the
  VS Code design language) was **sunset in Jan 2025** when its underlying framework
  (FAST Foundation) was deprecated and no rewrite was resourced. Community forks
  (`vscode-elements`) filled the gap. Lesson: shipping a widget toolkit is a
  long-term maintenance commitment; prefer widgets the app itself already maintains.

Sources: [contributes.configuration](https://code.visualstudio.com/api/references/contribution-points#contributes.configuration),
[Webview API](https://code.visualstudio.com/api/extension-guides/webview),
[Extension host](https://code.visualstudio.com/api/advanced-topics/extension-host),
[toolkit sunset](https://github.com/microsoft/vscode-webview-ui-toolkit/issues/561)

## Joplin — the closest analog (same JS plugin on desktop + Android/iOS)

> **Status check (2026-07):** shipped (Android v3.0, 2024) and actively
> maintained — plugin-runner architecture work was still landing in June 2026
> (e.g. loading plugin scripts from the filesystem instead of the bridge,
> [#15095](https://github.com/laurent22/joplin/pull/15095)). Notably, mobile
> plugin support is **still opt-in behind a security disclaimer ~2.5 years
> after shipping**, iOS remains constrained by App Store third-party-code
> rules, and new APIs (the 2026 AI namespace) land desktop-only — the
> platform gap is a permanent condition to manage, not a launch-phase issue.

- **Declaration**: `joplin.settings.registerSettings()` with declarative
  `SettingItem`s: `type` (Int/String/Bool/Array/Object/Button), `isEnum` +
  `options` (dropdown), `minimum/maximum/step`, `secure` (password + keychain),
  `advanced` (collapsed by default), `public` (false = hidden storage-only),
  `appTypes` (desktop/mobile/cli), `subType` (file/directory pickers — marked
  "Not supported on mobile!"), `storage` (Database vs synced settings file), plus
  `registerSection(name, {label, iconName})`. Registration is **re-done on every
  startup; only values persist** — so the host never migrates schemas.
- **Key trick**: plugin settings are namespaced (`plugin-<id>.<key>`) into the
  *same* Setting model as built-in settings, so the settings screen is generated
  by the same code path for free. A shared `settingsToComponents2(…, AppType, …)`
  iterator renders **React DOM widgets on desktop and React Native widgets on
  mobile** from one declaration — proof that a single declarative schema can be
  rendered with per-platform native affordances.
- **Runtime, desktop**: each plugin runs in a hidden Electron BrowserWindow with
  `nodeIntegration: true` — process isolation for robustness, *not* security.
  API calls become path-based strings over IPC.
- **Runtime, mobile** (the proven Android pattern): one hidden 1×1 WebView hosts
  **one sandboxed iframe per plugin**; the identical plugin bundle runs there. The
  bridge is a transport-agnostic path-based RPC (`RemoteMessenger`): messages are
  `InvokeMethod {methodPath: string[], args}` / `ReturnValueResponse` /
  `ErrorResponse`; callbacks are transferred as IDs held in a registry, with a
  `FinalizationRegistry` GC-ing dropped remote callbacks; messengers **chain**
  React Native ↔ WebView ↔ iframe. Unavailable APIs are per-platform stubs that
  throw (e.g. clipboard denied on iOS per App Store rules). Manifest gates:
  `platforms: ["desktop","mobile"]`, `app_min_version_mobile`. Plugin support on
  mobile is opt-in in settings.
- **Custom UI**: panels and dialogs are real webviews (`setHtml` + `addScript`,
  two-way `postMessage`); dialogs auto-extract `formData` from any `<form>` in the
  HTML — a cheap way to get structured values out of custom UI.
- **Distribution**: `npm publish` with `joplin-plugin-` name prefix + keyword; a
  crawler ingests `.jpl` (plain tar of webpack output) into a curated GitHub repo
  every 30 min; apps install/search from that repo. No permission model.

Sources: [settings API](https://joplinapp.org/api/references/plugin_api/classes/joplinsettings.html),
[plugin spec](https://joplinapp.org/help/dev/spec/plugins/),
[mobile debugging](https://joplinapp.org/help/api/references/mobile_plugin_debugging/),
[manifest](https://joplinapp.org/help/api/references/plugin_manifest/)

## Logseq — declarative schema with presentation hints, iframe sandbox

- **Declaration**: `logseq.useSettingsSchema([...])` with
  `{key, type: 'string'|'number'|'boolean'|'enum'|'object'|'heading', default,
  title, description (markdown), inputAs?: 'color'|'date'|'datetime-local'|'range'|'textarea',
  enumChoices?, enumPicker?: 'select'|'radio'|'checkbox'}`.
  Note `inputAs: 'range'` — slider is a *presentation hint* on a number, not a
  separate type. `heading` gives grouping. No schema → raw JSON editor fallback.
- **Rendering**: host-side generic renderer dispatches on type; text inputs
  debounced 1s; writes go through a host-owned per-plugin `settings.json`
  (`~/.logseq/settings/<plugin-id>.json`); `onSettingsChanged` pushes the full
  settings object back to the plugin. Even the plugin's `disabled` state is just a
  key in the same store.
- **Runtime**: plugins run in **sandboxed iframes** loaded from a custom privileged
  scheme (`lsp://logseq.io/...`) so plugin origin ≠ app origin (`lsp://logseq.com`)
  — origin isolation by URL scheme rather than iframe `sandbox=` attributes. The
  bridge is a vendored Postmate fork: handshake + upgrade to `MessageChannel`
  ports. An `effect: true` manifest flag runs a plugin same-origin ("discouraged…
  faces stricter review") — a one-bit permission model.
- **Distribution**: PR to a marketplace repo (manifest: `title/description/author/
  repo`, optional `icon/theme/effect/web`); binaries pulled from the plugin repo's
  GitHub releases.

Sources: [LSPlugin.ts](https://github.com/logseq/logseq/blob/master/libs/src/LSPlugin.ts),
[plugins_settings.cljs](https://github.com/logseq/logseq/blob/master/src/main/frontend/components/plugins_settings.cljs),
[marketplace](https://github.com/logseq/marketplace)

## Obsidian — the ergonomics benchmark (and its 1.13 course-correction)

- **Classic API** (what Brayan's "something similar to what Obsidian does" refers
  to): subclass `PluginSettingTab`, override `display()`, build rows imperatively:

  ```ts
  new Setting(containerEl)
      .setName('Show progress bar')
      .setDesc('Display a progress bar during review')
      .addToggle(t => t
          .setValue(this.plugin.settings.showBar)
          .onChange(async (v) => {
              this.plugin.settings.showBar = v;
              await this.plugin.saveSettings();
          }));
  ```

  Widget methods on `Setting`: `addToggle`, `addText`, `addTextArea`, `addSearch`,
  `addDropdown`, `addSlider` (`setLimits(min,max,step)`, `setDynamicTooltip()`),
  `addColorPicker`, `addMomentFormat`, `addButton`/`addExtraButton`,
  `addProgressBar`, `addComponent` (custom), plus `setHeading()`,
  `setErrorMessage()`, `setDisabled()`. Persistence is a plugin-owned JSON blob
  (`data.json`) via `loadData()/saveData()` with `Object.assign` defaulting —
  ~6 lines of boilerplate, no migration framework.
- **The important evolution**: Obsidian **1.13 (2026) deprecated imperative
  `display()`** in favour of declarative `getSettingDefinitions()` returning
  `{name, desc, aliases, visible, disabled, control: {type: 'toggle'|'dropdown'|
  'text'|'textarea'|'number'|'file'|'folder'|'slider'|'color', key, defaultValue,
  validate}}` items, with automatic read/write/persist binding via
  `getControlValue`/`setControlValue`. The stated reason: an opaque `display()`
  can't be indexed for **global settings search**, validated inline, or rendered
  adaptively on mobile. They kept `display()` and `render`-type definitions as
  escape hatches. Ten years of ecosystem experience landing on
  *declarative-first, imperative-fallback* is the strongest single signal for our
  design.
- **Runtime**: `main.js` is `require`d straight into the app renderer — full DOM,
  full `app` object, and Node/Electron on desktop. Docs are explicit: "Obsidian
  cannot reliably restrict plugins to specific permissions or access levels", hence
  Restricted Mode (community plugins off by default), developer policies (no
  obfuscation, no self-update, disclosure of network use), automated scanning +
  manual review for popular/flagged plugins.
- **Mobile**: the same `main.js` runs in a Capacitor WebView on iOS/Android;
  Node/Electron APIs crash → manifest `isDesktopOnly: true` is mandatory for
  plugins that import them; `Platform.isMobile` for runtime branching;
  `requestUrl()` proxies HTTP natively to dodge CORS.
- **Theming**: plugin UI is plain DOM in the app document, so the theme applies
  automatically; authors use CSS variables (`--background-primary`,
  `--text-normal`, `--interactive-accent`, …) redefined under `.theme-light`/
  `.theme-dark` body classes.
- **Distribution**: one-line PR to `community-plugins.json`; releases fetched
  straight from GitHub (tag == `manifest.json` version); `versions.json` maps
  plugin version → `minAppVersion` so old apps fetch older releases. Updates need
  no re-review; self-update mechanisms are banned.

Sources: [settings docs](https://docs.obsidian.md/Plugins/User+interface/Settings),
[obsidian.d.ts](https://github.com/obsidianmd/obsidian-api),
[plugin security](https://obsidian.md/help/plugin-security),
[mobile development](https://docs.obsidian.md/Plugins/Getting+started/Mobile+development)

## WebExtensions — the bring-your-own-HTML end

- `options_ui.page` points at an HTML file the addon ships; the browser renders it
  in an iframe inside about:addons (or a tab). Full HTML/CSS/JS freedom, zero
  standard widgets. The one attempt at native-look standardization
  (`browser_style`) was deprecated and removed (Firefox 115–118) — every options
  page ships its own CSS, and inconsistency is structural. This is the outcome to
  avoid.
- Persistence: `browser.storage.sync` (cross-device, 100 KB quota, 8 KB/item) vs
  `storage.local` (10 MB) with `storage.onChanged` events, and `managed` for
  enterprise policy. A clean local-vs-synced split worth copying.
- Permissions: the most mature model surveyed — install-time manifest
  `permissions`/`host_permissions` with user-facing warnings, plus
  `optional_permissions` requested at runtime **within a user gesture**
  (`permissions.request()`), revocable by the user, observable via
  `permissions.onAdded/onRemoved`. Directly relevant to
  [#20695](https://github.com/ankidroid/Anki-Android/issues/20695).

Sources: [options_ui](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/manifest.json/options_ui),
[storage](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/storage),
[optional_permissions](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/manifest.json/optional_permissions),
[browser styles deprecation](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/user_interface/Browser_styles)

## Anki ecosystem — what already exists (most important section)

**Desktop addon config today**: Python addons ship `config.json` (defaults) +
`config.md` (docs); user edits land in `meta.json`; `mw.addonManager.getConfig()/
writeConfig()` merge them. The default UI is a raw JSON editor;
`setConfigAction()` swaps in a hand-rolled Qt dialog. No widget API, no schema —
this is the gap the cross-platform settings UI fills.

**The shared web layer is already cross-platform.** `ts/` in ankitects/anki is a
SvelteKit app built to static output; the same bundle renders deck options, stats,
card info, change-notetype, import screens and (desktop, in progress) the editor
on **desktop, AnkiDroid and AnkiMobile**. AnkiDroid ships it as APK assets from
the Anki-Android-Backend AAR, serves GETs via `shouldInterceptRequest`
(`PageWebViewClient`), and mirrors desktop's RPC as POSTs to a localhost
NanoHTTPD (`AnkiServer`: `/_anki/<method>` protobuf, `/ankidroid/`, `/jsapi/`).

**`ts/lib/components` is the widget library Brayan asked for** — already themed
(day/night), localized, and rendered on all three platforms: `Switch`/`SwitchRow`,
`SpinBox`/`SpinBoxRow`/`SpinBoxFloatRow` (number selectors), `EnumSelector`/
`EnumSelectorRow` (dropdowns), `CheckBox`, `Select`, `TitledContainer`,
`SettingTitle`, `Modal`/`HelpModal`, `Collapsible`, `ItemChooser`, `VirtualTable`,
`RevertButton`, … Deck-options adds `StepsInputRow`, `DateInput`, `TabbedValue`,
`Warning`, etc. (No slider yet — Logseq's `inputAs: 'range'` shows how to add one
as a number-presentation variant.)

**Desktop already has an HTML addon-UI precedent — the deck-options JS API:**

```js
// Python: mw.addonManager.setWebExports(__name__, r".*\.js")
//         + inject via gui_hooks.deck_options_did_load
$deckOptions.then((options) => {
    options.addHtmlAddon(HTML, () => setup(options));   // or addSvelteAddon(component)
    const data = options.auxData();                     // Writable<Record<string, unknown>>
});
```

- `DeckOptionsPage.svelte` renders addon components inside
  `<TitledContainer title="Add-ons">`; `auxData()` round-trips unknown keys of the
  deck preset through `updateDeckConfigs`, so **addon settings stored there sync
  with the collection** — an existing, synced, per-preset addon-config store.
- `ts/routes/deck-options/index.ts` already exports a curated widget set for
  addons: `components = { TitledContainer, SpinBoxRow, SpinBoxFloatRow,
  EnumSelectorRow, SwitchRow }`.
- `ts/lib/tslib/runtime-require.ts` exposes `require("anki/...")` packages and the
  bundled Svelte runtime to addon code ("If they were to bundle their own runtime,
  things like bindings and contexts would not work").
- None of this is reachable on AnkiDroid today: no `setWebExports`/`/_addons/`
  static serving (our server rejects GET for everything but known assets), no
  `deck_options_did_load` equivalent, no addon config store in the web layer.

**Upstream direction signals** (align, don't diverge):

- dae, *Cross-platform JS addons for the reviewer* (forums, 2024): any endpoint
  exposed to the review screen is also exposed to shared decks; wants **iframe
  isolation** as more of the review screen moves to Svelte; addons need
  distribution infrastructure; security should tighten, not loosen. Matches
  David's position in [#20695](https://github.com/ankidroid/Anki-Android/issues/20695)
  that *addons, not card templates, are the security boundary*.
- [ankitects/anki#3833](https://github.com/ankitects/anki/issues/3833) (dae, 2025):
  cross-platform **template components** (declared per-template, Rust-layer
  implementation, managed in a shared Svelte page, iframe-sandboxed user content) —
  scoped to review/preview behaviour, explicitly *not* app UI. Blocked on the
  Svelte migration.
- [ankitects/anki#4529](https://github.com/ankitects/anki/issues/4529) (2026):
  proposal for a JS hooks API for the Svelte screens, since the Svelte port breaks
  Qt-webview addons — the desktop-side hook surface a shared settings page would
  slot into.
- *Anki's Growing Up* (2026): AnkiHub takes day-to-day stewardship, explicitly
  pledging "add-ons working across mobile platforms", clearer APIs, and more
  predictable releases — the political window for proposing a shared addon
  settings API upstream.

## Lessons for our design

1. **Declarative-first, escape-hatch-second.** VS Code, Joplin and Logseq are
   declarative; Obsidian started imperative and retrofitted declarative in 1.13
   for search/validation/mobile — after a decade of plugins. Card templates and
   shared Svelte screens make the same demand here: a schema the host can render
   with native affordances per platform, index for search, and validate.
2. **One schema, per-platform renderers.** Joplin proves a single declaration can
   render web widgets on desktop and native widgets on mobile. For Anki the even
   simpler move: render the *same* Svelte page everywhere, since all three clients
   already embed the same web bundle.
3. **Don't invent a widget toolkit; reuse the app's.** VS Code's standalone
   toolkit died of unfunded maintenance. Anki's `ts/lib/components` is maintained
   *because the app itself is built from it*, and deck-options already exports a
   curated subset to addons.
4. **Sandbox custom UI in iframes with a postMessage RPC.** Joplin (mobile),
   Logseq, and VS Code all converge on this; dae has independently named it as the
   direction. Joplin's chained `RemoteMessenger` (path-based calls, callback IDs)
   is the reference implementation for Android's extra RN/WebView hop —
   AnkiDroid's equivalent hop is Kotlin ↔ page WebView ↔ addon iframe.
5. **Host-owned, namespaced persistence.** Every system that generates UI also
   owns storage (keyed by addon id), so the host can render before addon code
   runs, sync it, and enforce quotas. Values persist; schemas are re-registered
   (Joplin) — no host-side migrations. Offer synced vs local scopes
   (WebExtensions `storage.sync`/`local`; Anki analogs: collection config /
   deck-preset `auxData` vs device prefs).
6. **Permissions: manifest-declared, install-time surfaced, runtime-escalated.**
   Only WebExtensions does this well; Joplin/Logseq/Obsidian punt to review and
   policy. #20695's capability scopes + WebExtensions' `optional_permissions`
   under user gesture is the model.
7. **Distribution: curated index + releases as CDN.** Obsidian/Logseq (registry PR
   + GitHub releases) and Joplin (npm crawler + curated repo) both work; all ban
   self-update. AnkiDroid's dormant npm/`ankidroid-js-addon` infra matches the
   Joplin shape. The registry decision is ecosystem-wide (AnkiWeb? AnkiHub?) — the
   settings API should not depend on it.
