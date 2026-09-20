# Singlenote TODO

Review backlog from an outside-scrutiny pass (2026-09-18): four reviewers
(platform, security/privacy, UX/accessibility, repo hygiene) tore the app
down. Everything below was verified against the source. Check items off as
they land; the `v1.0.0` checklist stays in [`docs/v1.0.0-plan.md`](docs/v1.0.0-plan.md).

## Critical — privacy credibility

- [x] Soften "fully offline" to "offline-first" everywhere (README, About,
  store copy). Changelog/update checks call `api.github.com` with a custom
  user agent (`util/GithubReleases.kt:25-40`, `ui/AboutScreen.kt:92,120`);
  keep the fine-print disclosure, fix the headline.
- [x] Backup rules: `allowBackup=true` with no extraction rules
  (`AndroidManifest.xml:12`) backs up the plaintext Room DB + DataStore to
  Drive, contradicting "uninstall wipes everything." Set `allowBackup=false`
  or exclude the DB, then document it.
- [x] Lockscreen leak: pinned notification is `VISIBILITY_PUBLIC` with full
  `BigTextStyle` at `IMPORTANCE_DEFAULT` (`notify/PinNotification.kt:31-35,74-80`).
  Add a visibility toggle (or `PRIVATE` minimum) and drop the channel to `LOW`.
- [x] Download action: `GithubReleases.kt:72` matches an asset literally named
  `app-release.apk`, but CI publishes `singlenote-vX.Y.Z-release.apk` — the
  in-app Download never downloads. Match by suffix or link the release page,
  and never pipe unverified APKs (pin SHA-256 or go Play-only).
- [x] `THIRD_PARTY_LICENSES` omits Room/Compose/Glance/DataStore/Navigation/
  Coroutines (all Apache-2.0 with attribution requirements) while claiming
  full coverage. List every shipped dependency.

## Critical — crashes and data loss

- [x] `runBlocking` DataStore read on the UI thread (`MainActivity.kt:194-195`,
  every cold start and resume). Use the SplashScreen API or an async theme gate.
- [x] Fixed-height no-scroll editor clips data (`ui/NoteScreen.kt:227-229,689-691`).
  At large font scales pasted content is unreachable and silently truncated.
  Cap length if needed, but let the field scroll.
- [x] Restore-conflict dialog crams Cancel/Swap/Replace into `confirmButton`
  (`ui/ArchiveScreen.kt:152-172`). Overflows at large font scale with wrong
  screen-reader order — split into dismiss/confirm/neutral.
- [x] Non-atomic writes: `saveActive`/`swapWithActive` are read-modify-write
  with no `@Transaction` (`data/NotesDatabase.kt:78-89,124-132`); concurrent
  flush + debounce can duplicate the ACTIVE row. Add `@Transaction`, a unique
  index, and serialize saves with a `Mutex`.

## Release pipeline

- [x] CI lints debug but ships release (`build.yml:96` runs `lintDebug` while
  release has minify+shrink). Add `lintRelease` — the plan already demands it.
- [x] Debug APK ships next to release with no checksums and nothing stopping
  users installing it. Quarantine debug to workflow artifacts or publish
  SHA-256 alongside.
- [x] Fork PRs can't build: the keystore step `base64 -d`s empty secrets on
  forks, then release signing fails (`build.yml:81-92`). Guard on secret
  presence with a release-only signing fallback.
- [x] Supply-chain basics: `contents: write` is over-broad, actions float on
  moving tags, no wrapper validation or artifact attestation for sideloaded APKs.

## Data and architecture

- [x] One app-scoped DataStore singleton instead of N instances on one file
  (`NotesDatabase.kt:65`, `MainActivity.kt:69-80`, `PinNotification.kt:46`).
- [x] Flush ordering: `flushSave()` is fire-and-forget while shade-Archive
  assumes it landed (`ui/NoteScreen.kt:163-186`, `NoteViewModel.kt:93-96`).
  Make it joinable or covered by the save `Mutex`.
- [x] Widget update storm: every debounced keystroke-save broadcasts
  `NOTE_UPDATED` → full Glance rebuild. Throttle/coalesce to 2–5 s.
- [x] `NoteChangedReceiver` does Room/Glance work on `Dispatchers.Default`
  with an unstructured scope and a silent catch-all
  (`widget/NoteChangedReceiver.kt:21-26`). Use `IO`, `goAsync`, log failures.
