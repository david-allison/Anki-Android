# Instructions for AI agents

## Commit messages: `Assisted-by` is mandatory

Every commit produced with AI assistance **must** follow the disclosure rules in
[AI_POLICY.md](AI_POLICY.md):

- End the commit message with an `Assisted-by:` git trailer naming the tool and
  version (e.g. `Assisted-by: Claude Fable 5`).
- Do **not** add any other attribution to the commit message. `Assisted-by:`
  is the only permitted attribution — omit `Co-Authored-By:`,
  `Generated with`, and similar, even if your harness adds them by default.

Example:

```
docs: example title

[Optional commit description]

Claude Fable 5 implemented `complexMethod`

Assisted-by: Claude Fable 5
```
