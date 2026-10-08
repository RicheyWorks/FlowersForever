# Automatic updates

Testers install FlowersForever once. After that the app keeps itself current:
it checks a small signed update list, says plainly when a new version is ready,
downloads and checks it, backs up the farm records, installs, and checks the
records on the first start of the new version. If that check fails, it goes back
to the previous version and puts the records back exactly as they were.

Status: built and covered by automated tests (headless). The production
publisher key and the update address
(`https://richeyworks.github.io/FlowersForever-updates/latest.json`) are built in, and the published list has no releases yet.
**Not yet tried with a real MSI upgrade on Windows.** See
[What still needs a real Windows test](#what-still-needs-a-real-windows-test).

## What a farm sees

| When | Message (plain words) |
|---|---|
| App opens, new version listed | "A new version of FlowersForever is ready: 1.2.1 (you have 1.2.0)." + what's new + "Before installing, FlowersForever saves a checked backup of your farm records…" Buttons: **Update now** / **Later** |
| Download checked | "FlowersForever 1.2.1 is downloaded and checked. When you choose Install now, FlowersForever will close, back up your farm records, install the update, and open again. Windows may ask you to allow the installer." **Install now** / **Later** |
| First start after the update | "FlowersForever was updated to 1.2.1. Your farm records were checked and are safe. A backup from before the update is kept at: …" |
| New version failed its check | "The update to 1.2.1 did not start correctly, so FlowersForever went back to 1.2.0 and restored your farm records from the backup made just before the update… Nothing was lost. FlowersForever will not offer 1.2.1 again." |
| Offline / server down | Nothing on launch. Help > Check for updates says: "FlowersForever could not reach the update server, which is fine. You can keep working offline; it will check again next time." |
| Up to date | "You have the latest version of FlowersForever (1.2.0)." |

**Help > Check for updates…** runs a check now, and offers **Turn off / Turn on
automatic checks**. The choice is saved in
`%LOCALAPPDATA%\FlowersForever\updates\update-preferences.properties`.

Unsaved work is protected: **Install now** runs the same "Unfinished work" check
as closing the window, so the farm can keep working instead.

## How it works

```
open app ──► GET latest.json (HTTPS, 5 s connect timeout) ──► verify Ed25519 signature
         └─ offline / 404 / bad signature → keep working, nothing changes

Update now ──► download MSI to updates\packages\<version>\ (.part file)
           ──► size + SHA-256 + Ed25519 package signature (+ Authenticode, later)
           ──► also secure the installer for the CURRENT version (needed to go back)
Install now ──► unfinished-work check ──► H2 SHUTDOWN, Spring closes
           ──► ZIP backup of the whole data folder + .sha256, re-read and compared file-by-file
           ──► record progress in updates\update-state.json
           ──► start hand-off script, app exits
hand-off   ──► wait for the app to exit ──► msiexec /i new.msi /passive /norestart /l*v log
           ──► reopen FlowersForever
first start of new version
           ──► Flyway migrate + Hibernate validate (normal start)
           ──► health check: no failed/pending Flyway steps, history validates, DB answers
           ├─ pass → "updated" notice, Activity History entry, done
           └─ fail (or the previous first start crashed)
                 ──► restore the pre-update ZIP (current folder kept as data.before-restore-*)
                 ──► msiexec /i previous.msi (jpackage MSIs allow downgrades) ──► reopen old version
                 ──► old version shows the "went back" notice, never offers that version again
```

Code: `src/main/java/com/flowerfarm/update/` (no new dependencies; Ed25519 and
SHA-256 come from the JDK, JSON from the Jackson already in Spring Boot), the
desktop prompts in `gui/DesktopUpdateController.java`, and the start-up guard in
`FlowerFarmApp.main` → `UpdateStartup`.

Files the updater keeps, all **beside** the data folder (a restore never touches them):

```
%LOCALAPPDATA%\FlowersForever\
  data\                         farm records (unchanged location)
  updates\update-state.json     progress of an update in flight; failed versions
  updates\update-preferences.properties
  updates\packages\<version>\   verified installers + signed .package.json sidecars
  updates\handoff\              generated .cmd hand-off script, msiexec logs and exit codes
%USERPROFILE%\Documents\FlowersForever Backups\
  FlowersForever-before-1.2.1-YYYYMMDD-HHMMSS.zip (+ .sha256)
```

The backup ZIP has the same layout and checksum sidecar as
`scripts/backup-data.ps1`, so `scripts/restore-data.ps1 -Archive <zip>` can
restore it by hand too. Pre-update backups are never deleted automatically.

### How each data-safety rule is met

1. **Consent, never while records are open.** Only after **Update now** and **Install now**; the unfinished-work check runs first.
2. **Verified backup first.** The database is shut down, the whole data folder is zipped to a second location, the SHA-256 sidecar is written, and the ZIP is re-read and every file's size and CRC compared with the original. Any failure stops the update before anything is installed.
3. **Refuse bad packages.** Wrong size, wrong SHA-256, wrong or missing Ed25519 signature, or (once enabled) a failed Authenticode check → the file is deleted and nothing is installed. The installer is checked again right before the database is closed. The app never asks anyone to bypass SmartScreen or antivirus.
4. **First launch must pass, or roll back.** See the flow above. A crash during the first start counts as a failure on the next start. If the backup cannot be restored or the old installer is missing, the app stays closed with a "contact RicheyWorks" message instead of opening possibly half-upgraded records; nothing is deleted.
5. **Staged rollout and pause.** Each release lists channels: `owner`, `early`, `stable`. An install on `owner` sees all three; `early` sees early + stable; `stable` sees stable only. `paused: true` in the manifest stops all offers.
6. **Activity History.** Update available, downloaded, rejected, stopped, started, updated, and rolled back are recorded under source **App updates**.
7. **No other network calls; can be turned off.** Only the manifest and the installers are fetched. With no update address or no publisher key the updater makes no request at all. The farm can turn automatic checks off; `flowerfarm.updates.enabled=false` turns updates off entirely.

## Configuration

| Property (env var) | Default | Meaning |
|---|---|---|
| `flowerfarm.updates.manifest-url` (`FLOWERFARM_UPDATES_MANIFEST_URL`) | `https://richeyworks.github.io/FlowersForever-updates/latest.json` | HTTPS address of `latest.json`. Set it to an empty value to make no update checks. |
| `flowerfarm.updates.channel` (`FLOWERFARM_UPDATES_CHANNEL`) | `stable` | `owner`, `early`, or `stable`. |
| `flowerfarm.updates.enabled` (`FLOWERFARM_UPDATES_ENABLED`) | `true` | Master switch. |
| `flowerfarm.updates.check-on-launch` | `true` | Check when the window opens. |
| `flowerfarm.updates.require-rollback-installer` | `true` | Refuse to update unless a checked installer for the current version is available. Keep on. |
| `flowerfarm.updates.authenticode-subject` | empty | When set, Windows installers must also carry a valid Authenticode signature whose subject contains this text. |
| `flowerfarm.updates.backup-directory` | `Documents\FlowersForever Backups` | Second location for pre-update backups. |
| `flowerfarm.updates.directory` (JVM property only) | `<data>\..\updates` | Update state and downloads. |

For the pilot, set the user environment variable `FLOWERFARM_UPDATES_CHANNEL=owner`
on Richey's PC and `early` on the first tester's PC; everyone else stays on `stable`.

**The address is built in** as the default in `src/main/resources/application.properties`,
so every build from this branch on checks
`https://richeyworks.github.io/FlowersForever-updates/latest.json`. Override it with the environment variable for testing.

## Where to host updates (free)

The release repository is private, so testers' apps cannot download from it.
In use: the separate **public, releases-only repository**
[`RicheyWorks/FlowersForever-updates`](https://github.com/RicheyWorks/FlowersForever-updates)
(created 2026-10-04, GitHub Pages from `main`, no source code):

- `latest.json` committed to the repository and served by **GitHub Pages**:
  `https://richeyworks.github.io/FlowersForever-updates/latest.json` (stable address, free, HTTPS).
- Installers attached to **GitHub Releases** of that repository:
  `https://github.com/RicheyWorks/FlowersForever-updates/releases/download/v1.2.1/FlowersForever-1.2.1.msi`
  (free, files up to 2 GB; GitHub redirects to its download CDN over HTTPS, which the app follows).

Trade-off: anyone who has the address can download the installer. Every file is
signed, so nobody can push a fake update, but the pilot installers become
reachable to anyone with the link. If that is ever not acceptable, a Cloudflare R2
bucket with a long unguessable public path (free tier 10 GB) works the same way;
only the URLs change (and the built-in address, in a release).

## Signing key

- One Ed25519 key pair signs the manifest and every installer. The **public**
  key is built into the app in `src/main/resources/update/trusted-update-keys.properties`
  as `richeyworks-2026-10-04` (key id `ed5504416fae61ed`).
- **Where the production private key lives:** `C:\Users\730ri\FlowersForever-signing\update-signing.key`
  on BLACKBEARD. The folder is outside every git checkout, and its permissions were
  reduced to the `730ri` account only (inherited permissions removed). That folder's
  `publish\` subfolder holds the editable `manifest.unsigned.json` and the last signed
  `latest.json` (neither is secret).
- **Richey: copy `update-signing.key` to a USB drive as an offline backup** (ideally
  an encrypted one, or a password manager attachment as a second copy), label it, and
  keep it away from the PC. Without this file no further updates can be published to
  installed copies; if the PC is lost and there is no backup, every tester would need
  to reinstall by hand with a new key.
- It was created with this command (already done; shown for a future key change):
  ```powershell
  mvn -B package -DskipTests
  pwsh scripts/update-release.ps1 keygen --out "$env:USERPROFILE\FlowersForever-signing\update-signing.key"
  ```
  Paste the printed `richeyworks-YYYY-MM-DD=…` line into `trusted-update-keys.properties` and commit that file.
- The **private** key file stays in `%USERPROFILE%\FlowersForever-signing\` and in an
  offline backup (password manager attachment or encrypted USB). Never commit,
  email, upload, or paste it. `.gitignore` also blocks `update-signing*.key`.
  If it is ever exposed: make a new key, ship a release listing both keys, then
  remove the old one in the next release.
- Later, to sign in CI: store the whole key file text as a protected
  environment secret (for example `FLOWERSFOREVER_UPDATE_SIGNING_KEY` in the
  `release-signing` environment) and pass `--private-key env:FLOWERSFOREVER_UPDATE_SIGNING_KEY`.
- Tests generate a throwaway key in memory on every run; no test key is stored anywhere.

**Authenticode later.** The Ed25519 check stays. When Microsoft Artifact Signing
is active, also set `flowerfarm.updates.authenticode-subject` (for example `CN=RicheyWorks`)
in the packaged Java options; `AuthenticodePublisherVerifier` then requires
`Get-AuthenticodeSignature` to report `Valid` with that subject before any
installer is used. A macOS Developer ID check is not written yet.

## Publishing an update

```powershell
$key = "$env:USERPROFILE\FlowersForever-signing\update-signing.key"
$site = "$env:USERPROFILE\FlowersForever-signing\publish"   # editable manifest + signed latest.json
# 1. Build both MSIs with the same --name/--vendor (the MSI upgrade code is derived from them).
# 2. Record the release, owner channel first. Include the CURRENT version too, so
#    installs can go back to it (once per version; it stays in the manifest):
pwsh scripts/update-release.ps1 add-release --manifest "$site\manifest.unsigned.json" --private-key $key `
  --version 1.2.0 --channels stable `
  --package "windows-msi=C:\releases\FlowersForever-1.2.0.msi@https://github.com/RicheyWorks/FlowersForever-updates/releases/download/v1.2.0/FlowersForever-1.2.0.msi"
pwsh scripts/update-release.ps1 add-release --manifest "$site\manifest.unsigned.json" --private-key $key `
  --version 1.2.1 --channels owner --minimum-version 1.2.0 --notes-file notes-1.2.1.txt `
  --package "windows-msi=C:\releases\FlowersForever-1.2.1.msi@https://github.com/RicheyWorks/FlowersForever-updates/releases/download/v1.2.1/FlowersForever-1.2.1.msi"
# 3. Sign and check.
pwsh scripts/update-release.ps1 sign --manifest "$site\manifest.unsigned.json" --private-key $key --out "$site\latest.json"
pwsh scripts/update-release.ps1 verify --signed "$site\latest.json" --package "windows-msi=C:\releases\FlowersForever-1.2.1.msi"
# 4. Upload the MSIs to a release of RicheyWorks/FlowersForever-updates, then publish latest.json:
gh release create v1.2.1 --repo RicheyWorks/FlowersForever-updates --title "FlowersForever 1.2.1" C:\releases\FlowersForever-1.2.1.msi
#    (commit latest.json to the repo's main branch; Pages serves it within a minute or two)
# 5. Widen the rollout when it looks good:
pwsh scripts/update-release.ps1 channels --manifest "$site\manifest.unsigned.json" --version 1.2.1 --channels owner,early
#    (later: owner,early,stable), then sign + push again.
# Emergency stop: pause (optionally --message "…"), sign, push. Undo with resume.
```

`manifest.unsigned.json` holds no secrets; it is kept beside the key so the public
repository contains only the signed list. If it is lost, rebuild it by decoding the
`payload` field of the published `latest.json` (Base64).

## Headless release check (tracker check #15, test builds only)

Check #15 (auto-update and rollback) can run with **no window at all**. The
launcher has a test-only update path that runs the same code as **Update now** /
**Install now**: the same signature and installer checks, the same verified
backup before installing, the same hand-off, and the same health check with
rollback on the first start. Only three things differ: it trusts a throwaway test
key you name, it only talks to addresses on this computer, and the installer runs
silently (`msiexec /qn` instead of `/passive`).

### It is off unless you turn it on

The path is refused (exit 64, before the data folder is even looked at) unless
the JVM property `flowerfarm.updates.headless-test=true` or the environment
variable `FLOWERFARM_UPDATES_HEADLESS_TEST=true` is set. Only the exact value
`true` opens it. Nothing in the packaged app or its installer sets either, so a
normal install ignores these options.

Safety rails, each covered by `HeadlessUpdateCommandTest`, `HeadlessUpdateGateTest`
and `UpdateReleaseToolTestFeedTest`:

- `--apply-update-from` needs `--trust-test-key`. The built-in release keys are
  never trusted on this path, and a test key that matches a release key is refused.
- With a test key, the feed address must be on this computer (`localhost`,
  `127.0.0.1` or `::1`; names are never looked up). Any other address is refused
  before a single request, so the production feed can never be used with a test
  key. Every installer address in the feed must also be on this computer.
- The release tool builds such a feed only with `--test-feed loopback`, and
  refuses to do that with a built-in release key. Without the option, installer
  addresses must still be `https`, and `sign` checks them again.
- `--trust-test-key` must point to a file holding only the public key. A file
  containing a private key is refused, and its contents are never printed or logged.
- A bad signature, a wrong installer, or records that are still open stop the
  update before anything is installed, exactly as in the app.

### Commands and exit codes

```
FlowersForever.exe --apply-update-from <http://127.0.0.1:PORT/latest.json> --trust-test-key <test.pub>
                   [--update-log <file>] [--update-backup-dir <folder>]
FlowersForever.exe --verify-update-launch [--update-log <file>]
```

`--apply-update-from` checks the feed, downloads and verifies the new installer
and the installer for the current version (needed to go back), makes the verified
backup, and hands off to a hidden script that
waits for the app to exit, runs `msiexec /i … /qn /norestart /l*v`, and starts the
new version with `--verify-update-launch`. That start runs the normal start-up
(Flyway migration, Hibernate check) without Swing or the web server, then the
first-start health check: pass → confirmed; fail → restore the backup (current
folder kept as `data.before-restore-*`), silently reinstall the previous version
and start it with `--verify-update-launch` again. `--verify-update-launch` on its
own is also a handy headless "do my records open?" check: it prints the table and
row counts.

Every step is appended to the log (default `updates\headless-update-check.log`
beside the data folder) as `time  mode  STEP  message`, ending with
`RESULT  exit=<code> <NAME> …`:

| Exit | Name | Meaning |
|---|---|---|
| 0 | OK | Apply: verified, backed up, handed off to the installer. Verify: records opened and checked (first start after an update confirmed). |
| 2 | BLOCKED | Verify: the app stayed closed to protect the records (for example the backup could not be restored). |
| 3 | ROLLED_BACK | Verify: the new version failed its first-start check; records restored and the previous version is being reinstalled. |
| 64 | REFUSED | Not enabled, bad arguments, a non-local feed or installer address with a test key, a release key or private key given as the test key. |
| 65 | NO_UPDATE | The feed offers nothing newer (or only a version that already failed). |
| 66 | REJECTED | Bad signature, wrong installer size or checksum, or a non-local installer address. Nothing installed. |
| 67 | NOT_INSTALLED | Not running from an installed copy, no installer for the current version, records still open, or the backup failed. Nothing installed. |
| 68 | OFFLINE | The local feed did not answer. |
| 70 | START_FAILED | Verify: the app could not start at all. |

### Exact steps for check #15 (Windows, elevated)

The MSI installs per machine, so `msiexec /qn` only works from an **elevated**
PowerShell (Run as administrator). Without elevation it fails with 1603 /
Error 1925 and changes nothing, which is what happened on 2026-10-08. The hidden
hand-off script inherits elevation from the launcher, so no permission prompt
appears. Never use the production key or the real update address here.

```powershell
# 0. Elevated Windows PowerShell 5.1. Work folder outside every checkout; test data folder, not the real one.
$w = "$env:TEMP\ff-check15"; $ev = "C:\Users\730ri\projects\install-checks\check15"
New-Item -ItemType Directory -Force $w, $ev, "$w\feed", "$w\data", "$w\backups" | Out-Null
$env:FLOWERFARM_DATA_DIR = "$w\data"           # updates\ lands beside it, in $w
$env:FLOWERFARM_UPDATES_HEADLESS_TEST = 'true'  # this session only

# 1. Build three MSIs from the same commit in throwaway worktrees under $w (version set only there):
#    1.3.0, 1.3.1, and 1.3.2 with a broken migration
#    (src/main/resources/db/migration/h2/V999__broken.sql containing invalid SQL).

# 2. Throwaway test key outside every repository; keep only the public part for the app.
$env:FLOWERSFOREVER_JAR = "<1.3.1 worktree>\target\flowerfarm-manager-1.3.1.jar"
powershell -File scripts\update-release.ps1 keygen --out "$w\keys\test.key"
(powershell -File scripts\update-release.ps1 public-key --private-key "$w\keys\test.key")[-1] |
    Set-Content -Encoding ascii "$w\keys\test.pub"

# 3. Local feed: 1.3.0 (to go back to) and 1.3.1, signed with the test key.
Copy-Item <1.3.0 msi>, <1.3.1 msi> "$w\feed"
$u = 'http://127.0.0.1:8765'
powershell -File scripts\update-release.ps1 add-release --manifest "$w\feed\manifest.unsigned.json" `
  --private-key "$w\keys\test.key" --version 1.3.0 --channels stable --test-feed loopback `
  --package "windows-msi=$w\feed\FlowersForever-1.3.0.msi@$u/FlowersForever-1.3.0.msi"
powershell -File scripts\update-release.ps1 add-release --manifest "$w\feed\manifest.unsigned.json" `
  --private-key "$w\keys\test.key" --version 1.3.1 --channels stable --minimum-version 1.3.0 --test-feed loopback `
  --package "windows-msi=$w\feed\FlowersForever-1.3.1.msi@$u/FlowersForever-1.3.1.msi"
powershell -File scripts\update-release.ps1 sign --manifest "$w\feed\manifest.unsigned.json" `
  --private-key "$w\keys\test.key" --out "$w\feed\latest.json" --test-feed loopback
$server = Start-Process jwebserver -ArgumentList '-b','127.0.0.1','-p','8765','-d',"$w\feed" -WindowStyle Hidden -PassThru

# 4. Install 1.3.0 silently, add sample records headlessly, check they open.
msiexec /i "$w\feed\FlowersForever-1.3.0.msi" /qn /norestart /l*v "$ev\install-1.3.0.log"
$exe = "${env:ProgramFiles}\FlowersForever\FlowersForever.exe"
& $exe --release-smoke --spring.profiles.active=demo          # seeds demo records, writes release-smoke.ok, exits
& $exe --verify-update-launch --update-log "$ev\baseline.log"  # RECORDS line = counts before the update

# 5. Update 1.3.0 -> 1.3.1.
& $exe --apply-update-from "$u/latest.json" --trust-test-key "$w\keys\test.pub" `
  --update-log "$ev\update-1.3.1.log" --update-backup-dir "$w\backups"
#    exit 0, then wait for the hand-off: update-1.3.1.log ends with a VERIFY RESULT exit=0 (records confirmed),
#    "$w\updates\handoff\" holds the msiexec log, and the installed version is 1.3.1. Compare RECORDS with baseline.

# 6. Broken update 1.3.1 -> 1.3.2: add 1.3.2 to the feed (add-release + sign as above), apply again.
#    Expect the 1.3.2 start to log ROLLBACK and RESULT exit=3, then 1.3.1 to start again and log exit=0 with the
#    baseline RECORDS, "data.before-restore-*" kept in $w, and a re-apply to return 65 (1.3.2 is never offered again).

# 7. Bad signature: change one byte of latest.json (or an MSI) and apply: exit 66, nothing installed.
# 8. Clean up: Stop-Process $server; msiexec /x {product code} /qn /l*v "$ev\uninstall.log";
#    check Program Files, the registry and shortcuts are clean; delete $w (test key included).
```

## What still needs a real Windows test

Steps 2 (apart from the permission prompt and progress bar), 4, 5 and 7 can also run
with no window using the [headless release check](#headless-release-check-tracker-check-15-test-builds-only).

All of this is covered by automated headless tests with fake installers; none
of it has run against real MSIs yet. Suggested pass on a spare Windows account:

1. Add the production public key and a test manifest address, build **1.2.0** and **1.2.1** MSIs, install 1.2.0, enter a few records.
2. Publish both (1.2.1 on `owner`), set `FLOWERFARM_UPDATES_CHANNEL=owner`, open the app → **Update now** → **Install now**. Confirm: no console window flashes; the backup ZIP appears in Documents; the Windows permission prompt and the msiexec progress bar appear; the app reopens as 1.2.1 with the "updated" notice; records are intact; Activity History shows the entries.
3. Cancel the Windows permission prompt on a second try → app reopens on 1.2.0 with "was not installed".
4. Build **1.2.2** with a deliberately broken migration (`V999__broken.sql` containing invalid SQL), publish, update → app must roll back to 1.2.1 with records intact and `data.before-restore-*` kept.
5. Tamper with a hosted MSI (or the manifest) → "rejected", nothing installed.
6. Unplug the network → no message on launch; Help > Check for updates shows the offline message.
7. Check the msiexec log in `updates\handoff\` and that the MSI upgraded in place (one entry in Apps & features).
8. Repeat with Defender on to confirm the hand-off script and msiexec are not flagged.

macOS: the DMG hand-off script is written but has not run on a Mac.

## Known limitations

- The jpackage MSI is per-machine (Program Files), so every update shows one Windows permission (UAC) prompt. A per-user MSI (`--win-per-user-install`) would avoid that but moves the install folder for existing testers (**owner decision**).
- Until Authenticode is in place, Windows shows the installer publisher as unknown in the permission prompt. The app's own Ed25519 check is what protects against fake updates in the meantime.
- A tester who installed 1.2.0 by hand needs the 1.2.0 installer listed in the manifest (step 2 above) so the app can go back if 1.2.1 fails; otherwise the update is refused with a plain message.
- Old manifests replayed by a network attacker can only make the app miss an update (it never installs an older version or anything unsigned).
