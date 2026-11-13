# Performance checks

PR merge policy, evidence format, regression limits and administrator activation
are documented in [merge-requirements.md](../../docs/merge-requirements.md).

Opt-in development tools; none ship in the application. Keep captures, generated
runners, fixture sources, and reports under ignored `captures/performance/`.
Use an isolated checkout for fixture builds: they temporarily modify build inputs
and restore them afterward. Never run another build in that checkout concurrently.

## Android

Build a separate `.benchmark` APK with deterministic events (sign it locally before
installing). Use the same fixture and tracing options for both comparison builds.

```sh
python3 scripts/performance/build_android.py --fixture --year --tracing --output captures/performance/app-unsigned.apk
python3 scripts/performance/android_benchmark.py --serial "$ANDROID_SERIAL" --scenario month --iterations 6 --compose-tracing --apk "$SIGNED_APK" --fixture-manifest "$FIXTURE_MANIFEST" --recorder "$PERFETTO_RECORDER" --output "$RUN_DIR"
python3 scripts/performance/analyze_android.py trace --processor "$TRACE_PROCESSOR" --trace "$RUN_DIR/trace.perfetto-trace" --output "$RUN_DIR/analysis"
python3 scripts/performance/analyze_android.py summary --root "$CAPTURE_ROOT" --output "$CAPTURE_ROOT/summary.json"
python3 scripts/performance/analyze_android.py compare --root "$COMPARISON_ROOT" --scenario month --runs 3
```

`summary` validates runs from one binary/fixture. `compare` expects
`before-month-1` … `before-month-3` and `after-month-1` … `after-month-3` under the
comparison root (prefixes are configurable). It retains rejected/incomplete runs
and marks comparisons ready only when accepted runs match recorded conditions.
The SQL queries stay separate so trace metrics remain inspectable.

## iOS

Install the intended Release app, then run the XCTest harness against its verified
binary. Physical devices require suitable signing and an available app slot.

```sh
python3 scripts/performance/ios_benchmark.py --device "$IOS_DEVICE" --expected-app "$RELEASE_APP" --only testPagingJourney --output captures/performance/ios
python3 scripts/performance/analyze_ios_samples.py captures/performance/ios-samples.xml
```

The second command summarizes an Instruments Time Profiler XML export. CPU samples
and XCTest elapsed time are not frame-hitch measurements.

## Desktop

```sh
python3 scripts/performance/desktop_benchmark.py run --root "$ISOLATED_CHECKOUT" --java "$JAVA_25" --app-image --year --output "$DESKTOP_CAPTURE"
python3 scripts/performance/desktop_benchmark.py summary --root "$DESKTOP_CAPTURE" --output "$DESKTOP_CAPTURE/summary.json"
```

Desktop frame-clock callbacks and CPU diagnostics are not presented-frame FPS.
The summarizer rejects incomplete runs or captures with an inactive window.

## Validate the tools

```sh
python3 -m unittest discover -s scripts/performance -p 'test_*.py'
```

Every command supports `--help`. For methodology and historical measurements,
see [performance.md](../../docs/performance.md) and
[performance-readiness.md](../../docs/performance-readiness.md).
