# RFC (draft): a cross-platform addon settings API for Anki

> **Status: draft for discussion, not submitted.** Audience: Anki upstream
> (dae / AnkiHub) and AnkiDroid/AnkiMobile maintainers. This is the "Phase 0"
> alignment step from [README.md](README.md): get agreement on a manifest settings
> schema and a shared settings renderer before more platform-specific code is
> written. It generalizes what AnkiDroid has already prototyped (see
> [status.md](status.md)) into a form all three clients can share.

## Problem

Addons need configuration UI. Today each platform would hand-roll it: Anki desktop
addons ship a raw-JSON editor or a bespoke Qt dialog; AnkiDroid has just prototyped
a native-widget renderer; AnkiMobile has nothing. That is three UIs, three
persistence models, and three security boundaries for one need — exactly the
"decisions that last a decade" Mike Hardy warned about on
[#7959](https://github.com/ankidroid/Anki-Android/issues/7959).

The prior art ([prior-art.md](prior-art.md)) is decisive: VS Code, Joplin and Logseq
are declarative-schema-first; Obsidian retrofitted a declarative API in 1.13 after a
decade of imperative plugins, specifically to get settings search, validation and
mobile-adaptive rendering. Anki should start where they ended up.

## Proposal

### 1. A declarative settings schema in the manifest

An addon declares its settings as data, not code, so the host can render them
without executing the addon — the key property for an untrusted-addon security model.
Value-bearing types map to standard widgets; presentation is a hint, not a new type
(Logseq's `inputAs`):

```jsonc
"settings": [
  { "type": "heading", "title": "Appearance" },
  { "type": "toggle", "key": "enabled",  "title": "Show bar", "default": true },
  { "type": "enum",   "key": "position", "title": "Position", "default": "top",
    "choices": [ { "value": "top", "label": "Top" }, { "value": "bottom", "label": "Bottom" } ] },
  { "type": "number", "key": "height", "title": "Height", "default": 4,
    "min": 1, "max": 24, "inputAs": "slider" }
]
```

Rules that make this non-breaking as it evolves (all validated in AnkiDroid's
prototype): unknown `type`s are tolerated and skipped, not fatal; anything a schema
cannot express falls back to a raw JSON editor (desktop's current behaviour, so never
a regression); the API version an addon targets is a **single declared fact**, and the
*host* owns the supported range and can widen it retroactively without addons
republishing.

### 2. One renderer, shared: a SvelteKit `addon-settings` route

Add `ts/routes/addon-settings/` to `ankitects/anki`, rendering the schema with the
existing `ts/lib/components` widgets (`SwitchRow`, `SpinBoxRow`, `EnumSelectorRow`,
… — the "switches, dropdowns, number selectors" Brayan asked for, already themed and
localized). All three clients already embed this bundle, so a single page renders
identically on desktop, AnkiDroid and AnkiMobile. Only a slider component is missing
(add it, or ship `inputAs:"slider"` as a SpinBox until it lands).

The page needs two host methods, defined platform-neutrally:
`getAddonSettings(addonId) → {schema, values}` and `setAddonSettings(addonId, values)`.
Each platform implements them against its own store (desktop `meta.json`; AnkiDroid
`AddonStateStore`; AnkiMobile equivalent).

### 3. Host-owned, namespaced persistence

Values are stored by the host, keyed by addon id, as a JSON object; `enabled`,
granted permissions and settings values are keys of the same object. Writes preserve
unknown keys (downgrade-safe). Offer a synced scope (collection config) and a local
scope (device), per WebExtensions' `storage.sync`/`local` split.

### 4. Custom UI as a sandboxed escape hatch

An addon that outgrows the schema declares a `settingsPage`, rendered in a sandboxed
iframe (opaque origin) whose only channel is `postMessage` to a host-relayed,
addon-scoped API. This is dae's stated direction (iframe isolation for third-party
code on Svelte screens, [ankitects/anki#3833](https://github.com/ankitects/anki/issues/3833))
and is prototyped in AnkiDroid.

### 5. Permissions — the gate, defined separately

Rendering the schema needs no permissions (no addon code runs). Everything an addon
*does* — reviewer scripts, collection reads/writes, network — must be gated by the
capability model under debate in
[#20695](https://github.com/ankidroid/Anki-Android/issues/20695): manifest-declared,
surfaced at install, dangerous ones escalated at runtime (WebExtensions'
`optional_permissions`). **This RFC does not settle permissions; it depends on them,
and nothing should ship to users before they exist.**

## Why now

*Anki's Growing Up* (2026): AnkiHub took stewardship pledging "add-ons working across
mobile platforms", clearer APIs and fewer breaking changes. A shared declarative
settings schema + one Svelte renderer is the smallest concrete step toward that, and
the highest-leverage: it is mostly a data format and one page, and everything
platform-specific hangs off it.

## What AnkiDroid can contribute today

A working reference implementation of §1, §3, §4 and the native form of §2 (see
[status.md](status.md)): the schema shape, tolerant validation, host-owned store,
sandboxed panel and background context, five sample addons, and ~68 tests — all
behind a dev flag, none of it yet a public API, waiting on this alignment and on
[#20695](https://github.com/ankidroid/Anki-Android/issues/20695).

## Open questions for upstream

1. Does the schema shape (types + `inputAs` hints) match what desktop deck-options
   addons already want, so the same declaration serves both?
2. Where does the renderer live — upstream `ts/routes/` (preferred) or carried as an
   Anki-Android-Backend patch until it lands?
3. Synced vs local storage backing for third-party data at the collection level — is
   collection config acceptable, or is a backend-blessed addon-data table needed?
4. Manifest-declared schema (VS Code) vs runtime registration (Joplin/Logseq): the
   former renders without running addon code and is proposed here; is dynamic schema
   ever needed enough to add runtime registration later?
