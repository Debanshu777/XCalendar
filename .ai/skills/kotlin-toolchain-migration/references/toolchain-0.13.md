# Kotlin Toolchain 0.13 compatibility notes

These findings were verified during XCalendar's migration. They are a known
baseline, not a claim that these versions remain latest or that every project
needs the same dependencies. Inspect current module files and CLI behavior first.

## Baseline and source mapping

| Setting | XCalendar migration baseline |
| --- | --- |
| Toolchain wrapper | 0.13.0, checksum verified in both wrappers |
| Default Kotlin / Compose / KSP | 2.4.20 / 1.12.1 / 2.3.12 |
| Provisioned JDK / JVM bytecode release | 25 / 23 |
| Android compile / target / minimum SDK | 37 / 36 / 33 |
| Application modules | `app/androidApp`, `app/desktopApp`, `app/iosApp` |
| Shared module | `app/shared` |
| Build plugins | `build-logic/app-config`, `build-logic/desktop-distribution` |

| Former Gradle source location | Toolchain location in owning module |
| --- | --- |
| `src/commonMain/kotlin` | `src` |
| `src/androidMain/kotlin` | `src@android` |
| `src/iosMain/kotlin` | `src@ios` |
| `src/desktopMain/kotlin` | `src@jvm` |
| `src/commonTest/kotlin` | `test` |
| `src/commonMain/composeResources` | `composeResources` |
| Android app `src/main/res` | `res` |
| Android app `src/main/AndroidManifest.xml` | `src/AndroidManifest.xml` |

An entry point moved into a platform-only application module belongs in that
module's `src/`. Use `src@iosArm64` and `src@iosSimulatorArm64` for code that must
see leaf-target generation. Adapt the layout to the project's actual targets.

## Room and Koin generation

Use `settings.kotlin.ksp.processors` and `processorOptions`. Processors execute
on the host JVM even for native targets. XCalendar uses
`io.insert-koin:koin-ksp-compiler-jvm:2.3.1` alongside Room's compiler; choosing the
Koin JVM artifact was necessary for processor resolution.

`room.schemaLocation: ./schemas` resolves from the module root. Repeating the
repository-relative module path can accidentally create nested directories such
as `app/shared/app/shared/schemas`. Check the exported location and schema diff.

On this release, generated KSP declarations are visible in leaf-platform
fragments. Keep common DI startup behind an `expect` function, and import the
generated Koin extension only in each leaf `actual` implementation:

```kotlin
// Shared src/: alias avoids ambiguity with a user-defined AppModule class.
import org.koin.core.module.Module as KoinModule
internal expect fun generatedAppModule(): KoinModule
```

```kotlin
// Each applicable leaf src@android/, src@jvm/, src@iosArm64/, etc.
import org.koin.core.module.Module as KoinModule
import org.koin.ksp.generated.module
internal actual fun generatedAppModule(): KoinModule = AppModule().module
```

Use the actual annotated module class name. Keep explicit return types on
providers that call expect/actual functions, for example
`fun getLocalDatabase(): AppDatabase`; missing type information caused Koin to
skip useful generation during this migration.

Preserve the project's Koin processor options intentionally. XCalendar retained
`KOIN_USE_COMPOSE_VIEWMODEL=true`, `KOIN_CONFIG_CHECK=false`, and
`KOIN_DEFAULT_MODULE=false`; disabling checks is not a general migration fix.
Its JVM integration test resolves generated repositories, a use case, and a
ViewModel, then writes and reads an in-memory Room database.

## Resources, dependencies, and native linkage

Declare `settings.compose.resources.packageName` when moving resources, then
update imports to the generated package. Generated resource sources belong in
ignored outputs, not version control.

Keep mutually dependent Compose, Navigation3, Lifecycle, and Adaptive libraries
compatible. With Compose 1.12.1, XCalendar aligned Lifecycle to 2.11.0,
Navigation3 to 1.2.0, and Adaptive to 1.3.0; older navigation-event native
artifacts produced a cyclic dependency during Apple linking. If this recurs,
inspect resolved dependencies rather than changing unrelated source code.

