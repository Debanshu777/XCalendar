import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from analyze_android import summarize
from desktop_benchmark import summarize as summarize_desktop


class ReadinessTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.run = self.root / "month-1"
        self.analysis = self.run / "analysis"
        self.analysis.mkdir(parents=True)
        self.metadata = {"scenario": "month", "workload_completed": True,
                         "apk_verified": True, "apk_sha256": "apk", "fixture_data_sha256": "fixture"}
        (self.run / "metadata.json").write_text(json.dumps(self.metadata))
        (self.run / "trace.perfetto-trace").write_bytes(b"test trace")
        (self.analysis / "frames.txt").write_text("frames,self_jank,app_deadline_misses,p50_ms,p95_ms,p99_ms,max_ms\n100,2,1,8,12,20,30\n")
        (self.analysis / "deadlines.txt").write_text("frames,unmatched_or_ambiguous,matched_frames,late_frames,p95_overrun_ms,p99_overrun_ms,median_scheduled_budget_ms\n100,0,100,4,-1,2,16.6\n")
        (self.analysis / "health.txt").write_text("name,severity,value\ncompact_sched_switch_skipped,info,2\n")

    def test_summary_keeps_jank_and_late_deadlines_distinct(self):
        result = summarize(self.root)
        self.assertEqual(result["groups"]["month"]["self_jank_pct"]["median"], 2)
        self.assertEqual(result["groups"]["month"]["late_frame_pct"]["median"], 4)

    def test_summary_command_writes_same_validated_report(self):
        output = self.root / "summary.json"
        subprocess.run([sys.executable, str(Path(__file__).with_name("analyze_android.py")),
                        "summary", "--root", str(self.root), "--output", str(output)], check=True)
        self.assertEqual(json.loads(output.read_text()), summarize(self.root))

    def test_incomplete_workload_cannot_be_accepted(self):
        self.metadata["workload_completed"] = False
        (self.run / "metadata.json").write_text(json.dumps(self.metadata))
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            summarize(self.root)

    def test_loading_must_fit_actual_trace_bounds(self):
        (self.analysis / "bounds.txt").write_text("start_epoch_seconds,end_epoch_seconds\n100,200\n")
        for timestamp in (99, 201):
            self.metadata["mock_phases"] = [{"epoch_seconds": timestamp, "phase": "refreshed"}]
            (self.run / "metadata.json").write_text(json.dumps(self.metadata))
            with self.assertRaisesRegex(ValueError, "outside recorded"):
                summarize(self.root)
        self.metadata["mock_phases"][0]["epoch_seconds"] = 150
        (self.run / "metadata.json").write_text(json.dumps(self.metadata))
        self.assertEqual(len(summarize(self.root)["runs"]), 1)

    def test_trace_loss_cannot_be_accepted(self):
        (self.analysis / "health.txt").write_text("name,severity,value\ntraced_buf_trace_writer_packet_loss,data_loss,1\n")
        with self.assertRaisesRegex(ValueError, "health"):
            summarize(self.root)

    def test_unmatched_deadline_cannot_be_accepted(self):
        path = self.analysis / "deadlines.txt"
        path.write_text(path.read_text().replace("100,0,100,4", "100,1,99,4"))
        with self.assertRaisesRegex(ValueError, "deadlines"):
            summarize(self.root)


class DesktopSummaryTests(unittest.TestCase):
    def test_summary_command_and_inactive_window_rejection(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "completed.txt").touch()
            (root / "environment.properties").write_text("activeAtStart=true\nactiveAtEnd=true\n")
            (root / "frame-clock.csv").write_text(
                "run,transition,callback_interval_ms\n" +
                "".join(f"{run},{transition},16\n" for run in range(1, 6) for transition in range(6)))
            (root / "process.csv").write_text(
                "run,process_cpu_ms,heap_used_bytes,active_at_end\n" +
                "".join(f"{run},100,1024,true\n" for run in range(1, 6)))
            output = root / "summary.json"
            subprocess.run([sys.executable, str(Path(__file__).with_name("desktop_benchmark.py")),
                            "summary", "--root", str(root), "--output", str(output)], check=True)
            self.assertEqual(json.loads(output.read_text()), summarize_desktop(root))
            (root / "environment.properties").write_text("activeAtStart=false\nactiveAtEnd=true\n")
            with self.assertRaisesRegex(ValueError, "Inactive"):
                summarize_desktop(root)


if __name__ == "__main__":
    unittest.main()
