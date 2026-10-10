# Reviewed security contexts

Source and caller review on 2026-10-10 classified exactly 38 FindSecBugs 1.14.0 Medium findings from the complete SpotBugs 4.10.4 report as the specific contexts below. Raw XML and SARIF retain every finding. The [manifest](security-reviewed-findings.json) is checked by [classify_sast.py](../scripts/security/classify_sast.py); it does not suppress scanner output.

| Context | Findings | Reviewed boundary |
| --- | ---: | --- |
| Desktop chooser filename suggestions | 21 | `new File(defaultName)` supplies a suggestion. Actual export I/O uses the approved local chooser selection; cancellation performs no write. Includes CRM, dashboard, harvest, inventory, market day, reports, rose, sync history, field science, cooler, florist, scouting and stem demand workflows. |
| Desktop chooser-selected extensions and staging | 3 | Reports, rose and dashboard helpers derive an extension or temporary file beside the approved local destination. |
| SafeFiles atomic writer | 1 | Current callers use approved desktop destinations or local backup/scouting configuration and derived receipt/checksum names. No HTTP caller supplies an arbitrary destination. |
| Configured CSV/Excel connector files and local JSON mirrors | 7 | Constructor/deployment configuration fixes the paths. HTTP connector operations choose registered connector names and cannot replace those paths with request filenames. |
| Inventory startup seed | 1 | A private final constructor `@Value` path is used only by the private startup seed loader. |
| Farm data snapshot staging | 1 | A configured export root fixes the parent; bounded request keys are hashed into derived staging names. |
| RecordsReadProbe.readAll metadata SQL | 1 | The package-private reader receives an existing connection from integrity or verified-backup probes. H2 `PUBLIC` base-table metadata supplies the identifier. The entire name is quoted and embedded quotes are doubled. The SQL-looking quoted-table/non-admin regression checks that existing records survive. |
| InstalledAppLauncher.relaunch | 1 | One absolute regular non-link FlowersForever executable must be the same file as the running process command. Relaunch has no arguments or shell. |
| PlatformInstaller.ProcessStarter system handoff | 1 | The helper admits the actual fixed OS interpreter, exact flags/argument count and one bounded generated script directly beneath the updates folder. Signed release metadata and package hashes are verified before handoff. |
| AuthenticodePublisherVerifier.signatureCommand | 1 | The absolute Windows system PowerShell executable runs a constant script. Installer paths are separate child-environment data read with `-LiteralPath`; output and completion time are bounded. |

These path decisions do not cover arbitrary HTTP paths. Harvest, inventory and audit CSV exports use a server-owned contained destination root; their negative path and failed-write tests remain in the normal suite. The process decisions cover local installed-app/update operations, without an HTTP command or updater configuration route. Operator-controlled local directories, OS environment and startup configuration remain trusted boundaries; these decisions do not establish safety against a malicious local owner replacing them.

Each accepted finding must match its exact type, priority, occurrence, occurrence maximum and canonical annotation SHA-256, with its rationale and relevant caller bindings. All 38 contexts require the exact primary source, class, method and JVM signature. The four SQL/process contexts additionally require their specific review kind and eligibility selector; other SQL/process sources or methods cannot use those decisions. High findings cannot be accepted. Observed raw `instanceHash` values remain in the manifest, XML and SARIF as provenance.

Manifest schema 2 fingerprints the complete raw `BugInstance` annotation tree. It preserves every tag, attribute value, semantic text, location, bytecode offset and duplicate annotation. It normalizes XML attribute order and whitespace-only serialization formatting, then sorts only the existing slots of `String role="Unknown source"` annotations by their complete content. All other annotation order remains significant. Raw `instanceHash` is excluded from the canonical hash because FindSecBugs can reorder those unordered taint-source strings and consequently change that hash without changing any finding evidence. Changed, added, missing or duplicate taint-source values still fail, as do changes to other annotations or locations.

The manifest binds every checked-in production Java file under `src/main/java` and the `pom.xml` content, not merely the named callers. Adding a new HTTP caller or changing Maven build/dependency configuration fails. Source symlinks and directory reparse points are rejected. Only CRLF-to-LF line ending normalization is applied. Analyzer version and the FindSecBugs artifact hash are bound too. These bindings detect changes to reviewed source/build inputs; they are not a complete runtime call graph proof.

The observed canonical identities came from the complete hosted scan of commit `5c2e955`, including the reviewed DTO/null guards, structural POSIX system-tool resolution and UI layout adjustments. Comparison with the preceding hosted scan confirmed five raw hash changes caused solely by swapped Unknown-source annotations. The older launcher location was separately rejected: its annotation location changed with an explicit null-parent guard. The canonical policy accepts ordering variance while continuing to reject changed evidence or locations. No unobserved finding is accepted.

A subsequent source/caller review renewed exactly three production file bindings. `SecurityConfig` enables cookie-backed CSRF in both modes while retaining the default XOR handler, and `AuthController` adds only the public, non-cacheable GET handshake. `ShopifyConnector` admits a fixed HTTPS store product endpoint, refuses redirects and bounds provider parsing. Its configured local-mirror path constructor and local-mode callers remain unchanged. These edits introduce no path, SQL or process configuration endpoint. All 545 final production Java files and the unchanged POM are bound, while the 38 observed finding annotations, locations and raw hash provenance remain unchanged. A complete hosted scan of the final code must match those exact contexts; source review does not approve any additional finding.

