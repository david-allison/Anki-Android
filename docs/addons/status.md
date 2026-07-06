# Addons — build status

> What has actually been built on the `addons-2` branch, as of 2026-07. This is a
> **developer-only, WIP** vertical slice behind the *Developer options → JS addons*
> flag; nothing is user-visible in release builds, and none of it is a public,
> supported API yet. The companion [README.md](README.md) is the design; this is the
> state of the implementation.

Everything lives in `com.ichi2.anki.jsaddons` unless noted. Every piece is
unit-tested (~68 tests in `AnkiDroid/src/test/java/com/ichi2/anki/jsaddons/`).

## Built

### Foundation (Phase 1)

- **Manifest model + validation** (`AddonData.kt`, `AddonModel.kt`,
  `AddonValidationResult`): npm-`package.json`-shaped, tolerant parsing
  (`ignoreUnknownKeys`), a sealed `Valid`/`Invalid` result. Version gating is by
  **range** (`ApiCompatibility` / `checkApiVersion` in `AnkiDroidJsAPIConstants`),
  not exact equality, with distinct "update addon" vs "update AnkiDroid" errors.
  Unknown `addonType`s and unknown setting types are tolerated, never fatal.
- **Storage** (`AddonStorage.kt`): all on-disk layout knowledge — including the
  npm `package/` nesting — behind one class; per-profile via the collection
  directory. Lists tolerate corrupt manifests; hidden/plain entries are ignored.
- **State store** (`AddonStateStore.kt`): host-owned, one JSON object per addon
  keyed by npm name, in a dedicated per-profile `"addons"` SharedPreferences file.
  `enabled` and `settings` are keys; `JsonObject`-merge writes keep unknown keys
  (downgrade-safe). Addons default to disabled.
- **Install from `.tgz`** (`AddonStorage.installFromTarball`): atomic — extract to
  a hidden staging dir, validate, then rename into place; corrupt tarball leaves
  nothing behind. Extraction reuses the zip-slip-safe `TgzPackageExtract`.
- **Browser** (`AddonsBrowserFragment`, `AddonsBrowserAdapter`): dev-flagged, hosted
  by `SingleFragmentActivity`; list / enable-disable / delete / install-from-file /
  open-settings.

### Reviewer injection

- **Script injection** (`AddonReviewerScripts.kt`, `ViewerResourceHandler`,
  `PreviewerHelpers.stdHtml`): enabled reviewer addons' scripts are added to the new
  study screen. **Two mechanisms built**; mechanism 1 is wired:
  1. `<script src>` under `/_addons/<name>/<main>` (desktop's scheme), served by the
     WebView resource interception with a path-traversal guard.
  2. `addonScriptsForEvaluation` — raw text (+ `sourceURL`) for `evaluateJavascript`.

### Settings (Phase 2)

- **Declarative schema** (`AddonSettings.kt`): a manifest `settings` array
  (heading/toggle/enum/number/text/textarea/action + `inputAs` hints); host renders
  it, so the settings screen runs **no addon code**. Minimal structural validation;
  `resolveSettingsValues` overlays stored values on declared defaults.
- **Settings values** (`AddonStateStore`): stored under the addon's `settings` key
  with the same merge discipline.
- **Settings UI — two mechanisms built, both wired** (`AddonSettingsFragment`):
  1. native widgets generated from the schema;
  2. a raw JSON editor over the stored values (the schema-less fallback, mirroring
     Anki desktop's config editor).
- **Runtime access for scripts** (`AddonSettingsBridge.kt`): each enabled reviewer
  addon's resolved settings are baked into the page; `ankidroid.addonSettings(name)`
  reads synchronously, `ankidroid.setAddonSetting(...)` writes back via a reviewer
  POST route. Scoped per addon.

### Sandboxed UI + background (Phase 3, minus permissions)

- **Custom settings panel** (`AddonPanelHost.kt`, `AddonSettingsPanelFragment`): an
  optional `settingsPage` runs in a `sandbox="allow-scripts"` iframe (opaque origin),
  reaching the host only via `postMessage` → a per-addon Kotlin bridge
  (`getSettings`/`setSettings`). `</`-escaping prevents `</script>` breakout
  (regression-tested).
- **Background context** (`AddonBackgroundHost.kt`, wired via `AppLifecycleObserver`):
  an optional `background` entry runs in a hidden, app-scoped WebView — one sandboxed
  iframe per background addon — started only while foregrounded with the dev flag on,
  torn down when backgrounded, never created when unused.

### Samples

`tools/sample-addons/` — five reviewer addons (session progress, configurable
auto-reveal, image zoom, card timer, custom-panel colour picker) + a build script
and a manifest-validity test.

## Not built (deliberately)

- **Permissions enforcement** — blocked on
  [#20695](https://github.com/ankidroid/Anki-Android/issues/20695) (the capability
  scopes and developer-contract decision). No addon capability is gated yet beyond
  the global dev flag; a reviewer addon's script currently has the same reach as card
  template JS. This is the gate before anything ships to users.
- **Network / registry install** — only local `.tgz` install exists; the npm/registry
  path stays dormant (distribution is an ecosystem decision).
- **The cross-platform Svelte settings page** — the endgame in
  [rfc.md](rfc.md). What's built renders settings *natively* on AnkiDroid; the shared
  web renderer needs the upstream RFC to land first.
- **Native-screen mount points** (deck list / browser / editor filters and panels
  from [addon-survey.md](addon-survey.md)) — reviewer-only for now.

## How to try it

1. Developer options → **JS addons** (enable) → **Addons browser**.
2. `tools/sample-addons/build-tarballs.sh`, then **Install from file** → pick a
   `.tgz` from `tools/sample-addons/out/`.
3. Toggle the addon on; tap it to configure; open the **new study screen**.
