# AnkiDroid JS addons: `addons-3`

A working, sandboxed JavaScript addon system for AnkiDroid. Developer-only, behind a flag, off by default.

| | |
|---|---|
| Branch | `addons-3` (local, stacked on `addons-2`) |
| Last commit | 2026-07-07 |
| Size | 52 commits · 75 files · +6.4k lines |
| Tests | 103 unit tests in `AnkiDroid/src/test/java/com/ichi2/anki/jsaddons/` |
| Code | `com.ichi2.anki.jsaddons`, plus ~150 lines of touch points in nine existing files |

## TL;DR

- Addons are npm-style packages (`package.json` + `index.js`). Install from a `.tgz` or from a registry index.
- Addon code never runs inside AnkiDroid's own pages. Each addon gets its own sandboxed iframe and talks to the host over `postMessage`.
- Settings are declared in the manifest and rendered natively. The settings screen runs no addon code.
- Capabilities are declared, shown at install, revocable, and enforced in Kotlin.
- Native screens are reached through declarative contributions (menu items), not code injection.

## What's built

| Area | What | Code |
|---|---|---|
| Manifest | npm-shaped, tolerant parsing, sealed `Valid` / `Invalid` result, API version checked as a range | `AddonData`, `AddonModel` |
| Storage | Per-profile layout. Atomic `.tgz` install: stage, validate, rename | `AddonStorage`, `TgzPackageExtract` |
| State | One JSON object per addon holding `enabled`, `settings`, `granted`. Merge-writes keep unknown keys | `AddonStateStore` |
| Browser | List, enable, disable, delete, install from file, install from registry, open settings | `AddonsBrowserFragment` |
| Page host | One sandboxed iframe per addon on every WebView page: reviewer, deck options, statistics, card info, congrats, change note type, import | `AddonPageHost`, `AddonPages` |
| Settings | Schema → native widgets. Raw JSON editor fallback. Optional sandboxed `settingsPage` | `AddonSettings`, `AddonSettingsFragment`, `AddonPanelHost` |
| Background | Hidden app-scoped WebView, one iframe per addon, runs only while foregrounded | `AddonBackgroundHost` |
| Native menus | Manifest-declared deck picker menu items. Clicks dispatch to the addon's background context | `AddonMenus` |
| Permissions | `decks` `notes` `cards` `media` × `read` `write`, plus `navigate` `network` `clipboard` | `AddonPermission` |
| Collection API | Gated async `decks.*` `notes.*` `cards.*` `media.*` | `AddonCollectionApi` |
| Registry | Fetch a JSON index, install through the same atomic path, semver update check | `AddonRegistry` |
| Settings host contract | `getAddonSettings` / `setAddonSettings`, ready for a shared Svelte page | `AddonSettingsHost` |
| Samples | Six addons and a tarball build script | `tools/sample-addons/` |

## Architecture

```
WebView page (reviewer, deck options, …)
┌───────────────────────────────────────────────────────────────┐
│  trusted relay (page JS)   ◄── postMessage ──►   addon iframe  │
│         │                                       sandbox=       │
│         │ AndroidAddonPage bridge               allow-scripts  │
└─────────┼─────────────────────────────────────────────────────┘
          ▼
   Kotlin: AddonPageBridge → AddonStateStore, AddonCollectionApi
```

- **Iframe** is untrusted. Opaque origin, no DOM access, no direct bridge.
- **Relay** is trusted page JS. It performs DOM effects on the addon's behalf. Grants are baked in from host state, so an addon cannot forge them.
- **Kotlin** re-checks every permission before touching state or the collection.

The same iframe + relay pattern powers the page host, the custom settings panel, and the background context.

## A minimal addon

`package.json`

```json
{
  "name": "ankidroid-sample-auto-reveal",
  "addonTitle": "Auto-reveal answer",
  "version": "1.1.0",
  "main": "index.js",
  "ankidroidJsApi": "0.0.3",
  "addonType": "reviewer",
  "keywords": ["ankidroid-js-addon"],
  "permissions": ["navigate"],
  "settings": [
    { "type": "number", "key": "delaySeconds", "title": "Reveal delay (seconds)", "default": 10, "min": 1, "max": 120 },
    { "type": "toggle", "key": "enabled", "title": "Enabled", "default": true }
  ]
}
```

`index.js`

```js
const { delaySeconds = 10, enabled = true } = ankidroid.settings;
let timer;
ankidroid.onEvent("question", () => {
  clearTimeout(timer);
  if (enabled) timer = setTimeout(() => ankidroid.navigate("ankidroid://show-answer"), delaySeconds * 1000);
});
ankidroid.onEvent("answer", () => clearTimeout(timer));
```

