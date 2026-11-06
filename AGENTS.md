# Working in XCalendar

## Build system and setup

XCalendar is a Kotlin/Compose Multiplatform application for Android, iOS, and
JVM desktop. Use the checked-in `./kotlin` wrapper (or `.\kotlin.bat` on Windows)
from the repository root. The project follows Kratos's Kotlin Toolchain layout;
there is no Gradle build.

Both wrappers pin Kotlin Toolchain **0.13.0** and verify the downloaded CLI's
checksum. This release supplies Kotlin **2.4.20**, Compose **1.12.1**, and KSP
**2.3.12**. Toolchain and Kotlin language versions are separate. Do not run
`./kotlin update` unless a toolchain upgrade is part of the task.

The CLI bootstraps its own runtime; compilation provisions JDK 25 automatically.
The application targets JVM release 23. Do not commit machine-specific JDK/SDK
paths, trust stores, credentials, or device IDs. Configure local environment
requirements outside the wrappers; use `KOTLIN_CLI_JAVA_OPTIONS` if a corporate
network requires a Java trust store.

Start with the actual configuration and pinned CLI help:

```sh
./kotlin --version
./kotlin show modules
./kotlin build --help
./kotlin test --help
./kotlin run --help
```

Android requires the Android SDK (compile SDK 37, minimum API 33, target API 36).
Use `ANDROID_HOME` to select an existing SDK. iOS requires macOS, full Xcode,
and an installed iOS SDK/simulator runtime. Install the Kotlin Toolchain and
Kotlin Multiplatform IDE plugins for YAML project support.

## Repository layout and configuration

- `project.yaml` registers applications, the shared library, and build plugins.
- `app/androidApp`: Android entry point, `src/AndroidManifest.xml`, and `res/`.
- `app/iosApp`: Kotlin view controller and Swift sources in `src/`, assets,
  signing configuration, and `module.xcodeproj`.
- `app/desktopApp`: JVM desktop entry point in `src/`.
- `app/shared`: shared calendar UI, domain, repositories, and database.
- `app/shared/src`: common code, preserving `com.debanshu.xcalendar` packages.
- `src@android`, `src@ios`, `src@jvm`: platform database adapters.
- `src@iosArm64`, `src@iosSimulatorArm64`: leaf-platform Koin generation bridges.
- `app/shared/test`: common use-case/ViewModel tests; `test@<platform>` holds
  platform-specific integration tests.
- `app/shared/composeResources`: Compose resource inputs. Generated accessors
  use `com.debanshu.xcalendar.resources`.
- `app/shared/schemas`: versioned Room schema history. Preserve schema version 1;
  database changes require migrations and updated schema exports.
- `app/app.module-template.yaml`: shared platform/compiler configuration.
- `libs.versions.toml`: application dependency versions, referenced as `$libs.*`.
- `build-logic/app-config`: toolchain plugin generating the optional API key.
- `build-logic/desktop-distribution`: packages the full JVM runtime classpath.
- `assets`: demo calendar/event data and README screenshots.

Use `module.yaml` for products, dependencies, and settings, with platform
qualifiers such as `dependencies@ios` and `settings@android`. Use
`test-dependencies` and `test-settings` for test-only configuration. Register
new modules in `project.yaml`. Keep platform APIs out of common code; use
`expect`/`actual` where needed. Do not introduce a second build system.

## Generated code and local configuration

Room and Koin processors run through the toolchain's built-in KSP integration.
Use the JVM Koin processor artifact because processors execute on the host.
Keep explicit return types on DI providers that call expect/actual functions.
KSP output is only visible to leaf platform fragments. Common code calls
`generatedAppModule()` through an `expect` declaration; each leaf platform
implements it using Koin's generated module. Do not import
`org.koin.ksp.generated` into `src/` or `src@ios/`.

The app-config plugin reads optional `API_KEY` from root `local.properties`
and generates `BuildKonfig` for all platforms. A missing file/key produces an
empty string. Never commit the local file, generated source, or API key. Keep
all generated outputs under ignored `build/`; edit inputs rather than outputs.

## Build, run, and package

```sh
./kotlin build -m androidApp -p android
./kotlin build -m desktopApp -p jvm
./kotlin build -m iosApp -p iosSimulatorArm64
./kotlin build -m iosApp -p iosArm64

./kotlin run -m androidApp
./kotlin run -m desktopApp
./kotlin run -m iosApp -p iosSimulatorArm64

./kotlin do packageDesktop -m desktopApp
./kotlin package -m androidApp -f aab
```

Use `-v release` for release builds. `./kotlin build` builds all configured
platforms, so use scoped commands on non-macOS hosts. Desktop distributions
contain host-specific Compose native libraries; package on each target OS.
Ship `XCalendar.jar` together with its `lib/` folder. The packaging plugin
preserves JARs with identical basenames from different Maven groups; the built-in
0.13 executable-JAR packager drops some AndroidX/JetBrains redirect dependencies.
For native installers, follow the JDK `jpackage` instructions in the README.
For iOS signing/archive work, use `app/iosApp/module.xcodeproj` and its existing
XCalendar identity. Swift imports the toolchain's `KotlinModules` framework.

## Android device work

Use the installed Google `android-cli` skill for SDK/device management, APK
installation, and UI inspection. Read its `SKILL.md` before using it. Build and
run through `./kotlin`; do not use `android create` or expect Gradle discovery
for this YAML project.

```sh
android --version
android info
adb devices -l
android emulator list
```

Use the same SDK as the Kotlin Toolchain. Select devices from the live list;
pass `--device-id <serial>` to `./kotlin run`. After UI changes, inspect
`android layout --device=<serial> --pretty` and a screenshot saved under
ignored `captures/`. Visually examine the PNG and check for crashes. Command
success alone is not UI validation. Do not commit local device identifiers.

## Test and validate changes

```sh
./kotlin test -m app-config -m desktop-distribution -p jvm
./kotlin test -m shared -p jvm
./kotlin test -m shared -p android
./kotlin test -m shared -p iosSimulatorArm64
```

In 0.13, `test -m` means `--include-module`; `build/run -m` means `--module`.
iOS tests require an available simulator. Follow existing `kotlin.test`
conventions. For shared changes, run relevant tests and build Android, desktop,
and iOS when available. For build changes, also verify resource generation,
Room/Koin generation, and the desktop distribution. Documentation-only work needs
command/configuration checks rather than a full build.

Report commands actually run and any unavailable platform checks. Treat cache
permission failures as environment restrictions and use the approved permission
mechanism; do not change wrappers or application code to bypass them. Avoid
routine cleans, preserving incremental outputs. Never commit `build/`, `.kotlin/`,
IDE state, or downloaded toolchain caches.

## Official references

- [Kotlin Toolchain 0.13](https://kotlin-toolchain.org/0.13/)
- [Source layout](https://kotlin-toolchain.org/0.13/user-guide/multiplatform/)
- [KSP integration](https://kotlin-toolchain.org/0.13/user-guide/advanced/ksp/)
- [Build plugins](https://kotlin-toolchain.org/0.13/user-guide/plugins/topics/tasks/)
- [iOS integration](https://kotlin-toolchain.org/0.13/user-guide/product-types/ios-app/)

Prefer pinned CLI help when online documentation differs from this release.
