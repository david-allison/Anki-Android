#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Render recorded input as touch dots. Requires ffmpeg/ffprobe; no Python packages.

The original screen recording remains untouched. A 60 fps delivery container
lets short taps disappear on time even when Android emits no new screen frame.
Repeated screen frames preserve real time; this does not repair rendering stalls.
"""

import argparse
import json
import math
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import zlib


def first_frame_elapsed_nanos(video):
    # Android screenrecord's Winscope V2 metadata uses the same elapsed clock as
    # SystemClock.elapsedRealtimeNanos(), avoiding a guessed recording-start delay.
    # https://android.googlesource.com/platform/frameworks/av/+/master/cmds/screenrecord/screenrecord.cpp
    data = video.read_bytes()
    magic = b"#VV1NSC0PET1ME2#"
    offset = data.find(magic)
    if offset < 0:
        raise ValueError("Recording lacks Android Winscope V2 timestamp metadata")
    offset += len(magic)
    version, _, count = struct.unpack_from("<IqI", data, offset)
    if version != 2 or count < 1 or offset + 16 + count * 8 > len(data):
        raise ValueError("Invalid screenrecord timestamp metadata")
    return struct.unpack_from("<Q", data, offset + 16)[0]


def write_dot(path):
    """A translucent white circle with a gray rim, visible on either theme."""
    size = 32
    rows = bytearray()
    for y in range(size):
        rows.append(0)  # PNG filter: none
        for x in range(size):
            radius = math.hypot(x + 0.5 - size / 2, y + 0.5 - size / 2)
            coverage = max(0, min(1, 13 - radius))
            shade = 100 if radius > 10.5 else 255
            rows.extend((shade, shade, shade, round(180 * coverage)))

    def chunk(kind, payload):
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload))

    path.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(rows))
        + chunk(b"IEND", b"")
    )


def commands(inputs, first_frame):
    events = []
    down_at = None
    for event in inputs:
        time = (event["elapsedNanos"] - first_frame) / 1e9
        if time < 0:
            raise ValueError("Recorded input precedes the video")
        action = event["action"]
        if action == 0:
            if down_at is not None:
                raise ValueError("Overlapping gestures")
            down_at = time
        elif down_at is None:
            raise ValueError("Gesture has no touch-down")
        if action in (1, 3):
            # Give a short tap at least 100 ms of visibility, without delaying UI.
            events.append((max(time, down_at + 0.1), -100, -100))
            down_at = None
        else:
            events.append((time, event["x"] - 16, event["y"] - 16))
    if down_at is not None:
        raise ValueError("Unfinished gesture")
    # A following tap supersedes the previous tap's minimum display time.
    for index in range(len(events) - 1):
        if events[index][1] == -100:
            events[index] = (min(events[index][0], events[index + 1][0]), -100, -100)
    return "\n".join(
        f"{time:.6f} overlay@touch x {x:.2f}, overlay@touch y {y:.2f};"
        for time, x, y in events
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path, help="Pulled editor-demo artifact directory")
    args = parser.parse_args()
    directory = args.directory.resolve()
    result = json.loads((directory / "result.json").read_text())
    if not result["passed"]:
        raise ValueError("Refusing to produce a final video from a failed take")
    ffmpeg, ffprobe = shutil.which("ffmpeg"), shutil.which("ffprobe")
    if not ffmpeg or not ffprobe:
        raise ValueError("Install ffmpeg and ffprobe first")
    video = directory / "take.mp4"
    epoch = first_frame_elapsed_nanos(video)
    inputs = json.loads((directory / "touches.json").read_text())
    if not inputs:
        raise ValueError("No recorded touch events")
    duration = float(subprocess.check_output([
        ffprobe, "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=duration",
        "-of", "default=noprint_wrappers=1:nokey=1", str(video),
    ]))
    with tempfile.TemporaryDirectory(prefix="editor-demo-touches-") as temporary:
        work = Path(temporary)
        write_dot(work / "dot.png")
        (work / "touches.txt").write_text(commands(inputs, epoch))
        subprocess.run([
            ffmpeg, "-hide_banner", "-y", "-i", str(video),
            "-loop", "1", "-framerate", "60", "-i", "dot.png",
            "-filter_complex_threads", "1", "-filter_complex",
            "[0:v]fps=60[screen];"
            "[screen][1:v]overlay@touch=x=-100:y=-100:format=auto,"
            "sendcmd=f=touches.txt,format=yuv420p[out]",
            "-map", "[out]", "-an", "-t", str(duration),
            "-c:v", "libx264", "-preset", "fast", "-crf", "18", "-threads", "4",
            "-movflags", "+faststart", str(directory / "demo.mp4"),
        ], cwd=work, check=True)
    (directory / "render.json").write_text(json.dumps({
        "firstFrameElapsedNanos": epoch,
        "sceneStartSeconds": (result["startedElapsedNanos"] - epoch) / 1e9,
        "durationSeconds": duration,
        "touchEvents": len(inputs),
        "deliveryFps": 60,
        "retimed": False,
    }, indent=2) + "\n")


if __name__ == "__main__":
    main()
