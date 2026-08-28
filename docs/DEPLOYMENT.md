# Windows farm deployment checklist

## Before installation

- Use a supported 64-bit Windows 10/11 workstation at 1366×768 or larger.
- Obtain the MSI, `bom.json`, `bom.xml`, `THIRD-PARTY.txt`, and checksum file through the approved private RicheyWorks channel.
- Verify the MSI SHA-256 value with `Get-FileHash`. The current installer is unsigned, so Windows may show an unknown-publisher warning.
- Record OWNER, HAND, and VIEWER credentials in the farm's password manager. Every LAN-enabled password must be at least 12 characters.
- Back up an existing installation with `scripts/backup-data.ps1`, verify the checksum, and copy the ZIP and `.sha256` file off the workstation before upgrading.

## Install and accept

1. Install the MSI for the current Windows user and launch Flowers Forever.
2. Confirm the dashboard opens at 1366×768 without clipped primary actions.
3. Confirm data is stored under `%LOCALAPPDATA%\FlowersForever\data`; packaged upgrades reuse this location.
4. For an upgrade, confirm startup reports successful Flyway validation/migration before entering new work. Stop and restore the pre-upgrade backup if migration fails.
5. Sign in as OWNER, HAND, and VIEWER. Verify OWNER and HAND can record work, VIEWER cannot mutate it, and only OWNER can clear audit history.
6. Complete the morning briefing, irrigation review, harvest, inventory, customer/order, market packing, fulfillment, reports, exports, and closeout paths.
7. Close and reopen the app; confirm the day's records remain.
8. Create and verify a backup, then retain it off-device.

Maintainers can validate an app image without opening the GUI by running
`FlowersForever.exe --release-smoke` with `FLOWERFARM_DATA_DIR` pointed at an empty test folder.

## Pre-release candidate validation

The private GitHub Release workflow has a non-publishing candidate path. A pull
request that changes the release workflow, POM version, or local packaging
script builds both the MSI and DMG on clean hosted runners. Each runner also
launches a temporary app image with `--release-smoke` and must produce
`release-smoke.ok` after the application context, database, and migrations start.

Maintainers can also start the workflow manually and optionally provide an exact
candidate version. Candidate runs upload a checksum-complete private bundle for
14 days, but cannot create a GitHub Release or publish a container. Only an exact
stable `vX.Y.Z` tag matching the POM can enter the publishing jobs.

An antivirus detection is a stop condition even when the checksum matches. Do
not add broad folder or process exclusions. Record the detection name, alert ID,
file SHA-256, and build commit; update security definitions and run a full scan.
After the source and build provenance are confirmed, a maintainer may submit the
specific file through [Norton's official false-positive review process](https://support.norton.com/sp/en/us/home/current/solutions/kb20090410134005EN).
External pilot distribution remains blocked until platform signing is active.

## Rollback

1. Close Flowers Forever and make a new backup of the current data.
2. Uninstall the current MSI. Do not delete `%LOCALAPPDATA%\FlowersForever\data`.
3. Install the prior approved MSI.
4. If the prior version cannot read the upgraded store, run `scripts/restore-data.ps1` with the pre-upgrade ZIP. The restore tool preserves the displaced data in a timestamped `.before-restore-*` directory.
5. Launch, verify inventory/customer/order totals, and record the rollback in the farm log.
