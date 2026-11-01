# Changelog

All notable changes to Singlenote are documented in this file. The format is
inspired by [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

Changes for the next release will be listed here.

## [0.4.2] - 2026-11-01

### Added

- Settings hub with Appearance, Typography, Language, Notifications, and About screens.
- Dynamic wallpaper colors on Android 12+, AMOLED dark mode, and preset or wallpaper-colored launcher icons.
- Bundled Google Sans Flex app font and a System, English, or Slovenčina language picker.
- Archive sorting by last change, archive date, or title, with saved sort order and relative timestamps.
- Archive JSON import and export, plus plain-text export; JSON preserves note content and timestamps.
- Undo for archive restore conflicts, returning both notes to their previous locations after Replace or Swap.
- A character-limit indicator after 500 characters, with feedback at the limit.
- A separate release workflow, contributor and translation guides, and local preview build instructions.

### Enhancements

- Long notes expand the editor to the available space and scroll inside the card; the caret is kept in view while editing.
- Settings, widget configuration, dialogs, archive cards, and snackbars use updated Material 3 styling.
- The widget supports scrolling long notes, configurable appearance and tap actions, and a placeholder in its configuration preview.
- The quick-settings tile opens the editor for a new note, archiving the current note first when present.
- The About changelog sheet opens fully, and the notification permission flow can direct users to Android settings.

### Bug Fixes

- Keep consecutive pending archive deletions hidden until the database confirms each deletion; Undo affects the latest deletion.
- Show a newly archived note as archived until it is edited after a restore; preserve its edit time across later restores and archives.
- Prevent settings pages that fit on screen from scrolling or collapsing their top bar.
- Align the editor and archive card margins and prevent archive text from being truncated.

### Miscellaneous

- Added schema migration, repository and ViewModel regression tests, and guidance for preview builds and release signing.

## [0.4.1] - 2026-09-23

### Enhancements

- Temporarily lock portrait orientation while landscape layout is being fixed.
- Polish About subtitles and disabled overflow-menu icons.

### Bug Fixes

- Copy notes as normal plain text instead of masked password text.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.4.1) · [Changes since v0.4.0](https://github.com/yungsamd17/singlenote/compare/v0.4.0...v0.4.1)

## [0.4.0] - 2026-09-20

### Added

- Material You wallpaper accent on Android 12+.
- Lock-screen notification content enabled by default, with a visibility setting.

### Bug Fixes

- Smooth keyboard dismissal and reopening, and retain Undo across screen switches.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.4.0)

## [0.3.4] - 2026-09-18

### Enhancements

- Introduced the sticky-note app icon, refreshed About header, release notes, legal pages, and copy confirmation.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.3.4)

## [0.3.3] - 2026-09-18

### Added

- About page, legal and project links, update checks, and in-app changelog.

### Enhancements

- Improved pinned notification behavior and launch theme handling.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.3.3)

## [0.3.2] - 2026-09-16

### Enhancements

- Updated pinned-notification title and actions, archive action contrast, and top-bar icons.
- Signed release builds with a dedicated release key.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.3.2)

## [0.3.1] - 2026-09-14

### Enhancements

- Added preset accent colors and improved editor keyboard transitions and archive actions.

### Bug Fixes

- Preserve notes saved with the small font when reopening the app.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.3.1)

## [0.3.0] - 2026-09-10

### Enhancements

- Reworked the editor and archive, adding restore conflict actions and bottom note controls.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.3.0)

## [0.2.6] - 2026-09-02

### Enhancements

- Redesigned the overflow menu and navigation transitions.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.2.6)

## [0.2.5] - 2026-08-24

### Miscellaneous

- Corrected the CI debug-keystore location.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.2.5)

## [0.2.4] - 2026-08-24

### Miscellaneous

- Configured a persistent CI debug keystore.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.2.4)

## [0.2.3] - 2026-08-24

### Miscellaneous

- Updated repository agent and commit conventions.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.2.3)

## [0.2.2] - 2026-08-24

### Miscellaneous

- Persisted the CI debug keystore for stable debug signatures.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.2.2)

## [0.2.1] - 2026-08-24

### Bug Fixes

- Centered the launcher icon glyph.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.2.1)

## [0.2.0] - 2026-08-24

[Release notes and comparison](https://github.com/yungsamd17/singlenote/releases/tag/v0.2.0)

## [0.1.0] - 2026-08-23

### Added

- First release: one active note, archive, widget, pinned notification, quick-settings tile, and appearance settings.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.1.0)
