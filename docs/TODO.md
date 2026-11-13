# Singlenote TODO

Backlog from the 2026-09-18 outside-scrutiny pass, re-verified against source on
2026-11-13 (`v1.0.0`, `versionCode 23`). Every open item below was confirmed in
code; the closed list at the bottom records what shipped and where. The 1.0 gate
lives in [`V1.0.0-PLAN.md`](V1.0.0-PLAN.md).

## Do next — 1.0 blockers

1. [x] **Room schema + migration coverage** — `exportSchema = true`
   (`data/NotesDatabase.kt:31`) with the `app/schemas/` v3 baseline committed.
   `MigrationTest` builds the v1/v2 schemas by hand, runs the production
   migrations through a real `SupportSQLiteOpenHelper.onUpgrade`, and diffs the
   result against a fresh Room v3 database; `NoteDaoTest` runs the DAO against
   SQLite, including the unique `activeSlot` guard. 202 JVM tests pass locally.
2. [x] **Test gate** — coverage is measured and gated (Kover, ≥70 % line
   coverage of the app logic, enforced by `koverVerifyDebug` in CI), the
   `PinNotification` branches and the archive import/export are covered by JVM
   tests. The last piece is one instrumented archive flow on a device, tracked
   under Engineering follow-ups.
3. [ ] **Store assets** — phone screenshots (≥2, 1080×1920), 1024×500 feature
   graphic, Play short (≤80 chars) + full description, F-Droid `fastlane/`
   metadata. Upload to the stores, then link; do not commit.

Target SDK 36 landed (`compileSdk`/`targetSdk` 36). Portrait remains on phones for
1.0.0; Android 16 ignores the lock at 600 dp and wider, so tablet/foldable rotation
is a documented limitation. Full backup/restore is deferred by decision. Docs were
reconciled with v1.0.0 on 2026-11-13.

## Engineering follow-ups

- [x] **Coverage tooling** — Kover 0.9.11 is wired in (`app/build.gradle.kts`)
  and `koverVerifyDebug` runs in CI: the app logic (data, util, notification
  rules, the three ViewModels) has to stay at or above 70 % line coverage. The
  scope and its exclusions are documented in `docs/BUILDING.md`. Compose UI,
  Android components and Room's generated code stay outside the gate — they
  need a device or a rendering tree.
- [x] **`PinNotification` tests** — the notification rules moved to
  `notify/PinnedNotificationContent.kt` (`pinnedNotificationContent`): no note,
  a blank note, disabled notifications, an unpinned note, the title-first-line
  rule and the lock-screen visibility. `PinNotificationChannelTest` covers the
  channel creation and the removal of the legacy channels.
- [x] **Archive file I/O is testable** — the JSON build/parse moved to
  `util/ArchiveFile.kt` (`buildArchiveJson` / `parseArchiveJson`) with
  `ArchiveFileTest` covering the round trip, malformed JSON, a foreign format,
  an unsupported version and notes without content or timestamps.
- [ ] **Duplicate imports** — importing the same export twice appends the notes
  a second time. Decide whether an import should skip notes that are already in
  the archive (same content and creation time) and report how many were added.
- [x] **Real DAO tests** — `NoteDaoTest` runs `NoteDao` against a real SQLite
  database (Robolectric): the unique `activeSlot` guard, archive/restore/swap/
  replace/undo transactions and the import path. `MigrationTest` covers
  `MIGRATION_1_2` / `MIGRATION_2_3`.
- [ ] **Instrumented archive flow** — no `app/src/androidTest/` despite the
  configured runner (`app/build.gradle.kts:27`). One end-to-end archive →
  restore → undo test closes the largest untested user path.
- [ ] **Predictive back** — under targetSdk 36 predictive back is on by default;
  verify the NavHost back stack, archive pair transition and dialogs on Android
  16 after the bump. Opt out only if the gesture behaviour breaks.
- [ ] **Dependency freeze** — `composeBom 2024.12.01`, Glance 1.1.1, DataStore
  1.1.1, WorkManager 2.7.1, Navigation 2.8.5, lifecycle 2.8.7, activity-compose
  1.9.3, core-ktx 1.15.0. The v1.0.0 release ships this tested set; the bump
  rides the next release. Dependabot stays security-only until then.
- [x] **16 KB page sizes** — checked against the published `v1.0.0` release APK:
  `zipalign -c -P 16` passes and every native library in it (the AndroidX
  `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so`) has
  16 KB (0x4000) ELF LOAD alignment. Play requires this for apps targeting 35+.
- [ ] **Performance guardrail** — no StrictMode run and no startup measurement.
  Add a debug-only StrictMode harness and record a cold-start baseline.
