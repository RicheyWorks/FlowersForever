# Reviewed file-path findings

On 2026-10-03, source and caller review classified exactly 21 FindSecBugs 1.14.0 Medium path findings as contextual desktop/operator behavior. The owner approved these specific contexts; raw XML and SARIF retain them. The [manifest](security-reviewed-findings.json) is checked by [classify_sast.py](../scripts/security/classify_sast.py).

| Context | Findings | Reviewed boundary |
| --- | ---: | --- |
| Desktop chooser suggestions in CrmTab, DashboardTab, HarvestLogTab, InventoryTab, MarketDayTab, ReportsTab, RoseVisualizerTab and SyncHistoryTab | 15 | `new File(defaultName)` sets a chooser suggestion. Actual export I/O uses the approved chooser selection (`APPROVE_OPTION` / `getSelectedFile()`); users intentionally choose local destinations. |
| CsvInventoryConnector and ExcelConnector configured locations | 5 | Paths originate in constructor/deployment configuration. HTTP connector operations can trigger an import/export but cannot replace these configured paths with request filenames. |
| InventoryService legacy startup seed | 1 | The seed path is operator `@Value` constructor configuration, independent of an HTTP request path. |

These decisions do not cover arbitrary HTTP paths. The separately repaired harvest/audit HTTP exports use a server-owned CSV destination root; their negative path tests remain part of the normal test suite. They also do not establish safety against a malicious local user who can rewrite trusted directories or configuration.

Each accepted finding must match its exact type, priority, instance hash, occurrence and occurrence maximum. Its rationale and relevant caller bindings are recorded. The manifest additionally binds every checked-in production Java file under `src/main/java` and the `pom.xml` content, not merely the existing callers: adding a new HTTP caller or changing Maven build/dependency configuration fails. Source symlinks and directory reparse points are rejected. Only CRLF-to-LF line ending normalization is applied. Analyzer version and FindSecBugs artifact hashes are bound too. These file bindings detect changes to the reviewed source/build inputs; they are not a complete runtime call graph proof.

Any new, changed, duplicate or absent reviewed finding fails. Any production Java edit/addition/removal or POM change requires a fresh source/build/caller review and an intentional manifest update; do not regenerate the manifest as an automatic way to make a failed check pass. There are no native category/class/method exclusions in the security profile. The unrelated general quality profile retains its existing report-only filter.
