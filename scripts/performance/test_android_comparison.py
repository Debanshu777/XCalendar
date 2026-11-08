"""Guard against reporting incomplete/lossy trace results as improvements."""
import json
from pathlib import Path
import tempfile
import unittest

from collect_android_comparison import compare, health_result, read_run


class ComparisonTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "analysis").mkdir()
        (self.root / "trace.perfetto-trace").write_bytes(b"test trace")
        (self.root / "recording.log").write_text("Wrote 10 bytes into trace\n1 file pulled\n")
        (self.root / "trace-config.pftxt").write_text("matching test config")
        (self.root / "metadata.json").write_text(json.dumps({"scenario": "dropdown", "iterations": 10,
            "model": "test", "android": "17", "compose_tracing": True}))
        self.health = self.root / "analysis/health.txt"
        self.health.write_text('"name","severity","value"\n"compact_sched_switch_skipped","info",8\n')
        (self.root / "analysis/frames.txt").write_text('"frames","self_jank","app_deadline_misses","p50_ms","p95_ms","p99_ms","max_ms"\n100,1,1,5.0,10.0,20.0,30.0\n')
        (self.root / "analysis/composition.txt").write_text('"name","executions","inclusive_ms","max_ms"\n"com.debanshu.xcalendar.ui.navigation.NavigationHost (BaseNavigation.kt:28)",400,20.0,1.0\n"com.debanshu.xcalendar.ui.navigation.NavigationHost.<anonymous> (BaseNavigation.kt:44)",800,5.0,1.0\n')
        (self.root / "analysis/cpu.txt").write_text('"name","running_cpu_ms"\n"endar.benchmark",2000.0\n"RenderThread",1000.0\n')

    def test_info_loss_is_rejected_while_compact_skips_are_disclosed(self):
        result = health_result([{"name": "trace_buffer_bytes_overwritten", "severity": "info", "value": "50"}])
        self.assertFalse(result["accepted"])
        clean = read_run(self.root, "before", 1)
        self.assertTrue(clean["accepted"])
        self.assertEqual(clean["health"]["informational_scheduler_skips"][0]["value"], 8)

    def test_named_function_count_excludes_nested_lambdas(self):
        run = read_run(self.root, "before", 1)
        self.assertEqual(run["composition"]["navigation_host"]["executions"], 400)
        self.assertEqual(run["composition"]["month_day_cell"]["executions"], 0)
        self.assertEqual(run["cpu"]["main_ms"], 2000)

    def test_incomplete_trace_and_workload_are_pending(self):
        (self.root / "recording.log").write_text("Wrote 10 bytes into trace\n")
        self.assertEqual(read_run(self.root, "before", 1)["status"], "pending")
        (self.root / "recording.log").write_text("Wrote 10 bytes into trace\n1 file pulled\n")
        metadata = json.loads((self.root / "metadata.json").read_text())
        metadata["workload_completed"] = False
        (self.root / "metadata.json").write_text(json.dumps(metadata))
        self.assertFalse(read_run(self.root, "before", 1)["accepted"])

    def test_rejected_run_does_not_enter_summary(self):
        before = read_run(self.root, "before", 1)
        self.health.write_text('"name","severity","value"\n"tokenizer_skipped_packets","info",25\n')
        after = read_run(self.root, "after", 1)
        result = compare([before, after], 1)
        self.assertEqual(result["accepted_runs"], {"before": 1, "after": 0})
        self.assertFalse(result["ready"])

    def test_configuration_mismatch_blocks_group_conclusion(self):
        before = read_run(self.root, "before", 1)
        (self.root / "trace-config.pftxt").write_text("different buffer size")
        after = read_run(self.root, "after", 1)
        result = compare([before, after], 1)
        self.assertFalse(result["ready"])
        self.assertIn("trace_config", result["mismatched_recorded_conditions"])
        self.assertNotIn("pairs", result)


if __name__ == "__main__":
    unittest.main()
