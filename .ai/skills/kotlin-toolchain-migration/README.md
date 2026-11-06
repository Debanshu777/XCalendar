# Kotlin Toolchain Migration skill

A portable Codex skill for migrating Kotlin/Compose Multiplatform builds from
Gradle to Kotlin Toolchain, or repairing an IDE that still treats a migrated
project as Gradle. It discovers the project's modules and integrations rather
than imposing a specific app layout or dependency stack. A reference repository
is optional. Version-specific notes are historical examples, not upgrade pins.

## Install and share

Copy or share this entire `kotlin-toolchain-migration` folder, including `agents/`
and `references/`. The package has no executable helpers, required external
skills, credentials, personal paths, or dependency on its original repository.
Build tools are needed only when performing a migration in the target project.

For personal Codex use, place the folder at:

- macOS/Linux: `~/.codex/skills/kotlin-toolchain-migration/`
- Windows: `%USERPROFILE%\.codex\skills\kotlin-toolchain-migration\`
- With a custom `CODEX_HOME`: `<CODEX_HOME>/skills/kotlin-toolchain-migration/`

If replacing an installed version, back up the old folder first. For a ZIP,
extract the top-level skill folder into the `skills` directory; avoid nesting
it twice. `SKILL.md` must be directly inside `kotlin-toolchain-migration/`.

For a repository copy, place it wherever your project keeps AI guidance, such as
`.ai/skills/kotlin-toolchain-migration/`, and link its `SKILL.md` from root
`AGENTS.md`. A custom `.ai/` folder alone is not a personal skill installation.

## Use

Open the target repository in Codex and invoke `$kotlin-toolchain-migration`.
For a copy that is not installed, explicitly ask Codex to read its `SKILL.md`.

Example requests:

- “Use `$kotlin-toolchain-migration` to migrate this repository. Preserve its
  current platforms and app identities.”
- “Use `$kotlin-toolchain-migration` and this reference repository to adopt its
  YAML build structure. Create a branch and PR.”
- “Use `$kotlin-toolchain-migration` to fix Android Studio invoking Gradle after
  the migration. Preserve my local IDE settings in a backup.”

Specify a target toolchain release, reference repository, desired platforms, or
publishing instructions when those matter. The skill does not require a
reference project, add missing integrations, or authorize publishing by itself.

## Package contents

- [SKILL.md](SKILL.md): discovery metadata and reusable migration workflow.
- `agents/openai.yaml`: Codex display metadata; automatic selection stays enabled.
- [IDE reimport](references/ide-reimport.md): diagnosis and reversible recovery.
- [0.13 compatibility notes](references/toolchain-0.13.md): conditional guidance
  for generated code, native dependencies, and desktop packaging.
