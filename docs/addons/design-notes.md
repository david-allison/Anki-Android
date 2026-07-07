# Addons — design notes (running log)

> A working log of the reasoning behind the harder addon decisions, written as I build
> them. Unlike [README.md](README.md) (the plan) and [status.md](status.md) (what's built),
> this captures **why** — the guesses I made where the spec is undecided, the alternatives
> I rejected, and the seams left for a real decision later. Everything here is WIP,
> developer-only, and explicitly *not* a committed API.
>
> These four areas ([README.md](README.md) called them "blocked / out of scope") are built
> on **best guesses**, because the real answers are upstream/ecosystem decisions
> (permissions: [#20695](https://github.com/ankidroid/Anki-Android/issues/20695);
> distribution: AnkiWeb vs AnkiHub; the shared renderer: ankitects/anki). The point is to
> have a working reference to argue *against*, not to pre-empt those decisions.

---

## 1. Permissions

**The problem.** Today a sandboxed addon still has real reach: it can `navigate` to the
app's own scheme, inject DOM into a page, and write its own settings. Nothing gates any of
it beyond the global dev flag. [#20695](https://github.com/ankidroid/Anki-Android/issues/20695)
is the open discussion; David's position there is that *addons are the security boundary*
and capabilities should be `read|write` scoped, with dangerous ones granted individually.

**My guess at the model.** I'm copying the shape that actually works in practice
(WebExtensions, from [prior-art.md](prior-art.md)) and adapting it to #20695's scopes:

- The manifest declares a `permissions` list of capability strings.
- Capabilities are coarse and namespaced: `decks:read`, `decks:write`, `notes:read`,
  `notes:write`, `cards:read`, `cards:write`, plus dangerous singletons `network`,
  `navigate`, and `clipboard`.
- **Unknown permission strings are tolerated** (parsed into an `Unknown` variant), never
  fatal — same forward-compat rule as everywhere else. An addon may request a capability a
  newer AnkiDroid defines; here it simply won't be granted.
- Following #20695: I do *not* split `delete` from `write` ("if a user can modify a note
  and we don't keep history, it may as well be deleted"). And "modify app settings" is left
  out entirely for now — it's the hardest to scope and isn't needed by anything built.

**Grant model.** WebExtensions distinguishes install-time (surfaced, auto-granted on
accept) from runtime-escalated (`optional_permissions`, requested under a user gesture). For
a first cut I do the simpler half: **declared permissions are surfaced at install and
granted on accept**; the user can **revoke** any of them later from the settings screen.
Runtime escalation (request-on-use) is a later refinement — noted, not built. Rationale: the
install-time list is the 90% case, revocation is the safety valve, and request-on-use needs
a UI-gesture plumbing through the sandbox relay that isn't worth it until a real addon needs
it.

**Storage.** Granted permissions are just another key in the addon's state object
(`granted`), reusing `AddonStateStore`'s merge discipline. Absent ⇒ not granted, consistent
with disabled-by-default. This keeps the "one JSON object per addon" invariant.

**Enforcement.** The `AddonPageHost` relay is the choke point: each relay method maps to a
required capability, and the Kotlin `AddonPageBridge` re-checks server-side (never trust the
iframe). `navigate` needs `navigate`; `setSetting` is always allowed (an addon owns its own
settings); DOM helpers need nothing today (they can't exfiltrate). The relay is the right
place because it already mediates every addon→host call.

**What I'm punting on, deliberately:** actual collection read/write APIs (`decks:read` etc.)
have nothing to gate yet — the relay exposes no collection access. So those capability
strings parse and surface and store, but gate nothing until a collection API exists. I'm
building the *permission plumbing* end-to-end with `navigate` as the one live example, so
adding a gated collection call later is a one-line `requirePermission(...)`.

**Where enforcement lives (the subtle bit).** The threat model is: the sandboxed **iframe
is untrusted**; the **page relay and Kotlin are trusted**. The iframe can only `postMessage`.
So enforcement can live in *either* trusted layer:

- `navigate` performs its effect in the page relay (`window.location.href`), so it's gated
  *there* — the relay checks a `grants` map that is **baked in from host state**, not sent by
  the addon, so the addon cannot forge a grant. This is client-side JS but still across the
  sandbox trust boundary, which is what matters.
- State-touching methods that reach Kotlin (`setSetting`, and any future collection call)
  are gated *server-side* in `AddonPageBridge`, re-reading `AddonStateStore.isGranted` — never
  trusting even the trusted page relay for the things that touch real data.

The addon also gets `ankidroid.permissions` and `ankidroid.hasPermission(id)` for
feature-detection, so it can behave gracefully when a capability was revoked.

**Live consequence:** the auto-reveal sample calls `navigate("ankidroid://show-answer")`, so
it now declares `"permissions": ["navigate"]`. If the user revokes it, auto-reveal silently
stops — exactly the behaviour we want, and the first end-to-end proof the gate works.

---

## 2. Distribution

**The problem.** Only local `.tgz` install exists. Users can't discover or update addons.

**Why this is a guess.** The registry is genuinely undecided at the ecosystem level: AnkiWeb
(desktop-only today), AnkiHub (the new steward), or an Obsidian/Logseq-style curated index +
GitHub releases. I can't pick that — so I build the *mechanism* and make the registry URL a
single injectable seam.

**The shape I chose**, because it reuses what's already in the repo: a JSON **index** at a
URL, an array of addon manifests each carrying `dist.tarball` (the npm-registry shape the
dormant `getAddonModelListFromJson` already parses). `AddonRegistry` wraps it with a sealed
`Success/Failure` result and never throws — a registry being down must degrade to "can't
reach registry", not a crash. Invalid entries in the index are skipped, not fatal (one bad
addon shouldn't hide the rest), reusing the same tolerance as local listing.

**Install + update**, next: downloading a `dist.tarball` to a temp file and handing it to the
existing atomic `installFromTarball` — so registry install and file install share one
code path and one set of guarantees. "Update available" is a plain semver compare of the
installed version against the registry version; no auto-update (Obsidian, Joplin and
WebExtensions all ban addon self-update, and so should we — updates go through the same
surfaced-permissions install).

**Deferred:** signing/verification of downloads, a revocation list (krmanik's `remove.txt`
idea), and the actual registry contents. The URL is a placeholder
(`ankidroid.org/addons/index.json`) that does not exist yet — intentionally, so nothing here
implies a committed registry location.

---

## 3. The cross-platform settings renderer (the contract half)

**The problem.** [rfc.md](rfc.md) argues the endgame is *one* SvelteKit `addon-settings`
page, rendered identically on desktop, AnkiDroid and AnkiMobile from the manifest schema.
That page lives in `ankitects/anki`, which isn't this repo — so I can't build the renderer.

**What I *can* build, and did:** the AnkiDroid half of the contract the page depends on. The
shared page would talk to each host the way every other Anki web page does — a localhost POST
to a named method — so the contract is just **two methods**:

- `getAddonSettings {addon} → {schema, values}` — the page renders `schema` (the manifest's
  settings definitions, serialized) filled from `values` (stored overlaid on defaults).
- `setAddonSettings {addon, values} → {}` — persists.

`AddonSettingsHost` implements both against `AddonStateStore`, and its test *is* the
executable spec: get returns schema+resolved-values, set round-trips. Desktop and AnkiMobile
implement the same two methods against their own stores (`meta.json`, etc.).

**Why JSON, not protobuf.** Anki's page↔host RPC is usually protobuf, but addon settings are
inherently open-shape JSON (arbitrary keys, unknown-key preservation), so the envelope is
JSON. This is a deliberate divergence, noted so nobody "fixes" it into a proto later.

**The seam left open:** wiring `AddonSettingsHost.handle(...)` into a `PageFragment` that
serves the `addon-settings` route. That's one `when` branch in `handlePostRequest` — trivial
— but pointless until the upstream route asset exists. So the contract is built and tested;
the wiring waits on upstream, exactly as [rfc.md](rfc.md)'s open question #2 describes.

---

## 4. Native-screen surfaces

**The problem.** [AddonPageHost](README.md) covers WebView pages. But the deck picker, card
browser and note editor are **native Android UI** — there's no page to inject a sandboxed
iframe into. Yet these are exactly where several top AnkiWeb addons live
([addon-survey.md](addon-survey.md): FSRS Helper's menu suite, Fastbar, browser columns).

**My guess: declarative contributions, not code injection.** An addon can't run code *in* a
native screen (there's no sandbox there, and letting it would be the opposite of the whole
security direction). So the only safe surface is **declarative**: the addon *declares* menu
items in its manifest (`menus: [{screen, id, title}]`), the host renders them as real native
`MenuItem`s (no addon code runs to draw them), and a click is **dispatched to the addon's
sandboxed background context** — where its code *does* run safely — via
`ankidroid.onMenuClick(cb)`. So the pattern is: *native declares, sandbox handles*.

**Why menus first.** Of the native surfaces, a menu item is the smallest, safest, most
universal contribution — it's just a label + a callback, no layout, no live DOM. Richer
native surfaces (browser columns, editor buttons) each need their own bespoke contribution
type and are much larger; a menu proves the *native-declares/sandbox-handles* pattern with
the least surface. The dispatch path (`AddonMenus.populate` → `AddonBackgroundHost.current
.fireMenuClick` → the addon's `onMenuClick`) is the reusable spine the others would follow.

**The `current` static.** Dispatching a menu click needs to reach the running background
host, which is owned by `AppLifecycleObserver`, not the clicked screen. I used a
companion `AddonBackgroundHost.current` set on start/cleared on stop. It's a little ugly
(global mutable state), but the lifetimes are strict (foreground-only, one instance) and it
avoids threading a host reference through every native screen. Flagged as a WIP seam, not a
pattern to love.

**Wired into:** DeckPicker's `onCreateOptionsMenu`, one `AddonMenus.populate(...)` line. Any
other native screen adds addon menu support with the same single call. A menu-contributing
addon pairs `menus` with a `background` script (that's where the click is handled), which is
the honest constraint: to *do* something from a native click, the addon needs a running
sandbox, and the background context is it.
