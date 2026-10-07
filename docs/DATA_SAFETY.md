# Data safety

This page explains, in plain words, how FlowersForever keeps a grower's records safe and how
each promise is tested. The tests run headless (no windows) in `mvn verify`.

## Where the records live (release check 1)

- All farm records are in one database file, `flowerfarm.mv.db`. It sits in the records folder
  on the grower's own computer. For an installed copy that is `%LOCALAPPDATA%\FlowersForever\data`;
  `FLOWERFARM_DATA_DIR` can point it somewhere else.
- Exports, attachments, logs and backups are saved in local folders too. Backups go to
  `Documents\FlowersForever Backups` unless the grower picks another folder.
- The built-in web API and the AI helper only listen on this computer (`127.0.0.1`). Network
  access is only opened in the optional `auth`/`container` server profiles.
- The internet is optional. Without it:
  - the update check reports that it is offline;
  - irrigation advice falls back to local climate averages instead of the Open-Meteo forecast;
  - the store and spreadsheet connectors (Shopify, Square, Airtable, Google Sheets, Farmbrite,
    Floranext, webhook) are off until the grower adds their own keys, and they use local mirror
    files.
- Nothing is sent anywhere automatically.

## Forced close in the middle of a save (release check 2)

**Risk found.** The database's default setting held recent saves in memory for about half a
second before writing them. Killing the app (Task Manager "End task", a crash) lost those saves.
In our first test this was every record and even the table itself.

**Fix.** The records now open with `WRITE_DELAY=0`. Each save is written to the file as soon as it
is committed, before the app tells the grower it was saved.

Exports (PDFs, CSVs, labels), backup checksum files and attachments are written to a temporary
file first. That file is flushed to disk and then swapped into place in one step. A crash leaves
either the old file or the new one, never half of each.

**Tests** (`ForcedCloseDataSafetyTest`):
- A separate Java process saves records using the app's exact database settings. It is killed the
  hard way four times, mid-save. Every save it had confirmed is still there afterwards, and the
  records pass the start-up check.
- With the old setting the same test fails (the confirmed saves are gone), so the test really
  catches the problem.
- A process that keeps replacing a 4 MB file is killed mid-write three times. The file is always
  wholly the old or wholly the new version.

## Power cut (release check 3)

**Risk found.** A power cut can leave the last write cut short, or leave parts of the file scrambled.
Before this change the app had no check at start-up. Damaged records would simply fail to open,
even when a good backup was sitting in the backups folder.

**Fix.** Before opening the records, FlowersForever now reads every table once (`DatabaseIntegrityGuard`).

- If the file is damaged, the damaged folder is kept as `data.damaged-<date>`. It is never deleted.
- The app then puts back the newest backup that both matches its checksum and opens cleanly. A
  backup that fails either check is skipped for the next older one.
- The grower sees a plain message saying what happened, which backup was used, and that anything
  entered after that backup is not included.
- If there is no usable backup, nothing is changed or deleted. The app stops with a plain message
  instead of opening damaged records.
- A records file that is only in use (another FlowersForever window, the AI helper) is never
  treated as damaged.
- Backups are flushed to disk before they count as made.

**Tests** (`PowerCutDataSafetyTest`):
- the last 2 KB of the file is cut off: every earlier record is still readable;
- the whole file is scrambled: the backup is put back, the damaged copy is kept, and the grower is told;
- the newest backup is damaged too: the older verified backup is used;
- there is no backup: nothing is touched and the message is plain;
- healthy records are left alone;
- the check runs at start-up before the records open.

## Failed saves (release check 4)

**Risk found.** Some save errors reached the grower as raw technical text, like
`General error: java.io.IOException …`.

**Fix.** Disk full, permission denied, read-only folder or media, file in use, and damaged records
are now recognised wherever the app reports a failed save. The grower is told what happened and
what to do, for example: "Saving the harvest did not finish: the disk is full. Free up some space
on this computer…".

The screens that already kept drafts after a failed save still do; the grower's typing is kept so
they can retry. Previously saved records are not affected.

**Tests** (`FailedSaveDataSafetyTest`):
- **Disk full:** a test file system makes every database write fail with Windows' own "There is
  not enough space on the disk" error part-way through a batch of saves. The warning is plain,
  and after "freeing space" and reopening, all earlier records are intact and healthy.
- **File in use:** a second Java process holds the records open. The save fails with "being used
  by another program", and the records are unchanged.
- **Read-only:** the records file is made read-only. The warning says so, and the records are
  unchanged.
- **Failed export:** an export whose final swap fails, or whose target is held open, leaves the
  previous file intact and leaves no temporary files behind.
- Windows error texts are matched to plain messages.

## What software alone cannot promise

The database writes each save to Windows as soon as it is committed. It does not force Windows'
own disk cache to the platters after every single save. On most PCs Windows writes that cache
out within moments, and the start-up check plus backups cover the rest. Still, a real power pull
right after a save is best confirmed on real hardware (see below).

## Still to try by hand on a real Windows PC

1. **Power pull:** save a few harvests, pull the plug (or hold the power button) within a second,
   then restart. Every harvest that showed "Saved" should still be there.
2. **Full disk:** use a nearly full USB stick as the records or backup folder, save until it is
   full, and check the warning wording.
3. **Read-only/locked:** set the records folder to read-only, or open the file in another program,
   and check the warning wording.
