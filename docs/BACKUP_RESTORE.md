# Backup and restore

Packaged Windows installs keep operational data in `%LOCALAPPDATA%\FlowersForever\data`. Portable JAR runs keep `./data` unless `FLOWERFARM_DATA_DIR` is set.

## In the app (no closing needed)

The **File** menu has three backup commands:

- **Back up now…** saves a backup while you keep working. FlowersForever takes a consistent copy of the open database, adds the `attachments` folder (so photo metadata and the verified bytes of each photo stay together), writes the ZIP plus its `.sha256` file, and then re-opens the ZIP to check every file before it says "backed up". The default folder is `Documents\FlowersForever Backups`; you can choose another folder or a USB drive.
- **Restore from a backup…** checks the chosen backup first (checksum, contents, and that the records open in this version; a backup from a newer version is refused). It then saves a backup of your current records, closes FlowersForever, swaps the records in, and reopens. If the restored records do not open, FlowersForever puts the records you had before back by itself. The records from before a restore are also kept beside the data folder as `data.before-restore-<date>`. The result is shown once and recorded in Activity History.
- **Automatic backups…** turns on daily or weekly backups to a folder you choose (ideally a USB drive or a synced folder on another device) and keeps the newest N automatic backups (default 10). Manual and before-restore backups are never deleted automatically. If the drive is unplugged, FlowersForever only notes it in the status bar and tries again later; after three missed backups it shows a warning.

Backups from the app and from the scripts below use the same ZIP layout and `.sha256` check file, so either can restore the other's backups.

## Backup scripts

Close the app before either operation. This is required for a transactionally consistent embedded-database copy.

```powershell
powershell -File scripts/backup-data.ps1
powershell -File scripts/restore-data.ps1 -Archive 'C:\path\FlowersForever-YYYYMMDD-HHMMSS.zip'
```

The backup script creates a ZIP plus a SHA-256 sidecar. Restore refuses a missing or mismatched checksum, refuses a database still in use, validates that the archive contains the H2 store, and retains existing data beside the restored directory. The database includes its Flyway migration history, while content-addressed production-task photos live in the `attachments` folder inside the same data directory; one backup therefore retains both photo metadata and verified bytes. Keep at least one daily and one weekly copy on a different device. Test a restore quarterly on a non-production Windows account, including opening one restored attachment.

For a portable deployment, pass the same explicit directory to both scripts with `-DataDir`, or set `FLOWERFARM_DATA_DIR`. Connector mirrors, exports, logs, and the database are included. Credentials supplied through environment variables or an external properties file are not included and must be recovered from the password manager.

## Automatic backups before updates

Before installing an automatic update, FlowersForever closes the database and writes
`FlowersForever-before-<version>-YYYYMMDD-HHMMSS.zip` plus its `.sha256` file to
`Documents\FlowersForever Backups` (same layout as `backup-data.ps1`). The update only
continues after the ZIP is re-read and every file matches. These backups are never
deleted automatically and can be restored with `restore-data.ps1`. See
[Automatic updates](AUTO_UPDATES.md).