The ten-pass coding campaign received a fresh review of all 38 boundaries and their
changed caller bindings. Connector edits change numeric mappings, with the configured
local files and registered-name HTTP dispatch retained. Desktop quantity/report edits
retain chooser approval and cancellation checks; file suggestions are not export
destinations. Startup sample opt-in does not expose a seed-path setter. The new worker
profile GET returns farm vocabulary without accepting filenames, SQL or process arguments.
Three added Java files introduce product metadata, quantity arithmetic and the package-private
records reader. The records reader moves the same quoted H2 metadata SQL from
`DatabaseIntegrityGuard.check` to `RecordsReadProbe.readAll`, now shared with
`RestoreService.probeRecords`; its exact eligibility selector is replaced, not broadened.
The installed-app and signed-installer process boundaries are unchanged.

A fresh Windows scan and the hosted Java 17 scan of campaign commit `22de4aa` have
identical canonical annotations at all 38 reviewed method boundaries, including
both chooser suggestions in `presentBedProduction`. No additional Medium or High
security finding was accepted. Locations and bytecode offsets changed by the reviewed
refactoring are intentionally renewed from those observed reports; their full canonical
hashes remain exact. The final two general-quality cleanups affect no security finding
annotation. The manifest binds all 548 production Java files and the unchanged POM.
The fresh scan reports zero analysis errors and zero missing classes; all 33 security
evidence guard tests pass. Hosted verification must still match this exact renewed evidence.

The CSRBT completeness and readings change received a fresh Windows scan and
source/caller review on 2026-10-10. The new library button opens the fixed bundled
`library.html` through the existing manifest-verified extraction and desktop browser
boundary. The chooser-selected export path and cancellation behavior are unchanged.
Only the existing `FieldScienceTab.saveSelectedText` finding annotations changed:
source lines moved by four and the enclosing class span grew; its sink, bytecode
offsets, and taint sources remain the same. The other 37 canonical annotations match.
The three new telemetry Java files use static prepared JDBC statements and bounded
values; raw frames never become SQL identifiers, file paths, or commands. The opt-in
workflow uses the existing token, CSRF, mutation, and sensitive-read boundaries and
introduces no physical action. The source manifest now binds all 551 production Java
files and the unchanged POM. The fresh scan has 38 previously reviewed Medium
contexts, zero High findings, zero analysis errors, and zero missing classes. No new
finding or broader context was accepted. Hosted verification must match this evidence.

The farmer-language follow-up received another fresh Windows scan and source/caller
review on 2026-10-10. Eleven production files change displayed strings only.
`FieldScienceTab` also adds a local, read-only source-credit visibility toggle; it
does not invoke a host action, file operation, or network request. Chooser approval,
cancellation, overwrite checks, destination handling, and role checks are retained.
The existing `FieldScienceTab.saveSelectedText` finding changes only its enclosing
class span by 13 lines; its primary location, method span, sink, bytecode offsets,
and taint sources are unchanged. The other 37 canonical annotations match the
preceding review. All 551 production Java bindings were renewed after reviewing
these changes; the POM, scanner, eligibility selectors, and caller rationales are
unchanged. The fresh scan has exactly the same 38 reviewed Medium contexts, no
High or new findings, zero analysis errors, and zero missing classes. The source-bound
classification gate and all 33 security evidence guard tests pass.

The desktop Equipment readings addition received a fresh Windows scan and source/caller
review on 2026-10-10. `EquipmentReadingsTab` performs manual draft review, explicit
confirmation, barn-role checks, and background service calls; it has no file chooser,
network adapter, process invocation, or physical-action path. Dynamic equipment text
uses a plain text confirmation component and an HTML-disabled table renderer.
`ManualFarmReading` reversibly encodes bounded UTF-8 display names into the existing
identifier contract and parses exact decimal values. `FarmReadingService` adds
validation without database writes and bounded descending history using fixed SQL
and bound cursor/limit parameters. Desktop Spring injection and catalog/navigation
wiring expose the tab without enabling the optional harness transport. All 553
production Java files and the unchanged POM are bound. The complete scan retains
exactly the same 38 reviewed Medium canonical annotations and caller contexts, no
High or new Medium findings, zero analysis errors, and zero missing classes.

The follow-up shell integration includes equipment drafts, unconfirmed saves, and
pending saves in existing farm-change and exit decisions. Successful saves and
explicitly discarded drafts establish a clean form baseline. Loading/save state
flags use volatile visibility across asynchronous callbacks. These changes add no
new file, process, SQL-construction, network, or authorization transport boundary;
the complete scan and source fingerprints were renewed after this caller review.

The desktop spreadsheet increment adds an approved local read boundary:
`FarmFileChooser.chooseEquipmentSpreadsheet` returns only the farmer's selected
file, and `EquipmentReadingsTab` calls `EquipmentSpreadsheet.read` off the event
thread. The reader accepts only regular non-link CSV files, opens with
`NOFOLLOW_LINKS`, reads at most 1 MiB plus one byte, decodes strict UTF-8, and bounds
columns, cells and rows before review. No HTTP caller supplies this path. Review
tables render imported text literally, with Swing HTML disabled. Exact decimals
are validated and normalized before preview, including extreme zero exponents.
Names and event references are data; neither becomes SQL, a path, or a command.
Source, metric and event keys enter the existing fixed prepared-statement intake
in one transaction. Role checks are repeated after selection and review and before
the background intake; pending imports and kept previews participate in shell
exit and farm-change guards. The four modified production callers and the new
reader were reviewed together. All 554 production Java files and the unchanged
POM are bound. The complete scan retains the same 38 reviewed Medium contexts,
with no High or additional Medium finding, no analysis error and no missing class.

Any new, changed, duplicate or absent reviewed finding fails. Any production Java edit/addition/removal or POM change requires a fresh source/build/caller review and an intentional manifest update; do not regenerate the manifest automatically to make a failed check pass. There are no native category/class/method exclusions in the security profile. The separate general quality profile retains its existing exclusions and Medium-or-higher zero-violation gate.
