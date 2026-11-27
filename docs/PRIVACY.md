# Privacy Policy

Singlenote is offline-first and collects nothing.

## Data collected

None. No accounts, no analytics, no ads, no tracking identifiers.

## On your device

Your note, archive and settings stay in the app's private storage on your device. They are stored unencrypted (plaintext database and preferences): anyone with access to your unlocked device can read them. Automatic app backup is disabled. Uninstalling the app removes its private data. Files you explicitly export are stored wherever you choose and remain there until you delete them. This includes full backups (notes, app settings, widget settings and language) created from Settings → Backup & restore.

## Copy and share

Copy puts plain text on the system clipboard; the system may show a preview, and it may linger in keyboard history. Share sends plain text to the app you choose. The system share sheet also offers **Save as note**: text you share into Singlenote becomes a new note on this device (the current note is archived first, as with New note). Nothing is uploaded. Backup and notes-only export write unencrypted JSON or plain-text files to the location you select through the Android document picker; no storage permission is requested. A document provider may store or synchronize those files according to its own settings. Importing a backup reads the file you pick and can replace the selected data on this device. See [ARCHIVE.md](ARCHIVE.md).

## Permissions

- **Notifications (optional):** only to pin your note so it stays visible.
- **Foreground service (only while a note is pinned):** keeps the pinned notification and its lock/unlock updates alive on devices that stop background work. It only shows the pinned-note notification.
- **Boot receiver:** re-posts the pinned note after a restart.
- **Document picker (on demand):** backup and import use the system file picker, which grants access to the single file you choose. No storage permission is requested or held.

The app never asks for location, camera or microphone access.

## File and share access

These are optional actions, not app permissions. Backups use the Android document picker, which opens only the file you choose. Sharing text into Singlenote through the system share sheet needs no access to your files; the text becomes a new note on this device.

## Network

Checking for updates contacts GitHub once at launch when **Check for updates on launch** is on (the default in Settings → Updates), and whenever you open the changelog or tap **Check for updates**. Turn launch checks off in Settings → Updates to stop the automatic request. No accounts, no analytics, no tracking; your note and settings are never sent anywhere.

## Contact

Questions: yungsamd@proton.me.

Full Privacy Policy: [github.com/yungsamd17/singlenote/blob/main/docs/PRIVACY.md](https://github.com/yungsamd17/singlenote/blob/main/docs/PRIVACY.md)
