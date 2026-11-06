---
name: kotlin-toolchain-migration
description: Migrate Kotlin or Compose Multiplatform projects from Gradle to Kotlin Toolchain YAML modules, using a reference repository such as Kratos, and repair stale Gradle IDE imports after migration. Use for an explicitly requested build-system migration or problems with an already migrated project; ordinary Gradle dependency updates do not require this workflow.
---

# Kotlin Toolchain migration

Preserve application behavior and platform identities while replacing the build
system. Treat a reference project as evidence of conventions, not a template to
copy wholesale. This skill captures the XCalendar migration to Kratos-style
modules; release-specific findings are separate from the workflow.

## Select the work

- **Migration:** inspect the source build and reference repository, then use the
  workflow below. If the request only says to adopt another project's setup,
  establish whether it includes changing the build system before removing Gradle.
  Reuse an explicit choice already made in the conversation.
- **Already migrated, but IDE invokes Gradle:** read
  [IDE reimport](references/ide-reimport.md). Diagnose the local import before
  changing build configuration or Java versions.
- **KSP, resources, native linkage, or packaging failures on 0.13:** read the
  relevant section of [0.13 compatibility notes](references/toolchain-0.13.md).
  Recheck behavior before carrying a workaround to a different toolchain release.

## Inspect the contract being migrated

Read repository instructions and Git status, including staged changes. Identify
application/library modules, source sets, resources, tests, version catalogs,
code generators, custom Gradle tasks, Xcode build phases, signing settings,
distribution tasks, and CI commands.

Record the behavior to retain: Android application ID and SDK levels, Apple
bundle IDs and deployment targets, JVM release, database paths/schema history,
resource accessor packages, entry points, and optional local configuration.
Do not print credential values while inspecting configuration.

Find the reference project's actual root. A supplied path can name a library
subdirectory; inspect its parents for `project.yaml` and wrappers. Read only the
relevant modules and build plugins. Do not import unrelated native integrations,
machine paths, trust stores, credentials, or application identities.

If a new branch is requested, create one before edits, normally under `codex/`.
Keep unrelated staged/unstaged changes separate when committing. Creating or
publishing a PR is conditional on the user's request, not implied by this skill.

## Establish versions and structure

Inspect both reference wrappers and their checksums. For a requested latest
upgrade, verify the current release against official sources. Otherwise retain
the agreed pin. Kotlin Toolchain, Kotlin language, Compose, and KSP versions are
different values: obtain defaults from the selected CLI rather than inferring
them from the toolchain version.

Use the checked-in wrapper and release-specific help:

```sh
./kotlin --version
./kotlin show modules
./kotlin show settings
./kotlin build --help
./kotlin test --help
./kotlin run --help
```

Keep the POSIX wrapper executable, preserve Windows compatibility, and retain
download checksum verification. Let the toolchain provision its runtime/JDK;
preserve the intended bytecode release separately. Handle local proxy/trust-store
needs through supported local environment settings rather than wrapper edits.

For Kratos-style structure, register modules/plugins in `project.yaml`, place
application entry points under `app/`, and use `module.yaml` per module. Move
common code to `src/`, platform code to `src@<platform>/`, common tests to `test/`,
and platform tests to `test@<platform>/`. Preserve package names. Move Compose
resources and Room schemas with their owning shared module.

Use a shared module template for genuinely shared settings. Keep application
dependencies in the version catalog; replace Gradle BOM/plugin mechanics with
supported toolchain settings or explicit library versions. Check exported
dependencies where an entry-point module needs APIs from a shared library.

## Port integrations

Translate custom tasks only when equivalent behavior is needed. Prefer built-in
toolchain capabilities; use `build-logic/` plugins for remaining generation or
packaging work. Keep their outputs under ignored build directories.

For Room/Koin, optional API-key generation, Compose resources, and desktop
packaging, consult [0.13 compatibility notes](references/toolchain-0.13.md).
Verify generated code is consumed by each target; compilation of handwritten
sources alone is insufficient.

Preserve the existing Xcode project's assets, configuration, and signing identity.
Adapt its source paths, framework import, and Kotlin build phase to the selected
toolchain. Verify the wrapper path from Xcode's working directory and inspect the
actual shared scheme names. Account for customized settings before considering
replacement with a generic starter project.

Remove obsolete Gradle wrappers, settings, plugins, and CI tasks when their
replacement behavior is in place. Preserve ignored user files; their presence
does not make the migrated repository a Gradle build.

## Verify and hand over

Build each supported application on an appropriate host. Run relevant common and
platform tests, plus focused tests for custom generation and packaging logic.
Check resource accessors, Koin resolution, and Room read/write behavior against
an isolated database. Compare committed schema history against the baseline.

Verify a clean checkout without optional local configuration. Keep it isolated
from the working copy. For desktop distribution changes, launch the actual
packaged result; successful compile/test tasks do not prove its runtime classpath
is complete. Inspect startup logs and the app UI when available.

Use supported device tools for mobile smoke tests. Never uninstall a user's
installed app to overcome a signing-key mismatch. Prefer an emulator/simulator
or a disposable checkout with a distinct test application ID. Isolate desktop
test data and remove only artifacts created for the test.

Update setup documentation and CI to use the pinned wrapper, concrete module
names, and host-specific tasks. Include the IDE reimport step for existing
checkouts. Distinguish application builds, library target builds, startup checks,
signed archives, and untested platforms in the result.

If a PR was requested, inspect the final diff, commit only intended files, push
the branch, and create/update a concrete PR with validation and remaining limits.
Do not commit IDE backups, local properties, generated sources, device IDs, or
machine-specific cache paths. Report unavailable IDE/CI verification explicitly.