- [x] Tests stop where risk starts: zero coverage for `NoteRepository`,
  DAO queries, `ArchiveViewModel` swap/replace, `PinNotification` branches,
  `GithubReleases.parse`. No `androidTest/` dir despite the declared runner.
  ( Landed: repository, DAO-adjacent, swap/replace, parse. Still open:
  `PinNotification` branches and an instrumented archive-flow test. )
- [x] Dependencies ~9 months stale (Kotlin 2.1.0, AGP 8.7.3, BOM 2024.12.01),
  no Dependabot/Renovate. The 2.1→2.2 + AGP 8→9 jump breaks KSP all at once.
  ( Landed: Dependabot config, then the coordinated jump itself — Kotlin 2.3,
  AGP 9, KSP 2.3, Room 2.8, coroutines 1.11, Gradle 9. Held: `composeBom`
  (needs SDK 37) and KSP-less Kotlin 2.4. Version updates frozen since,
  security-only. )

## Accessibility and UX

- [x] `selectable` abused as `clickable` on Settings/About rows
  (`SettingsScreen.kt:260,291`, `AboutScreen.kt:459`) — TalkBack announces
  fake selection states. Use `clickable(role=Button)` / `toggleable`.
- [x] Empty-note archive/delete FABs are alpha-only, still focusable and
  tappable (`ui/NoteScreen.kt:540-575`). Set `enabled=hasContent`.
- [x] Radio rows miss 48dp and `RadioButton(onClick=null)` isn't focusable
  (`SettingsScreen.kt:340-345`). 48dp rows, `Role.RadioButton`, `selectableGroup`.
- [x] Editor has no accessible label: the hint overlay is invisible to TalkBack
  (`ui/NoteScreen.kt:693-825`). Add `semantics { hint = … }`.
- [x] No Undo anywhere — archive/delete/clear are irreversible with
  informational-only snackbars.
- [x] Archive snackbar ignores insets (`ArchiveScreen.kt:107`) and sits under
  the gesture bar; match the note screen's lifted host.
- [x] Changelog error state is a dead end (`ui/AboutScreen.kt:281-285`) — add
  a Retry button instead of dismiss-and-reopen.
- [x] Blank first frame while prefs load (`NoteScreen.kt:432-435`,
  `SettingsScreen.kt:98-101`) — spinner with a "Loading" label for TalkBack.
- [x] Widget: no preview/description, 2x1 cell rendering 6 lines that always
  ellipsize, hardcoded English fallback (`SinglenoteWidget.kt:66`) instead of
  `strings.xml`.
- [x] Tile subtitle has no TalkBack distinction between label and subtitle
  (`tile/NoteTileService.kt:37`).

## Hardening nits

- [x] No tapjacking defense (`filterTouchesWhenObscured`) on one-tap
  Archive/Delete/Pin.
- [x] Plaintext SQLite + prefs, no encryption story — fine if disclosed next
  to the "privacy first" claim, not fine silent.
- [x] Copy isn't `EXTRA_IS_SENSITIVE` (API 33+) and lingers in keyboard history
  (`ui/NoteScreen.kt:250-251`); Share is one-tap `text/plain` to any target.
- [x] `singleTask` with no `onNewIntent`; `BOOT_COMPLETED` without
  `MY_PACKAGE_REPLACED` (pin vanishes on every update); no explicit
  `usesCleartextTraffic=false` while `http://` links render.
- [x] `portrait` lock + `adjustNothing` bans foldables, tablets, landscape and
  multi-window (`AndroidManifest.xml:23-24`). ( Landed: portrait lock removed,
  multi-window left enabled. `adjustNothing` deliberately kept — the editor's
  keyboard glide depends on a non-relayouting window. )
- [x] Deprecated `URL()` + raw `HttpURLConnection` (`GithubReleases.kt:27`);
  `openUrl()` with no `ActivityNotFoundException` guard (`AboutScreen.kt:491-493`);
  `@Database(exportSchema=false)` ships the next migration blind.
  ( Landed: fetch modernized, link guard added. `exportSchema` stays `false`
  until the baseline JSON is generated by a real build — debug/release KSP
  race on the shared schema dir otherwise; the `room.schemaLocation` arg is
  already in place. )
- [x] Issue template asks for "browser" on a native app, no version/device/
  logcat fields; PR template placeholder fits bots, not humans.
- [x] Docs-only CI skip forgets extensionless `THIRD_PARTY_LICENSES`
  (`build.yml:34-35`).

## Docs drift (recurring)

- [x] README + plan version references rot on every release (`v0.3.3` named
  while `0.3.4` shipped). Fold the bump into the release checklist.
- [ ] Screenshots / feature graphic / store copy still missing (plan item).
