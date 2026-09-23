<div align="center">

<img src="docs/icon.png" width="128" alt="Singlenote app icon">

# **Single**note

**Focus on one note at a time.**

A minimalist, offline-first Android note app: write a single active note and keep it
visible everywhere — home screen widget, pinned notification, and quick settings tile.

[![Build](https://img.shields.io/github/actions/workflow/status/yungsamd17/singlenote/build.yml?style=for-the-badge&label=Build&color=11131A)](https://github.com/yungsamd17/singlenote/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/yungsamd17/singlenote?style=for-the-badge&logo=github&color=11131A)](https://github.com/yungsamd17/singlenote/releases/latest)
[![License](https://img.shields.io/badge/License-MIT-11131A?style=for-the-badge)](LICENSE)
[![Android](https://img.shields.io/badge/Android-10+-11131A?style=for-the-badge&logo=android&logoColor=3DDC84)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-11131A?style=for-the-badge&logo=kotlin&logoColor=7F52FF)](https://kotlinlang.org)

[Download](https://github.com/yungsamd17/singlenote/releases/latest) · [Docs](docs/README.md) · [Report a bug](https://github.com/yungsamd17/singlenote/issues) · [Request a feature](https://github.com/yungsamd17/singlenote/issues)

</div>

## Features

- **One active note** — automatic saving as you type, flushed when you leave the app
- **Home screen widget** — the current note at a glance
- **Pinned notification** — keeps your note visible in the notification shade
- **Quick settings tile** — one swipe away
- **Archive** — archive the current note, restore it later, or delete it permanently
- **Share & copy** — straight from the top bar, with confirmation
- **Theming** — light / dark / system, Material 3 design with per-accent palettes
- **Typography** — default / monospace / serif fonts, three text sizes
- **Changelog & updates** — per-version release notes and update checks in About
- **Privacy first** — offline-first; no accounts, no analytics, no tracking.
  Notes and settings are stored unencrypted in the app's private storage.
  Permissions: notifications (pinned note), and internet solely for release
  notes and update checks, only when you open them

## Roadmap

`v0.4.1` is the current release. Track the checklist for the first stable release in [`docs/v1.0.0-plan.md`](docs/v1.0.0-plan.md) — features, improvements, fixes and release steps for `v1.0.0`.

The outside-scrutiny review backlog (privacy, correctness, accessibility, repo hygiene) lives in [`docs/TODO.md`](docs/TODO.md) — work through it anytime.

## Download

Grab the latest APK from [Releases](https://github.com/yungsamd17/singlenote/releases/latest):

| Requirement | Detail |
|---|---|
| **OS** | Android 10 (API 29) or newer |
| **APK** | `singlenote-vX.Y.Z-release.apk` recommended — debug build also available |

## Build

Requires JDK 17 and the Android SDK (API 35). Open the project in Android Studio, or:

```sh
./gradlew assembleDebug
```

CI builds and tests every push to `main`; tagged releases (`v*`) automatically publish
installable APKs to [Releases](https://github.com/yungsamd17/singlenote/releases).

## Tech

| Area | Stack |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Storage | Room (active note + archive) |
| Settings | DataStore Preferences |
| Widget | Glance |
| Navigation | Navigation Compose |
| Async | Kotlin Coroutines |
| SDK / JVM | minSdk 29 · target/compile SDK 35 · JVM 17 |

## Credits

Inspired by [Mononote](https://www.digitalminimalist.com/apps/mononote), rebuilt from
scratch for Android by [yungsamd17](https://github.com/yungsamd17).

- Launcher glyph: [Bootstrap Icons](https://icons.getbootstrap.com) (`sticky-fill`, MIT),
  composed with [s17 Labs Icon Maker](https://s17labs.github.io/tools/icon-maker)
- `license` / `format_paint` glyphs: [Material Symbols](https://fonts.google.com/icons)
  by Google (Apache License 2.0)

## License

[MIT License](LICENSE) · [Third-party notices](THIRD_PARTY_LICENSES)
