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

## First keyboard opening benchmark

`keyboard.py` is an opt-in UI performance test. It opens a fresh Compose editor
through the Add FAB, measures its automatic keyboard opening, hides the keyboard,
and taps the field to measure reopening. Each pair gets its own Perfetto trace.
It does not type, save notes, reset data, compile the app, or change device settings.
It refuses the production package and non-debuggable builds, and stops if another
activity takes the foreground.

Prepare an isolated debug install with the Compose editor enabled, an empty Basic
note selected, and automatic keyboard opening enabled. Leave it on the deck list
with the FAB collapsed. Supply a coordinate inside the first field when the
keyboard is closed; do not point it at a quick action. Requires Android 12+,
Python 3, adb, and a local `trace_processor_shell` binary.

```sh
python3 tools/editor-startup-profile/keyboard.py capture \
  --serial DEVICE_SERIAL --package com.ichi2.anki.composefieldsprobe \
  --reopen-at 200 800 --iterations 5 \
  --trace-processor /path/to/trace_processor_shell \
  --output AnkiDroid/build/reports/compose-editor/keyboard-benchmark/run-1
```

For a slow emulator, add `--settle-seconds 3` so the capture does not hide the
keyboard before its opening animation finishes. This extends the observation
window and trace duration; it does not change the measured timestamps or budgets.

The output includes device/build/keyboard metadata, raw traces, individual samples
and medians in `results.json`. Measurements distinguish:

- Touch delivery to the app → IME animation start (includes editor startup for
  the fresh case). ADB command overhead is excluded.
- IME service `showSoftInput` → animation start, and the animation duration itself.
- Editor frames missing their deadline **during** the animation, and its longest
  main-thread frame. Startup frames are excluded from this jank percentage.

Animation start/end are Android trace events, not physical screen presentation or
proof that a keypress has been accepted. The first iteration is retained; it is
not discarded as warm-up. The app process remains running between iterations.
Cold-process launch is a separate experiment. Emulator timings validate the test,
not phone performance; debug and release results must not be mixed.

To turn the benchmark into a regression gate, choose budgets for the same device,
build type and keyboard. For example, append `--max-first-ms 400
--max-missed-percent 5`. These are configurable limits, not universal targets.
Any exceeded budget exits unsuccessfully after writing results. Missing or
ambiguous trace data also fails, never silently reports zero latency/jank.
Reanalyse a saved capture without connecting a device:

```sh
python3 tools/editor-startup-profile/keyboard.py analyze \
  --output AnkiDroid/build/reports/compose-editor/keyboard-benchmark/run-1 \
  --trace-processor /path/to/trace_processor_shell --max-first-ms 400
```

Run the host regression tests with the `unittest` command above. Keep traces local.
