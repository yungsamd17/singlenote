# Build, test and preview

## Requirements

Use JDK 17, Android SDK Platform 35, and the repository's Gradle wrapper. Build
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
```

On Windows use `gradlew.bat` for the equivalent tasks. The APK is
`app/build/outputs/apk/debug/app-debug.apk`. JVM tests cover ViewModels, repository
mutations, text/release helpers and scrolling calculations. Lint reports are
under `app/build/reports/`. These checks complement device testing; they do not
prove the rendered UI or launcher behavior.

A normal debug build uses `com.yungsamd17.singlenote`, the same application ID as
the release. Its debug signature may differ from an installed release signature.
Use a preview to test alongside your regular app.

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
the settings hub and every sub-screen, both languages, large system text,
light/dark/AMOLED themes, dynamic colors on Android 12+, notification permission,
lock-screen privacy, tile behavior, and widget resizing/scrolling. Add a widget
from the preview app when verifying preview widget behavior.

A settings page that fits beneath the expanded top bar should stay still when
swiped. On a smaller viewport or with larger text, overflowing pages should scroll
and collapse the bar. See [UI behavior](EDITOR-AND-SETTINGS.md).

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
