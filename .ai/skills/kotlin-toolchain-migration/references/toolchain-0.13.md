# Kotlin Toolchain 0.13 compatibility notes

These findings come from a verified migration using Toolchain 0.13.0. They are
historical compatibility evidence, not a preset for every project or a claim
about the latest release. Apply only relevant sections, and revalidate on the
selected release before adopting a workaround.

## Baseline and source mapping

| Setting | Observed 0.13.0 default |
| --- | --- |
| Default Kotlin / Compose / KSP | 2.4.20 / 1.12.1 / 2.3.12 |

Discover JDK, bytecode release, SDK levels, deployment targets, and module paths
from the target project. Preserve them unless the chosen toolchain requires a
documented compatibility change or the user requested an upgrade.

| Former Gradle source location | Toolchain location in owning module |
| --- | --- |
| Platform-only product `src/main/kotlin` | `src` |
| Platform-only product `src/test/kotlin` | `test` |
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
on the host JVM even for native targets. A working Koin 2.3.1 configuration used
`io.insert-koin:koin-ksp-compiler-jvm:2.3.1` alongside Room's compiler. Choosing
the Koin JVM artifact was necessary for processor resolution; align its version
with the project's Koin annotations rather than blindly copying this pin.

`room.schemaLocation: ./schemas` resolves from the module root. Repeating the
repository-relative module path can accidentally duplicate that path under the
module. Check the exported location and schema diff.

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
skip useful generation in the observed migration.

Preserve the project's Koin processor options intentionally, including
`KOIN_USE_COMPOSE_VIEWMODEL`, `KOIN_CONFIG_CHECK`, and `KOIN_DEFAULT_MODULE` if
configured. Disabling checks is not a general migration fix. A useful integration
test resolves representative generated bindings and, when Room is present,
writes and reads an in-memory database.

## Resources, dependencies, and native linkage

Declare `settings.compose.resources.packageName` when moving resources, then
update imports to the generated package. Generated resource sources belong in
ignored outputs, not version control.

Keep mutually dependent Compose, Navigation3, Lifecycle, and Adaptive libraries
compatible. One verified combination used Compose 1.12.1, Lifecycle 2.11.0,
Navigation3 1.2.0, and Adaptive 1.3.0 after older navigation-event native artifacts
produced a cyclic dependency during Apple linking. If this recurs, inspect
resolved dependencies; do not impose these libraries or versions on other stacks.

Entry-point modules must see the APIs they import; a shared library may need to
export a dependency such as Koin core. Retain consistent explicit versions when
replacing a Gradle BOM, and preserve the project's HTTP engines and platform
dependency choices.

## Optional local configuration

If the existing build generates configuration, preserve its public contract and
missing-value behavior. For example, an optional `local.properties` key may
produce `BuildKonfig.API_KEY` with an empty-string default. These names and
defaults are examples, not required inputs. A build plugin can resolve the
configuration relative to its owning module and write generated sources into
its task output directory.

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

Reproduce a packaging failure before adding custom logic. A verified workaround
uses a build plugin to read `${module.runtimeClasspath}`, copy each JAR to `lib/`
with an ordered unique prefix, and create a launcher JAR with the
application main class and URI-escaped relative manifest `Class-Path` entries.
This preserves classpath order, same-named dependencies, and paths with spaces.
Ship the launcher and `lib/` together. Clean obsolete dependencies only inside
the task-owned output directory.

For this workaround, a useful regression test loads resources from two same-named
JARs through the launcher manifest and checks stale dependency removal. A packaged-app startup
check verifies actual classes and Compose native libraries are usable.

Discover the project's packaging command; custom names such as `packageDesktop`
are not built-in toolchain commands. Inspect the plugin and its printed output
path. Native installers can use a full JDK's `jpackage` with the
distribution as `--input` and the launcher as `--main-jar`. Build on each target
OS; Compose native libraries are host-specific. Testing `--type app-image` does
not establish that signed installers or DMG/MSI/DEB packages work.

## iOS and verification commands

For the tested 0.13 integration, the iOS app uses `module.xcodeproj`, Swift imports
`KotlinModules`, and the Kotlin build phase invokes the configured wrapper with
`tool xcode-integration`.
Inspect the selected release's generated integration and retain the project's
bundle identity, deployment target, assets, and signing configuration. Track a
usable shared scheme and verify the scheme actually selected by the CLI/IDE.

Command patterns: substitute module names from `show modules`; execute only
targets that exist in the project on supported hosts. Angle brackets denote
placeholders, not literal shell arguments.

```sh
./kotlin build -m <android-application> -p android
./kotlin build -m <jvm-application-or-library> -p jvm
./kotlin build -m <ios-application> -p iosSimulatorArm64
./kotlin build -m <multiplatform-library> -p iosArm64
./kotlin test -m <build-plugin-or-library> -p jvm
./kotlin test -m <multiplatform-library> -p android
./kotlin test -m <multiplatform-library> -p iosSimulatorArm64
```

`test -m` means `--include-module`; `build/run -m` means `--module` in 0.13.
A library's `iosArm64` build validates device-target library compilation, not a
signed iOS application archive. Simulator tests need an available runtime.
Avoid running simulator tests and app smoke checks simultaneously on the same
simulator: the test runner can shut it down.

Consult the pinned CLI first, then official documentation as needed:

- [Kotlin Toolchain 0.13](https://kotlin-toolchain.org/0.13/)
- [Multiplatform layout](https://kotlin-toolchain.org/0.13/user-guide/multiplatform/)
- [KSP](https://kotlin-toolchain.org/0.13/user-guide/advanced/ksp/)
- [Build-plugin tasks](https://kotlin-toolchain.org/0.13/user-guide/plugins/topics/tasks/)
- [iOS applications](https://kotlin-toolchain.org/0.13/user-guide/product-types/ios-app/)
