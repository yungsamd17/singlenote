# AGENTS.md

Guidance for AI coding agents (OpenCode, Claude Code, etc.) working in this repository.

## Project Overview

Singlenote is a minimal, fully offline Android note app: one active note, an archive,
home-screen widget, quick-settings tile, and an optional pinned notification.
Kotlin + Jetpack Compose (Material 3), Room, DataStore Preferences, Glance widget.

- Package: `com.yungsamd17.singlenote`
- minSdk 29, target/compile SDK 35, JVM 17
- Author/maintainer: yungsamd17 (https://github.com/yungsamd17)

## Build & Verify

```bash
./gradlew assembleDebug          # build debug APK
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # Android lint
```

- CI (`.github/workflows/build.yml`) runs `clean assembleDebug assembleRelease testDebugUnitTest lintDebug` on every push/PR, and publishes APKs to a GitHub Release on `v*` tags.
- Local sandboxes often lack the Android SDK/JDK — if Gradle can't run, rely on careful code review and let CI verify. Never skip updating tests when changing shared interfaces.

## Architecture

```
app/src/main/java/com/yungsamd17/singlenote/
  MainActivity.kt      # Compose NavHost: note / archive / settings, theme from DataStore
  data/
    Note.kt            # Room entity
    NoteDao.kt         # queries (active note + archive)
    NotesDatabase.kt   # Room DB, NoteStore/ArchiveStore interfaces, NoteRepository impl
    NotePreferences.kt # DataStore prefs + preference keys/constants
  ui/
    NoteScreen.kt / NoteViewModel.kt       # editor + top bar (archive, pin, menu)
    ArchiveScreen.kt / ArchiveViewModel.kt # restore/delete archived notes
    SettingsScreen.kt / SettingsViewModel.kt
  widget/              # Glance home-screen widget + NoteChangedReceiver
  tile/                # quick-settings tile service
  notify/              # pinned-note notification
```

Key patterns:

- `NoteStore` / `ArchiveStore` are interfaces; ViewModels depend on them, `NoteRepository` implements both. Any interface change must be applied to the repository AND to test fakes (see `NoteViewModelTest.FakeNoteStore`).
- All user-visible text lives in `app/src/main/res/values/strings.xml` — never hardcode strings in composables.
- Settings persist via `NotePreferences` (DataStore). Expose flows as `StateFlow` with `SharingStarted.WhileSubscribed(5000)` in ViewModels.
- Note edits debounce-save (500ms) and flush on ON_STOP; repository broadcasts `ACTION_NOTE_UPDATED` so widget/tile/notification refresh.

## UI Conventions

- Material 3 only. Settings follow the card-per-row style: one rounded `Card` (16dp corners) per row, 8dp gaps between cards, section labels in `colorScheme.primary`, switches use `thumbContent` check/close icons.
- Top bar: icon `IconButton`s wrapped in `TooltipIconButton` (tooltip helper in `NoteScreen.kt`).
- Overflow menu: plain text `DropdownMenuItem`s (no leading icons), 56dp min row height, 200dp min menu width.
- Dialogs always provide a Cancel/close `TextButton` in `confirmButton`.

## Commit Messages

Format: `type: short imperative summary` (lowercase after type, no trailing period).
Keep commits atomic — one logical change per commit.

| Type | Use for |
|---|---|
| `feat` | new user-facing feature |
| `fix` | bug fix |
| `refactor` | code change that neither fixes nor adds behavior |
| `style` | formatting/UI polish without logic change |
| `test` | adding or fixing tests |
| `docs` | documentation only |
| `chore` | build, deps, CI, tooling |
| `release` | version bump / release tagging |

Examples:

```
feat: add archive screen with restore and delete
fix: keep keyboard open while typing in the editor
refactor: extract NoteStore interface from NoteRepository
style: card-per-row settings layout with switch thumb icons
test: cover debounced save behavior in NoteViewModelTest
docs: document commit message types in AGENTS.md
chore: bump compileSdk to 35
release: v0.1.0
```

## Pull Requests

- One feature/fix per PR, branch named after the change (e.g. `settings-redesign-pin-toggle`).
- PR body: summary of changes, table or list of touched areas, testing checklist (tick before merge).
- End the PR body with an AI attribution line in this exact format:

  ```
  Built with {model} in the {agent} harness.
  ```

  Example: `Built with ox-alpha in the OpenCode harness.`

- Do **not** put AI attribution in GitHub Release notes — releases stay clean.
- CI must pass before merging.

## Releases

1. Ensure `versionName`/`versionCode` in `app/build.gradle.kts` are correct.
2. Tag on `main`: `git tag vX.Y.Z && git push origin vX.Y.Z`.
3. The workflow builds APKs and creates the GitHub Release (`generate_release_notes: true`); curate the notes afterwards with `gh release edit vX.Y.Z` (keep the attached APKs).

## Gotchas

- Do not commit secrets, keystores, or local-only files (e.g. `.and-code/`).
- `BuildConfig.VERSION_NAME` is used in the About dialog — keep `buildConfig = true`.
- Release APKs are signed with the debug keystore for now (see `build.gradle.kts`); don't "fix" this silently.
