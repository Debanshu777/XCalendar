#!/usr/bin/env python3
"""Repeatable Android release workloads. Requires android CLI and adb.

Does not clear app data or change global device settings. Run on an unlocked,
idle device. UI targets come from the live accessibility hierarchy.
"""
import argparse
import hashlib
import json
import pathlib
import re
import statistics
import subprocess
import sys
import time

PACKAGE = "com.debanshu.xcalendar.benchmark"
ACTIVITY = PACKAGE + "/com.debanshu.xcalendar.MainActivity"


def file_sha256(path):
    checksum = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            checksum.update(block)
    return checksum.hexdigest()


def validated_fixture(path):
    """Validate the manifest against the retained data/source, before device work."""
    manifest = json.loads(path.read_text())
    data_path = path.with_name("fixture-data.json")
    source_path = path.with_name("PerformanceUiFixture.kt")
    if manifest.get("schema_version") != 1 or not str(manifest.get("scope", "")).startswith("UI-only"):
        raise ValueError("Expected a version 1 UI-only fixture manifest")
    if file_sha256(data_path) != manifest.get("data_sha256"):
        raise ValueError("Fixture data checksum does not match the manifest")
    if file_sha256(source_path) != manifest.get("source_sha256"):
        raise ValueError("Fixture Kotlin source checksum does not match the manifest")
    if "// Fixture data SHA-256: " + manifest["data_sha256"] not in source_path.read_text():
        raise ValueError("Fixture source was generated from different data")
    data = json.loads(data_path.read_text())
    for kind in ("event", "calendar", "account", "holiday"):
        count = manifest.get(kind + "_count")
        if type(count) is not int or count != len(data[kind + "s"]):
            raise ValueError(f"Fixture {kind} count does not match the data")
    if manifest["event_count"] <= 0 or manifest.get("selected_date") != data.get("selected_date"):
        raise ValueError("Fixture must have events and a matching selected date")
    return manifest


