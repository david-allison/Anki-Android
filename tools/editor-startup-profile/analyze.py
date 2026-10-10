# SPDX-License-Identifier: GPL-3.0-or-later
"""Convert ART v3 dual-clock samples into weighted stacks; no external packages."""

from pathlib import Path
from collections import defaultdict, Counter
import json, struct, sys

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent


def art(path):
    data = path.read_bytes()
    split = data.index(b"*end\n") + 5
    header = data[:split].decode()
    section = ""
    methods = {}
    threads = {}
    metadata = {}
    for line in header.splitlines():
        if line.startswith("*"):
            section = line
            continue
        if section == "*threads":
            k, v = line.split("\t", 1)
            threads[int(k)] = v
        elif section == "*methods":
            parts = line.split("\t")
            methods[int(parts[0], 16)] = parts[1] + "." + parts[2] + parts[3]
        elif "=" in line:
            k, v = line.split("=", 1)
            metadata[k] = v
    magic, version, offset, start, size = struct.unpack_from("<4sHHQH", data, split)
    assert (magic, version, size) == (b"SLOW", 3, 14)
    assert metadata["data-file-overflow"] == "false"
    assert (len(data) - split - offset) % size == 0
    records = defaultdict(list)
    for tid, encoded, cpu, wall in struct.iter_unpack("<HIII", data[split + offset :]):
        records[tid].append((wall, cpu, encoded & ~3, encoded & 3))
    output = []
    mismatch = 0
    for tid, events in records.items():
        events.sort(key=lambda e: e[0])
        stack = []
        prev = None
        cpu_weights = Counter()
        wall_weights = Counter()
        intervals = []
        for wall, cpu, method, action in events:
            if prev is not None and stack:
                dw, dc = wall - prev[0], cpu - prev[1]
                assert dw >= 0 and dc >= 0, (tid, dw, dc)
                if dc:
                    cpu_weights[tuple(stack)] += dc
                if dw:
                    wall_weights[tuple(stack)] += dw
                if dw:
                    intervals.append((prev[0], wall, dc, tuple(stack)))
            if action == 0:
                stack.append(method)
            elif action in (1, 2):
                if not stack or stack[-1] != method:
                    mismatch += 1
                    raise ValueError(("unbalanced stack", tid, method, stack[-5:]))
                stack.pop()
            else:
                raise ValueError(action)
            prev = (wall, cpu)

        def named(weights):
            return [[list(map(methods.__getitem__, s)), w] for s, w in weights.items()]

        inclusive = Counter()
        self_time = Counter()
        for s, w in cpu_weights.items():
            self_time[methods[s[-1]]] += w
            for name in set(map(methods.__getitem__, s)):
                inclusive[name] += w
        output.append(
            {
                "thread": threads[tid],
                "tid": tid,
                "cpuUs": sum(cpu_weights.values()),
                "wallUs": sum(wall_weights.values()),
                "cpuStacks": named(cpu_weights),
                "wallStacks": named(wall_weights),
                "topInclusive": inclusive.most_common(50),
                "topSelf": self_time.most_common(50),
                "firstWallUs": events[0][0],
                "lastWallUs": events[-1][0],
            }
        )
    return {
        "label": path.stem,
        "metadata": metadata,
        "threads": sorted(output, key=lambda t: -t["cpuUs"]),
        "mismatches": mismatch,
    }


if __name__ == "__main__":
    for path in ROOT.glob("native-*.trace"):
        result = art(path)
        path.with_suffix(".stacks.json").write_text(json.dumps(result))
        print(result["label"], "elapsed", result["metadata"]["elapsed-time-usec"])
        for t in result["threads"][:8]:
            print(
                t["thread"],
                "CPU",
                round(t["cpuUs"] / 1000, 2),
                "wall",
                round(t["wallUs"] / 1000, 2),
            )
        main = next(t for t in result["threads"] if t["thread"] == "main")
        print("MAIN inclusive:")
        print("\n".join(f"{v/1000:.2f} {k}" for k, v in main["topInclusive"][:35]))
        print("MAIN self:")
        print("\n".join(f"{v/1000:.2f} {k}" for k, v in main["topSelf"][:20]))
