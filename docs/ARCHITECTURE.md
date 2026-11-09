# Architecture and persistence

Singlenote is a native Kotlin/Compose Android app with one active note, an archive,
and three ways to surface the active note outside the editor. It supports Android
10+ (API 29), compiles/targets API 35, and uses JVM 17. Dependencies are pinned in
`gradle/libs.versions.toml`; follow the existing stack when changing one area.

## Entry points and navigation

`SinglenoteApplication` owns the shared repository. `MainActivity` applies the
persisted theme and bundled app font, coordinates locale transitions, and hosts
Navigation Compose. Note/archive/settings ViewModels are activity-scoped so
pending saves and undo work survive in-app navigation.

The settings routes are `settings`, `settings/appearance`, `settings/typography`,
`settings/language`, `settings/notifications`, and `settings/about`. The editor,
archive, and legal screens retain their own routes. Widget actions arrive through
validated intents and return to the note screen.

## Note data flow

```text
Compose screen → ViewModel → NoteStore / ArchiveStore → NoteRepository → NoteDao → Room
                                                   ↘ PreferencesStore → NotePreferences → DataStore
                                                   ↘ ACTION_NOTE_UPDATED → widget / tile / notification
```

`Note.kt` is the Room entity, including nullable `editedAt` and `archivedAt` timestamps. `NoteDao.kt` owns queries and transactional active /
archive operations. `AppDatabase` in `NotesDatabase.kt` registers schema migrations.
The unique active-slot index protects the one-active-note invariant. A repository
mutex serializes compound note mutations; DAO transactions handle their database
boundaries. Schema version 3 adds archive and edit timestamps; existing archived notes use the
previous `updatedAt` as their archive time and start with no edit time. Update
schema/migrations and tests together when data changes.

`NoteViewModel` updates editor state immediately, debounce-saves after 500 ms,
and flushes on lifecycle stop. Stored notes remain intact when changing font or
size. Repository content-change broadcasts are throttled with a trailing update;
archive, restore, delete and other explicit mutations notify immediately. Avoid
adding a second writer or bypassing repository invalidation.

Swap and Replace capture an `ArchiveRestoreUndo` snapshot inside the same Room
transaction as the state change. The main ViewModel owns the undo snackbar and
flushes incoming-note edits before reversing it. Undo archives the incoming row
and restores the original active row, including its ID and timestamps after
Replace. The transaction refuses to overwrite a different active note. Delayed
archive deletions also re-check row state, so they cannot delete a note recovered
to main during an undo window. These snapshots are in memory, like the existing
editor undo state; they are not a durable history or a database schema change.

## Preferences and locale

`PreferencesStore` abstracts DataStore and `NoteStore` exposes settings to
ViewModels. Theme, accent, font type, text size, archive sort/order, notification/lock-screen switches,
dynamic colors, AMOLED, app font and launcher icon choice use `NotePreferences`.
Settings ViewModels generally expose flows with `SharingStarted.WhileSubscribed(5000)`;
the editor observes text size eagerly so changes are ready when returning from Settings.
Preserve existing keys and stored values when changing their presentation.

Language is separate: `AppLanguage` uses Android per-app locales on Android 13+
and SharedPreferences plus a context/resource override on Android 10–12. An empty
tag follows the system. The activity handles locale/layout-direction changes in
place and crossfades a snapshot over the themed window. New languages normally
need resources, one list entry, and a locale-config entry; see
[TRANSLATE-APP.md](TRANSLATE-APP.md).

Launcher icon variants are packaged adaptive icons selected through activity
aliases. `AppIcon` enables the destination alias before disabling the others with
`DONT_KILL_APP`. After a user selection changes the active alias, the activity
closes its task so the user can reopen it from the launcher. Preset icons use
packaged colors; the dynamic icon uses an Android 12+ system wallpaper accent.
Launcher caching and monochrome themed icons remain under launcher control.

## External note surfaces

- `widget/`: Glance widget, per-widget configuration in `WidgetStyleStore`,
  scrollable note content, and update receiver. A launcher controls outer size.
  The store keeps the settings of every widget and the settings changed last,
  so a widget that is added again starts with them.
- `tile/`: quick-settings service displays a note preview and launches the editor for a new note, archiving the active note first.
- `notify/`: pinned-note notification, content visibility, actions and boot/update
  restoration. Turning off notifications also clears the pinned preference.

Keep notification permission handling, intent validation and broadcast package
scoping intact. Follow-app widget accents refresh when the app accent changes.

## Shared UI and text

`SettingsGroup` applies segmented card shapes with rounded outer corners; `SettingsSectionHeader` styles
category labels. Shared overflow measurement controls settings scrolling.
`NoteEditorField` retains local selection/composition state and scrolls the cursor
into the available viewport while preserving manual scrolling between edits.
`LinkedText` / `MarkdownText` render links and lightweight markup from resources.
All user-visible copy belongs in `res/values/strings.xml` with matching Slovak keys.
Google Sans Flex is bundled under SIL OFL 1.1; its license ships in assets.

## Tests and privacy boundaries

JVM tests use store/DAO fakes instead of Android storage. Interface changes must
update every implementation and fake. UI timing, keyboard behavior, launcher
scrolling and locale transitions additionally need device verification.

Notes/settings are unencrypted in private app storage; cloud backup is disabled.
There are no accounts, analytics or tracking. `GithubReleases` fetches public
release notes/update metadata only on demand. The legal statements in
[PRIVACY.md](PRIVACY.md) and [TERMS.md](TERMS.md) describe the user-facing policy.
