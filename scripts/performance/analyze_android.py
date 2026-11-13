#!/usr/bin/env python3
"""Analyze Android traces, summarize one build, or compare repeated before/after runs.

Reports retain workload, trace-health and matching-condition validation.
Frame percentiles summarize individual runs, not pooled distributions or FPS.
"""
import argparse
import csv
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import statistics
import subprocess
import sys

PACKAGE = "com.debanshu.xcalendar.benchmark"
INFORMATIONAL_SCHEDULER_SKIPS = {"compact_sched_switch_skipped", "compact_sched_waking_skipped"}
COMPOSABLES = {
    "calendar_app": r"com\.debanshu\.xcalendar\.CalendarApp \(",
    "calendar_app_content": r"com\.debanshu\.xcalendar\.CalendarApp\.<anonymous> \(",
    "navigation_host": r"com\.debanshu\.xcalendar\.ui\.navigation\.NavigationHost \(",
    "month_day_cell": r"com\.debanshu\.xcalendar\.ui\.screen\.monthScreen\.components\.DayCell \(",
    "topbar_day_cell": r"com\.debanshu\.xcalendar\.ui\.components\.TopAppBarDayCell \(",
    "bottom_navigation": r"com\.debanshu\.xcalendar\.ui\.components\.CalendarBottomNavigationBar \(",
    "nav_bar_surface": r"com\.debanshu\.xcalendar\.ui\.components\.NavBarSurface \(",
    "compose_recompose": r"Compose:recompose$",
    "recomposer_recompose": r"Recomposer:recompose$",
}


def csv_results(path, columns):
    """Read one complete CSV result, rejecting unknown/truncated formats."""
    text = path.read_text(encoding="utf-8")
    if not text.endswith("\n"):
        raise ValueError(f"Possibly incomplete result: {path}")
    # Older manual exports appended trace_processor diagnostics after the table.
    lines = [line for line in text.splitlines() if line.strip() and
             not line.startswith("column ") and not re.match(r"^\[\d", line)]
    reader = csv.DictReader(lines)
    if not reader.fieldnames or not set(columns).issubset(reader.fieldnames):
        raise ValueError(f"Unexpected CSV schema: {path}")
    rows = list(reader)
    if any(None in row or any(value is None for value in row.values()) for row in rows):
        raise ValueError(f"Truncated/malformed CSV row: {path}")
    return rows


def number(value):
    return float(value) if "." in str(value) else int(value)


def first_match(pattern, text, convert=str):
    match = re.search(pattern, text, re.MULTILINE)
    return convert(match[1]) if match else None


def health_result(rows):
    counters = [{**row, "value": number(row["value"])} for row in rows]
    nonzero = [row for row in counters if row["value"] != 0]
    informational = [row for row in nonzero if row["severity"] == "info" and
                     row["name"] in INFORMATIONAL_SCHEDULER_SKIPS]
    rejected = [row for row in nonzero if row not in informational]
    return {"accepted": not rejected, "rejected_counters": rejected,
            "informational_scheduler_skips": informational, "all_reported_counters": counters}


def conditions(metadata, log):
    battery, thermal = metadata.get("battery", ""), metadata.get("thermal", "")
    current_hal = thermal.split("Current temperatures from HAL:")[-1].split("Current cooling devices from HAL:")[0]
    sensor_status = [{"name": name, "status": int(status)} for name, status in
                     re.findall(r"mName=([^,}]+), mStatus=(\d+)", current_hal) if int(status) != 0]
    return {
        "model": metadata.get("model"), "android": metadata.get("android"),
        "scenario": metadata.get("scenario"), "iterations": metadata.get("iterations"),
        "compose_tracing": metadata.get("compose_tracing"),
        "battery_temperature_c": first_match(r"^\s*temperature:\s*(\d+)", battery, lambda v: int(v) / 10),
        "battery_level": first_match(r"^\s*level:\s*(\d+)", battery, int),
        "power": {key: first_match(r"^\s*" + key + r" powered:\s*(true|false)", battery, lambda v: v == "true")
                  for key in ["AC", "USB", "Wireless"]},
        "thermal_status": first_match(r"^Thermal Status:\s*(\d+)", thermal, int),
        "nonzero_current_hal_sensor_status": sensor_status,
        "device_utc": metadata.get("device_utc", metadata.get("device_time_utc")),
        "device_timezone": metadata.get("timezone", metadata.get("device_timezone")),
        "recording_filename_time_unspecified_zone": first_match(r"perfetto-traces/(\d{4}-\d{2}-\d{2}_\d{2}-\d{2})", log),
        "apk_sha256": metadata.get("apk_sha256"),
        "fixture_data_sha256": metadata.get("fixture_data_sha256"),
    }