Entry-point modules must see the APIs they import. XCalendar exports Koin core
from its shared module. Retain explicit Ktor versions when replacing the Gradle
BOM and preserve the platform engines (OkHttp on Android/JVM, Darwin on iOS).

## Optional local configuration

XCalendar's `app-config` plugin generates the existing `BuildKonfig.API_KEY`
contract from root `local.properties`. A missing file/key produces an empty
string. Its task resolves the input relative to the module and writes generated
source into its task output directory.

When recreating this behavior, test absence, changes/removal, and Kotlin string
escaping (quotes, backslashes, dollar signs, newlines, and control characters).
Use a clean-checkout build to confirm the plugin framework also accepts the
missing input, not just a unit test that invokes the task function directly.
Do not copy the local key or generated source into the skill or repository.

## Desktop distribution

In the tested 0.13 executable-JAR packaging path, different Maven groups with
the same JAR basename collided. AndroidX class-containing artifacts could be
replaced by JetBrains redirect artifacts, resulting in a startup
`NoClassDefFoundError` for `androidx.lifecycle.ViewModel` despite passing tests.

Reproduce a packaging failure before adding custom logic. XCalendar's
`desktop-distribution` plugin uses `${module.runtimeClasspath}`, copies each
JAR to `lib/` with an ordered unique prefix, and creates a launcher JAR with the
application main class and URI-escaped relative manifest `Class-Path` entries.
This preserves classpath order, same-named dependencies, and paths with spaces.
Ship the launcher and `lib/` together. Clean obsolete dependencies only inside
the task-owned output directory.

The regression test loads resources from two same-named JARs through the
launcher manifest and checks stale dependency removal. A packaged-app startup
check verifies actual classes and Compose native libraries are usable.

For XCalendar, use `./kotlin do packageDesktop -m desktopApp`. This is a project
plugin command, not a built-in toolchain command. Inspect the plugin and its
printed output path. Native installers use a full JDK's `jpackage` with the
distribution as `--input` and the launcher as `--main-jar`. Build on each target
OS; Compose native libraries are host-specific. Testing `--type app-image` does
not establish that signed installers or DMG/MSI/DEB packages work.

## iOS and verification commands

The migrated iOS app uses `module.xcodeproj`, Swift imports `KotlinModules`, and
its Kotlin build phase invokes the configured wrapper with `tool xcode-integration`.
Inspect the selected release's generated integration and retain the project's
bundle identity, deployment target, assets, and signing configuration. Track a
usable shared scheme and verify the scheme actually selected by the CLI/IDE.

Commands for the XCalendar module names (scope to supported hosts):

```sh
./kotlin build -m androidApp -p android
./kotlin build -m desktopApp -p jvm
./kotlin build -m iosApp -p iosSimulatorArm64
./kotlin build -m shared -p iosArm64
./kotlin test -m app-config -m desktop-distribution -p jvm
./kotlin test -m shared -p jvm
./kotlin test -m shared -p android
./kotlin test -m shared -p iosSimulatorArm64
./kotlin do packageDesktop -m desktopApp
```

`test -m` means `--include-module`; `build/run -m` means `--module` in 0.13.
The shared `iosArm64` build validates device-target library compilation, not a
signed iOS application archive. Simulator tests need an available runtime.
Avoid running simulator tests and app smoke checks simultaneously on the same
simulator: the test runner can shut it down.

Consult the pinned CLI first, then official documentation as needed:

- [Kotlin Toolchain 0.13](https://kotlin-toolchain.org/0.13/)
- [Multiplatform layout](https://kotlin-toolchain.org/0.13/user-guide/multiplatform/)
- [KSP](https://kotlin-toolchain.org/0.13/user-guide/advanced/ksp/)
- [Build-plugin tasks](https://kotlin-toolchain.org/0.13/user-guide/plugins/topics/tasks/)
- [iOS applications](https://kotlin-toolchain.org/0.13/user-guide/product-types/ios-app/)
