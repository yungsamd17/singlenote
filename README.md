# Singlenote

Focus on one note at a time. A minimalist Android note app: write a single active note,
keep it visible on your home screen (widget), in your notification shade (pinned
notification) and quick settings (tile), then archive or delete it when done.

Inspired by the concept of [Mononote](https://www.digitalminimalist.com/apps/mononote),
rebuilt from scratch for Android. Fully offline, no permissions beyond notifications,
no tracking.

[![Build](https://github.com/yungsamd17/singlenote/actions/workflows/build.yml/badge.svg)](https://github.com/yungsamd17/singlenote/actions/workflows/build.yml)

## Features

- One active note with automatic saving
- Home screen widget showing the current note
- Pinned persistent notification with the current note
- Quick Settings tile showing the current note
- Archive with restore, permanent delete
- Share / copy the current note
- Font family (default / mono / serif) and text size options
- Light / dark / system theme

## Build

Requires JDK 17. Open in Android Studio or:

```sh
./gradlew assembleDebug
```

APKs are built automatically by GitHub Actions on every push to `main`.
Tagged releases (`v*`) publish installable APKs under
[Releases](https://github.com/yungsamd17/singlenote/releases).

## Tech

Kotlin · Jetpack Compose (Material 3) · Room · Glance widgets · DataStore

minSdk 29 (Android 10) · targetSdk 35

## License

[MIT](LICENSE)