def read_run(directory, side, index, package=PACKAGE):
    result = {"side": side, "index": index, "path": str(directory.resolve()),
              "status": "pending", "accepted": False, "warnings": []}
    required = ["recording.log", "trace.perfetto-trace", "trace-config.pftxt", "metadata.json"]
    required += ["analysis/" + name + ".txt" for name in ["health", "frames", "composition", "cpu"]]
    missing = [name for name in required if not (directory / name).is_file()]
    if missing:
        result["pending_files"] = missing
        return result
    log = (directory / "recording.log").read_text()
    recorded_bytes = first_match(r"Wrote (\d+) bytes", log, int)
    trace = directory / "trace.perfetto-trace"
    if recorded_bytes is None or "1 file pulled" not in log or trace.stat().st_size != recorded_bytes:
        result["pending_reason"] = "Recording/pull completion and byte count are not confirmed"
        return result
    analysis = directory / "analysis"
    if any((analysis / (name + ".txt")).stat().st_mtime < trace.stat().st_mtime
           for name in ["health", "frames", "composition", "cpu"]):
        result["pending_reason"] = "Analysis predates the current trace"
        return result
    try:
        metadata = json.loads((directory / "metadata.json").read_text())
        if metadata.get("workload_completed") is False:
            result["pending_reason"] = "Runner did not confirm completion of the workload"
            return result
        health = health_result(csv_results(analysis / "health.txt", ["name", "severity", "value"]))
        frame_rows = csv_results(analysis / "frames.txt", ["frames", "self_jank", "app_deadline_misses", "p95_ms", "p99_ms"])
        if len(frame_rows) != 1:
            raise ValueError("Expected exactly one frame summary")
        frames = {key: number(value) for key, value in frame_rows[0].items()}
        if frames["frames"] <= 0 or any(frames[key] > frames["frames"] or frames[key] < 0
                                       for key in ["self_jank", "app_deadline_misses"]):
            raise ValueError("Invalid frame counts")
        frames["self_jank_pct"] = 100 * frames["self_jank"] / frames["frames"]
        frames["app_deadline_miss_pct"] = 100 * frames["app_deadline_misses"] / frames["frames"]
        composition_rows = csv_results(analysis / "composition.txt", ["name", "executions", "inclusive_ms", "max_ms"])
        if not composition_rows or metadata.get("compose_tracing") is not True:
            raise ValueError("Named composition comparison requires confirmed Compose tracing and slice results")
        composition = {}
        for key, pattern in COMPOSABLES.items():
            matched = [row for row in composition_rows if re.match(pattern, row["name"])]
            composition[key] = {
                "executions": sum(int(row["executions"]) for row in matched),
                "inclusive_ms": sum(float(row["inclusive_ms"]) for row in matched),
                "max_ms": max((float(row["max_ms"]) for row in matched), default=0),
                "matched_names": [row["name"] for row in matched],
            }
        cpu_rows = csv_results(analysis / "cpu.txt", ["name", "running_cpu_ms"])
        explicit_main = "is_main_thread" in (cpu_rows[0] if cpu_rows else {})
        main = [row for row in cpu_rows if row.get("is_main_thread") == "1"] if explicit_main else [
            row for row in cpu_rows if row["name"] in (package, package[-15:])]
        render = [row for row in cpu_rows if row["name"] == "RenderThread"]
        if len(main) != 1 or not render:
            raise ValueError("Main/RenderThread CPU cannot be identified from saved results")
        cpu = {"main_ms": float(main[0]["running_cpu_ms"]),
               "render_ms": sum(float(row["running_cpu_ms"]) for row in render),
               "main_identification": "is_main_thread" if explicit_main else "inferred_from_exact_package_or_15_character_thread_name",
               "main_thread_name": main[0]["name"], "reported_thread_rows": cpu_rows}
        result.update({"health": health, "frames": frames, "composition": composition, "cpu": cpu,
                       "conditions": conditions(metadata, log), "trace_bytes": recorded_bytes,
                       "config_sha256": hashlib.sha256((directory / "trace-config.pftxt").read_bytes()).hexdigest(),
                       "status": "accepted" if health["accepted"] else "rejected_health",
                       "accepted": health["accepted"]})
        if not explicit_main:
            result["warnings"].append("Legacy CPU query lacks is_main_thread; main CPU identification uses the benchmark thread name.")
        if not result["conditions"]["device_utc"] or not result["conditions"]["device_timezone"]:
            result["warnings"].append("Device wall-clock/time zone were not both recorded; filename time has no asserted time zone.")
        if result["conditions"]["thermal_status"] not in (None, 0):
            result["warnings"].append("Nonzero global thermal status; compare device conditions before attributing a timing change.")
        gfx = directory / "gfxinfo.txt"
        if gfx.exists():
            text = gfx.read_text()
            result["gfxinfo"] = {
                "frames": first_match(r"^Total frames rendered:\s*(\d+)", text, int),
                "janky": first_match(r"^Janky frames:\s*(\d+)", text, int),
                "p95_ms": first_match(r"^95th percentile:\s*(\d+)ms", text, int),
                "p99_ms": first_match(r"^99th percentile:\s*(\d+)ms", text, int),
                "stats_since_monotonic_ns": first_match(r"^Stats since:\s*(\d+)ns", text, int),
            }
    except (ValueError, KeyError, TypeError) as error:
        result.update({"status": "invalid_analysis", "reason": str(error)})
    return result


