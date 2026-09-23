# Windows farm deployment checklist

## Before installation

- Use a supported 64-bit Windows 10/11 workstation at 1366×768 or larger.
- Obtain the MSI, `bom.json`, `bom.xml`, `THIRD-PARTY.txt`, and checksum file through the approved private RicheyWorks channel.
- Verify the MSI SHA-256 value with `Get-FileHash` and confirm its valid signature and expected publisher. Private unsigned candidates are for maintainer validation; external pilots require an approved signed release.
- Record OWNER, HAND, and VIEWER credentials in the farm's password manager. Every LAN-enabled password must be at least 12 characters.
- Back up an existing installation with `scripts/backup-data.ps1`, verify the checksum, and copy the ZIP and `.sha256` file off the workstation before upgrading.

## Personalize the farm identity

Set the farm profile before customer-facing use. The name, location, tagline,
contact details, and time zone appear in the desktop header and generated
invoices, statements, packing lists, and reports. Use environment variables for
an installed app:

```powershell
[Environment]::SetEnvironmentVariable("FLOWERFARM_PROFILE_NAME", "Evergreen & Bloom Farm", "User")
[Environment]::SetEnvironmentVariable("FLOWERFARM_PROFILE_LOCATION", "Port Orchard, WA", "User")
[Environment]::SetEnvironmentVariable("FLOWERFARM_PROFILE_TAGLINE", "Seasonal flowers, grown nearby", "User")
[Environment]::SetEnvironmentVariable("FLOWERFARM_PROFILE_EMAIL", "hello@example.com", "User")
[Environment]::SetEnvironmentVariable("FLOWERFARM_PROFILE_PHONE", "360-555-0100", "User")
[Environment]::SetEnvironmentVariable("FLOWERFARM_PROFILE_WEBSITE", "example.com", "User")
[Environment]::SetEnvironmentVariable("FLOWERFARM_PROFILE_TIME_ZONE", "America/Los_Angeles", "User")
```

For a portable JAR, the same values may be placed in a gitignored
`application-local.properties` file beside the JAR using the
`flowerfarm.profile.*` property names. Restart FlowersForever after changing the
profile, then export one invoice and one report to confirm the buyer-facing
identity.

## Enable phones and tablets

Run one authenticated `container` deployment for the farm and publish it through
an HTTPS reverse proxy on the trusted farm network. Workers open
`https://<farm-server>/worker/`; OWNER and HAND accounts can submit field work,
while VIEWER remains read-only. Do not run separate app processes against copies
of the same H2 files.

Before rollout, test the field screen on the farm's actual phones and tablets:

1. Sign in and confirm the work date follows `FLOWERFARM_PROFILE_TIME_ZONE`.
2. Record a test harvest and verify it in Harvest Log and Inventory.
3. Complete a test production task and verify its labor, cost, and observation.
4. Attach a test field photo while completing a production task. Verify its
   caption and bytes through the farm AI or worker attachment API.
5. Interrupt connectivity after choosing another photo, submit the task, and
   confirm the pending panel retains both the photo and completion. Restart the
   server, choose **Retry now**, and verify exactly one photo and completion.
6. Search **Count inventory**, save a physical count, and verify the same quantity
   in desktop Inventory and through the farm's AI read-back.
7. Load a count, change the same SKU elsewhere, and confirm the stale phone count
   is retained for review rather than overwriting the newer quantity.
8. Create a Confirmed order due today, review it under **Due handoffs**, complete
   it, and verify the order becomes Fulfilled, inventory is deducted once, and
   the farm's AI reads the same state.
9. Load another handoff, change its instructions elsewhere, and confirm the stale
   phone handoff remains pending for review instead of completing the changed order.
10. Interrupt connectivity during a test entry, confirm the pending count remains,
   reconnect, and verify exactly one resulting record.
11. Confirm the pending count is zero before clearing browser data or replacing a
   device.

HTTPS is required on LAN devices for installable/offline web-app features and to
protect HTTP Basic credentials. The loaded screen queues submissions in its
browser profile and retries on reconnect, page open, or **Retry now**; it does
not run a permanent background process after the browser is fully closed.

## Install and accept

1. Install the MSI for the current Windows user and launch FlowersForever.
2. Confirm the dashboard opens at 1366×768 without clipped primary actions.
3. Confirm data is stored under `%LOCALAPPDATA%\FlowersForever\data`; packaged upgrades reuse this location.
4. For an upgrade, confirm startup reports successful Flyway validation/migration before entering new work. Stop and restore the pre-upgrade backup if migration fails.
5. Sign in as OWNER, HAND, and VIEWER. Verify OWNER and HAND can record work, VIEWER cannot mutate it, and only OWNER can clear audit history.
6. Complete the morning briefing, irrigation review, harvest, inventory, customer/order, market packing, fulfillment, reports, exports, and closeout paths.
7. Close and reopen the app; confirm the day's records remain.
8. Create and verify a backup, then retain it off-device.

