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
