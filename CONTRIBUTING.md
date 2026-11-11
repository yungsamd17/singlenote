# Contributing to Singlenote

Singlenote is a small, offline-first Android note app. Contributions can improve
code, translations, accessibility, tests, and documentation. Read [AGENTS.md](AGENTS.md)
for repository-specific rules and [the documentation index](docs/README.md) for
technical details.

## Report a bug or propose a change

Search [existing issues](https://github.com/yungsamd17/singlenote/issues) first.
Use [the issue chooser](https://github.com/yungsamd17/singlenote/issues/new/choose)
for a bug or feature request. Include your app version, Android version, device,
steps to reproduce, expected result, and actual result. For UI or keyboard issues,
a screenshot or short recording helps. Use sample notes and remove private data.
Discuss changes to the app's scope, data handling, or dependencies before a large
implementation; preserve its one-active-note and offline-first design.

## Set up a checkout

Fork [Singlenote](https://github.com/yungsamd17/singlenote), then clone your fork:

```bash
git clone https://github.com/YOUR-USERNAME/singlenote.git
cd singlenote
git remote add upstream https://github.com/yungsamd17/singlenote.git
git switch -c fix/short-description
```

Open the root folder in Android Studio. Use JDK 17, the Android SDK for API 36,
and the included Gradle wrapper. See [BUILDING.md](docs/BUILDING.md) for complete
setup, SDK/build-tool troubleshooting, and a separately installable preview.

```bash
./gradlew -PpreviewBuild=true assembleDebug
./gradlew testDebugUnitTest lintDebug
```

## Find the relevant code

```text
app/src/main/java/com/yungsamd17/singlenote/
  MainActivity.kt       Navigation, theme and language transition integration
  data/                 Room entities/DAO, repository and DataStore preferences
  ui/                   Compose screens, ViewModels and shared settings cards
  util/                 Language, launcher icon, release notes and text helpers
  widget/               Glance widget, configuration and refresh handling
  tile/                 Quick-settings tile
  notify/               Pinned notification and actions
app/src/main/res/       Strings, locales, icons and bundled font
app/src/test/           JVM unit tests and store fakes
```

See [ARCHITECTURE.md](docs/ARCHITECTURE.md) for data flow and
[EDITOR-AND-SETTINGS.md](docs/EDITOR-AND-SETTINGS.md) for expected UI behavior.
Translation work has its own [step-by-step guide](docs/TRANSLATE-APP.md).

## Code conventions

Follow existing Kotlin and Material 3 patterns. Put user-visible text in Android
resources and keep every supported translation's keys/placeholders aligned with
English. Reuse `SettingsGroup`, `SettingsSectionHeader`, and tooltip buttons.
Preserve settings keys, stored values, the locale crossfade, keyboard focus timing
and note saving. ViewModels depend on `NoteStore`/`ArchiveStore`; update repository
implementations and every test fake when an interface changes. Keep
Android-specific side effects outside pure text/layout helpers so important cases
can be tested locally.

Fonts and other required assets must work offline. Bundle appropriate licenses;
do not introduce downloadable fonts, analytics, accounts or background network
requests. Network access for release notes is an explicit user action.
Do not commit secrets, keystores, local SDK paths, APKs, or review-only media.

## Verify your change

Run relevant JVM tests and Android lint; build the debug APK. Describe any check
blocked by your environment. Never treat an unrun check as passed.
`scripts/ci-local-build.sh` runs the same checks as CI locally; see
[BUILDING.md](docs/BUILDING.md). For UI changes,
inspect English plus every locale you touched, light/dark themes, small screens
and large system text. For editing, check long notes, typing/pasting at different
positions, selection, keyboard open/close/reopen, and the Done/Pinned transition.
For widget changes, check resizing and long-note scrolling in a launcher. Record
motion changes.

## Commits and pull requests

Use a branch off current `main`, with one concern per PR and atomic commits.
Commit and PR titles use `type(scope): short imperative summary`, lowercase after
the type and no trailing period. Examples:

```text
fix(editor): keep the active line visible while typing
docs(translations): explain language registration
```

Push your fork branch and [open a pull request](https://github.com/yungsamd17/singlenote/compare/main...main?expand=1)
against `main`, using **compare across forks** if needed. Follow the
[PR template](.github/PULL_REQUEST_TEMPLATE.md): explain what changed and why,
list touched areas, link related issues, and provide actual validation results.
UI changes need before/after screenshots; motion changes need a short video.
Upload that evidence directly to GitHub. AI-assisted changes end the PR body with
`Built with {model} in the {agent} harness.` using the actual model and harness;
keep AI attribution out of commits and release notes.

All changes land through reviewed pull requests. CI must pass on the latest commit
before merge. Do not push directly to `main`, merge on stale checks, or add
co-author/sign-off trailers unless explicitly requested by the maintainer.
Maintainer release steps live in [RELEASES.md](docs/RELEASES.md).