Maintainers can validate an app image without opening the GUI by running
`FlowersForever.exe --release-smoke` with `FLOWERFARM_DATA_DIR` pointed at an empty test folder.

## Connect the farm's AI

The Windows installer includes `FlowersForeverAI.exe` beside the desktop
launcher. It is a console-capable stdio MCP entry point with the packaged Java
runtime and packaged data-directory behavior already configured. Give the AI
client that absolute executable path and these process-only environment values:

```text
FLOWERFARM_HARNESS_TOKEN=<at-least-24-random-characters>
FLOWERFARM_HARNESS_ALLOW_SENSITIVE_READ=true
FLOWERFARM_HARNESS_ALLOW_MUTATIONS=true
```

Run `FlowersForeverAI.exe --mcp-doctor` before registration. Continue only when
its one-line JSON says `ready:true` and `dataDirectory` is the farm's intended
store. Read-back/resume needs sensitive-read permission; imports and live farm
operations need mutation permission. The doctor lists missing settings and
never returns the token. See the [AI harness guide](AI_HARNESS.md) for portable
JAR, macOS, client-configuration, migration, and reconciliation details.

## Pre-release candidate validation

The private GitHub Release workflow has a non-publishing candidate path. A pull
request that changes the release workflow, POM version, or local packaging
script builds both the MSI and DMG on clean hosted runners. Each runner also
launches a temporary app image with `--release-smoke` and must produce
`release-smoke.ok` after the application context, database, and migrations start.
The same clean images must pass the AI launcher's doctor and a redirected MCP
stdio ping. Windows uses the console launcher path a real AI client uses; macOS
uses the additional executable inside the signed app bundle.
The Windows desktop gate also requires a zero exit code within 90 seconds and
valid marker contents. A marker from an earlier launch is rejected, and a hung
smoke process is stopped before the check fails. The signed Windows release and
private draft preview use the same gate.

For a local unsigned Windows build, set the POM to its exact stable version and
run `pwsh -File scripts/build-release.ps1` with JDK 17, Maven, and a compatible
WiX toolchain available. Each invocation uses its own
`target/release-<version>-<GUID>` build directory and writes the app image, ZIP,
MSI, JAR, SBOMs, notices, and checksums to
`dist/release-<version>-<GUID>`. Existing native binaries and
`dist/harness-evidence` remain intact. The script runs Maven quality verification,
then the AI doctor/stdio and desktop startup/shutdown checks against the same
temporary database before packaging the tested image. `-SkipMsi` omits only the
MSI; `-SkipTests` skips Maven tests, while both launcher checks still run. Failed
build directories remain available for investigation and have no completed
checksum manifest.

Maintainers can also start the workflow manually and optionally provide an exact
candidate version. Candidate runs upload a checksum-complete private bundle for
7 days, but cannot create a GitHub Release or publish a container. Only an exact
stable `vX.Y.Z` tag matching the POM can enter the publishing jobs.

Temporary verified JAR, release metadata, and unsigned installer uploads expire
after 1 day; the assembled candidate bundle provides the 7-day review copy.
CI coverage, SBOM, test, and analysis reports also expire after 7 days. Signed
installer and notarization-evidence uploads retain their existing 14-day window;
published GitHub Release assets are separate from these workflow artifacts.
Download a candidate before its review window ends. If intermediate artifacts
expire before a delayed job or rerun consumes them, start a complete new run.
These settings apply to future uploads; older artifacts require separate cleanup.

When Actions artifact quota prevents preview packaging, maintainers can use the
[private draft preview path](PRIVATE_PREVIEW.md). It transfers the single verified
JAR and tested installers through an unpublished private draft release, preserving
existing Actions archives. This is an explicit manual option; normal candidates
and signed releases keep the existing path.

An antivirus detection is a stop condition even when the checksum matches.
Save the alert details and send them to RicheyWorks through the private support
channel. Maintainers can collect hashes and publisher details without launching
the blocked app using `scripts/collect-windows-security-report.ps1`.
Follow the [Windows antivirus review](ANTIVIRUS_REVIEW.md) for the exact Norton
case and replacement-build checks; keep protection enabled during review.
External pilot distribution remains blocked until platform signing is active.
Maintainers preparing the signing accounts should follow the
[native signing setup](SIGNING_SETUP.md); credentials belong only in the
protected GitHub `release-signing` environment.

## Rollback

1. Close FlowersForever and make a new backup of the current data.
2. Uninstall the current MSI. Do not delete `%LOCALAPPDATA%\FlowersForever\data`.
3. Install the prior approved MSI.
4. If the prior version cannot read the upgraded store, run `scripts/restore-data.ps1` with the pre-upgrade ZIP. The restore tool preserves the displaced data in a timestamped `.before-restore-*` directory.
5. Launch, verify inventory/customer/order totals, and record the rollback in the farm log.
