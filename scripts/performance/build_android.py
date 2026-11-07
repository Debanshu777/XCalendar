#!/usr/bin/env python3
"""Build an isolated release APK; optionally include Compose tracing tools.

Temporary build inputs are restored even if compilation fails. Do not run other
builds concurrently. Tracing dependencies are never part of the shipping app.
"""
import argparse
import pathlib
import shutil
import signal
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--tracing", action="store_true")
    args = parser.parse_args()
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
        subprocess.run([str(ROOT / "kotlin"), "build", "-m", "androidApp", "-p", "android", "-v", "release"], cwd=ROOT, check=True)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / "build/tasks/_androidApp_buildAndroidRelease/gradle-project-release-unsigned.apk", args.output)
    finally:
        for path, content in originals.items():
            path.write_bytes(content)


if __name__ == "__main__":
    main()
