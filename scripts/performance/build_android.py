#!/usr/bin/env python3
"""Build an isolated release APK; optionally include Compose tracing tools.

Temporary build inputs are restored even if compilation fails. Do not run other
builds concurrently. Tracing dependencies are never part of the shipping app.
"""
import argparse
from contextlib import nullcontext
import pathlib
import shutil
import signal

from fixture import run_command, temporary_fixture

ROOT = pathlib.Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--tracing", action="store_true")
    parser.add_argument("--fixture", action="store_true", help="Build with the deterministic 300-event UI fixture; never writes repository data")
    parser.add_argument("--paging", action="store_true", help="Use the 900-event adjacent-month fixture (requires --fixture)")
    parser.add_argument("--year", action="store_true", help="Use 3,650 events across 2026 (requires --fixture)")
    parser.add_argument("--load-delay-ms", type=int, default=0)
    parser.add_argument("--batch-delay-ms", type=int, default=0)
    parser.add_argument("--refresh-delay-ms", type=int, default=0)
    parser.add_argument("--fixture-output", type=pathlib.Path, help="Fixture artifacts/manifest directory (default: <APK stem>-fixture beside the APK)")
    args = parser.parse_args()
    if ((args.year or args.load_delay_ms or args.batch_delay_ms or args.refresh_delay_ms) and not args.fixture) or (args.year and args.paging):
        parser.error("Mock data/loading options require --fixture; choose --year or --paging")
    if min(args.load_delay_ms, args.batch_delay_ms, args.refresh_delay_ms) < 0:
        parser.error("Mock delays must be nonnegative")
    if args.paging and not args.fixture:
        parser.error("--paging requires --fixture")
    if args.fixture_output and not args.fixture:
        parser.error("--fixture-output requires --fixture")
    module = ROOT / "app/androidApp/module.yaml"
    manifest = ROOT / "app/androidApp/src/AndroidManifest.xml"
    originals = {p: p.read_bytes() for p in (module, manifest)}
    def interrupted(signum, frame):
        raise SystemExit(128 + signum)

    signal.signal(signal.SIGTERM, interrupted)
    try:
        if originals[module].decode().count("applicationId: com.debanshu.xcalendar\n") != 1:
            raise RuntimeError("Expected exactly one shipping applicationId; refusing to modify unknown inputs")
        if originals[manifest].decode().count("        <activity") < 1:
            raise RuntimeError("Expected an activity in the Android manifest")
        config = originals[module].decode().replace(
            "applicationId: com.debanshu.xcalendar\n",
            "applicationId: com.debanshu.xcalendar.benchmark\n",
        )
        if args.tracing:
            config = config.replace("dependencies:\n", "dependencies:\n"
                "  - androidx.compose.runtime:runtime-tracing:1.12.1\n"
                "  - androidx.tracing:tracing-perfetto:1.0.0\n"
                "  - androidx.tracing:tracing-perfetto-binary:1.0.0\n")
        module.write_text(config)
        manifest.write_text(originals[manifest].decode().replace(
            "        <activity", '        <profileable android:shell="true" />\n        <activity', 1))
        fixture_context = temporary_fixture(
            ROOT, args.fixture_output or args.output.parent / (args.output.stem + "-fixture"), paging=args.paging,
            year=args.year, load_delay_ms=args.load_delay_ms, batch_delay_ms=args.batch_delay_ms,
            refresh_delay_ms=args.refresh_delay_ms
        ) if args.fixture else nullcontext()
        with fixture_context:
            run_command([str(ROOT / "kotlin"), "build", "-m", "androidApp", "-p", "android", "-v", "release"], cwd=ROOT)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / "build/tasks/_androidApp_buildAndroidRelease/gradle-project-release-unsigned.apk", args.output)
    finally:
        for path, content in originals.items():
            path.write_bytes(content)


if __name__ == "__main__":
    main()
