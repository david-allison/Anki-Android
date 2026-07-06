# Sample JS addons

Small, useful reviewer addons exercising the WIP JS addons system
(see `docs/addons/README.md`). Each follows the npm package layout the
installer expects: the addon's content lives under `package/`.

| Addon | What it does | What it exercises |
|---|---|---|
| `ankidroid-sample-session-progress` | Thin progress bar at the top, filling as you review towards a session goal | State that survives across cards; `_showQuestion` wrapping |
| `ankidroid-sample-auto-reveal` | Reveals the answer after a configurable delay on the question side | Declarative settings schema read at runtime (`ankidroid.addonSettings`); native action (`ankidroid://show-answer`) |
| `ankidroid-sample-image-zoom` | Tap an image on a card to view it fullscreen; tap again to dismiss | DOM injection/overlays; event delegation over swapped card content |
| `ankidroid-sample-card-timer` | A small elapsed-time badge for the current card | Periodic updates; question/answer lifecycle; theme variables |
| `ankidroid-sample-custom-panel` | Colour-picks an answer-button accent via a custom settings page | A sandboxed `settingsPage` talking to the host via `ankidroidAddon.getSettings/setSettings` |

## Build

```
./build-tarballs.sh
```

writes an installable `.tgz` per addon into `out/`.

## Install

1. Settings → Developer options → enable **JS addons**
2. Open **Addons browser** → ⋮ → **Install from file** → pick the `.tgz`
3. Enable the addon with its toggle
4. Addons run in the **new study screen** only

## Notes for addon authors

- Scripts are injected once per study session, after AnkiDroid's own scripts.
  Card content is swapped into `#qa` by `_showQuestion`/`_showAnswer`; wrap those
  globals (or use `onUpdateHook`) to react per card. Module state persists across
  cards within a session.
- `ankidroidJsApi` declares the single API version the addon was developed
  against — a fact, not a range; AnkiDroid decides compatibility.
