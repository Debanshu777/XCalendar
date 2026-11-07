#!/usr/bin/env python3
"""Repeatable Android release workloads. Requires android CLI and adb.

Does not clear app data or change global device settings. Run on an unlocked,
idle device. UI targets come from the live accessibility hierarchy.
"""
import argparse
import json
import pathlib
import re
import statistics
import subprocess
import sys
import time

PACKAGE = "com.debanshu.xcalendar.benchmark"
ACTIVITY = PACKAGE + "/com.debanshu.xcalendar.MainActivity"


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--serial", required=True)
    p.add_argument("--output", required=True, type=pathlib.Path)
    p.add_argument("--scenario", choices=["startup", "month", "navigation", "schedule", "dropdown", "dialog"], required=True)
    p.add_argument("--iterations", type=int, default=10)
    p.add_argument("--recorder", type=pathlib.Path)
    p.add_argument("--compose-tracing", action="store_true")
    args = p.parse_args()
    if args.iterations < 1:
        p.error("--iterations must be positive")
    args.output.mkdir(parents=True, exist_ok=True)

    def run(*cmd):
        return subprocess.check_output(list(map(str, cmd)), text=True, stderr=subprocess.STDOUT)

    def adb(*cmd):
        return run("adb", "-s", args.serial, *cmd)

    def shell(*cmd):
        return adb("shell", *cmd)

    def nodes(value):
        if isinstance(value, dict):
            yield value
            for child in value.values():
                yield from nodes(child)
        elif isinstance(value, list):
            for child in value:
                yield from nodes(child)

    def layout():
        return json.loads(run("android", "layout", "--device=" + args.serial))

    def target(label, tree):
        matches = [n for n in nodes(tree) if n.get("content-desc") == label or n.get("text") == label]
        if not matches:
            raise RuntimeError("UI target missing: " + label)
        return re.findall(r"\d+", matches[0]["center"])

    def tap(point):
        shell("input", "tap", *point)

    shell("am", "force-stop", PACKAGE)
    shell("am", "start", "-W", "-n", ACTIVITY)
    time.sleep(3)
    tree = layout()
    points = {label: target(label, tree) for label in ["Month", "Week", "3 Day", "Day", "Schedule", "Add Event"]}
    if args.scenario == "dropdown":
        points["dropdown"] = target("Toggle Month Dropdown", tree)
    app = next(n for n in nodes(tree) if n.get("type") == "APPLICATION" and n.get("active"))
    _, _, width, height = map(int, re.findall(r"\d+", app["bounds"]))
    if args.scenario == "schedule":
        tap(points["Schedule"])
        time.sleep(2)
    if args.compose_tracing:
        result = shell("am", "broadcast", "-a", "androidx.tracing.perfetto.action.ENABLE_TRACING",
                       PACKAGE + "/androidx.tracing.perfetto.TracingReceiver")
        (args.output / "tracing-enable.txt").write_text(result)
        if '"exitCode":1' not in result and '"exitCode":2' not in result:
            raise RuntimeError("Compose tracing was not enabled: " + result)
    metadata = {"scenario": args.scenario, "iterations": args.iterations,
                "model": shell("getprop", "ro.product.model").strip(),
                "android": shell("getprop", "ro.build.version.release").strip(),
                "battery": shell("dumpsys", "battery"),
                "thermal": shell("dumpsys", "thermalservice"),
                "compose_tracing": args.compose_tracing}
    (args.output / "metadata.json").write_text(json.dumps(metadata, indent=2))
    trace = None
    trace_log = None
    if args.recorder:
        config = pathlib.Path(__file__).with_name("android.pftxt").read_text()
        if args.scenario == "dropdown":
            # The baseline's height animation composes all month cells each
            # frame, producing more data than the other workloads.
            config = config.replace("size_kb: 131072", "size_kb: 262144")
        # Leave room for UI commands and verification as well as animations.
        duration = max(30000, args.iterations * (6500 if args.scenario == "dialog" else 4500) + 15000)
        config = config.replace("duration_ms: 60000", f"duration_ms: {duration}")
        if args.compose_tracing:
            # AndroidX's SDK producer is named after the package, independent of
            # the current PID. Keep other apps' track_event producers disabled.
            source = 'data_sources {\n  config {\n    name: "track_event"'
            if config.count(source) != 1:
                raise RuntimeError("Expected one track_event source in android.pftxt")
            config = config.replace(
                source,
                'data_sources {\n  producer_name_filter: ' + json.dumps(PACKAGE)
                + '\n  config {\n    name: "track_event"',
            )
            # The service buffer and producer shared memory are separate. Dense
            # Compose bursts can exhaust the default producer buffer even when
            # the 128 MiB service buffer has room; request a larger producer SMB.
            config += ('\nproducers {\n  producer_name: ' + json.dumps(PACKAGE)
                       + '\n  shm_size_kb: 8192\n  page_size_kb: 32\n}\n')
        config_path = args.output / "trace-config.pftxt"
        config_path.write_text(config)
        trace_log = (args.output / "recording.log").open("w")
        trace = subprocess.Popen([sys.executable, str(args.recorder), "--serial", args.serial, "-n", "-c", str(config_path),
                                  "-o", str(args.output / "trace.perfetto-trace")], stdout=trace_log, stderr=subprocess.STDOUT)
        time.sleep(3)
        if trace.poll() is not None:
            raise RuntimeError("Trace recorder exited early; inspect recording.log")
    shell("dumpsys", "gfxinfo", PACKAGE, "reset")
    samples = []
    try:
        for i in range(args.iterations):
            if args.scenario == "startup":
                result = shell("am", "start", "-S", "-W", "-n", ACTIVITY)
                (args.output / f"startup-{i:02}.txt").write_text(result)
                if "Status: ok" not in result or "LaunchState: COLD" not in result:
                    raise RuntimeError("Expected cold process start: " + result)
                samples.append(int(re.search(r"TotalTime: (\d+)", result)[1]))
                time.sleep(2)
            elif args.scenario == "navigation":
                tap(points[["Week", "3 Day", "Day", "Schedule", "Month"][i % 5]])
                time.sleep(2)
            elif args.scenario in ("month", "schedule"):
                if args.scenario == "month":
                    a, b = (int(width * .8), int(width * .2)) if i % 2 == 0 else (int(width * .2), int(width * .8))
                    shell("input", "swipe", str(a), str(int(height * .5)), str(b), str(int(height * .5)), "400")
                else:
                    shell("input", "swipe", str(width // 2), str(int(height * .7)), str(width // 2), str(int(height * .3)), "500")
                time.sleep(1)
            elif args.scenario == "dropdown":
                tap(points["dropdown"])
                time.sleep(1)
            else:
                tap(points["Add Event"])
                time.sleep(1)
                dialog = layout()
                if not any(n.get("class") == "android.widget.EditText" for n in nodes(dialog)):
                    raise RuntimeError("Add event dialog did not open")
                shell("input", "keyevent", "4")
                time.sleep(1)
            if not shell("pidof", PACKAGE).strip():
                raise RuntimeError("App process exited during workload")
            print(f"{args.scenario} {i + 1}/{args.iterations}", flush=True)
        (args.output / "gfxinfo.txt").write_text(shell("dumpsys", "gfxinfo", PACKAGE, "framestats"))
        (args.output / "memory.txt").write_text(shell("dumpsys", "meminfo", PACKAGE))
        (args.output / "final-layout.json").write_text(json.dumps(layout(), indent=2))
        if samples:
            summary = {"cold_process_start_ms": samples, "median_ms": statistics.median(samples),
                       "min_ms": min(samples), "max_ms": max(samples)}
            (args.output / "summary.json").write_text(json.dumps(summary, indent=2))
            print(json.dumps(summary), flush=True)
    finally:
        if trace is not None:
            trace.wait(timeout=180)
            trace_log.close()
            if trace.returncode:
                raise RuntimeError("Trace recording failed")


if __name__ == "__main__":
    main()