- [ ] **Widget regression watch** — re-test on MIUI/One UI after any Glance bump;
  the in-process render is deliberately compensating for their worker behaviour.

## Feature follow-ups

- [x] **Archive search** — a search action in the top bar opens a field that
  filters the archive by note text as you type (case-insensitive, 150 ms
  debounced). The query lives in `ArchiveViewModel` so it survives rotation;
  export and clear still act on the whole archive, never just the matches.

## Post-1.0.0 (decided 2026-11-11)

- [ ] **Full backup/restore** — the active note, preferences, widget styles and
  accent are not exportable; `allowBackup="false"` makes uninstall irreversible.
  The archive JSON export stays the only backup in 1.0.0. Revisit with dedupe and
  file-size limits.
- [ ] **Landscape / tablets / foldables** — full layouts for 600 dp and wider;
  Android 16 already ignores the portrait lock there. Revisit the lock after the
  layouts pass the QA matrix.
- [ ] **Swipe actions** — add archive swipe with haptics + Undo; the current
  buttons stay until then.

## Decisions to keep

These were flagged in the original review and resolved by an explicit product
decision, not by deleting the code. Don't “fix” them again without revisiting the
trade-off:

- **Clipboard stays plain text** — `EXTRA_IS_SENSITIVE` makes Gboard mask the
  note as a password in suggestions/history (`ui/NoteScreen.kt:350-362`); the
  privacy policy discloses the clipboard caveat.
- **Pinned channel stays `IMPORTANCE_DEFAULT`** — the old `LOW` channel never
  surfaced on the lock screen and was replaced by `pinned_note_v4` with
  `VISIBILITY_PRIVATE` plus a user toggle
  (`notify/PinNotification.kt:27-51,107-110`).
- **`allowBackup="false"` stays** — plaintext DB/prefs must not go to Drive;
  backup is an explicit user export instead.
- **Full backup/restore stays out of 1.0.0** — archive export is the only backup
  for this release; the README and plan say so plainly.
- **Portrait lock stays for 1.0.0** — a deliberate scope decision, not a design
  choice; phones stay portrait, while Android 16 ignores the lock at 600 dp and
  wider (documented). Full layouts are post-1.0.0.
- **Dependencies stay frozen** except security updates until after 1.0.0.

## Closed in the 2026-09-18 pass (verified 2026-11-13)

- **Privacy** — offline-first headline with the GitHub fetch disclosed;
  `allowBackup=false`; PRIVATE notification + lock-screen toggle; release-asset
  matching fixed; every shipped dependency listed in `THIRD_PARTY_LICENSES`.
- **Crashes/data loss** — async theme gate (no UI-thread `runBlocking`);
  scrollable editor; restore-conflict dialog split and wrapping; `@Transaction` +
  write `Mutex` + unique `activeSlot` index with duplicate collapse
  (`MIGRATION_1_2`); exported Room schema plus `MigrationTest`/`NoteDaoTest`
  covering both migrations and the DAO against real SQLite.
- **Build** — target/compile SDK 36 (the current Play requirement), with the
  portrait lock documented as phone-only; toolchain checks stay green.
- **Release pipeline** — `lintRelease` in both workflows; debug APK only as a
  1-day artifact; fork signing guard; pinned actions, minimal permissions,
  wrapper validation, APK attestation, SHA-256 logging.
- **Data/architecture** — one DataStore singleton; `flushSave()` ordered through
  the save mutex; 1 s broadcast coalescing; `goAsync` + IO + logged failures in
  `NoteChangedReceiver`; repository/swap/parse tests; Kotlin 2.3 + AGP 9 + Room
  2.8 upgrade; Dependabot security-only.
- **Accessibility/UX** — real `clickable`/`toggleable` roles; disabled empty-note
  FABs; 48 dp radio rows with `Role.RadioButton`; editor hint semantics; Undo for
  archive/delete/clear; inset-aware archive snackbar; changelog Retry; labelled
  loading states; widget preview/description + localized fallback; tile TalkBack
  description.
- **Hardening** — tapjacking guard; plaintext storage disclosed; `singleTask`
  `onNewIntent` + `MY_PACKAGE_REPLACED` re-pin; `usesCleartextTraffic=false`;
  modern URL fetch + `ActivityNotFoundException` guard; issue/PR templates
  fixed; docs-only CI skip handles extensionless files.
- **Docs** — README/plan version refs, AGENTS.md conventions, archive docs, and a
  full reconciliation with v1.0.0 on 2026-11-13 (README, CONTRIBUTING, AGENTS,
  ARCHITECTURE, BUILDING, ARCHIVE, TRANSLATE-APP, RELEASES,
  EDITOR-AND-SETTINGS).
