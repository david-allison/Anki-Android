# SPDX-License-Identifier: GPL-3.0-or-later
"""Opt-in, on-device benchmark of a fresh editor's IME and a subsequent reopening.

Uses only Python's standard library, adb and Perfetto's trace_processor_shell.
No app instrumentation, compilation changes, settings changes or note editing.
Start on the deck list of an isolated debug install with an empty Basic note selected.
"""

import argparse
import csv
import io
import json
from pathlib import Path
import re
import statistics
import subprocess
import time
import xml.etree.ElementTree as ET


ACTIVITY = "com.ichi2.anki.noteeditor.compose.ComposeNoteEditorActivity"


def package_name(value):
    if value == "com.ichi2.anki" or not re.fullmatch(r"[A-Za-z]\w*(?:\.\w+)+", value):
        raise ValueError("Specify an isolated test package; production AnkiDroid is forbidden")
    return value


def query(processor, trace, sql):
    result = subprocess.run(
        [processor, "query", str(trace), sql], capture_output=True, text=True, check=True
    )
    return list(csv.DictReader(io.StringIO(result.stdout)))


def load_trace(processor, trace, package):
    package_name(package)
    # A ring buffer can retain FrameTimeline but lose the process-name packet.
    app = f"""SELECT upid FROM process WHERE name='{package}' UNION
        SELECT upid FROM actual_frame_timeline_slice
        WHERE layer_name GLOB '*VRI-{package}/*'
           OR layer_name GLOB 'TX - {package}/*'"""
    slices = query(processor, trace, f"""
        SELECT s.name, s.ts, s.dur,
            CASE WHEN coalesce(t.upid, pt.upid) IN ({app}) THEN 1 ELSE 0 END is_app
        FROM slice s
        LEFT JOIN thread_track tt ON tt.id=s.track_id
        LEFT JOIN thread t USING(utid)
        LEFT JOIN process_track pt ON pt.id=s.track_id
        WHERE s.name GLOB 'deliverInputEvent*'
           OR s.name GLOB 'performResume:*ComposeNoteEditorActivity'
           OR s.name IN ('IMS.showSoftInput', 'InsetsAnimation: ime')
           OR (t.is_main_thread=1 AND s.name GLOB 'Choreographer#doFrame *'
               AND s.name NOT GLOB '*resynced*')
    """)
    frames = query(processor, trace, f"""
        SELECT ts, dur, jank_type FROM actual_frame_timeline_slice
        WHERE upid IN ({app})
          AND layer_name GLOB '*{package}/{ACTIVITY}#*'
    """)
    for row in slices + frames:
        row.update(ts=int(row["ts"]), dur=int(row["dur"]))
    for row in slices:
        row["is_app"] = row["is_app"] == "1"
    return slices, frames


def measure(event, slices, frames):
    """Bounds only select the gesture; durations use device trace timestamps."""
    inside = lambda r: event["start_ns"] <= r["ts"] < event["end_ns"]
    window = sorted((s for s in slices if inside(s)), key=lambda s: s["ts"])
    app = [s for s in window if s["is_app"]]
    touches = [s for s in app if s["name"].startswith("deliverInputEvent")]
    animations = [s for s in app if s["name"] == "InsetsAnimation: ime" and s["dur"] > 0]
    shows = [s for s in window if s["name"] == "IMS.showSoftInput" and s["dur"] >= 0]
    if not touches or len(animations) != 1 or not shows:
        raise ValueError("Missing/ambiguous touch, IME show or animation; reject incomplete trace")
    touch, animation = touches[0], animations[0]
    if not touch["ts"] <= shows[0]["ts"] <= animation["ts"]:
        raise ValueError("IME did not open after the measured gesture")
    start, end = animation["ts"], animation["ts"] + animation["dur"]
    if end > event["end_ns"]:
        raise ValueError("Capture ends before the keyboard animation completes")
    # Count each app surface frame once, not both display and surface timelines.
    animated_frames = [f for f in frames if start <= f["ts"] < end and f["dur"] > 0]
    if not animated_frames:
        raise ValueError("No editor frames during IME animation")
    do_frames = [
        s["dur"] / 1e6 for s in app
        if s["name"].startswith("Choreographer#doFrame ") and s["dur"] > 0
        and start <= s["ts"] < end
    ]
    if not do_frames:
        raise ValueError("No app main-thread frame timings")
    resumes = [s for s in app if s["name"].startswith("performResume:")]
    if event["kind"] == "fresh" and len(resumes) != 1:
        raise ValueError("Expected a newly resumed editor, not an already open screen")
    missed = sum("App Deadline Missed" in f["jank_type"] for f in animated_frames)
    preparation_frames = [
        s["dur"] / 1e6 for s in app
        if s["name"].startswith("Choreographer#doFrame ") and s["dur"] > 0
        and touch["ts"] <= s["ts"] < start
    ]
    return {
        "kind": event["kind"],
        "inputToAnimationMs": (start - touch["ts"]) / 1e6,
        "imeShowToAnimationMs": (start - shows[0]["ts"]) / 1e6,
        "imeShowWorkMs": shows[0]["dur"] / 1e6,
        "animationMs": animation["dur"] / 1e6,
        "inputToAnimationEndMs": (end - touch["ts"]) / 1e6,
        "editorResumeToAnimationMs": (start - resumes[0]["ts"]) / 1e6 if resumes else None,
        "appFrames": len(animated_frames),
        "missedFrames": missed,
        "missedPercent": 100 * missed / len(animated_frames),
        "maxAnimationDoFrameMs": max(do_frames),
        "maxPreparationDoFrameMs": max(preparation_frames, default=0),
    }


