"""Restoration and deterministic-data checks; no app builds or device access."""

from datetime import datetime
import json
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import unittest

import fixture
from android_benchmark import validated_fixture


def calendar_source(lifecycle=True):
    collect = "collectAsStateWithLifecycle" if lifecycle else "collectAsState"
    return ("package com.debanshu.xcalendar\nfun CalendarApp() {\n"
            f"    val calendarUiState by calendarViewModel.uiState.{collect}()\n"
            "    val backStack = rememberNavBackStack(config, NavigableScreen.Month)\n"
            "    // Existing layout and editor wiring must survive unchanged.\n}\n").encode()


class FixtureTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / fixture.SOURCE_DIR / "CalendarApp.kt"
        self.source.parent.mkdir(parents=True)
        self.generated = self.source.with_name(fixture.OBJECT_NAME + ".kt")
        self.output = self.root / "captures/fixture"
        self.original = calendar_source()
        self.source.write_bytes(self.original)

    def assert_restored(self):
        self.assertEqual(self.source.read_bytes(), self.original)
        self.assertFalse(self.generated.exists())
        self.assertFalse((self.root / "build/performance-ui-fixture.lock").exists())

    def test_fixture_distribution_and_determinism(self):
        data = fixture.fixture_data()
        self.assertEqual(data, fixture.fixture_data())
        self.assertEqual(len(data["events"]), 300)
        self.assertEqual(len({e["id"] for e in data["events"]}), 300)
        for day in range(1, 31):
            self.assertEqual(sum(e["startLocal"].startswith(f"2026-11-{day:02}") for e in data["events"]), 10)
        for event in data["events"]:
            start, end = map(datetime.fromisoformat, [event["startLocal"], event["endLocal"]])
            self.assertGreater(end, start)
            if event["kind"] == "overnight":
                self.assertGreater(end.date(), start.date())
            if event["isAllDay"]:
                self.assertEqual((start.hour, start.minute, end.hour, end.minute), (0, 0, 0, 0))
        source = fixture.kotlin_source(data)
        self.assertEqual(source, fixture.kotlin_source(fixture.fixture_data()))
        manifest = fixture.write_artifacts(self.output, data, source)
        self.assertEqual(manifest["events_by_kind"], {"daytime_overlap": 180, "overnight": 60, "all_day": 60})
        self.assertEqual(manifest["data_sha256"], fixture.sha256((self.output / "fixture-data.json").read_bytes()))
        self.assertEqual(manifest["source_sha256"], fixture.sha256((self.output / "PerformanceUiFixture.kt").read_bytes()))
        self.assertEqual(json.loads((self.output / "fixture-data.json").read_text()), data)
        self.assertEqual(validated_fixture(self.output / "fixture-manifest.json")["event_count"], 300)

    def test_runner_rejects_mismatched_fixture_count_and_checksum(self):
        manifest = fixture.write_artifacts(self.output)
        manifest_path = self.output / "fixture-manifest.json"
        manifest["event_count"] = 299
        manifest_path.write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, "count"):
            validated_fixture(manifest_path)
        fixture.write_artifacts(self.output)
        (self.output / "fixture-data.json").write_text("{}")
        with self.assertRaisesRegex(ValueError, "checksum"):
            validated_fixture(manifest_path)

    def test_patch_supports_both_collectors_and_preserves_surrounding_source(self):
        for lifecycle in (False, True):
            original = calendar_source(lifecycle)
            patched = fixture.patched_calendar_app(original)
            self.assertEqual(patched.count(b"val calendarUiState = PerformanceUiFixture.uiState"), 1)
            self.assertEqual(patched.count(b"LaunchedEffect(dateStateHolder)"), 1)
            self.assertIn(b"// Existing layout and editor wiring must survive unchanged.", patched)
            self.assertNotIn(b"val calendarUiState by", patched)

    def test_success_restores_exact_bytes_and_removes_generated_input(self):
        previous_handler = signal.getsignal(signal.SIGTERM)
        with fixture.temporary_fixture(self.root, self.output) as manifest:
            self.assertEqual(manifest["event_count"], 300)
            self.assertTrue(self.generated.exists())
            self.assertEqual(self.source.read_bytes(), fixture.patched_calendar_app(self.original))
        self.assert_restored()
        self.assertEqual(signal.getsignal(signal.SIGTERM), previous_handler)
        self.assertEqual((self.output / "CalendarApp.original.kt").read_bytes(), self.original)

    def test_failure_and_interrupt_restore(self):
        for error in (RuntimeError("failed build"), KeyboardInterrupt(), SystemExit(143)):
            with self.assertRaises(type(error)):
                with fixture.temporary_fixture(self.root, self.output):
                    raise error
            self.assert_restored()

    def test_failed_child_restores(self):
        with self.assertRaises(subprocess.CalledProcessError) as error:
            with fixture.temporary_fixture(self.root, self.output):
                fixture.run_command([sys.executable, "-c", "raise SystemExit(7)"], self.root)
        self.assertEqual(error.exception.returncode, 7)
        self.assert_restored()

    def test_existing_generated_input_is_never_overwritten(self):
        self.generated.write_bytes(b"keep existing source")
        with self.assertRaises(RuntimeError):
            with fixture.temporary_fixture(self.root, self.output):
                self.fail("Must not enter context")
        self.assertEqual(self.source.read_bytes(), self.original)
        self.assertEqual(self.generated.read_bytes(), b"keep existing source")

    def test_unknown_or_duplicate_source_is_rejected_before_mutation(self):
        for original in (b"unknown app source", calendar_source() + calendar_source()):
            self.source.write_bytes(original)
            with self.assertRaises(RuntimeError):
                with fixture.temporary_fixture(self.root, self.output):
                    self.fail("Must not enter context")
            self.assertEqual(self.source.read_bytes(), original)
            self.assertFalse(self.generated.exists())
            self.assertFalse(self.output.exists())

    def test_nested_context_is_rejected_and_outer_context_restores(self):
        with fixture.temporary_fixture(self.root, self.output):
            with self.assertRaises(RuntimeError):
                with fixture.temporary_fixture(self.root, self.output):
                    self.fail("Must not enter nested context")
        self.assert_restored()

    def test_artifacts_in_common_source_tree_are_rejected(self):
        with self.assertRaises(ValueError):
            with fixture.temporary_fixture(self.root, self.root / "app/shared/src/artifacts"):
                self.fail("Must not enter context")
        self.assert_restored()


if __name__ == "__main__":
    unittest.main()