def metric_values(run):
    values = {key: run["frames"][key] for key in ["frames", "p50_ms", "p95_ms", "p99_ms", "max_ms", "self_jank_pct", "app_deadline_miss_pct"]}
    values.update({"main_cpu_ms": run["cpu"]["main_ms"], "render_cpu_ms": run["cpu"]["render_ms"]})
    values.update({key + "_executions": value["executions"] for key, value in run["composition"].items()})
    return values


def describe(values):
    return {"n": len(values), "values": values, "median": statistics.median(values),
            "min": min(values), "max": max(values)} if values else {"n": 0, "values": []}


def recorded_capture_order(runs):
    """Report observed chronology; directory grouping does not imply run order."""
    entries = []
    for run in runs:
        try:
            timestamp = datetime.fromisoformat(run["conditions"]["device_utc"].replace("Z", "+00:00"))
            if timestamp.utcoffset() is None:
                raise ValueError("Missing time zone")
        except (KeyError, AttributeError, TypeError, ValueError):
            return "Unknown: some accepted runs lack a valid device UTC start timestamp."
        entries.append((timestamp, f'{run["side"]}-{run["index"]}'))
    if not entries or len({timestamp for timestamp, _ in entries}) != len(entries):
        return "Unknown: accepted run start timestamps are missing or ambiguous."
    return "Recorded device UTC start order: " + ", ".join(label for _, label in sorted(entries)) + ". Randomization is not recorded."


