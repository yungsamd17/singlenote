# Archive and backups

The archive holds notes moved out of the main editor. Open it from the editor's
archive button. Each card shows the complete note, its time, and Restore and
Delete actions. The more menu contains sorting, import, export, and Clear archive.

## Timestamps and sorting

A note's first archive records its archive time and shows **Archived**. Editing
it after restoring it records an edit time; subsequent restores and archives do
not change that edit time unless the content changes. Cards normally show
**Edited** when an edit time exists and **Archived** otherwise. Relative labels
are refreshed when the screen resumes; older entries show a date.

**Sort notes** saves both the selected sort and order:

- **Last changed** (default) uses the edit time if present, otherwise the archive time.
- **Date archived** uses the archive time and always displays **Archived**.
- **Title** sorts the note text alphabetically; its header still uses the normal time.

Date sorts offer newest or oldest first; title offers A to Z or Z to A. Opening
the archive or changing sort/order returns the list to the top. Phones stay in
portrait for 1.0.0; Android 16 ignores the lock on screens 600 dp and wider, so
tablet/foldable rotation is not supported yet.

Existing archived notes migrated from the previous database use their old
`updatedAt` as the best available archive time and have no recorded edit time.
An edit timestamp already present in a development-version database is retained;
the app cannot reliably reconstruct the note's earlier history.

## Restore and delete

If the main note is empty, Restore moves the selected note to the editor. When
there is an active note, choose **Cancel**, **Swap** (archive the current note),
or **Replace** (remove the current note). Replace and Swap show an Undo snackbar
on the main screen. Undo brings back the previous main note and returns the
incoming note to the archive, including edits made during the Undo window.

Delete and Clear archive also offer a short Undo window. Pending archive deletions
are hidden while the app waits and the latest deletion can be undone; confirming
another deletion does not cancel an earlier one. Undo is temporary and in memory;
it is not a backup or durable history.

## Export and import

Open the archive more menu and choose **Export notes**:

- **JSON** exports archived notes in Singlenote's versioned format. Each entry
  includes the full content, first line as `title`, creation/update times, and
  nullable edit/archive timestamps. Use this format to import notes back into
  Singlenote.
- **Plain text** exports note contents separated by `---`. It does not contain
  timestamps or structured title metadata and cannot be imported by the JSON importer.

Use Android's document picker to choose where to save the file. **Import notes**
accepts a compatible Singlenote JSON archive and adds its notes to the archive
with new database IDs. It does not replace the current archive. Save a copy of
any important notes before testing import or delete behavior.

The JSON format lives in `util/ArchiveFile.kt` (`buildArchiveJson` /
`parseArchiveJson`) and is covered by `ArchiveFileTest`. It carries a `format`
marker and a `version` number: a file from another app, a malformed file or a
newer archive version is rejected with the "import failed" message instead of
importing half the notes. Importing the same file twice adds the notes twice.

These exports include **archived notes only**: they do not include the active
note, app settings, widgets, or notification preferences. Full app backup/restore
is planned for after 1.0.0. Export files are readable, unencrypted files; keep them in a
location you trust. The app does not upload them for you.

See [BUILDING.md](BUILDING.md) for preview builds and device verification, and
[V1.0.0-PLAN.md](V1.0.0-PLAN.md) for the broader backup/restore goal.