Optional manifest fields:

| Field | Purpose |
|---|---|
| `pages` | Which pages to inject into (`reviewer`, `deck-options`, `statistics`, `card-info`, `congrats`, `change-notetype`, `import`). Falls back to `addonType`. |
| `background` | A headless script, e.g. `background.js` |
| `settingsPage` | Custom settings HTML, rendered sandboxed |
| `menus` | `[{ "screen": "deck-picker", "id": "…", "title": "…" }]` |

Settings types: `heading` `toggle` `enum` `number` `text` `textarea` `action`.

## Addon API

### Page context: `ankidroid.*`

| Call | Permission |
|---|---|
| `settings` · `setSetting(key, value)` | none |
| `log(msg)` | none |
| `onEvent(type, cb)` · `onDomEvent(selector, type, cb)` | none |
| `injectStyle(css)` · `addElement(position, html)` · `setElementHtml(id, html)` · `setElementStyle(id, prop, value)` · `removeElement(id)` | none |
| `permissions` · `hasPermission(id)` | none |
| `navigate("ankidroid://…")` | `navigate` |
| `decks.all()` · `decks.current()` | `decks:read` |
| `decks.add(name)` | `decks:write` |
| `notes.find(search)` · `notes.info(noteId)` | `notes:read` |
| `notes.addTags(noteIds, tags)` · `notes.removeTags(noteIds, tags)` | `notes:write` |
| `cards.find(search)` · `cards.info(cardId)` | `cards:read` |
| `cards.suspend(cardIds)` · `cards.unsuspend(cardIds)` · `cards.setFlag(cardIds, flag)` | `cards:write` |
| `media.have(filename)` | `media:read` |
| `media.addFile(filename, base64)` | `media:write` |

Collection calls return promises: `await ankidroid.notes.find("deck:current")`.

### Events

| Event | Detail | Fired by |
|---|---|---|
| `question` | | page host |
| `answer` | | page host |
| `answered` | `{ rating }` | reviewer, from the answer flow |

### Background context: `background.js`

`ankidroid.settings` · `ankidroid.log(msg)` · `ankidroid.onMenuClick(cb)`

### Custom settings panel: `settingsPage`

`ankidroidAddon.getSettings()` · `ankidroidAddon.setSettings(values)`

## Permissions

- Declared in the manifest. Listed in a dialog after install and granted on accept. Declining installs the addon with nothing granted.
- Revocable per addon from its settings screen. A revoked capability makes the call a silent no-op.
- Unknown strings are tolerated and never granted, so a future capability doesn't break old builds.
- No `delete` scope. Write implies delete, per #20695.

## Try it

1. Settings → Developer options → enable **JS addons** → open **Addons browser**.
2. Run `tools/sample-addons/build-tarballs.sh`, then **Install from file** and pick a `.tgz` from `tools/sample-addons/out/`.
3. Enable the addon, tap it to configure, open the new study screen.

Samples: session progress bar, auto-reveal, image zoom, card timer, custom colour panel, menu greeter.

## Not built, on purpose

| Gap | Why |
|---|---|
| A real registry | The URL is a placeholder, `ankidroid.org/addons/index.json`. AnkiWeb vs AnkiHub vs a curated index is an ecosystem decision. |
| Shared Svelte settings page | Needs an `addon-settings` route in `ankitects/anki`. The AnkiDroid half of the contract is done and tested. |
| Native surfaces beyond menus | Browser columns, editor buttons, and answer-bar filters each need their own contribution type. |
| Request-on-use permissions | Install-time grant plus revoke covers the common case. |
| Download signing, revocation list | Deferred until a registry exists. |
| Old reviewer | Addons target the new study screen only. |

**Known security gap.** Card template JS shares the page scope with the trusted relay, so a malicious card could call a granted addon's capabilities. This is the same limitation as the existing JS API. Not shippable as a security boundary yet.

## Docs on the branch

All under `docs/addons/`.

| File | What |
|---|---|
| `design-notes.md` | Why each hard decision went the way it did. The most current doc. |
| `status.md` | Per-feature build status. Its "Not built" list is out of date: registry install and native menus exist. |
| `README.md` | The original plan and phased roadmap. |
| `rfc.md` | Draft cross-platform RFC for upstream. |
| `desktop-hooks.md` | All 167 Anki desktop hooks mapped onto this model. |
| `prior-art.md` | Obsidian, Joplin, Logseq, VS Code, WebExtensions, Anki desktop. |
| `addon-survey.md` | Top AnkiWeb addons and whether they would port. |
