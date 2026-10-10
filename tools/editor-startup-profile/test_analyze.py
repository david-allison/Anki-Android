# SPDX-License-Identifier: GPL-3.0-or-later
"""Known ART-format fixture: distinguish CPU weights from time spent waiting."""

import struct
import tempfile
import unittest
from pathlib import Path

from analyze import art


class ArtTraceTest(unittest.TestCase):
    def trace(self, overflow=False):
        header = (
            "*version\n3\ndata-file-overflow="
            + str(overflow).lower()
            + "\nclock=dual\n*threads\n3\tmain\n*methods\n"
            + "0x0\tExample\troot\t()V\tExample.java\n"
            + "0x4\tExample\twork\t()V\tExample.java\n*end\n"
        ).encode()
        binary = struct.pack("<4sHHQH", b"SLOW", 3, 32, 1000, 14).ljust(32, b"\0")
        # Enter root, enter work, leave work, leave root: wall 150 us, CPU 60 us.
        events = [(3, 0, 10, 100), (3, 4, 20, 130), (3, 5, 50, 200), (3, 1, 70, 250)]
        return (
            header + binary + b"".join(struct.pack("<HIII", *event) for event in events)
        )

    def parse(self, data):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.trace"
            path.write_bytes(data)
            return art(path)

    def test_cpu_and_wait_weights_are_distinct(self):
        result = self.parse(self.trace())
        thread = result["threads"][0]
        self.assertEqual(result["mismatches"], 0)
        self.assertEqual(thread["cpuUs"], 60)
        self.assertEqual(thread["wallUs"], 150)
        self.assertEqual(
            dict(thread["topSelf"]), {"Example.root()V": 30, "Example.work()V": 30}
        )
        self.assertEqual(
            dict(thread["topInclusive"]), {"Example.root()V": 60, "Example.work()V": 30}
        )

    def test_overflow_is_rejected(self):
        with self.assertRaises(AssertionError):
            self.parse(self.trace(overflow=True))

    def test_partial_binary_record_is_rejected(self):
        with self.assertRaises(AssertionError):
            self.parse(self.trace()[:-1])


if __name__ == "__main__":
    unittest.main()
