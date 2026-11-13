#!/usr/bin/env python3
"""Run an opt-in macOS month-transition diagnostic in an isolated checkout.

Uses a separate user.home (no shipping database/session), a 900-event fixture,
JFR, and Compose frame-clock samples. Samples are NOT presented-frame FPS.
Do not run another build in this checkout during temporary input injection.
"""
import argparse
import csv
import json
import math
import statistics
from pathlib import Path
import shutil

from fixture import temporary_fixture, run_command


def summarize(root):
    if not (root / "completed.txt").exists():
        raise ValueError("Desktop workload did not complete")
    with (root / "frame-clock.csv").open() as stream:
        callbacks = list(csv.DictReader(stream))
    with (root / "process.csv").open() as stream:
        processes = list(csv.DictReader(stream))
    environment = dict(line.strip().split("=", 1) for line in
                       (root / "environment.properties").read_text().splitlines()
                       if "=" in line and not line.startswith("#"))
    if environment.get("activeAtStart") != "true" or environment.get("activeAtEnd") != "true" or any(
            row.get("active_at_end") != "true" for row in processes):
        raise ValueError("Inactive desktop window; capture is diagnostic only")
    runs = []
    for run in range(1, 6):
        samples = [row for row in callbacks if int(row["run"]) == run]
        if {int(row["transition"]) for row in samples} != set(range(6)):
            raise ValueError(f"Incomplete desktop round {run}")
        values = sorted(float(row["callback_interval_ms"]) for row in samples)
        process = [row for row in processes if int(row["run"]) == run]
        if len(process) != 1 or any(value <= 0 for value in values):
            raise ValueError(f"Invalid desktop samples for round {run}")
        runs.append({"run": run, "callbacks": len(values),
                     "callback_p95_ms": values[math.ceil(len(values) * .95) - 1],
                     "callback_p99_ms": values[math.ceil(len(values) * .99) - 1],
                     "callback_max_ms": max(values),
                     "process_cpu_ms": float(process[0]["process_cpu_ms"]),
                     "heap_used_bytes": int(process[0]["heap_used_bytes"])})
    return {"scope": "Programmatic month requests; frame-clock callbacks request frames and include settling; not GPU/presentation FPS. Round 0 excluded as warmup.",
            "environment": environment,
            "runs": runs, "medians": {key: statistics.median(r[key] for r in runs)
                                         for key in ("callback_p95_ms", "callback_p99_ms", "callback_max_ms", "process_cpu_ms", "heap_used_bytes")}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    summary = commands.add_parser("summary", help="Summarize an existing completed capture")
    summary.add_argument("--root", type=Path, required=True)
    summary.add_argument("--output", type=Path, required=True)
    p = commands.add_parser("run", help="Build and run an isolated desktop diagnostic")
    p.add_argument("--root", type=Path, required=True, help="Isolated source checkout")
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--java", type=Path, required=True, help="JDK 25 java executable")
    p.add_argument("--app-image", action="store_true", help="Package a macOS .app so it can be foregrounded and inspected by native UI tools")
    p.add_argument("--wait-for-start", action="store_true", help="Wait for <output>/start.signal before measuring; useful while other builds finish")
    p.add_argument("--year", action="store_true", help="Use the full-year fixture instead of three months")
    p.add_argument("--load-delay-ms", type=int, default=0)
    p.add_argument("--batch-delay-ms", type=int, default=0)
    p.add_argument("--refresh-delay-ms", type=int, default=0)
    args = parser.parse_args()
    if args.command == "summary":
        args.output.write_text(json.dumps(summarize(args.root), indent=2) + "\n")
        return
    root, output = args.root.resolve(), args.output.resolve()
    if output.exists() and any(output.iterdir()):
        p.error("Use a new empty output directory; stale measurement artifacts are not accepted")
    if root == Path(__file__).resolve().parents[2]:
        p.error("Use an isolated checkout, not the working repository")
    output.mkdir(parents=True, exist_ok=True)
    if args.wait_for_start:
        (output / "start.signal").unlink(missing_ok=True)
    else:
        (output / "start.signal").touch()
    main_file = root / "app/desktopApp/src/com/debanshu/xcalendar/Main.kt"
    probe = main_file.with_name("DesktopPerformanceProbe.kt")
    original = main_file.read_bytes()
    anchor = "            CalendarApp()"
    if original.decode().count(anchor) != 1 or original.decode().count('            title = "XCalendar",') != 1 or probe.exists():
        raise RuntimeError("Unknown desktop entry point or existing probe")
    (output / "Main.original.kt").write_bytes(original)
    try:
        shutil.copy2(Path(__file__).with_name(probe.name), probe)
        patched = original.decode().replace(anchor, anchor + "\n            DesktopPerformanceProbe { exitApplication() }")
        patched = patched.replace('            title = "XCalendar",',
                                  '            title = "XCalendar Benchmark",\n'
                                  '            state = androidx.compose.ui.window.rememberWindowState(width = androidx.compose.ui.unit.Dp(1100f), height = androidx.compose.ui.unit.Dp(800f)),')
        main_file.write_text(patched)
        with temporary_fixture(root, output / "fixture", paging=not args.year, year=args.year,
                               load_delay_ms=args.load_delay_ms, batch_delay_ms=args.batch_delay_ms,
                               refresh_delay_ms=args.refresh_delay_ms, gate_loading=True):
            # The built-in executable-JAR runner drops redirect dependencies in
            # Toolchain 0.13. Use this repository's complete-classpath packager.
            run_command([str(root / "kotlin"), "do", "packageDesktop", "-m", "desktopApp"], root)
            jars = list((root / "build/tasks").glob("*/distribution/XCalendar.jar"))
            if len(jars) != 1:
                raise RuntimeError("Expected one packaged desktop distribution")
            java_options = ["--enable-native-access=ALL-UNNAMED", f'-Duser.home={output / "home"}',
                            f'-Dxcalendar.benchmark.output={output}',
                            f'-Dxcalendar.benchmark.firstMonth={1 if args.year else 11}',
                            f'-XX:StartFlightRecording=filename={output / "profile.jfr"},settings=profile,dumponexit=true']
            if args.app_image:
                package = output / "bundle"
                command = [str(args.java.resolve().with_name("jpackage")), "--type", "app-image",
                           "--name", "XCalendarBenchmark", "--input", str(jars[0].parent),
                           "--main-jar", "XCalendar.jar", "--dest", str(package),
                           "--runtime-image", str(args.java.resolve().parent.parent)]
                for option in java_options:
                    command += ["--java-options", option]
                run_command(command, root)
                run_command([str(package / "XCalendarBenchmark.app/Contents/MacOS/XCalendarBenchmark")], root)
            else:
                run_command([str(args.java.resolve()), *java_options, "-jar", str(jars[0])], root)
        if not (output / "completed.txt").exists():
            raise RuntimeError("Desktop workload did not complete")
    finally:
        main_file.write_bytes(original)
        probe.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
