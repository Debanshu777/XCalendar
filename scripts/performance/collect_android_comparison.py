#!/usr/bin/env python3
"""Collect repeated Android runs from saved analyze_android.py results only.

Example:
  python3 scripts/performance/collect_android_comparison.py --root captures/performance/council/round-1 --scenario dropdown --runs 3

No device access, builds, trace loading, or polling. Missing/incomplete runs stay
pending. Lossy runs remain in the JSON but never enter accepted-run summaries.
Frame percentiles are summarized across runs; they are not pooled percentiles.
"""

import argparse
import csv
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import statistics

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
            "capture_order": "All before repetitions, then all after repetitions; not interleaved or randomized pairs.",
            "ready": all(len(group) == expected_runs for group in groups.values()) and not mismatches,
            "accepted_runs": {side: len(group) for side, group in groups.items()},
            "mismatched_recorded_conditions": mismatches,
            "runs_without_recorded_timezone": missing_zones,
            "metrics": summaries}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--scenario", default="dropdown")
    parser.add_argument("--runs", type=int, default=3, help="Repetitions per build group; these are not interleaved pairs")
    parser.add_argument("--before-prefix", help="Default: before-<scenario>; appends -1, -2, etc.")
    parser.add_argument("--after-prefix", help="Default: after-<scenario>; appends -1, -2, etc.")
    parser.add_argument("--package", default=PACKAGE)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
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
                        "The before and after repetitions are groups; all-before/all-after ordering does not eliminate thermal or order effects.",
                        "Nonzero info compact scheduler skips are disclosed separately; other reported nonzero health counters reject the run.",
                        "A ready comparison requires complete accepted runs and matching recorded configuration/conditions; unrecorded wall-clock/timezone remain limitations."],
              "runs": runs, "comparison": compare(runs, args.runs)}
    output = args.output or args.root / (args.scenario + "-comparison.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({"output": str(output), "ready": result["comparison"]["ready"],
                      "accepted_runs": result["comparison"]["accepted_runs"],
                      "statuses": {Path(run["path"]).name: run["status"] for run in runs}}, indent=2))


if __name__ == "__main__":
    main()