def compare(runs, expected_runs):
    groups = {side: [run for run in runs if run["side"] == side and run["accepted"]] for side in ["before", "after"]}
    metric_names = sorted({key for run in groups["before"] + groups["after"] for key in metric_values(run)})
    summaries = {}
    for metric in metric_names:
        row = {side: describe([metric_values(run)[metric] for run in group]) for side, group in groups.items()}
        if all(row[side]["n"] for side in ["before", "after"]):
            before, after = row["before"]["median"], row["after"]["median"]
            row.update({"median_delta": after - before, "median_delta_pct": 100 * (after - before) / before if before else None})
        summaries[metric] = row
    accepted = groups["before"] + groups["after"]
    mismatches = [key for key in ["model", "android", "scenario", "iterations", "compose_tracing", "fixture_data_sha256"]
                  if len({json.dumps(run["conditions"][key], sort_keys=True) for run in accepted}) > 1]
    if len({run["config_sha256"] for run in accepted}) > 1:
        mismatches.append("trace_config")
    known_zones = {run["conditions"]["device_timezone"] for run in accepted if run["conditions"]["device_timezone"]}
    if len(known_zones) > 1:
        mismatches.append("device_timezone")
    missing_zones = sum(not run["conditions"]["device_timezone"] for run in accepted)
    return {"expected_runs_per_group": expected_runs,
            "capture_order": recorded_capture_order(accepted),
            "ready": all(len(group) == expected_runs for group in groups.values()) and not mismatches,
            "accepted_runs": {side: len(group) for side, group in groups.items()},
            "mismatched_recorded_conditions": mismatches,
            "runs_without_recorded_timezone": missing_zones,
            "metrics": summaries}



def rows(path):
    with path.open() as stream:
        return list(csv.DictReader(stream))


def summarize(root):
    runs = []
    for path in sorted(root.glob("*/analysis/frames.txt")):
        run = path.parent.parent
        metadata = json.loads((run / "metadata.json").read_text())
        if not metadata.get("workload_completed") or not metadata.get("apk_verified"):
            raise ValueError(f"Incomplete or unverified capture: {run.name}")
        health = rows(path.with_name("health.txt"))
        permitted = {"compact_sched_switch_skipped", "compact_sched_waking_skipped"}
        if any(float(r["value"]) != 0 and not (r["name"] in permitted and r["severity"] == "info") for r in health):
            raise ValueError(f"Trace health failed: {run.name}")
        frame_rows = rows(path)
        deadline_rows = rows(path.with_name("deadlines.txt"))
        if len(frame_rows) != 1 or len(deadline_rows) != 1:
            raise ValueError(f"Expected one frame/deadline summary: {run.name}")
        frames = {k: float(v) for k, v in frame_rows[0].items()}
        deadlines = {k: float(v) for k, v in deadline_rows[0].items()}
        if frames["frames"] <= 0 or deadlines["unmatched_or_ambiguous"] != 0 or deadlines["matched_frames"] != frames["frames"]:
            raise ValueError(f"Missing/ambiguous frame deadlines: {run.name}")
        frames["self_jank_pct"] = 100 * frames["self_jank"] / frames["frames"]
        frames["late_frame_pct"] = 100 * deadlines["late_frames"] / deadlines["matched_frames"]
        phases = metadata.get("mock_phases", [])
        if phases:
            bounds = rows(path.with_name("bounds.txt"))
            if len(bounds) != 1:
                raise ValueError(f"Missing trace-clock bounds: {run.name}")
            start = float(bounds[0]["start_epoch_seconds"])
            end = float(bounds[0]["end_epoch_seconds"])
            if not all(start <= phase["epoch_seconds"] <= end for phase in phases):
                raise ValueError(f"Mock delivery outside recorded interval: {run.name}")
        scenario = metadata["scenario"] + ("-forward" if metadata.get("paging_pattern") == "forward" else "")
        with (run / "trace.perfetto-trace").open("rb") as stream:
            trace_hash = hashlib.file_digest(stream, "sha256").hexdigest()
        runs.append({"run": run.name, "scenario": scenario,
                     "apk_sha256": metadata["apk_sha256"], "fixture_sha256": metadata["fixture_data_sha256"],
                     "trace_sha256": trace_hash,
                     "conditions": {key: metadata[key] for key in
                                    ("iterations", "compose_tracing", "device_timezone", "font_scale", "model", "android",
                                     "paging_pattern", "mock_phases", "swipe_intervals",
                                     "device_utc", "device_utc_end", "recording_ready_device_utc")
                                    if key in metadata},
                     "frames": frames, "deadlines": deadlines})
    if not runs:
        raise ValueError("No completed traces")
    if len({(r["apk_sha256"], r["fixture_sha256"]) for r in runs}) != 1:
        raise ValueError("Root contains mixed binaries or fixtures; summarize each build separately")
    groups = {}
    for scenario in sorted({r["scenario"] for r in runs}):
        group = [r for r in runs if r["scenario"] == scenario]
        groups[scenario] = {"runs": len(group)}
        for metric in ("p95_ms", "p99_ms", "max_ms", "self_jank_pct", "late_frame_pct"):
            values = [r["frames"][metric] for r in group]
            groups[scenario][metric] = {"median": statistics.median(values), "min": min(values), "max": max(values)}
    return {"scope": "UI fixture; per-run summaries, not pooled distributions or presented FPS", "groups": groups, "runs": runs}


