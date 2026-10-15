# SPDX-License-Identifier: GPL-3.0-or-later
"""Regression tests for the benchmark's attribution and failure conditions."""

import unittest

from keyboard import check_budget, measure, package_name, summarize


def slice_at(name, start_ms, duration_ms=1, app=True):
    return dict(name=name, ts=int(start_ms*1e6), dur=int(duration_ms*1e6), is_app=app)


class KeyboardBenchmarkTest(unittest.TestCase):
    def setUp(self):
        self.event = dict(kind="fresh", start_ns=0, end_ns=1_000_000_000)
        self.slices = [
            slice_at("deliverInputEvent", 100),
            slice_at("performResume:ComposeNoteEditorActivity", 130),
            slice_at("IMS.showSoftInput", 250, 40, app=False),
            slice_at("InsetsAnimation: ime", 350, 300),
            slice_at("Choreographer#doFrame 1", 360, 12),
            slice_at("Choreographer#doFrame 2", 400, 18),
        ]
        self.frames = [
            dict(ts=360_000_000, dur=14_000_000, jank_type="None"),
            dict(ts=400_000_000, dur=20_000_000, jank_type="App Deadline Missed, App Resynced Jitter"),
        ]

    def test_separates_delay_from_animation_using_trace_not_adb_bounds(self):
        row = measure(self.event, self.slices, self.frames)
        self.assertEqual(250, row["inputToAnimationMs"])
        self.assertEqual(100, row["imeShowToAnimationMs"])
        self.assertEqual(40, row["imeShowWorkMs"])
        self.assertEqual(300, row["animationMs"])
        self.assertEqual(550, row["inputToAnimationEndMs"])
        self.assertEqual(220, row["editorResumeToAnimationMs"])
        self.assertEqual(50, row["missedPercent"])

    def test_excludes_keyboard_process_and_frames_outside_animation(self):
        self.slices += [
            slice_at("InsetsAnimation: ime", 200, 500, app=False),
            slice_at("Choreographer#doFrame 3", 380, 200, app=False),
            slice_at("Choreographer#doFrame 4", 200, 100),
        ]
        self.frames.append(dict(ts=300_000_000, dur=100_000_000, jank_type="App Deadline Missed"))
        row = measure(self.event, self.slices, self.frames)
        self.assertEqual(18, row["maxAnimationDoFrameMs"])
        self.assertEqual(100, row["maxPreparationDoFrameMs"])
        self.assertEqual(2, row["appFrames"])

    def test_lost_trace_data_is_not_reported_as_zero_jank(self):
        for missing in ("deliverInputEvent", "IMS.showSoftInput", "InsetsAnimation: ime"):
            with self.subTest(missing=missing), self.assertRaises(ValueError):
                measure(self.event, [s for s in self.slices if s["name"] != missing], self.frames)
        with self.assertRaises(ValueError):
            measure(self.event, self.slices, [])

    def test_rejects_truncated_or_multiple_animations(self):
        with self.assertRaises(ValueError):
            measure(dict(self.event, end_ns=500_000_000), self.slices, self.frames)
        with self.assertRaises(ValueError):
            measure(self.event, self.slices + [slice_at("InsetsAnimation: ime", 700, 200)], self.frames)

    def test_fresh_requires_a_new_editor_but_reopen_does_not(self):
        slices = [s for s in self.slices if not s["name"].startswith("performResume:")]
        with self.assertRaises(ValueError):
            measure(self.event, slices, self.frames)
        self.assertIsNone(measure(dict(self.event, kind="reopen"), slices, self.frames)["editorResumeToAnimationMs"])

    def test_budgets_fail_only_for_measured_regressions(self):
        row = measure(self.event, self.slices, self.frames)
        summary = summarize([row, dict(row, kind="reopen")])
        self.assertEqual([], check_budget(summary, 250, 50))
        self.assertEqual(3, len(check_budget(summary, 249, 49)))
        self.assertEqual([], check_budget(summary, None, None))

    def test_summary_weights_jank_by_frames_not_by_iteration(self):
        row = measure(self.event, self.slices, self.frames)
        other = dict(row, appFrames=8, missedFrames=0)
        summary = summarize([row, other, dict(row, kind="reopen")])
        self.assertEqual(10, summary["fresh"]["missedPercent"])
        with self.assertRaises(ValueError):
            summarize([row])

    def test_refuses_production_or_shell_metacharacters(self):
        for name in ("com.ichi2.anki", "app;exit", "app'", "app name"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                package_name(name)
        self.assertEqual("com.ichi2.anki.debug", package_name("com.ichi2.anki.debug"))


if __name__ == "__main__":
    unittest.main()
