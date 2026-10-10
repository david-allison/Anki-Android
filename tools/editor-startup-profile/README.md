# Editor startup profiles

Host-only decoding and visualization tools. They do not contact a phone or network.
The capture scripts, temporary patches, exact APKs, raw traces, source maps, and
receipts live in `AnkiDroid/build/reports/compose-editor/full-profile/`.

From the repository root:

```sh
python3 -m unittest discover -s tools/editor-startup-profile -v
python3 tools/editor-startup-profile/analyze.py AnkiDroid/build/reports/compose-editor/full-profile
python3 tools/editor-startup-profile/render.py AnkiDroid/build/reports/compose-editor/full-profile
```

The renderer consumes the ART decoder's `native-{cold,warm}.stacks.json` and
`v8-{cold,warm}.{cpuprofile,symbols.json}`. It produces an offline HTML flame graph,
a Speedscope file, and summaries. These names refer to the recorded experiments;
the V8 captures have a diagnostic navigation gate and are not startup benchmarks.

ART v3 dual-clock records are sorted within each thread, replayed as stack
transitions, and weighted by the CPU/wall interval since the previous transition.
This is an approximation from sampled stacks, not method-entry instrumentation.
No time before the first or after the last event is extrapolated. Inclusive
weights overlap. Speedscope output groups identical stacks and therefore does
not preserve chronology; inspect the original traces for chronological analysis.

Keep the native CPU, native elapsed, and V8 sampled elapsed views distinct.
Do not upload artifacts. See [the profile report](../../docs/compose-note-editor-profile.md)
for interpretation and coverage limits.
