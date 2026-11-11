# Singlenote TODO

Backlog from the 2026-09-18 outside-scrutiny pass, re-verified against source on
2026-11-11 (`v0.5.1`, `versionCode 22`). Every open item below was confirmed in
code; the closed list at the bottom records what shipped and where. The 1.0 gate
lives in [`V1.0.0-PLAN.md`](V1.0.0-PLAN.md).

## Do next — 1.0 blockers

1. [ ] **Target Android 16 (API 36)** — `compileSdk`/`targetSdk` are 35
   (`app/build.gradle.kts:13,23-24`). Play has required 36 for new apps and
   updates since 2026-08-31, so no 1.0.0 submission can pass until this lands.
   Re-test insets, the now-default predictive back and dynamic color after the
   bump.
2. [ ] **Room schema + migration coverage** — `exportSchema = false`
   (`data/NotesDatabase.kt:28`) and no `app/schemas/`. Generate the baseline JSON
   from a clean build, commit it, flip the flag, and add `MigrationTestHelper`
   tests for 1→2→3 plus a real DAO test for the unique `activeSlot` index.
3. [ ] **Test gate** — add coverage tooling (JaCoCo/Kover), `PinNotification`
   branches, archive import/export tests, real DAO tests, and one instrumented
   archive flow. The plan's ≥70% target must be measured, not assumed.
4. [ ] **Store assets** — phone screenshots (≥2, 1080×1920), 1024×500 feature
   graphic, Play short (≤80 chars) + full description, F-Droid `fastlane/`
   metadata. Upload to the stores, then link; do not commit.

Portrait remains locked for 1.0.0 by decision; landscape/tablet moves to the
post-1.0.0 section. Full backup/restore is deferred by decision. Docs were
reconciled with v0.5.1 on 2026-11-11.

## Engineering follow-ups

- [ ] **Coverage tooling** — no JaCoCo/Kover task, so the plan's ≥70% goal cannot
  be measured. Add it, then target `NoteRepository`, `ArchiveViewModel` and
  `NoteViewModel` first.
- [ ] **`PinNotification` tests** — notification builder branches (blank note,
  disabled notifications, title-first-line, lock-screen visibility, channel
  migration) have zero coverage. Extract the builder from the side effects so it
  can be tested on the JVM.
- [ ] **Archive file I/O is untestable** — JSON build/parse lives in
  `ArchiveScreen.kt:163-183,602-611`. Move it to `util/ArchiveFile.kt` and add
  round-trip, malformed-file, version-mismatch and duplicate-import tests.
  Imports currently append duplicates on every run.
- [ ] **Real DAO tests** — `NoteRepositoryTest` uses a fake DAO; the SQL in
  `NoteDao` (archive/restore/swap/replace/undo) never runs against SQLite in CI.
- [ ] **Instrumented archive flow** — no `app/src/androidTest/` despite the
  configured runner (`app/build.gradle.kts:27`). One end-to-end archive →
  restore → undo test closes the largest untested user path.
- [ ] **Predictive back** — under targetSdk 36 predictive back is on by default;
  verify the NavHost back stack, archive pair transition and dialogs on Android
  16 after the bump. Opt out only if the gesture behaviour breaks.
- [ ] **Dependency freeze** — `composeBom 2024.12.01`, Glance 1.1.1, DataStore
  1.1.1, WorkManager 2.7.1, Navigation 2.8.5, lifecycle 2.8.7, activity-compose
  1.9.3, core-ktx 1.15.0. Re-open after compileSdk 36; keep Dependabot
  security-only until then.
- [ ] **16 KB page sizes** — confirm the release APK aligns 16 KB ELF segments
  (`zipalign -c -P 16` or the Play pre-launch report). Pure Kotlin should pass,
  but Play requires it for apps targeting 35+.
- [ ] **Performance guardrail** — no StrictMode run and no startup measurement.
  Add a debug-only StrictMode harness and record a cold-start baseline.
- [ ] **Widget regression watch** — re-test on MIUI/One UI after any Glance bump;
  the in-process render is deliberately compensating for their worker behaviour.

## Feature follow-ups

- [ ] **Archive search** — debounced, case-insensitive filter over archived note
  text. `ArchiveScreen.kt` has sort state but no query state.

## Post-1.0.0 (decided 2026-11-11)

- [ ] **Full backup/restore** — the active note, preferences, widget styles and
  accent are not exportable; `allowBackup="false"` makes uninstall irreversible.
  The archive JSON export stays the only backup in 1.0.0. Revisit with dedupe and
  file-size limits.
- [ ] **Landscape / tablets / foldables** — remove the portrait lock
  (`AndroidManifest.xml:34`) once the layouts pass the QA matrix; multi-window is
  already enabled.
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
  choice; landscape, tablets and foldables are post-1.0.0.
- **Dependencies stay frozen** except security updates until compileSdk 36.

## Closed in the 2026-09-18 pass (verified 2026-11-11)

- **Privacy** — offline-first headline with the GitHub fetch disclosed;
  `allowBackup=false`; PRIVATE notification + lock-screen toggle; release-asset
  matching fixed; every shipped dependency listed in `THIRD_PARTY_LICENSES`.
- **Crashes/data loss** — async theme gate (no UI-thread `runBlocking`);
  scrollable editor; restore-conflict dialog split and wrapping; `@Transaction` +
  write `Mutex` + unique `activeSlot` index with duplicate collapse
  (`MIGRATION_1_2`).
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
  full reconciliation with v0.5.1 on 2026-11-11 (README, CONTRIBUTING, AGENTS,
  ARCHITECTURE, BUILDING, ARCHIVE, TRANSLATE-APP, RELEASES,
  EDITOR-AND-SETTINGS).