def analyze_trace(args):
    args.output.mkdir(parents=True, exist_ok=True)
    processor = args.processor.resolve()
    with processor.open('rb') as source:
        header = source.read(200)
    command = [sys.executable, str(processor)] if b'python' in header.split(b'\n')[0] else [str(processor)]
    queries = Path(__file__).with_name('android_queries')
    for name in ('health', 'bounds', 'frames', 'deadlines', 'composition', 'cpu', 'page_work'):
        with (args.output / (name + '.txt')).open('w') as output:
            subprocess.run(command + ['-q', str(queries / (name + '.sql')), str(args.trace)], stdout=output, check=True)


def collect_comparison(args, parser):
    if args.runs < 1:
        parser.error("--runs must be positive")
    runs = []
    for side, prefix in [("before", args.before_prefix), ("after", args.after_prefix)]:
        prefix = prefix or side + "-" + args.scenario
        runs.extend(read_run(args.root / f"{prefix}-{i}", side, i, args.package) for i in range(1, args.runs + 1))
    result = {"schema_version": 1, "scenario": args.scenario, "package": args.package,
              "collected_at_utc": datetime.now(timezone.utc).isoformat(),
              "notes": ["Actual FrameTimeline slice durations/tags; gfxinfo metrics have a separate definition.",
                        "Composition counts include initial composition; nested inclusive times overlap.",
                        "CPU totals cover the entire trace, including idle time and tracing overhead.",
                        "Median/range describe per-run metrics, not a pooled frame distribution or confidence interval.",
                        "Capture order is inferred from recorded device UTC timestamps; repetitions do not eliminate thermal or order effects.",
                        "Nonzero info compact scheduler skips are disclosed separately; other reported nonzero health counters reject the run.",
                        "A ready comparison requires complete accepted runs and matching recorded configuration/conditions; unrecorded wall-clock/timezone remain limitations."],
              "runs": runs, "comparison": compare(runs, args.runs)}
    output = args.output or args.root / (args.scenario + "-comparison.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({"output": str(output), "ready": result["comparison"]["ready"],
                      "accepted_runs": result["comparison"]["accepted_runs"],
                      "statuses": {Path(run["path"]).name: run["status"] for run in runs}}, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    trace = commands.add_parser("trace", help="Run Perfetto queries on a trace")
    trace.add_argument("--processor", type=Path, required=True)
    trace.add_argument("--trace", type=Path, required=True)
    trace.add_argument("--output", type=Path, required=True)
    summary = commands.add_parser("summary", help="Validate and summarize captures for one build")
    summary.add_argument("--root", type=Path, required=True)
    summary.add_argument("--output", type=Path, required=True)
    comparison = commands.add_parser("compare", help="Compare repeated before/after captures")
    comparison.add_argument("--root", type=Path, required=True)
    comparison.add_argument("--scenario", default="dropdown")
    comparison.add_argument("--runs", type=int, default=3, help="Repetitions per build; capture order is read from device timestamps")
    comparison.add_argument("--before-prefix", help="Default: before-<scenario>; appends -1, -2, etc.")
    comparison.add_argument("--after-prefix", help="Default: after-<scenario>; appends -1, -2, etc.")
    comparison.add_argument("--package", default=PACKAGE)
    comparison.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.command == "trace":
        analyze_trace(args)
    elif args.command == "summary":
        args.output.write_text(json.dumps(summarize(args.root), indent=2) + "\n")
    else:
        collect_comparison(args, comparison)


if __name__ == "__main__":
    main()
