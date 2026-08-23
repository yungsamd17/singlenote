<div align="center">

# Singlenote

**Focus on one note at a time.**

A minimalist, fully offline Android note app: write a single active note and keep it
visible everywhere — home screen widget, pinned notification, and quick settings tile.

[![Build](https://github.com/yungsamd17/singlenote/actions/workflows/build.yml/badge.svg)](https://github.com/yungsamd17/singlenote/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/yungsamd17/singlenote?logo=github)](https://github.com/yungsamd17/singlenote/releases/latest)
[![License](https://img.shields.io/github/license/yungsamd17/singlenote)](LICENSE)
[![Android](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)

[Download](https://github.com/yungsamd17/singlenote/releases/latest) · [Report a bug](https://github.com/yungsamd17/singlenote/issues) · [Request a feature](https://github.com/yungsamd17/singlenote/issues)

</div>

## Features

- **One active note** — automatic saving as you type, flushed when you leave the app
- **Home screen widget** — the current note at a glance
- **Pinned notification** — keeps your note visible in the notification shade
- **Quick settings tile** — one swipe away
- **Archive** — archive the current note, restore it later, or delete it permanently
- **Share & copy** — straight from the top bar
- **Theming** — light / dark / system, Material 3 design
- **Typography** — default / monospace / serif fonts, three text sizes
- **Privacy first** — fully offline; no accounts, no analytics, no tracking.
  The only permission requested is notifications (for the pinned note)

## Download

Grab the latest APK from [Releases](https://github.com/yungsamd17/singlenote/releases/latest):

| Requirement | Detail |
|---|---|
| **OS** | Android 10 (API 29) or newer |
| **APK** | `app-release.apk` recommended — `app-debug.apk` also available |

## Build

Requires JDK 17 and the Android SDK (API 35). Open the project in Android Studio, or:

```sh
./gradlew assembleDebug
```

CI builds and tests every push to `main`; tagged releases (`v*`) automatically publish
installable APKs to [Releases](https://github.com/yungsamd17/singlenote/releases).

## Tech

Kotlin · Jetpack Compose (Material 3) · Room · DataStore · Glance widgets

minSdk 29 · target/compile SDK 35 · JVM 17

## Credits

Inspired by [Mononote](https://www.digitalminimalist.com/apps/mononote), rebuilt from
scratch for Android by [yungsamd17](https://github.com/yungsamd17).

## License

[MIT](LICENSE)
