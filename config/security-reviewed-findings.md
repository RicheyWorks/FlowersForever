# Reviewed security contexts

Source and caller review on 2026-10-09 classified exactly 38 FindSecBugs 1.14.0 Medium findings from the complete hosted SpotBugs 4.10.4 report as the specific contexts below. Raw XML and SARIF retain every finding. The [manifest](security-reviewed-findings.json) is checked by [classify_sast.py](../scripts/security/classify_sast.py); it does not suppress scanner output.

| Context | Findings | Reviewed boundary |
| --- | ---: | --- |
| Desktop chooser filename suggestions | 21 | `new File(defaultName)` supplies a suggestion. Actual export I/O uses the approved local chooser selection; cancellation performs no write. Includes CRM, dashboard, harvest, inventory, market day, reports, rose, sync history, field science, cooler, florist, scouting and stem demand workflows. |
| Desktop chooser-selected extensions and staging | 3 | Reports, rose and dashboard helpers derive an extension or temporary file beside the approved local destination. |
| SafeFiles atomic writer | 1 | Current callers use approved desktop destinations or local backup/scouting configuration and derived receipt/checksum names. No HTTP caller supplies an arbitrary destination. |
| Configured CSV/Excel connector files and local JSON mirrors | 7 | Constructor/deployment configuration fixes the paths. HTTP connector operations choose registered connector names and cannot replace those paths with request filenames. |
| Inventory startup seed | 1 | A private final constructor `@Value` path is used only by the private startup seed loader. |
| Farm data snapshot staging | 1 | A configured export root fixes the parent; bounded request keys are hashed into derived staging names. |
| DatabaseIntegrityGuard.check metadata SQL | 1 | H2 `PUBLIC` base-table metadata supplies the identifier. The entire name is quoted and embedded quotes are doubled. The SQL-looking quoted-table/non-admin regression checks that existing records survive. |
| InstalledAppLauncher.relaunch | 1 | One absolute regular non-link FlowersForever executable must be the same file as the running process command. Relaunch has no arguments or shell. |
| PlatformInstaller.ProcessStarter system handoff | 1 | The helper admits the actual fixed OS interpreter, exact flags/argument count and one bounded generated script directly beneath the updates folder. Signed release metadata and package hashes are verified before handoff. |
| AuthenticodePublisherVerifier.signatureCommand | 1 | The absolute Windows system PowerShell executable runs a constant script. Installer paths are separate child-environment data read with `-LiteralPath`; output and completion time are bounded. |

These path decisions do not cover arbitrary HTTP paths. Harvest, inventory and audit CSV exports use a server-owned contained destination root; their negative path and failed-write tests remain in the normal suite. The process decisions cover local installed-app/update operations, without an HTTP command or updater configuration route. Operator-controlled local directories, OS environment and startup configuration remain trusted boundaries; these decisions do not establish safety against a malicious local owner replacing them.

Each accepted finding must match its exact type, priority, instance hash, occurrence and occurrence maximum, with its rationale and relevant caller bindings. The four SQL/process contexts additionally require their explicit review kind and exact source, class, method and JVM signature; the corresponding primary location in raw XML must match too. Other SQL/process sources or methods cannot use those decisions. High findings cannot be accepted.

The manifest binds every checked-in production Java file under `src/main/java` and the `pom.xml` content, not merely the named callers. Adding a new HTTP caller or changing Maven build/dependency configuration fails. Source symlinks and directory reparse points are rejected. Only CRLF-to-LF line ending normalization is applied. Analyzer version and the FindSecBugs artifact hash are bound too. These bindings detect changes to reviewed source/build inputs; they are not a complete runtime call graph proof.

The observed identities came from hosted commit `5b9dd61`. Subsequent explicit null DTO rejection, null path guards, structural POSIX system-tool resolution and UI-only layout adjustments were reviewed against the same sink/caller boundaries before binding the final sources. The canonical fresh hosted report must match the manifest's exact identities; any difference fails the gate and requires another review. No unobserved finding is accepted in anticipation of that scan.

Any new, changed, duplicate or absent reviewed finding fails. Any production Java edit/addition/removal or POM change requires a fresh source/build/caller review and an intentional manifest update; do not regenerate the manifest automatically to make a failed check pass. There are no native category/class/method exclusions in the security profile. The separate general quality profile retains its existing exclusions and Medium-or-higher zero-violation gate.
