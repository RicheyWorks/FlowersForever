# Backup and restore

Packaged Windows installs keep operational data in `%LOCALAPPDATA%\FlowersForever\data`. Portable JAR runs keep `./data` unless `FLOWERFARM_DATA_DIR` is set.

## In the app (no closing needed)

The **File** menu has three backup commands:

- **Back up now…** saves a backup while you keep working. FlowersForever takes a consistent copy of the open database, adds the `attachments` folder (so photo metadata and the verified bytes of each photo stay together), writes the ZIP plus its `.sha256` file, and then re-opens the ZIP to check every file before it says "backed up". The default folder is `Documents\FlowersForever Backups`; you can choose another folder or a USB drive.
- **Restore from a backup…** checks the chosen backup first (checksum, contents, and that the records open in this version; a backup from a newer version is refused). It then saves a backup of your current records, closes FlowersForever, swaps the records in, and reopens. If the restored records do not open, FlowersForever puts the records you had before back by itself. The records from before a restore are also kept beside the data folder as `data.before-restore-<date>`. The result is shown once and recorded in Activity History.
- **Automatic backups…** turns on daily or weekly backups to a folder you choose (ideally a USB drive or a synced folder on another device) and keeps the newest N automatic backups (default 10). Manual and before-restore backups are never deleted automatically. If the drive is unplugged, FlowersForever only notes it in the status bar and tries again later; after three missed backups it shows a warning.

Backups from the app and from the scripts below use the same ZIP layout and `.sha256` check file, so either can restore the other's backups.

## Recovery when records are damaged

At startup, FlowersForever can recover damaged records from a verified backup made for the
same records directory. App backups and Java updater backups write a small local receipt
outside the records folder, under `backup-settings/backup-receipts` (or the configured backup
settings location). The receipt binds the original directory to the archive's exact checksum.
Keep these settings when moving or protecting your installation. A shared backup folder alone
does not authorize using another farm's archive.

Automatic recovery skips archives with missing receipts, changed contents, or incompatible
database migrations. Older backups and backups made by the PowerShell script remain usable
through manual restore. Moving your records directory may also require manual restore because
the receipt identifies its original location.

Before swapping folders, FlowersForever saves recovery instructions with the exact retained
folder and records-directory identity. Interrupted recovery resumes before opening the farm;
the previous folder remains available until the restored records successfully open. A recovery
instruction belonging to another directory, or an older instruction without a known source,
stops startup with guidance and preserves the files for review. See [Data safety](DATA_SAFETY.md)
for the automated checks and remaining hardware acceptance work.

## Backup scripts

Close the app before either operation. This is required for a transactionally consistent embedded-database copy.

```powershell
powershell -File scripts/backup-data.ps1
powershell -File scripts/restore-data.ps1 -Archive 'C:\path\FlowersForever-YYYYMMDD-HHMMSS.zip'
```

The backup script creates a ZIP plus a SHA-256 sidecar. Restore refuses a missing or mismatched checksum, refuses a database still in use, validates that the archive contains the H2 store, and retains existing data beside the restored directory. The database includes its Flyway migration history, while content-addressed production-task photos live in the `attachments` folder inside the same data directory; one backup therefore retains both photo metadata and verified bytes. Keep at least one daily and one weekly copy on a different device. Test a restore quarterly on a non-production Windows account, including opening one restored attachment.

Saved **Farm setup** choices and specialty notebooks, including Aquatic cohorts, Tree blocks, Hive checks, Mushroom batches, Cranberry beds, and Pumpkin patches, are in the H2 database and return with this data-directory backup. The AI farm-data canonical record snapshots currently omit Farm setup and the specialty notebooks, so use the database backup to recover those records.

**Field science → Save an export…** stores a reviewed UTF-8 text copy in H2,
along with its selected CSRBT instrument, source URL and pinned commit, title,
optional filename and note, byte length, and SHA-256 of the saved text. Those
archive entries, and any separately confirmed **Record a finding** Knowledge
Log notes, return with the H2 backup. Saving an export does not move or attach
the original `.eco`, `.txt`, or `.csv` file. The backup script does not collect
browser-local drafts or files saved elsewhere, such as Downloads; retain those
separately if you need the original. A browser draft alone is not backed up.
Select an archived entry and choose **Save selected text…** to write a separate
UTF-8 file of the reviewed saved body. This read-only action is available to
VIEWER and preserves the stored BOM, line endings, and trailing spaces; it does
not recover the original browser file or include archive metadata. The app
asks before replacing an existing destination. Back up that new file
separately when it is saved outside the data directory.

For a portable deployment, pass the same explicit directory to both scripts with `-DataDir`, or set `FLOWERFARM_DATA_DIR`. Connector mirrors, exports, logs, and the database are included. Credentials supplied through environment variables or an external properties file are not included and must be recovered from the password manager.

## Automatic backups before updates

Before installing an automatic update, FlowersForever closes the database and writes
`FlowersForever-before-<version>-YYYYMMDD-HHMMSS.zip` plus its `.sha256` file to
`Documents\FlowersForever Backups` (same layout as `backup-data.ps1`). The update only
continues after the ZIP is re-read and every file matches. These backups are never
deleted automatically and can be restored with `restore-data.ps1`. See
[Automatic updates](AUTO_UPDATES.md).
