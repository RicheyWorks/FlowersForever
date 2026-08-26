# Windows farm deployment checklist

## Before installation

- Use a supported 64-bit Windows 10/11 workstation at 1366×768 or larger.
- Download the MSI, `bom.json`, `bom.xml`, and checksum file from the same release.
- Verify the MSI SHA-256 value with `Get-FileHash`. The current installer is unsigned, so Windows may show an unknown-publisher warning.
- Record OWNER, HAND, and VIEWER credentials in the farm's password manager. Every LAN-enabled password must be at least 12 characters.
- Back up an existing installation with `scripts/backup-data.ps1` and copy the resulting ZIP and `.sha256` file off the workstation.

## Install and accept

1. Install the MSI for the current Windows user and launch Flowers Forever.
2. Confirm the dashboard opens at 1366×768 without clipped primary actions.
3. Confirm data is stored under `%LOCALAPPDATA%\FlowersForever\data`; packaged upgrades reuse this location.
4. Sign in as OWNER, HAND, and VIEWER. Verify OWNER and HAND can record work, VIEWER cannot mutate it, and only OWNER can clear audit history.
5. Complete the morning briefing, irrigation review, harvest, inventory, customer/order, market packing, fulfillment, reports, exports, and closeout paths.
6. Close and reopen the app; confirm the day's records remain.
7. Create and verify a backup, then retain it off-device.

Maintainers can validate an app image without opening the GUI by running
`FlowersForever.exe --release-smoke` with `FLOWERFARM_DATA_DIR` pointed at an empty test folder.

## Rollback

1. Close Flowers Forever and make a new backup of the current data.
2. Uninstall the current MSI. Do not delete `%LOCALAPPDATA%\FlowersForever\data`.
3. Install the prior approved MSI.
4. If the prior version cannot read the upgraded store, run `scripts/restore-data.ps1` with the pre-upgrade ZIP. The restore tool preserves the displaced data in a timestamped `.before-restore-*` directory.
5. Launch, verify inventory/customer/order totals, and record the rollback in the farm log.