def visible_timed_fixture_event(nodes, width, height):
    """Slots 01–06 are daytime events; 09–10 are all-day and must not qualify."""
    grid = (width * .12, height * .15, width, height * .85)
    for node in nodes:
        if not re.search(r"November \d{2} / 0[1-6] — .*planning:", node.get("text", "")):
            continue
        bounds = list(map(int, re.findall(r"-?\d+", node.get("bounds", ""))))
        if len(bounds) == 4 and max(bounds[0], grid[0]) < min(bounds[2], grid[2]) and max(bounds[1], grid[1]) < min(bounds[3], grid[3]):
            return True
    return False


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--serial", required=True)
    p.add_argument("--output", required=True, type=pathlib.Path)
    p.add_argument("--scenario", choices=["startup", "month", "navigation", "schedule", "dropdown", "dialog", "populated"], required=True)
    p.add_argument("--iterations", type=int, help="Default: 10; populated defaults to one complete five-view cycle")
    p.add_argument("--recorder", type=pathlib.Path)
    p.add_argument("--compose-tracing", action="store_true")
    p.add_argument("--apk", type=pathlib.Path, help="Verify installed base APK SHA-256 against this signed artifact and record it")
    p.add_argument("--fixture-manifest", type=pathlib.Path, help="Validate and retain the generated UI fixture manifest/data/source checksums")
    args = p.parse_args()
    if args.iterations is None:
        args.iterations = 1 if args.scenario == "populated" else 10
    if args.iterations < 1:
        p.error("--iterations must be positive")
    if args.scenario == "populated" and not args.fixture_manifest:
        p.error("--scenario populated requires --fixture-manifest")
    fixture = validated_fixture(args.fixture_manifest) if args.fixture_manifest else None
    apk_checksum = file_sha256(args.apk) if args.apk else None
    args.output.mkdir(parents=True, exist_ok=True)
    if fixture:
        (args.output / "fixture-manifest.json").write_text(json.dumps(fixture, indent=2))

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

    if args.apk:
        package_paths = [line.removeprefix("package:").strip() for line in shell("pm", "path", PACKAGE).splitlines()
                         if line.startswith("package:")]
        base_apks = [path for path in package_paths if path.endswith("/base.apk")]
        if len(base_apks) != 1 or shell("sha256sum", base_apks[0]).split()[0] != apk_checksum:
            raise RuntimeError("Installed base APK differs from --apk; install the intended release before benchmarking")
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
                "compose_tracing": args.compose_tracing,
                "device_utc": shell("date", "-u", "+%Y-%m-%dT%H:%M:%SZ").strip(),
                "device_timezone": shell("getprop", "persist.sys.timezone").strip(),
                "apk_sha256": apk_checksum, "apk_verified": args.apk is not None,
                "fixture_data_sha256": fixture["data_sha256"] if fixture else None,
                "fixture_source_sha256": fixture["source_sha256"] if fixture else None,
                "fixture_counts": {kind: fixture[kind + "_count"] for kind in ("event", "calendar", "holiday", "account")} if fixture else None,
                "workload_completed": False}
    if args.scenario == "populated":
        metadata["workload"] = "One cycle: Day, 3 Day, Week each with one fixed upward swipe and visible 9 am plus daytime fixture-event bounds validation; Schedule upward swipe plus visible fixture-row validation; Month opposite swipes. Accessibility validation contributes overhead equally to both builds."
    (args.output / "metadata.json").write_text(json.dumps(metadata, indent=2))
    trace = None
    trace_log = None
    if args.recorder:
        config = pathlib.Path(__file__).with_name("android.pftxt").read_text()
        if args.scenario in ("dropdown", "populated"):
            # The baseline's height animation composes all month cells each
            # frame, producing more data than the other workloads.
            config = config.replace("size_kb: 131072", "size_kb: 262144")
        # Leave room for UI commands and verification as well as animations.
        iteration_duration = 45000 if args.scenario == "populated" else 6500 if args.scenario == "dialog" else 4500
        duration = max(30000, args.iterations * iteration_duration + 15000)
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
            elif args.scenario == "populated":
                for label in ("Day", "3 Day", "Week"):
                    tap(target(label, layout()))
                    time.sleep(1)
                    # About 5–6 hours at 60 dp/hour on the tested phone. Keep
                    # gestures fixed across builds; fail rather than adapt until
                    # a different amount of work happens to reach the target.
                    shell("input", "swipe", str(int(width * .6)), str(int(height * .75)),
                          str(int(width * .6)), str(int(height * .35)), "700")
                    time.sleep(.75)
                    working_hours = layout()
                    (args.output / f"populated-{i + 1:02}-{label.replace(' ', '-').lower()}.json").write_text(json.dumps(working_hours, indent=2))
                    labels = list(nodes(working_hours))
                    visible_nine = False
                    for node in labels:
                        if node.get("text") == "9 am":
                            bounds = list(map(int, re.findall(r"-?\d+", node.get("bounds", ""))))
                            if len(bounds) == 4 and height * .15 < bounds[1] < bounds[3] < height * .85:
                                visible_nine = True
                    if not visible_nine or not visible_timed_fixture_event(labels, width, height):
                        raise RuntimeError(f"{label} did not expose 9 am and a visible daytime fixture event; inspect saved populated layout before changing the fixed workload")
                tap(target("Schedule", layout()))
                time.sleep(1)
                shell("input", "swipe", str(width // 2), str(int(height * .7)), str(width // 2), str(int(height * .3)), "500")
                time.sleep(1)
                schedule_rows = layout()
                (args.output / f"populated-{i + 1:02}-schedule.json").write_text(json.dumps(schedule_rows, indent=2))
                if not visible_timed_fixture_event(list(nodes(schedule_rows)), width, height):
                    raise RuntimeError("Schedule did not expose a visible daytime fixture row; inspect its saved layout")
                tap(target("Month", layout()))
                time.sleep(1)
                for start, end in ((.8, .2), (.2, .8)):
                    shell("input", "swipe", str(int(width * start)), str(int(height * .5)),
                          str(int(width * end)), str(int(height * .5)), "400")
                    time.sleep(1)
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
        metadata["workload_completed"] = True
        metadata["device_utc_end"] = shell("date", "-u", "+%Y-%m-%dT%H:%M:%SZ").strip()
        (args.output / "metadata.json").write_text(json.dumps(metadata, indent=2))
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
