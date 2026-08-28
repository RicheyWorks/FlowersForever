# Backup and restore

Packaged Windows installs keep operational data in `%LOCALAPPDATA%\FlowersForever\data`. Portable JAR runs keep `./data` unless `FLOWERFARM_DATA_DIR` is set.

Close the app before either operation. This is required for a transactionally consistent embedded-database copy.

```powershell
powershell -File scripts/backup-data.ps1
powershell -File scripts/restore-data.ps1 -Archive 'C:\path\FlowersForever-YYYYMMDD-HHMMSS.zip'
```

The backup script creates a ZIP plus a SHA-256 sidecar. Restore refuses a missing or mismatched checksum, refuses a database still in use, validates that the archive contains the H2 store, and retains existing data beside the restored directory. The database includes its Flyway migration history, so restore always returns the records and schema version together. Keep at least one daily and one weekly copy on a different device. Test a restore quarterly on a non-production Windows account.

For a portable deployment, pass the same explicit directory to both scripts with `-DataDir`, or set `FLOWERFARM_DATA_DIR`. Connector mirrors, exports, logs, and the database are included. Credentials supplied through environment variables or an external properties file are not included and must be recovered from the password manager.
