#!/usr/bin/env python3
"""Generate an XCTest runner under ignored captures/ and benchmark an installed app.

Build/install XCalendar through ./kotlin first. This is a test runner only;
the application remains entirely managed by the pinned Kotlin Toolchain.
"""
import argparse
import hashlib
import json
import pathlib
import plistlib
import shutil
import subprocess


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--device", required=True)
    p.add_argument("--output", type=pathlib.Path, required=True)
    p.add_argument("--only", choices=["testColdLaunch", "testCalendarJourney", "testEventDialog", "testMonthRoundTrip", "testProfileJourney"])
    p.add_argument("--build-only", action="store_true")
    p.add_argument("--trace", action="store_true", help="Attach Time Profiler during testProfileJourney")
    p.add_argument("--expected-app", type=pathlib.Path, help="Verify the installed executable matches this release .app")
    args = p.parse_args()
    if args.trace and (args.only != "testProfileJourney" or args.build_only):
        p.error("--trace requires --only testProfileJourney and cannot use --build-only")
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    if args.expected_app:
        info = plistlib.loads((args.expected_app / "Info.plist").read_bytes())
        bundle = info["CFBundleIdentifier"]
        if bundle != "com.debanshu.xcalendar.XCalendar":
            p.error("The expected app does not match the XCTest target bundle")
        installed = pathlib.Path(subprocess.check_output(
            ["xcrun", "simctl", "get_app_container", args.device, bundle, "app"], text=True,
        ).strip())
        executable = info["CFBundleExecutable"]
        def digest(path):
            with path.open("rb") as source:
                checksum = hashlib.sha256()
                for block in iter(lambda: source.read(1024 * 1024), b""):
                    checksum.update(block)
                return checksum.hexdigest()
        expected_hash = digest(args.expected_app / executable)
        if digest(installed / executable) != expected_hash:
            raise RuntimeError("Installed app differs from --expected-app; install the intended release first")
        (output / "app-build.json").write_text(json.dumps({"bundle_id": bundle, "executable_sha256": expected_hash}, indent=2))
    project = output / "CalendarBenchmarks.xcodeproj"
    project.mkdir(exist_ok=True)
    shutil.copy2(pathlib.Path(__file__).with_name("CalendarBenchmarks.swift"), output)
    (project / "project.pbxproj").write_text('''// !$*UTF8*$!
{ archiveVersion = 1; classes = {}; objectVersion = 56; objects = {
  A00000000000000000000001 = {isa = PBXProject; buildConfigurationList = A00000000000000000000002; compatibilityVersion = "Xcode 14.0"; developmentRegion = en; mainGroup = A00000000000000000000003; productRefGroup = A00000000000000000000004; projectDirPath = ""; targets = (A00000000000000000000005,); };
  A00000000000000000000002 = {isa = XCConfigurationList; buildConfigurations = (A00000000000000000000006,); defaultConfigurationIsVisible = 0; defaultConfigurationName = Release; };
  A00000000000000000000003 = {isa = PBXGroup; children = (A00000000000000000000007,A00000000000000000000004,); sourceTree = "<group>"; };
  A00000000000000000000004 = {isa = PBXGroup; children = (A00000000000000000000008,); name = Products; sourceTree = "<group>"; };
  A00000000000000000000005 = {isa = PBXNativeTarget; buildConfigurationList = A00000000000000000000009; buildPhases = (A0000000000000000000000A,A0000000000000000000000B,); buildRules = (); dependencies = (); name = CalendarBenchmarks; productName = CalendarBenchmarks; productReference = A00000000000000000000008; productType = "com.apple.product-type.bundle.ui-testing"; };
  A00000000000000000000006 = {isa = XCBuildConfiguration; name = Release; buildSettings = {SDKROOT = iphoneos; IPHONEOS_DEPLOYMENT_TARGET = 16.0; SWIFT_VERSION = 5.0; CLANG_ENABLE_MODULES = YES; }; };
  A00000000000000000000007 = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = CalendarBenchmarks.swift; sourceTree = "<group>"; };
  A00000000000000000000008 = {isa = PBXFileReference; explicitFileType = wrapper.cfbundle; includeInIndex = 0; path = CalendarBenchmarks.xctest; sourceTree = BUILT_PRODUCTS_DIR; };
  A00000000000000000000009 = {isa = XCConfigurationList; buildConfigurations = (A0000000000000000000000C,); defaultConfigurationIsVisible = 0; defaultConfigurationName = Release; };
  A0000000000000000000000A = {isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = (A0000000000000000000000D,); runOnlyForDeploymentPostprocessing = 0; };
  A0000000000000000000000B = {isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; files = (); runOnlyForDeploymentPostprocessing = 0; };
  A0000000000000000000000C = {isa = XCBuildConfiguration; name = Release; buildSettings = {PRODUCT_BUNDLE_IDENTIFIER = com.debanshu.xcalendar.benchmarks; PRODUCT_NAME = "$(TARGET_NAME)"; GENERATE_INFOPLIST_FILE = YES; CODE_SIGNING_ALLOWED = NO; TARGETED_DEVICE_FAMILY = "1,2"; SWIFT_VERSION = 5.0; SWIFT_OPTIMIZATION_LEVEL = "-O"; }; };
  A0000000000000000000000D = {isa = PBXBuildFile; fileRef = A00000000000000000000007; };
}; rootObject = A00000000000000000000001; }
''')
    schemes = project / "xcshareddata/xcschemes"
    schemes.mkdir(parents=True, exist_ok=True)
    (schemes / "CalendarBenchmarks.xcscheme").write_text('''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
  <BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="NO" buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="YES"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="A00000000000000000000005" BuildableName="CalendarBenchmarks.xctest" BlueprintName="CalendarBenchmarks" ReferencedContainer="container:CalendarBenchmarks.xcodeproj"/></BuildActionEntry></BuildActionEntries></BuildAction>
  <TestAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES"><Testables><TestableReference skipped="NO"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="A00000000000000000000005" BuildableName="CalendarBenchmarks.xctest" BlueprintName="CalendarBenchmarks" ReferencedContainer="container:CalendarBenchmarks.xcodeproj"/></TestableReference></Testables></TestAction>
</Scheme>
''')
    command = ["xcodebuild", "build-for-testing" if args.build_only else "test", "-project", str(project), "-scheme", "CalendarBenchmarks",
               "-destination", "platform=iOS Simulator,id=" + args.device,
               "-derivedDataPath", str(output / "build"), "-resultBundlePath", str(output / "results.xcresult"),
               "-parallel-testing-enabled", "NO", "CODE_SIGNING_ALLOWED=NO"]
    if args.only:
        command.append("-only-testing:CalendarBenchmarks/CalendarBenchmarks/" + args.only)
    else:
        command.append("-skip-testing:CalendarBenchmarks/CalendarBenchmarks/testProfileJourney")
    if not args.trace:
        subprocess.run(command, check=True)
        return

    # Target only XCalendar, after XCTest has launched its new process. The
    # diagnostic test pauses before interacting so Instruments can attach.
    recorder = None
    with (output / "instruments.log").open("w") as log:
        test = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
        try:
            for line in test.stdout:
                print(line, end="", flush=True)
                if "PROFILE_READY" in line and recorder is None:
                    recorder = subprocess.Popen([
                        "xcrun", "xctrace", "record", "--template", "Time Profiler",
                        "--device", args.device, "--attach", "XCalendar", "--time-limit", "60s",
                        "--output", str(output / "cpu.trace"),
                    ], stdout=log, stderr=subprocess.STDOUT)
            if test.wait() != 0:
                raise RuntimeError("XCTest failed; inspect the test log")
            if recorder is None:
                raise RuntimeError("The diagnostic test did not signal PROFILE_READY")
            if recorder.wait(timeout=180) != 0:
                raise RuntimeError("Instruments failed; inspect instruments.log")
        finally:
            if test.poll() is None:
                test.terminate()
            if recorder is not None and recorder.poll() is None:
                recorder.terminate()


if __name__ == "__main__":
    main()
