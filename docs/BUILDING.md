# Build, test and preview

## Requirements

Use JDK 17, Android SDK Platform 36, and the repository's Gradle wrapper. Build
Tools are selected by the Android Gradle Plugin (the current setup requests
Build Tools 36). Install missing packages and accept their licenses through
Android Studio's SDK Manager. Dependency downloads require network access on a
fresh checkout; installed app fonts and note storage work offline.

Set the SDK location with `ANDROID_HOME`, or put your own path in the ignored
root `local.properties` file:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Do not copy someone else's JDK/SDK path into the repository's Gradle settings.
The version catalog is `gradle/libs.versions.toml`; app IDs, SDK levels and version
numbers live in `app/build.gradle.kts`.

## Build and check

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew koverVerifyDebug
```

On Windows use `gradlew.bat` for the equivalent tasks. The APK is
`app/build/outputs/apk/debug/app-debug.apk`. JVM tests cover ViewModels, repository
mutations, text/release helpers and scrolling calculations. The Room tests
(`MigrationTest`, `NoteDaoTest`) run on Robolectric, which needs a native SQLite
runtime: Linux arm64 hosts require Robolectric 4.18 or newer (the pinned
`4.18-beta-1` ships the aarch64 library). Lint reports are under
`app/build/reports/`. These checks complement device testing; they do not
prove the rendered UI or launcher behavior.

### Coverage

`koverVerifyDebug` measures the line coverage of the app's logic and fails
below **70 %**. The scope is set in `app/build.gradle.kts` and covers what a
JVM test can reach:

| Scope | Contents |
|---|---|
| `com.yungsamd17.singlenote.data.*` | Room entity/DAO, the repository, DataStore preferences |
| `com.yungsamd17.singlenote.util.*` | archive file I/O, language override, launcher icon, diagnostics, link/markdown parsing |
| `com.yungsamd17.singlenote.notify.*` | the pinned-notification rules |
| `ui.NoteViewModel`, `ui.ArchiveViewModel`, `ui.SettingsViewModel` | the ViewModels |

Outside the scope, and therefore not measured: Compose UI, Android components
(activity, quick-settings tile, receivers, widget) and the Glance widget
rendering — those need a device or a rendering tree. Room's generated `_Impl`
classes are out too: the DAO tests already exercise them, so counting their
lines line by line would only measure generated code.

`:app:koverHtmlReport` writes the same numbers as a browsable report to
`app/build/reports/kover/html/`, `:app:koverXmlReport` as XML.

Room writes the exported schema to `app/schemas/` on every build. Commit the
changed JSON with the entity or migration that produced it — the file is the
baseline the migration tests and the next schema bump compare against.

A normal debug build uses `com.yungsamd17.singlenote`, the same application ID as
the release. Its debug signature may differ from an installed release signature.
Use a preview to test alongside your regular app.

## Run the CI checks locally

`scripts/ci-local-build.sh` runs the same task list as the Build workflow
(`clean assembleDebug assembleRelease testDebugUnitTest koverVerifyDebug lintDebug lintRelease`):

```bash
scripts/ci-local-build.sh
```

It installs the missing SDK packages, creates the debug keystore the way the
workflow creates it, and signs the release build with a local test key. A
locally built release APK has a different signature than the published one, so
Android refuses to install it over the released app: uninstall the released app
first, which clears its data, or build a preview instead.

Single tasks work as well:

```bash
scripts/ci-local-build.sh assembleDebug
```

| Variable | Purpose |
|---|---|
| `ANDROID_HOME` | SDK location (default `~/.android-sdk`) |
| `GRADLE_HEAP` / `GRADLE_HEAP_SMALL` | Gradle daemon heap per stage |
| `CI_LOCAL_STAGED` | Run every task in its own Gradle invocation, so the heap is released between them (default on machines with less than 4 GB of RAM) |
| `CI_LOCAL_CPUS` | Pin the build to a few CPUs, for example `0,1` |
| `CI_LOCAL_CLEAN` | Run `clean` before the other tasks |
| `CI_LOCAL_LOG_DIR` | Where the per-stage logs are written |

On an ARM64 Linux host the script runs the x86-64-only build tools through
qemu-user with a small amd64 sysroot, and points AGP at the wrapped aapt2 with
`android.aapt2FromMavenOverride`.

## Build a separate preview

```bash
./gradlew -PpreviewBuild=true assembleDebug
```

| Property | Preview value |
|---|---|
| Application ID | `com.yungsamd17.singlenote.preview` |
| Launcher label | Singlenote Preview |
| Signing | Local debug key |
| APK path | `app/build/outputs/apk/debug/app-debug.apk` |
| Version | Current `versionName` / `versionCode`; no automatic bump |

The preview has separate notes, settings, permissions and widgets. It starts as a
fresh installation and does not import the release app's data. Each rebuild
writes to the same APK path, so copy/rename an APK before switching variants if
you need to keep both artifacts. Preview mode is a Gradle property, not another
build type; keep passing it for every preview build or install command.

With a compatible device/emulator and USB or wireless debugging:

```bash
./gradlew -PpreviewBuild=true installDebug
```

Or copy the APK to the device's Downloads folder and tap it in Files. Android
may ask you to allow installation from that file manager. Debug builds from a
different local signing key may not update an existing preview. Keep your debug
key consistent; uninstalling a preview deletes its own notes and settings.

## Verify on a device

Check the editor with short and long notes, cursor movement, multiline paste,
keyboard resizing and quick keyboard reopen. Check archive restore/delete/undo, rapid consecutive deletions, JSON import/export and sorting,
the settings hub and every sub-screen, the locales you touched, large system text,
light/dark/AMOLED themes, dynamic colors on Android 12+, notification permission,
lock-screen privacy, tile behavior, and widget resizing/scrolling. Add a widget
from the preview app when verifying preview widget behavior.

A settings page that fits beneath the expanded top bar should stay still when
swiped. On a smaller viewport or with larger text, overflowing pages scroll under
the pinned bar — the bar never collapses. See [UI behavior](EDITOR-AND-SETTINGS.md).

## Troubleshooting

- **SDK location not found:** set a valid `ANDROID_HOME` or `sdk.dir`.
- **Missing platform/build tools or unaccepted licenses:** use SDK Manager to
  install the requested versions and accept licenses. Do not lower compileSdk to
  work around an incomplete local installation.
- **Wrong Java version:** point `JAVA_HOME` or Android Studio's Gradle JDK setting
  to JDK 17, then restart the Gradle daemon.
- **D8 Java heap exhaustion:** merging the full debug dependency set may exceed
  a small heap. This project defaults to 2 GB. Keep machine-only overrides in
  `~/.gradle/gradle.properties` or pass them on the command line. For a constrained
  machine, reducing worker concurrency can lower simultaneous memory use:

  ```bash
  ./gradlew -PpreviewBuild=true --max-workers=1 assembleDebug
  ```

- **ARM Linux build host:** official SDK executables may be x86-64. Use a
  supported local toolchain or an appropriate emulator/wrapper for those tools;
  an `android.aapt2FromMavenOverride` must point to an executable named `aapt2`.
  Keep host-specific wrappers and paths outside git. Do not distribute a
  manually assembled APK as if it had passed the normal Gradle build.
- **Signature/install conflict:** verify the application ID and signing key;
  use the preview package for side-by-side testing.
- **Launcher icon refresh:** aliases use packaged preset accents or the Android 12+ wallpaper accent.
  Changing the icon closes the app; reopen it from the launcher. Launcher cache
  refresh timing varies.

CI validates PRs and main with [Build](../.github/workflows/build.yml) and uploads
`singlenote-debug-apk` artifacts. Tag-based signing/publication is handled by
[Release](../.github/workflows/release.yml); see [RELEASES.md](RELEASES.md).
