# Repair a stale Gradle IDE import

Use this procedure when a project intentionally migrated to Kotlin Toolchain
still invokes Gradle from Android Studio or IntelliJ. It is not a repair for a
normal Gradle project opened at the wrong directory or missing tracked files.

## Diagnose before resetting

Confirm the intended branch contains `project.yaml`, `module.yaml` files, and
the Kotlin wrapper. Check whether Gradle settings were intentionally removed.
Inspect local `.idea/gradle.xml`, `.idea/AndroidProjectSystem.xml`, and run
configurations for a retained Gradle link or build-before-launch task.

A characteristic failure combines a Gradle JVM fallback warning with
"Directory ... does not contain a Gradle build". Look for `.idea/gradle.xml`
linking the repository root, or `AndroidProjectSystem.xml` selecting
`com.android.tools.idea.GradleProjectSystem`. If the Gradle build was intentionally
removed, changing Java installations will not fix this stale import.

Check whether the Kotlin Toolchain plugin is installed, enabled, and compatible
with the IDE. A plugin directory on disk alone does not prove it is loaded.
Use available IDE tools, settings UI, or relevant IDE logs for that distinction.

## Reimport without losing local state

1. Save pending edits and close only the affected project. Keep other projects
   open. Do not move IDE state while the project is open: it may be rewritten.
2. Back up that project's `.idea/` directory to a new, uniquely named location
   outside active IDE settings. An ignored directory such as
   `.kotlin/ide-backups/<timestamp>/.idea` can work; verify the ignore rule first.
   Keep the backup
   recoverable; do not delete global IDE settings, SDKs, or caches.
3. Reopen the repository root with **File → Open**, with the Kotlin Toolchain
   plugin enabled. Let the IDE import the YAML project.
4. Verify modules and toolchain run configurations appear, and the active build
   action no longer invokes Gradle. Select the appropriate app module.
5. Confirm a scoped wrapper build independently. CLI success and IDE import
   success are separate observations; report both accurately.

If an appropriate tool can complete reimport, use it within the existing task
authorization. If UI automation is unavailable or unresponsive, stop repeating
the same actions, preserve the backup, and tell the user exactly which reopen
step remains. Do not claim that the IDE has synced based on a CLI build alone.

Use `./kotlin show modules` to discover runnable modules, then
`./kotlin run -m <application-module>` (substitute the actual name). On Windows,
use `.\kotlin.bat`. If a different error follows reimport, investigate that
error independently.

Do not run `gradle init`, recreate Gradle build files, change every configured
JDK, or copy another project's `.idea` directory to suppress this symptom.
Document the one-time reimport step in the migration's setup instructions.