def summarize(rows):
    result = {}
    for kind in ("fresh", "reopen"):
        group = [r for r in rows if r["kind"] == kind]
        if not group:
            raise ValueError(f"Missing {kind} measurements")
        result[kind] = {
            key: statistics.median(r[key] for r in group)
            for key in ("inputToAnimationMs", "imeShowToAnimationMs", "animationMs",
                        "inputToAnimationEndMs", "maxAnimationDoFrameMs", "maxPreparationDoFrameMs")
        }
        result[kind]["samples"] = len(group)
        result[kind]["missedPercent"] = (
            100 * sum(r["missedFrames"] for r in group) / sum(r["appFrames"] for r in group)
        )
    return result


def check_budget(summary, max_first_ms, max_missed_percent):
    failures = []
    if max_first_ms is not None and summary["fresh"]["inputToAnimationMs"] > max_first_ms:
        failures.append(f"fresh input-to-animation median exceeds {max_first_ms} ms")
    if max_missed_percent is not None:
        for kind, metrics in summary.items():
            if metrics["missedPercent"] > max_missed_percent:
                failures.append(f"{kind} missed frames exceed {max_missed_percent}%")
    return failures


def analyze(args):
    manifest = json.loads((args.output / "capture.json").read_text())
    rows = []
    for trial in manifest["trials"]:
        slices, frames = load_trace(args.trace_processor, args.output / trial["trace"], manifest["package"])
        for event in trial["events"]:
            rows.append(dict(iteration=trial["iteration"], **measure(event, slices, frames)))
    summary = summarize(rows)
    failures = check_budget(summary, args.max_first_ms, args.max_missed_percent)
    result = dict(metadata=manifest["metadata"], samples=rows, summary=summary, failures=failures)
    (args.output / "results.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(summary, indent=2), flush=True)
    if failures:
        raise SystemExit("; ".join(failures))


class Device:
    def __init__(self, args):
        self.command = [args.adb, "-s", args.serial]
        self.package = package_name(args.package)

    def adb(self, *args):
        return subprocess.check_output(self.command + list(args), text=True, timeout=30)

    def shell(self, *args):
        return self.adb("shell", *args)

    def guard(self, activity):
        top = self.shell("dumpsys", "activity", "activities")
        if not any("topResumedActivity=" in l and f"{self.package}/{activity}" in l for l in top.splitlines()):
            raise RuntimeError(f"Expected {activity} in foreground; stopping")
        if "mWakefulness=Awake" not in self.shell("dumpsys", "power"):
            raise RuntimeError("Device asleep; stopping")

    def ime(self):
        return "mInputShown=true" in self.shell("dumpsys", "input_method")

    def uptime_ns(self):
        # Only used for broad gesture bounds; never used as a latency timestamp.
        return int(float(self.shell("cat", "/proc/uptime").split()[0]) * 1e9)

    def tap(self, point):
        self.shell("input", "tap", *map(str, point))

    def back(self):
        self.shell("input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.6)


def capture(args):
    device = Device(args)
    device.guard("com.ichi2.anki.DeckPicker")
    if "DEBUGGABLE" not in device.shell("dumpsys", "package", device.package):
        raise RuntimeError("Only debuggable test packages are supported")
    if int(device.shell("getprop", "ro.build.version.sdk")) < 31:
        raise RuntimeError("FrameTimeline requires Android 12 or newer")
    if device.ime():
        raise RuntimeError("Start with the keyboard closed on the deck list")
    args.output.mkdir(parents=True, exist_ok=False)
    # Find the FAB outside the timed region; UIAutomator's idle wait would mask startup.
    xml = f"/data/local/tmp/editor-keyboard-{time.time_ns()}.xml"
    device.shell("uiautomator", "dump", xml)
    try:
        tree = ET.fromstring(device.shell("cat", xml))
    finally:
        device.shell("rm", xml)
    fab = next(n for n in tree.iter("node") if n.get("resource-id") == f"{device.package}:id/fab_main")
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", fab.attrib["bounds"]))
    add = ((x1+x2)//2, (y1+y2)//2)
    apk = next(p.removeprefix("package:") for p in device.shell("pm", "path", device.package).splitlines()
               if p.endswith("/base.apk"))
    metadata = {
        "model": device.shell("getprop", "ro.product.model").strip(),
        "sdk": device.shell("getprop", "ro.build.version.sdk").strip(),
        "fingerprint": device.shell("getprop", "ro.build.fingerprint").strip(),
        "ime": device.shell("settings", "get", "secure", "default_input_method").strip(),
        "packageVersion": [l.strip() for l in device.shell("dumpsys", "package", device.package).splitlines()
                           if "versionName=" in l or "versionCode=" in l],
        "apkSha256": device.shell("sha256sum", apk).split()[0],
        "build": "debug; warm process; fresh editor followed by reopening; existing device settings",
    }
    manifest = dict(package=device.package, metadata=metadata, trials=[])
    ime_package = metadata["ime"].split("/")[0]
    package_name(ime_package)
    for iteration in range(1, args.iterations+1):
        device.guard("com.ichi2.anki.DeckPicker")
        device.tap(add)  # Expand the FAB and allow its existing preload/animation.
        time.sleep(0.3)
        duration = 12 + 2 * max(0, args.settle_seconds - 1.5)
        config = f'''buffers {{ size_kb: 131072 fill_policy: RING_BUFFER }}
duration_ms: {int(duration * 1000)}
data_sources {{ config {{ name: "linux.ftrace" ftrace_config {{
 ftrace_events: "sched/sched_switch" ftrace_events: "sched/sched_waking"
 atrace_categories: "gfx" atrace_categories: "view" atrace_categories: "dalvik"
 atrace_categories: "am" atrace_categories: "wm" atrace_categories: "input"
 atrace_apps: "{device.package}" atrace_apps: "{ime_package}"
}} }} }}
data_sources {{ config {{ name: "linux.process_stats"
 process_stats_config {{ scan_all_processes_on_start: true }} }} }}
data_sources {{ config {{ name: "android.surfaceflinger.frametimeline" }} }}
'''
        config_file = args.output / f"{iteration}.pbtxt"
        config_file.write_text(config)
        name = f"editor-keyboard-{time.time_ns()}"
        remote = f"/data/misc/perfetto-traces/{name}"
        remote_config = f"/data/misc/perfetto-configs/{name}.pbtxt"
        device.adb("push", str(config_file), remote_config)
        device.shell("perfetto", "--txt", "-c", remote_config, "-o", remote, "--background-wait")
        started = time.monotonic()
        trial = dict(iteration=iteration, trace=f"{iteration}.pftrace", events=[])
        for kind, point in (("fresh", add), ("reopen", args.reopen_at)):
            device.guard("com.ichi2.anki.DeckPicker" if kind == "fresh" else ACTIVITY)
            if device.ime():
                raise RuntimeError("Keyboard already open before measured gesture")
            start = device.uptime_ns()
            device.tap(point)
            time.sleep(args.settle_seconds)
            device.guard(ACTIVITY)
            if not device.ime():
                raise RuntimeError("Keyboard did not open; check field coordinates and editor preference")
            trial["events"].append(dict(kind=kind, start_ns=start, end_ns=device.uptime_ns()))
            device.back()
            if device.ime():
                raise RuntimeError("Keyboard did not close")
        device.guard(ACTIVITY)
        device.back()  # No typing/saving/discard confirmation is performed by this benchmark.
        device.guard("com.ichi2.anki.DeckPicker")
        time.sleep(max(0, duration + 1 - (time.monotonic() - started)))
        device.adb("pull", remote, str(args.output / trial["trace"]))
        device.shell("rm", remote, remote_config)
        manifest["trials"].append(trial)
        (args.output / "capture.json").write_text(json.dumps(manifest, indent=2) + "\n")
        print(f"Captured fresh/reopen pair {iteration}/{args.iterations}", flush=True)
    analyze(args)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("capture", "analyze"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--trace-processor", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    parser.add_argument("--package")
    parser.add_argument("--reopen-at", nargs=2, type=int, metavar=("X", "Y"))
    parser.add_argument("--iterations", type=int, default=5)
    parser.add_argument("--settle-seconds", type=float, default=1.5,
                        help="Time before hiding each keyboard; increase for slower emulators (1.5–10)")
    parser.add_argument("--max-first-ms", type=float)
    parser.add_argument("--max-missed-percent", type=float)
    args = parser.parse_args()
    if args.mode == "capture":
        if not all((args.serial, args.package, args.reopen_at)) or not 1 <= args.iterations <= 20:
            parser.error("capture requires serial, package, reopen-at and 1–20 iterations")
        if not 1.5 <= args.settle_seconds <= 10:
            parser.error("settle-seconds must be between 1.5 and 10")
        capture(args)
    else:
        analyze(args)


if __name__ == "__main__":
    main()
