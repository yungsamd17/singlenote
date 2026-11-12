# Changelog

All notable changes to Singlenote are documented in this file. The format is
inspired by [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

Changes for the next release will be listed here.

### Added

- **Quick capture from the notification shade.** The pinned notification gained a "New note" action: tap it and Singlenote opens with an empty note and the cursor in it. A note that is already on screen is **archived**, never deleted, so nothing you wrote is thrown away — and the Undo bar still offers it back.
- **Archive search.** A search button in the archive opens a field that filters archived notes as you type, ignoring case and surrounding spaces, and tells you when nothing matches. Back closes the search before it leaves the archive. Exporting and clearing still apply to the whole archive, never only to the matches.
- **Four new languages.** Italiano, Nederlands, Português (Brasil) and Türkçe, each fully translated across the editor, settings, archive, widget setup, About, legal text and diagnostics. The Android 13+ per-app language picker lists them too.

### Changed

- **Built for Android 16.** The app now targets SDK 36, so it follows the current back-gesture and 16 KB page-size requirements on Android 16 while keeping the same behaviour on older versions.
- **The language list is alphabetical.** The picker shows the device-language row first, then English, then the remaining languages in alphabetical order by their English name, so a language is easy to find.
- **The language rows read like a language list.** Each row now leads with the English name ("German"), shows the native name underneath ("Deutsch") and carries a round badge with the region code, the way other apps present their language pickers.
- **The selected language is marked in its badge.** The picked row swaps the region code for a check mark on an accent-tinted circle, and the radio buttons on the right are gone.
- **Cleaner language names.** English is just "English" (the badge already carries the region), Brazilian Portuguese reads "Portuguese (Brazil)" and the Chinese translation reads "Chinese (Simplified)" — and Chinese moved to the end of the list, next to the other regional variants.
- **The Settings hub names the language you are reading.** With "Your device language" selected, the Language row shows the language the phone actually uses instead of just "System".
- **The archive search button appears only when it helps.** With no archived note or just one, the search action is left out of the app bar; from two notes on, it shows up on its own.
- **The archive always starts at the top.** Opening the archive returns to the first note, and changing the sort, opening the search or clearing it jumps back to the first row instead of keeping an offset that belonged to the old list.
- **Clearing the archive search closes the keyboard.** The clear button in the search field empties the query and puts the keyboard away, so the list gets the screen back right away.

### Fixed

- The archive no longer flashes in the wrong order when it opens: it paints the list once, already sorted by the order you chose, instead of showing the default order for a frame and animating every card into place.
- TalkBack improvements: status messages such as "Note archived" are announced, screen headings and the pinned-note button are described correctly, and small controls meet the 48 dp touch target.
- Widget settings are remembered: removing a widget and adding it again starts with the same theme, accent, sizes and tap action instead of the defaults.

## [0.5.1] - 2026-11-09

### Fixed

- The settings and About toolbars stay put while the page scrolls: they no longer collapse away, and the scroll no longer stutters or jumps.
- The "Your device language" row in the language picker is as tall as the other language rows.
- Language rows name the language instead of the country: "Polski — Polish", "Deutsch — German", and so on.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.5.1) · [Changes since v0.5.0](https://github.com/yungsamd17/singlenote/compare/v0.5.0...v0.5.1)

## [0.5.0] - 2026-11-07

### Added

- Six new languages — German, Spanish, French, Polish, Russian and Simplified Chinese — with a standard Android language picker and a per-app language entry.
- Monochrome mode turns every color into a shade of gray, overriding the accent and wallpaper colors while destructive actions and the character limit still read as warnings.
- Optional first line of the note as the pinned notification title; the body then starts at the second line instead of repeating it.
- Tooltips on every icon-only button, including the editor, archive, About and legal screens.
- The character-limit indicator now fades and scales in instead of popping over the text.
- The app-icon picker warns that changing the icon closes Singlenote.

### Changed

- Settings and About toolbar titles are centred like the note and archive screens, with content scrolling under an opaque bar.
- Motion is unified through shared durations: navigation, the Done/Pin swap, archive timestamps and the editor entrance.
- The archive now opens from the left and leaves the same way, matching its place beside the note.

### Fixed

- The editor's archive and delete buttons no longer sit on top of each other, so archiving is reachable again.
- The character-limit shake is drawn without re-laying out the screen, and its colors no longer snap.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.5.0) · [Changes since v0.4.6](https://github.com/yungsamd17/singlenote/compare/v0.4.6...v0.5.0)

## [0.4.6] - 2026-11-05

### Fixed

- Home-screen widget no longer stays stuck on the loading spinner. The widget is now rendered by the app itself instead of a background job that some phones never ran, so it fills in as soon as you add it — no app start needed.
- Widget style changes (theme, accent, size, opacity) now apply right away when you press "Update widget", instead of waiting for the next app start.
- Fixed a crash when adding a widget to the home screen, and when changing the app language.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.4.6) · [Changes since v0.4.5](https://github.com/yungsamd17/singlenote/compare/v0.4.5...v0.4.6)

## [0.4.5] - 2026-11-04

### Fixed

- Home-screen widget no longer stays stuck on the loading spinner. On some phones the app process was reclaimed before the widget could render, which left the widget loading forever; the widget session is now re-established when the app is opened (and after a reboot or app update), so a stuck widget recovers on its own.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.4.5) · [Changes since v0.4.4](https://github.com/yungsamd17/singlenote/compare/v0.4.4...v0.4.5)

## [0.4.4] - 2026-11-04

### Added

- Hidden diagnostics screen: tap the version line five times in About to copy build, device, widget and crash details, so a broken release install can be reported without a debugger.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.4.4) · [Changes since v0.4.3](https://github.com/yungsamd17/singlenote/compare/v0.4.3...v0.4.4)

## [0.4.3] - 2026-11-03

### Bug Fixes

- Stop the home screen widget from getting stuck on the loading spinner after the v0.4.2 widget rewrite.

[Release notes](https://github.com/yungsamd17/singlenote/releases/tag/v0.4.3) · [Changes since v0.4.2](https://github.com/yungsamd17/singlenote/compare/v0.4.2...v0.4.3)

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
