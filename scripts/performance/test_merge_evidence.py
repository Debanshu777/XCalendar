import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from check_merge_evidence import EvidenceError, validate_report


BASE = "a" * 64
HEAD = "b" * 64


def sample_report():
    run = {
        "valid_complete": True,
        "p95_ms": 20.0,
        "p99_ms": 30.0,
        "late_frame_pct": 1.0,
        "max_ms": 35.0,
        "startup_ms": 1000.0,
        "memory_bytes": 100_000_000,
        "stalls_ge_40ms": 0,
    }
    return {
        "version": 1,
        "base_source_sha256": BASE,
        "head_source_sha256": HEAD,
        "metric_kind": "presented_frames",
        "fixture": {"year": 2026, "events": 3650},
        "platforms": {
            platform: {
                "device": f"device-{platform}",
                "config": "release; 60Hz; fixture=2026/3650",
                "artifacts": {
                    "before_url": "https://evidence.example/before",
                    "after_url": "https://evidence.example/after",
                },
                "scenarios": {
                    scenario: {
                        "before": [dict(copy.deepcopy(run), run_id=f"{platform}-{scenario}-before-{i}")
                                   for i in range(5)],
                        "after": [dict(copy.deepcopy(run), run_id=f"{platform}-{scenario}-after-{i}")
                                  for i in range(5)],
                    }
                    for scenario in ("alternating", "forward", "loading_refresh")
                },
            }
            for platform in ("android", "ios", "macos")
        },
    }


class MergeEvidenceTests(unittest.TestCase):
    def test_valid_report_passes(self):
        self.assertEqual(validate_report(sample_report(), BASE, HEAD), [])

    def test_digest_must_match_cli_arguments(self):
        report = sample_report()
        report["head_source_sha256"] = "c" * 64
        with self.assertRaisesRegex(EvidenceError, "does not match"):
            validate_report(report, BASE, HEAD)

    def test_digest_arguments_must_be_full_sha256(self):
        with self.assertRaisesRegex(EvidenceError, "64-character"):
            validate_report(sample_report(), "a" * 40, HEAD)

    def test_version_and_fixture_require_exact_integer_values(self):
        report = sample_report()
        report["version"] = True
        with self.assertRaisesRegex(EvidenceError, "version must be integer 1"):
            validate_report(report, BASE, HEAD)
        report = sample_report()
        report["fixture"]["events"] = 3651
        with self.assertRaisesRegex(EvidenceError, "events must be integer 3650"):
            validate_report(report, BASE, HEAD)

    def test_all_platforms_and_scenarios_are_required(self):
        report = sample_report()
        del report["platforms"]["ios"]
        with self.assertRaisesRegex(EvidenceError, "exactly android, ios, and macos"):
            validate_report(report, BASE, HEAD)

    def test_each_side_needs_five_complete_runs(self):
        report = sample_report()
        report["platforms"]["android"]["scenarios"]["forward"]["after"].pop()
        with self.assertRaisesRegex(EvidenceError, "at least 5 runs"):
            validate_report(report, BASE, HEAD)
        report = sample_report()
        report["platforms"]["android"]["scenarios"]["forward"]["before"][0]["valid_complete"] = 1
        with self.assertRaisesRegex(EvidenceError, "valid_complete must be true"):
            validate_report(report, BASE, HEAD)

    def test_callback_metrics_and_non_https_evidence_are_rejected(self):
        report = sample_report()
        report["metric_kind"] = "callback_interval"
        with self.assertRaisesRegex(EvidenceError, "callback metrics"):
            validate_report(report, BASE, HEAD)
        report = sample_report()
        report["platforms"]["ios"]["artifacts"]["after_url"] = "http://example.com/report"
        with self.assertRaisesRegex(EvidenceError, "HTTPS URL"):
            validate_report(report, BASE, HEAD)

    def test_bad_numbers_and_boolean_numbers_are_rejected(self):
        report = sample_report()
        report["platforms"]["macos"]["scenarios"]["alternating"]["before"][0]["p95_ms"] = float("nan")
        with self.assertRaisesRegex(EvidenceError, "finite non-negative"):
            validate_report(report, BASE, HEAD)

    def test_metrics_are_internally_consistent_and_run_ids_unique(self):
        report = sample_report()
        run = report["platforms"]["android"]["scenarios"]["alternating"]["before"][0]
        run["p99_ms"] = 19
        with self.assertRaisesRegex(EvidenceError, "p95_ms <= p99_ms <= max_ms"):
            validate_report(report, BASE, HEAD)
        report = sample_report()
        group = report["platforms"]["android"]["scenarios"]["alternating"]
        group["after"][0]["run_id"] = group["before"][0]["run_id"]
        with self.assertRaisesRegex(EvidenceError, "run_id values must be unique"):
            validate_report(report, BASE, HEAD)
        report = sample_report()
        run = report["platforms"]["android"]["scenarios"]["alternating"]["after"][0]
        run["max_ms"] = 40
        with self.assertRaisesRegex(EvidenceError, "agree with stalls_ge_40ms"):
            validate_report(report, BASE, HEAD)
        report = sample_report()
        report["platforms"]["macos"]["scenarios"]["alternating"]["before"][0]["memory_bytes"] = True
        with self.assertRaisesRegex(EvidenceError, "finite non-negative"):
            validate_report(report, BASE, HEAD)

    def test_thresholds_use_median_by_platform_and_scenario(self):
        report = sample_report()
        group = report["platforms"]["android"]["scenarios"]["forward"]
        for run in group["after"]:
            run["p95_ms"] = 21.01  # 5.05% over the 20 ms baseline.
        failures = validate_report(report, BASE, HEAD)
        self.assertTrue(any("android/forward: p95_ms" in item for item in failures))

    def test_percentage_point_memory_max_and_stall_gates(self):
        report = sample_report()
        group = report["platforms"]["ios"]["scenarios"]["loading_refresh"]
        for run in group["after"]:
            run["late_frame_pct"] = 1.51
            run["memory_bytes"] = 102_100_000
            run["max_ms"] = 42
            run["stalls_ge_40ms"] = 1
        failures = validate_report(report, BASE, HEAD)
        self.assertTrue(any("late_frame_pct" in item for item in failures))
        self.assertTrue(any("memory_bytes" in item for item in failures))
        self.assertTrue(any("max_ms" in item for item in failures))
        self.assertTrue(any("newly introduced >=40 ms stalls" in item for item in failures))

        # Any new stall is gated, even when the median count would remain zero.
        report = sample_report()
        group = report["platforms"]["macos"]["scenarios"]["alternating"]
        group["after"][0]["stalls_ge_40ms"] = 1
        group["after"][0]["max_ms"] = 40
        group["after"][0]["p99_ms"] = 39
        failures = validate_report(report, BASE, HEAD)
        self.assertTrue(any("newly introduced >=40 ms stalls" in item for item in failures))

    def test_cli_fails_when_default_report_is_missing(self):
        script = Path(__file__).with_name("check_merge_evidence.py")
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run(
                [sys.executable, str(script), "--base-source-sha256", BASE,
                 "--head-source-sha256", HEAD],
                cwd=directory, capture_output=True, text=True,
            )
        self.assertEqual(result.returncode, 1)
        self.assertIn("performance-evidence/merge.json", result.stderr)


if __name__ == "__main__":
    unittest.main()
