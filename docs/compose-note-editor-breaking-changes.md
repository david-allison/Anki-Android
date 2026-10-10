# Compose note editor: breaking changes and compatibility tradeoffs

This records the deliberate behavior changes and compatibility risks in the
startup optimization series, including the backend fields bundle. “Breaking”
here includes visual and timing changes; it does not mean a collection-format
migration. The editor remains behind the development flag.

## Visible changes

| Change | Consequence |
| --- | --- |
| Use static localized “Processing…” text during startup | The initial screen and unready field overlay no longer animate. Busy actions, picker loading, and tablet preview retain their spinners. Save/input readiness and recovery gates remain unchanged. |
| Make the outer WebView/page background transparent | The native app background shows around and between fields. Labels and icons use the app theme foreground, including dark/black modes; sticky label backgrounds match the native surface. Field surfaces and note HTML colours remain unchanged. |
| Remove the desktop editor's three field shadows | Fields look flatter. Rounded corners, borders, focus/error outlines, and styling inside the editable HTML remain. This is the latest three-line Android CSS change. |
| Load CodeMirror and MathJax when needed | First opening HTML source, or first rendering math, pays the engine-loading cost. Those features remain available; ordinary fields avoid paying it up front. |
| Load deck and note-type choices when their dialog opens | A picker can initially show a spinner. Its options are no longer guaranteed to be available when the fields become usable. |
| Draw the native shell before constructing WebView | The toolbar/metadata area can appear before the fields. A first native frame does not mean the editor is ready. |
| Publish native readiness after draft persistence and initial autofocus | Save and other readiness-gated controls become enabled later in the initialization sequence. Consumers must not treat field creation alone as native readiness. |
| Apply keyboard insets to the fields/toolbar and preview rather than the whole scaffold | Keyboard animation uses different layout boundaries. Small windows, split-screen, and tablet keyboard layouts need continued coverage. The preview threshold now uses window width rather than an extra layout subcomposition. |

## App-wide WebView policy

**The exact local asset host bypasses Safe Browsing reputation checks.** The
editor registers `.appassets.androidplatform.net` using AndroidX's exact-host
syntax before navigation. This list applies to every WebView in this APK, not
just the editor. External hosts and subdomains retain checks; the setting itself
stays enabled. The editor still rejects non-local/non-GET requests and retains
its CSP and origin-restricted bridge. APK storage isolation and the origin do
not change. Other features must not serve remote content under this trusted
local host. Any future allowlist owner must coordinate with this registration,
because the API replaces the app-wide list.

Unsupported providers and rejected registrations use normal navigation/checks.
The callback ignores destroyed views. Registration cost is included in the
[216 → 200 ms repeat comparison](../AnkiDroid/build/reports/compose-editor/safe-browsing/README.md).

## Backend and integration assumptions that change

**The fields page no longer brings the full application shell.** Its standalone
entry mounts the shared fields page without the SvelteKit router, and uses a
small field-specific stylesheet instead of the full application stylesheet.
New components or custom integrations cannot assume router initialization,
unrelated Bootstrap utilities, or other pages' generated exports are present.
They must declare their own dependencies. The retained rich-text shadow styles,
CodeMirror styles, and MathJax support are not being replaced with plain inputs.

**The asset entry point comes from the capability marker.** Android reads
`entryPoint` from `backend/editor-fields.json`; the new bundle specifies
`editor-fields.html`. Packaging must include that file and its referenced
assets. A missing `entryPoint` still falls back to `index.html` for older
compatible fields bundles. Hard-coding the previous bootstrap/chunk layout is
not a supported integration approach.

**Page initialization can precede collection binding.** Translation requests
wait for binding; media requests before binding return an empty 404. Binding
the same WebView to a different collection media directory is rejected. Future
startup hooks must respect that lifecycle instead of assuming collection/media
access is available in the WebView constructor.

**Field style updates are batched.** Color, font family, font size, and direction
are applied together, with one rewrite of the base style tag rather than four.
The tag remains synchronized for add-ons, but code observing each intermediate
mutation can see a different sequence. CSS URLs are also imported directly
instead of through dynamic JavaScript wrapper modules. These are shared backend
component changes, so desktop/add-on compatibility deserves review too.

## Changes intended to preserve compatibility

- Explicit IndexedDB commit still waits for transaction completion, with an
  automatic-commit fallback. No draft schema, key, or durability-policy change.
- Faster draft-ID formatting preserves the same lowercase SHA-256 IDs.
- Named translation imports remove unused code, not supported translations.
- Toolbar-only state observation and concurrent startup preserve the field
  protocol and snapshot-based Save behavior.

## Existing boundaries, not new optimization regressions

The new editor requires a backend exposing the version 1 fields page. It uses
an app-private virtual HTTPS origin and a restricted field bridge, rather than
exposing the full desktop editor environment. Image Occlusion and Aedict retain
their existing flows. Preview deck placeholders and recovery across a collection
Save remain the limitations described in the [editor overview](compose-note-editor.md).

## Verification and remaining limits

The latest shadow change passed six Add/Edit/recovery/tablet tests; source-editing
and tablet preview screenshots were inspected. Earlier backend changes passed
scoped browser/build checks, but the full backend `just check` could not execute
Sass in the desktop Qt CSS build. This is not a claim of complete desktop or
add-on compatibility.

Cold startup improved, but the latest late-frame share did not improve and the
100 ms target remains unmet. See the [loading report](compose-note-editor-loading.md)
for measured results and qualifications.
