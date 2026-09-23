# Human-interaction harness audit

Audit date: 2026-09-04

## Unfinished customer details — September 22, 2026

Customer forms previously had no clean baseline, so Load selected, double-click,
and Clear form could replace entered details without a discard decision. The
shell warning covered order work and running writes but omitted customer-only
drafts. CRM now compares every raw field and the edit identity with a clean
baseline, and failed saves remain unfinished even when values match that baseline.
Explicit load/clear, an acknowledged save, or deletion of the loaded account
resets it; refresh, search, and account-review handoffs leave entered work intact.

Dirty replacement uses an injected confirmation with **Keep editing** as the
default. Confirmed load/clear decisions compare raw form and write revisions,
current access/pending work, refresh failure, and the complete selected load
target before replacing anything. Newer decisions and changed-then-restored
fields invalidate an older approval. Cancel/X preserves the original form and
edit identity. Customer drafts now join the existing close/sign-out guard, while
untouched loaded accounts remain clean. This is local session state, not recovery
after application exit.

`CrmCustomerDraftSafetyTest` exercises baselines, exact raw fields, failure and
acknowledgement boundaries, replacement decisions, and stale/nested modals.
Eighteen new real-CRM shell cases in `ProductionReadinessUiTest` verify drafts,
failed saves, no-change loading, and pending writes across exit/sign-out choices.
All 53 draft cases and 38 shell cases passed in focused verification. The draft
suite's first run had one overbroad exit-warning assertion, corrected to preserve
independent order warnings after customer deletion while checking form cleanup.
Full evidence is recorded in `dist/harness-evidence/customer-drafts-quality.log`
and `latest.json`.

The full clean run passed 1,800 tests plus two packaged-JAR checks, zero
failures/errors/skips, and all 176 suite/phase floors. Coverage is 87.11% lines
and 70.24% branches; Checkstyle and SpotBugs report zero findings. Both SBOM
formats were generated. A separate `--check` confirmed source/report consistency
for `6b233d2afee7b84063381d433b0991eb886e7194e40d8b9812ffaf42d7b014d7`.

## Customer and packing action decisions — September 22, 2026

The next review found that pressing Enter in Customer notes could submit through
a disabled Save button after a failed CRM refresh. The table's double-click
handler could also replace a captured customer form during a pending save, and
customer deletion did not recheck the account or access after its confirmation.
Customer action entry points now share busy, refresh, saved-result, and access
guards. Deletion captures the complete reviewed row and raw form with a write
revision; changed selection/details/fields or a newer write invalidate the older
approval. Loading uses the sorted model row. Read-only account inspection after
a failed refresh remains available without permitting writes.

Market Day had a corresponding final-review gap: role, scope, checks, or refresh
could change during the modal review without invalidating approval. It now
captures an immutable plan, checked-order signatures, and a decision revision,
and rechecks before dispatch. The service receives an immutable snapshot.
Preflight rejection previously displayed "rebuild required" while leaving the
completion action enabled; a persistent lock now requires a successful rebuild.
Failed reloads and role changes cannot remove that lock. Global refreshes during
completion queue until its result, preserving the visible packing evidence.

`CrmCustomerDecisionSafetyTest` exercises keyboard and double-click entry points,
raw-field retention while pending, and post-modal deletion decisions.
`MarketDayCompletionDecisionTest` exercises stale approvals, direct submission
guards, rebuild recovery, immutable captured plans, and queued refreshes.
These are Swing component/service-mock checks; the existing transactional
fulfillment checks still enforce live record and inventory conditions.

Focused verification passed all 38 CRM cases and 48 packing cases (22 new and
26 existing), with zero failures/errors/skips. The clean full verification log
is `dist/harness-evidence/action-decisions-quality.log`; source/report evidence
is `dist/harness-evidence/latest.json`.

The full clean run passed 1,729 tests plus two packaged-JAR checks, with zero
failures/errors/skips and all 175 suite/phase floors met. Coverage is 87.08% lines
and 70.15% branches; Checkstyle and SpotBugs report zero findings. Both SBOM
formats were generated. A separate `--check` verified source/report consistency
for `c72e1d0bc1997d580844af053bdc5d1afd5c7a6fb8335653b75d40307d2510ff`.

## Per-product inventory drafts — September 22, 2026

The follow-up replaces the single count/edit retry slots with separate drafts
keyed by saved product ID and operation. A different SKU's failure, rejected
modal decision, successful save, or deletion cannot replace another product's
draft. Same-ID renames retain identity; a new same-name product does not inherit
the missing original's work. Refresh, filtering, handoffs, and permissions do
not prune drafts. Only the acknowledged operation's captured draft is cleared,
before any failed readback can affect its saved outcome.

Retained drafts include the original product snapshot, complete raw edit fields
or count/unit, and their own duplicate-name conflict. Reopened drafts keep
corrections on Cancel/X. If loaded saved details changed, the retry form warns
and the review dialog shows full saved and entered values. The count warning
also preserves its original unit. These guards do not replace transactional
version checks against other clients, and drafts remain local to the running app.

The new review dialog lists operation and ID before potentially long product
names, offers copyable plain-text values even for missing products, and navigates
to the exact original product without writing. VIEWER can inspect or discard
local drafts. Discard confirms exactly one captured entry; reference checks
reject a decision made before a newer correction, discard, or save. The same
check prevents an older count/edit modal from submitting over newer retained
values. Delete describes affected drafts and clears only acknowledged product
work. Exit warnings include every retained entry, including missing products.

Focused checks passed 83 cases, followed by 50 final recovery/render cases.
New tests cover multiple products and operations, exact field spelling, rename,
filter/refresh, missing/same-name replacements, nested modal corrections, local
review/discard, viewer recovery, and deletion acknowledgement. Four standalone
light/dark renders at 580×300 and 700×380 verify selection, complete copyable
text, and scroll access through long/malformed/missing-product data. Images are
in `target/ui-previews/inventory-drafts`. Final clean isolated evidence is kept
in `dist/harness-evidence/inventory-drafts-quality.log` and `latest.json`.

The clean run passed 1,669 tests plus two packaged-JAR checks with zero failures,
errors, or skips, and met all 173 suite/phase floors. Coverage is 86.99% lines and
69.96% branches. Checkstyle and SpotBugs report zero findings; both SBOM formats
were generated. A separate `--check` confirmed report/source consistency for
`6bc40b12b5b93dbc070c75988508dd8c91f4840b13eb4cf3d466e5166fe500c1`.

## Inventory acknowledgements and release isolation — September 22, 2026

Inventory count, edit, and delete workers previously committed their write and
read the entire catalog in the same failing result. A readback exception therefore
reported an acknowledged operation as unsaved and retained a replayable draft.
The worker now returns the saved result separately from readback failure. Host
refresh exceptions also preserve the acknowledged result. Further inventory
mutations require a successful fresh read; selection, filtering, and permission
updates cannot erase the recovery warning or bypass the action guards.

Refreshes requested during writes wait until the write resolves. Modal count,
edit, and delete decisions recheck product fields, selection, and access before
submission. A rejected count/edit keeps its entered values tied to the reviewed
product. Count-next waits through the host's inventory refresh and rechecks the
next identity and visible workspace before opening. Inventory pending writes
and retained drafts now participate in the shell's exit/sign-out decision.
These client checks do not add transactional version preconditions across clients.

The first compact renders exposed a collapsed stock table after recovery text
wrapped. The final layout keeps feedback on full-width rows, provides a vertical
workspace scrollbar and a usable table height, and separates mutation actions
from reports. Four light/dark cases at 850×500 and 1180×760 verify complete
saved-product identity and real scroll access to every action and the table.
Ten modal cases, fourteen mutation/refresh cases, and five additional shell cases
cover the behavior. The existing failed-refresh contract now requires disabled
writes, matching its documented promise. Successful unrelated saves no longer
clear another product's retained draft. Multiple different failed drafts still
share one count/edit retry slot; that existing limitation needs a separate pass.

Local release builds now use unique target/dist subdirectories and preserve
existing evidence and native binaries. All Windows release paths require a fresh,
valid desktop marker plus zero process exit within 90 seconds, with timeout
cleanup. Thirteen new simulated release/desktop checks and seventeen existing
launcher checks passed. No native installer or application was built or launched.

Focused verification passed 81 cases. A subsequent 102-case pass had two compact
render failures; after the layout correction, all 33 targeted dialog/render/UI
cases passed. Full clean isolated quality evidence is recorded in
`dist/harness-evidence/inventory-quality.log` and `dist/harness-evidence/latest.json`.
The final clean run passed 1,635 tests plus two packaged-JAR checks with zero
failures/errors/skips, all 170 suite/phase floors, and every quality gate.
Coverage is 86.97% lines and 69.89% branches. Checkstyle and SpotBugs report no
violations/findings/errors; online verification generated both SBOM formats.
The verified source hash is
`93824fd73331d65eb787007eaf7897a8e83b35bd20629e4f886bb4b7f6ee33d0`.

## Harvest draft protection and native readiness — September 21, 2026

The next shipping review reproduced silent quick-entry replacement through load,
copy, and clear, including history double-clicks while an asynchronous write was
pending. The form now compares complete raw fields and its edit target with a clean
baseline. Replacing changed work requires an explicit decision; unmodified saved
rows and retained setup after a successful add stay clean. Copies and failed saves
remain drafts even when their values happen to equal that baseline. Crop-choice
refresh cannot write an old preset back into a newly typed crop.

Replacement decisions capture and recheck both the draft and selected record.
Deletion identifies edits it would discard and checks the draft again after its
confirmation. Sample lines cannot overwrite an entered batch without consent.
Harvest exit/sign-out warnings include quick entry, batch lines, and running
mutations. The frame's pending-result wording also covers CRM and packing writes;
exiting never claims to undo records already committed.

Compact renders exposed zero-height entry fields, unreachable history, clipped
filters, and a status row competing with buttons. Entry now uses two wide columns,
separate action/status rows, wrapped plain-text status labels, and vertical
workspace scrolling. History filters remain reachable and identity columns retain
readable widths with horizontal table scrolling. Loading or copying a saved row
reveals the quick-entry editor. The README's actual harvest screenshot is refreshed.

Review then reproduced another dynamic state: revealing new-SKU setup alongside
queued-refresh and failed-report messages pushed the heading/status outside the
header even at 1180 pixels. The header now keeps access/Refresh beside its title,
places refresh/report feedback on wrapping rows, and reveals inventory setup in
a separate row. All four rendered theme/size cases include that real reveal path.
An initial full verification was stopped for this additional correction and is
not counted as passing evidence.

Sixteen new draft-safety cases and six shell cases cover the decisions and pending
results. Four new light/dark render cases check 850×500 and 1180×760, real scroll
reachability, complete messages, target identity, fields, filters, and history.
The earlier broad focused run passed 138/144 checks; its six layout/legacy-layout
failures were corrected and all six passed in targeted reruns. Initial new-test
compile/selector mistakes were corrected separately and are not product defects.
Final clean isolated verification passed 1,602 tests plus two packaged-JAR checks,
with no failures, errors, or skips. Line coverage is 86.91% and branch coverage
69.72%; coverage, Checkstyle, SpotBugs, all 167 suite/phase floors, and source/report
evidence checks passed. A subsequent `--check` confirmed the evidence still matches
source hash `4a02d03f7da0edd9de15dce4ec2bfa76ded991d393492d2dbce5f9101560196a`.

The Windows native launcher smoke still required exactly 33 farm tools although
the current doctor exposes 38. Both native smoke helpers now check the doctor's
required capabilities, empty missing-operation list, valid count, and requested
data directory. The combined script entry passed 17 tests, including eleven
synthetic Windows doctor reports. Current and expanded catalogs pass; missing
required operations cannot hide behind an unchanged tool count.

Evidence lives in `dist/harness-evidence/harvest-*.log`, with baseline screenshots
under `dist/harness-evidence/harvest-baseline` and final renders under
`target/ui-previews/harvest-entry`. No native app binary was launched or rebuilt;
these are component and simulated-launcher checks.

## Production Studio usability and draft safety — September 21, 2026

The owner's next shipping priority was marketing clarity and human-interface
polish. Baseline renders at 850×500 (the content space left on a compact laptop)
showed unusable planting/task panels, horizontally clipped recipe/handoff forms,
and nearly collapsed report tables. The new full-width, vertically scrolling
forms use associated human labels, readable table columns, explicit planning and
field-work views, and repeated task identity beside actuals. Status and recovery
messages wrap while preserving plain accessible text. There are now 36 leaf routes.

The same review found that navigation/F5 replaced unsaved frost and task actuals
and loaded all production services on the EDT. Background refresh now uses a
captured query and generation before applying a completed snapshot. Frost
baselines and task-ID draft maps preserve edits, including work on reordered,
changed, filtered, or missing tasks. A stale context, failed task read, or
superseded refresh keeps writes disabled until review. Explicit reload/discard
actions ask before replacing drafts; the frame includes Studio in its close guard.

Actuals saves capture task identity and values on the EDT and run in the
background. Success remains success when subsequent task/cost reads fail. A
missing task readback keeps writes blocked, and exit during a save explains that
already saved records are not undone. Other template/recipe/handoff writes retain
their existing synchronous service calls; no domain-wide record-version lock is
claimed by the client-side task review checks.

Independent review found and fixed two premature write-enablement paths after
interrupted refresh and missing post-save task readback. Rendered checks also
found Swing HTML CSS width inflation collapsing a table; actual pixel-based text
measurement and a shrinkable label minimum now preserve its viewport.

Focused verification passed 42 cases. Fifteen new refresh/safety cases and four
rendered theme/size cases exercise eight Studio views, full recovery messages,
and scroll reachability. The strengthened visual cases also passed independently.
Full clean isolated verification passed 1,576 tests and two packaged-JAR checks,
with no failures, errors, or skips. Line coverage is 86.88% and branch coverage
69.62%; coverage, Checkstyle, SpotBugs, test floors, and source/report evidence
checks all passed. A subsequent `--check` confirmed the saved evidence matches
the current source. The complete log is `dist/harness-evidence/ship-ui-quality.log`.
Screenshots are in `target/ui-previews/production-studio`; baseline examples are
in `dist/harness-evidence/production-baseline`. These are rendered Swing fixtures,
not evidence of native input or a rebuilt installer.

## Transport, worker, and desktop wiring — September 21, 2026

MCP previously exposed only action tools, so observation-only plugins and the
gateway's full manifest had no MCP path. Resource discovery/read now connects
those operations to the same authenticated gateway used by REST. The manifest
retains typed observation schemas; snapshot reads are live and reapply disclosure policy;
current-protocol reads have zero cache lifetime and private scope. Unknown URIs,
commands-only plugins, unsupported cursors, serialization failures, legacy
envelopes, permission revocation, and a real configured farm plugin are covered.

Farm-data 1.10 connects five missing actions: `get-season-settings`,
`list-order-handoffs`, `count-inventory`, `complete-order-handoff`, and
`get-operation-receipt`. Counts and fulfillment reuse the worker service's
review checks, atomic writes/receipts, and cross-surface operation-key identity.
Gateway request IDs remain process-local; durable receipt recovery is explicit
and requires sensitive-read permission. Fresh execution after restart still
requires current mutation permission and honors any supplied deadline.

Skipped-task previews now use the same pure state validation as locked actuals
writes, returning row issues while keeping receipt-backed imports recoverable.
The first focused run exposed a joined transactional validator marking a caught
preview conflict rollback-only and producing HTTP 500. The validator is now a
pure function invoked on an already loaded task and repeated after locking at
commit. The other initial failure was a test comparing JSON integer node classes
instead of their identical wire representations; the test now round-trips both.

Swing's action journal checks deadlines at the actual EDT callback boundary,
preserving the accepted receipt and publishing a failed status without invoking
expired work. It does not interrupt already started callbacks. Production Studio
refreshes cost/availability readouts after task/planting writes, preserves saved
frost settings when the following read fails, and refreshes handoff links after
recipe deletion. Unrelated drafts survive these targeted refreshes; post-write
read failures report the saved action and the relevant refresh control.

The readiness doctor now requires a usable data-directory path and independently
checks all 38 farm action names, covering previously omitted production tools
and the new worker operations. Its exit code follows aggregate readiness.
The packaged process smoke also reads MCP resources and verifies stock-count
receipt recovery between separate JVMs sharing the farm database.

Focused verification passed 102 cases after the two corrections. This pass adds
52 Java regression cases and raises the test floors to 163 suite/phase entries.
Final online `python -B scripts/verify_harness.py --isolated` passed 1,557 tests
plus two packaged MCP checks with zero failures/errors/skips. All 163 suite/phase
floors passed; Checkstyle and SpotBugs reported zero findings. Coverage gates
passed at 86.72% lines / 69.39% branches. XML/JSON SBOMs were generated, and
18 Python evidence-gate tests passed. Evidence is in
`dist/harness-evidence/latest.json`; the full log is
`dist/harness-evidence/wiring-quality.log`.

## CSRBT ecosystem comparison and hardening — September 21, 2026

Compared the harness contract and verification tools with CSRBT commit
`2a80194777f159cc4111467a282c199ff174effe`; see the
[implementation comparison and follow-up acceptance criteria](HARNESS_ECOSYSTEM.md).
This pass enforces supplied deadlines on fresh nonphysical commands across all
three transports, preserves replay-before-expiry semantics, closes JSON-RPC
protocol diagnostic redaction gaps, and replaces permissive numeric punctuation
stripping with a strict grammar. Batch regressions require malformed second rows
to leave both farm records and source receipts unwritten. Exact range checks
cover huge positive/negative exponents and floating-point underflow.

The verification runner performs a clean build, checks per-class test floors,
rejects stale/missing/failed/skipped/flaky reports, and hashes source/build inputs
and JUnit/quality evidence. Independent review caught two evidence hazards:
unchanged test counts did not prove unchanged reports, and Git-only enumeration
missed ignored source files. The runner now hashes report contents and actual
input files. Baseline reconciliation also found two stale XML reports and
Surefire header collisions for identically named nested tests; clean reports and
distinct testcase identities prevent either from inflating/losing test counts.

The previously uncommitted work passed 1,394 tests plus two packaged MCP checks,
coverage, Checkstyle, and SpotBugs before commit `d615837` was pushed. GitHub jobs
for that revision did not start because of the account billing/spending limit.
No remote build result is inferred from local verification.

Final online `python -B scripts/verify_harness.py --isolated` passed 1,505 tests
plus two packaged MCP checks with zero failures/errors/skips. Checkstyle and
Medium-or-higher SpotBugs passed with zero findings; coverage was 86.33% lines
and 68.98% branches. Both SBOM formats were generated. Eighteen Python evidence
fault-injection tests passed, and `--check` confirmed unchanged source and report
hashes. This pass adds 111 Java regressions. Evidence and logs are under
`dist/harness-evidence/`; verified output is under `target/harness-verification`.
The isolated build preserves an older native executable that prevented cleaning
the ordinary target directory. No native app was stopped or installer rebuilt.

## Numeric precision and recovery after interruption — September 13, 2026

REST command maps and MCP frames previously rounded decimal tokens through
`Double`. Raw-wire regressions reproduced changed values, underflow to zero,
overflow rejection, changed REST arguments replaying as identical, and distinct
MCP numeric IDs or arguments collapsing into one execution. Scoped decimal
binding now preserves those values; command copies normalize decimal scale
without accepting decimal tokens as canonical integers. MCP canonical hashes
retain the earlier double spelling only when that decimal spelling is exact.

Migration handling preserves explicit negative floating zero with a second
streaming token scan of an already validated frame, including nested arrays and
escaped object keys. It builds no second full tree. Bounded replay-only probes
check supported historical rounded identities before new precise execution. A
known ambiguous legacy receipt produces a tool error, rather than an exact
replay claim or repeated effect. Ordinary lossless decimal and negative-zero
receipts retain their existing identities. This cannot recover an evicted
non-durable receipt or an unsupported historical formatter. Domain numeric
ranges still apply after transport parsing; exact transport storage does not
expand those ranges.

Mapped JSON cells now preserve decimal precision as well. Near-integer recipe
pack sizes can no longer round into accepted integers, full-width whole values
remain exact, and fractional spreadsheet dates fail an exact integer check.
HTTP/database tests prove rejected recipe imports save neither recipes nor
source receipts. The separate permissive numeric text punctuation parser remains
a follow-up candidate: malformed grouping/currency/percent placement can still
be stripped into a different numeric value. This pass does not change that text
conversion grammar or documented live-offset pagination semantics.

Optimus durable receipt recovery conditionally clears only the matching pending
execution claim, using a locked persistence operation and read-back. Cleanup
failure leaves the accepted receipt accessible and reports its persistence
status. Cached responses retain their original status; fresh staging and discard
paths retry cleanup only when a fresh adapter sample and durable receipt agree
on operation ID, plan fingerprint, request, and robot. Tests protect newer plans,
changed claims, wrong robots, silent no-op cleanup, and ongoing outages. An
independent review found no additional defect in those cleanup boundaries.
The related staging/safe-pose regression showed that replacing the adapter's
current operation could erase a completion already observed by the plugin.
Plugin 0.23 now journals that terminal sample before either replacement. A
failed append prevents the new staging/dispatch until retry; emergency-stop
behavior is unchanged. Two initial tests reproduced lost `SUCCEEDED` history
and dispatch despite failed history persistence.

Composed Swing controls previously bypassed standalone password-field rejection:
password renderers could disclose raw model values and password combo editors
could be read or changed through their parent. Recursive renderer guards and
editor checks now reject those reads/actions without including the secret in
errors. Discovery keeps the known password-backed combo as unsupported, without
reading its draft or discarding the whole snapshot. Fifteen tests cover these
boundaries and normal-control recovery. These are supported extension-control
fixtures; no current production screen uses the affected compositions. The
separate lifecycle cancellation review found no new actionable defect.

This pass adds 59 regression cases. The initial 14 transport cases reproduced
11 failures (`target/harness-numeric-red.log`). Combined initial recovery and
password tests reproduced 16 failures in 19 cases
(`target/harness-recovery-password-red.log`). Mapping reproduced seven failures
in a 24-case run after the transport fixes (`target/harness-mapping-precision-red.log`).
Follow-on recovery cases reproduced the cached-response block and terminal-history
loss (`target/harness-recovery-cached-red.log`,
`target/harness-terminal-history-red.log`). The isolated migration red used an
older prior-pass handler because a review output directory shadowed the intended
classes; its sixth failure is not counted as a new defect. A fresh isolated
seven-case run passed against the current classes
(`target/harness-numeric-migration-isolated-green.log`). Maven focused runs passed
74 initial precision/contract cases and 82 combined numeric/migration/mapping/
framing/password cases (`target/harness-precision-focused.log`,
`target/harness-numeric-migration-focused.log`). The Optimus-focused run passed
40 of 41 cases; its new persistence fixture compared nanosecond timestamps
against the database's rounded values. The fixture now uses millisecond-aligned
timestamps (`target/harness-optimus-recovery-focused.log`).

Final offline `mvn -o -Pquality verify` passed **1,394 tests plus two packaged-JAR
MCP checks**, with zero failures/errors/skips
(`target/harness-numeric-recovery-final-quality.log`). All 69 worker journeys
and the 35-route catalog passed. Coverage gates passed at 86.71% lines and
69.27% branches. Checkstyle reports zero violations; Medium-or-higher SpotBugs
reports zero findings/errors. The build completed in 5:17. Offline mode skipped
SBOM generation. Native installer, external-service, and hardware acceptance
were not performed. Changes remain local and uncommitted.

## Recovery contracts and MCP framing — September 13, 2026

`McpFrameReader` replaces lossy UTF-8 decoding before JSON parsing. Malformed
bytes previously became replacement characters and reached the handler. Each
frame now has a strict decoder with fixed byte/character buffers; incomplete
code points survive buffer boundaries, while malformed or oversized frames are
drained before the next request. Existing decoded UTF-16 size accounting and
LF/CRLF/CR framing remain supported. Seven tests cover invalid byte sequences,
valid multibyte and replacement-character text, decoder boundaries, size limits,
and EOF recovery. The real MCP process smoke sends an invalid-byte inventory
request, expects a parse error, and verifies it created no record.

`create-orders` now advertises and validates only DRAFT/CONFIRMED. Historical
imports and mapped history retain all four statuses. The orders catalog adds
`operationalWriteSchema` while retaining its historical `writeSchema`. Thirteen
cases check discovery through REST/MCP, the catalog, batch preflight,
defaults, live states, and historical terminal states.

Corrected source rows whose targets were deleted previously escaped as service
404 responses, while unchanged retries returned the documented import conflict.
Only receipt-backed target updates now translate the locked service's missing
target exception to 409. Five real REST/database cases cover inventory, customers,
growing templates, planting plans, and floral recipes; receipts remain unchanged
and deleted targets are not recreated. Ordinary update/domain errors retain
their existing behavior.

New export part names use ASCII digits. Older localized names are accepted only
for the exact padded index in one decimal-digit alphabet. Fixed metadata
serialization replaces application-dependent hashes and date output. Strict
metadata reads preserve decimal scale and use standard Jackson timestamp units:
fractional seconds and integral milliseconds. Up to eight streamed checksum
candidates use stored raw values with supported map ordering, indentation, and
Unicode escaping. Existing files and checksums are never rewritten; exact create
replay compares normalized request fields only after integrity validation.
Thirteen new export cases cover locales, legacy metadata, nanosecond/millisecond
precision, new formatting independence, and altered metadata rejection.

Legacy recovery supports compact JSON and default pretty JSON using the current
host newline style. Custom date formats, custom integral epoch-second serializers,
and older pretty hashes with a different newline style remain outside this
compatibility contract. New metadata has a fixed compact checksum representation.

The action journal needed no production change. Three new deterministic tests
prove that its limit of 16 pending actions rejects only fresh work, a pending modal survives
eviction at 256 receipts, and an interrupted caller keeps one execution and a
stable receipt through retries. An independent frame-decoder review added the
two large-buffer regressions; independent export review caught the integer
timestamp unit issue before final verification.

This pass adds 41 regression cases. Initial failure evidence is in
`target/harness-next-utf8-red.log` (two malformed-input failures),
`target/harness-next-contract-red.log` (four schema failures; journal tests pass),
`target/harness-next-resume-red.log` (five 404 conflicts and three locale failures),
`target/harness-next-export-format-red.log` (six metadata recovery failures), and
`target/harness-next-export-millis-red.log` (an incorrect far-future timestamp).
Focused runs passed 21 framing/journal cases, 47 combined cases including the real
MCP process, and 16 export cases before the last millisecond rule. Logs:
`target/harness-next-framing-journal-green.log`,
`target/harness-next-focused-green.log`, and
`target/harness-next-export-format-green.log`.

Final offline `mvn -o -Pquality verify` passed **1,335 tests plus two packaged-JAR
MCP checks**, with zero failures/errors/skips
(`target/harness-next-final-quality.log`). This includes all 69 worker journeys
and the 35-route catalog. Checkstyle and Medium-or-higher SpotBugs report zero
violations/findings/errors. Reported coverage is 86.65% lines and 69.04% branches;
both gates passed. Offline mode skipped SBOM generation. Native installer,
external-service, and hardware acceptance were not performed.

## Deep harness and API follow-up — September 13, 2026

MCP no longer filters a known tool out before attempting safe receipt recovery.
The shared gateway replay-only entry point authenticates, checks sensitive-read
policy, validates exact command identity, and recovers cached or durable receipts
without executing a fresh plugin action. Expired physical receipts survive policy
revocation, while fresh denied actions and cached sensitive reads remain blocked.
Recovered observations are filtered against current disclosure policy.

New MCP identities canonicalize expiry instants and use a fixed JSON writer,
independent of pretty printing and Unicode escaping. Exact historical envelopes
also check current/compact/default-pretty legacy hashes through replay-only calls.
Recovered legacy responses retain their original request IDs. Old arbitrary
formatters and changed spellings of old noncanonical expiry values remain outside
that bounded migration; replay does not create a persistent alias for those hashes.
Harness-specific REST and MCP error paths now apply the shared redactor and the
same 240-character bound, including blank-message fallbacks.

Mapped JSON cells reject duplicate properties and trailing JSON/garbage instead
of silently discarding content. REST coverage verifies a malformed second recipe
prevents all rows and receipts from being written. Harvest preview calls the
same side-effect-free batch validation as import for farm-zone dates and rounded
inventory quantities.

The plugin's encompassing transaction previously held a connection while export
creation opened its own REQUIRES_NEW transaction. With one available connection,
creation timed out. `FarmDataCommandTransactions` now wraps only commands needing
the outer boundary; export creation retains its dedicated repeatable-read
transaction, while file reads/catalog/mapping suggestions open none. A real H2
pool-size-one test covers creation, receipt/key replay, and file reads while the
sole connection is deliberately held. Another verifies later-row failure rolls
back earlier writes and import receipts. Flyway uses a separate test datasource
because H2 migrations themselves require more than one connection. This does not
claim unsupported database isolation modes or caller-owned outer transactions work.

Snapshots bind context before descriptor construction and verify attachment,
active root, and tracked state afterward. A change produces `ready=false` without
controls/token. The synchronous alias-retargeting probe uses an explicitly custom
Swing getter; no production getter with that callback was identified. Screenshot
tests use ordinary attachment/root replacement during a blocked paint and verify
rejection plus stable recovery. Renamed/reordered table columns are read and sorted
by `TableColumn.headerValue`, including duplicate-label rejection.

This pass adds 32 regressions. Failure evidence is in
`target/harness-deep-red.log` (transport/parser failures plus an initial fixture
startup failure), `target/harness-deep-observation-transaction-red.log` (six Swing
failures and two reproduced connection timeouts after correcting the fixture),
and `target/harness-deep-harvest-red.log` (two incorrect ready previews).
Focused offline validation passed 110 tests with zero failures/errors/skips
in `target/harness-deep-focused.log`.

The first full run completed 1,294 tests with one existing date-sensitive CRM
fixture failure and no errors/skips (`target/harness-deep-full-quality.log`).
That fixture expected September 12 to remain in the future; it now captures a
date seven days ahead using the same default-zone clock as the review helper.
Production early/overdue warning behavior is unchanged.

Final offline `mvn -o -Pquality verify` passed **1,294 tests plus two packaged
executable-JAR MCP process checks**, with zero failures, errors, or skipped tests
(`target/harness-deep-final-quality.log`). This includes all 69 worker journeys
and the 35-route catalog. Checkstyle has zero violations; Medium-or-higher
SpotBugs reports zero bugs/errors. Reported coverage is 86.44% lines and 68.81%
branches, exceeding the 75%/55% gates. Offline mode skipped SBOM generation;
that artifact was not verified by this run. No native installer or external
service acceptance was performed.

The singleton gateway still serializes command execution. Native OS input,
external services, custom header-renderer pixels, and independent hardware
emergency stops remain separate acceptance concerns.

## Harness and API follow-up — September 13, 2026

Attachment content reads and uploads now preserve positive signed 64-bit IDs.
Both commands previously narrowed IDs to 32 bits despite the domain using
`Long`. Regressions cover values above `Integer.MAX_VALUE`, `Long.MAX_VALUE`,
overflow, fractional values, and exact REST/MCP responses and receipt replay.
Invalid IDs fail before invoking the attachment services.

Desktop text edits now fail if a synchronous listener replaces the field's
document; checking the detached document had allowed a false success receipt.
Queued failure/replay and fresh-request recovery are covered. Option-label
matching now observes the original combo model for replacement or notifications
from renderers and rejects a refresh before selecting from the changed model.
The temporary listener is removed before normal selection callbacks so CRM's
rebuilds of equal option records continue to work.

The REST harness now registers a strict mapper specifically for command bodies.
Duplicate keys, trailing input, unknown envelope fields, coerced request IDs or
action names, and invalid expiry types/formats receive `400 MALFORMED_BODY`
before dispatch. Missing/null optional fields, valid offset timestamps, vendor
JSON media types, and the attachment parser allowance are preserved. Ordinary
API DTO binding remains unchanged. Actual server integration checks exercise
the registered converter and its attachment allowance.

The common API error handler now returns 415 for unsupported request media and
400 for missing required multipart parts. Unacceptable response media returns
an empty 406, avoiding a false internal-error incident and a second failed
attempt to serialize a JSON error that the client cannot accept.

The eight attachment/Swing regressions failed before their fixes: 33 tests,
six assertion failures and two old ID-range errors in
`target/harness-api-boundary-red.log`. The initial focused Maven pass succeeded
with 54 tests, zero failures/errors/skips
(`target/harness-api-boundary-focused.log`).

The broader offline run passed **515 tests with zero failures, errors, or skips**,
including all 69 worker journeys, the 35-route catalog, REST/MCP, controller,
attachment-service, worker workflow, and Optimus simulator checks
(`target/harness-api-boundary-validation.log`). The final stringified-epoch
expiry guard was added after that run compiled; the recompiled API suite passed
**32 tests**, including all 18 strict command-body cases and actual REST
integration, with zero failures/errors/skips
(`target/harness-api-boundary-final-api.log`). This pass adds 31 regressions.
Checkstyle and the Medium-or-higher SpotBugs gate passed with zero violations,
findings, or analysis errors (`target/harness-api-boundary-quality.log`).
Changes remain uncommitted locally; native installers, OS input, external
services, and GitHub work were not exercised.

## Local follow-up — September 7, 2026

The desktop test driver now checks visibility through every ancestor to its
root. An inactive tab's controls or old success message cannot satisfy the
active journey. Repeated selectors resolve on the visible screen, nested-tab
selection ignores hidden tab sets, and disabled tabs or tab sets reject
navigation. Structural `named` assertions still inspect the component tree;
actions on those components enforce the same visibility boundary.

The runtime desktop snapshot now checks each published selector against the
same name aliases accepted by commands, retaining the discovery contract's
uniqueness requirement across component types. A selector that collides with
another visible component's name or button text uses an existing unique
production component name when available; otherwise it remains unaddressable.
Disabled aliases remain ambiguous; hiding the conflicting component makes the
remaining selector usable again.

The full catalog check now collects addressability failures across every route
before reporting them together. It found six label collisions: Dashboard's
More farm tools, CRM's More order actions, the three Activity History filters,
and the Inventory rose product selector. Existing component names resolve three;
the history filters now have stable names without changing their display labels.

The stricter visibility boundary also exposed a real Market Day handoff defect:
opening an order selected the outer CRM screen but left its Customers workspace
active. The handoff now opens Orders and reveals the selected row. The empty
Trend Analysis journey now opens Model details before reading that hidden panel.

Focused visibility, disabled-navigation, alias, and fallback regressions cover
these cases. The original implementation failed the four initial visibility
regressions and the alias regression before their fixes. These changes concern
deterministic Swing behavior; native input and assistive-technology limits below
still apply.

Local validation passed **366 tests with zero failures, errors, or skips**,
including all 69 desktop journeys, all 35 published runtime routes, surrounding
priority/production workflows, rendered entry contracts, the REST-to-Swing
bridge, gateway policy/replay, and MCP protocol checks. The offline Maven log
is `target/harness-local-final-validation.log`. This pass does not rebuild the
native installers or claim new native pointer/keyboard coverage.
Checkstyle reports zero violations and the Medium-or-higher SpotBugs gate
reports zero findings/errors; see `target/harness-local-quality.log`.

## Continued local audit — input bounds and retry behavior

The human driver's `setSpinner` no longer bypasses model bounds through direct
`setValue` calls. Numeric inputs retain the model's existing type and must be
exactly representable; date limits and list membership are also checked before
changing the editor, model, or listeners. Three regressions cover accepted
endpoints, invalid values, and recovery.

Desktop action receipts now freeze their first published response atomically.
A duplicate call that first receives `PENDING` cannot later receive a different
original receipt because the callback completed during the dispatch grace
period. Live `action-status` still reports completion. A deterministic concurrent
regression verifies the same receipt for all callers and a single callback.
The usual singleton gateway already serializes requests; direct plugin callers
and separate gateways sharing a plugin also now receive stable receipts.

Live farm-data paging now rejects an empty page whose reported total indicates
more records. Such a page previously returned `hasMore=true` with the same
offset, allowing a client to loop indefinitely after concurrent writes. The
explicit error requests a retry instead of reporting false completion. Two
regressions cover the database page contract and the published plugin action;
legitimate terminal pages remain valid. Complete exports retain their existing
repeatable-read snapshot semantics.

The three paging/receipt regressions failed before the fixes. Final offline
validation passed **190 tests, zero failures/errors/skips**, including all 69
desktop journeys and 35 routes, modal lifecycle, domain exports, gateway, and
MCP integration. Logs: `target/harness-continuation-red.log` and
`target/harness-continuation-validation.log`.
Checkstyle and the Medium-or-higher SpotBugs gate also passed with zero
violations/findings/errors; see `target/harness-continuation-quality.log`.

## Continued local audit — observation-bound actions

Swing desktop plugin 1.7 adds `metadata.contextToken` to ready observations and
the optional `expected-context` string to all 18 queued actions. The bridge
checks it on the EDT immediately before invoking the action. A changed active
dialog, replaced component, altered control state, or notified model change
rejects the callback with `DesktopContextChangedException`. Unchanged
observations keep the token, and unavailable observations omit it. The token is
random and contains neither field values nor hashes of them.

The tracker records visible component/model identities and subscribes to Swing
change notifications, without enumerating table rows or rendering domain data.
It releases subscriptions when components leave the active tree and when the
desktop attachment is replaced or detached. Once a compared state invalidates
an observation, returning to an older tree cannot revive its token.

Replay continues to precede this check. A completed action remains replayable
after the screen changes; a rejected action requires a new observation and new
request ID to retry. Commands without the optional argument keep their existing
selector-at-execution semantics. Model notifications and event-thread UI
mutation remain prerequisites; this is not a transaction over hidden domain
state or a guarantee of completed background persistence. See the
observation-binding section in `AI_HARNESS.md`.

Twenty-four new regressions cover the context contract: 10 helper cases,
13 plugin/gateway cases, and one REST-wire case. They include same-selector
replacement, reordered/updated rows, modal replacement, listener cleanup,
legacy commands, stale/successful replay, and attachment replacement both before
dispatch and during context resolution. A million-row virtual table throws on
cell access to verify that context tracking does not read its records.

Offline validation passed **222 tests with zero failures, errors, or skips**,
including all 69 desktop journeys and 35 runtime routes. Logs:
`target/harness-context-focused.log` (30 focused tests) and
`target/harness-context-validation.log` (complete selected suite). This pass
remains local and does not rebuild or exercise native installers.
Checkstyle reports zero violations, and the Medium-or-higher SpotBugs gate
reports zero findings/errors; see `target/harness-context-quality.log`.

## Continued local audit — runtime interaction and perception coverage

The runtime desktop plugin is now version 1.8 with 30 commands. This pass closes
gaps between the deterministic journey driver's reach and the live API:

- `scroll` and `set-divider` use the real scrollbar/divider models, with bounds
  and availability read through `read-layout`. A named pane or a named control
  inside it can identify the target. Lookup stops at the current active root.
- `set-toggle` requests an exact selected state and only clicks when needed.
  Grouped deselection, wrong control types, and unavailable controls are rejected.
  Its menu-path form, shared with `read-control`, handles closed popup toggles
  such as Dark Mode without opening a native menu. Enabled state is checked
  through the logical menu hierarchy. MCP coverage verifies menu reads, changes,
  unchanged-state no-ops, and receipt replay.
- `choose-option-index` can choose distinct model entries with identical rendered
  labels; `clear-selection` removes table/list selection through normal models.
  Selection clearing checks the final row/column state and fails if synchronous
  listeners restore it, with regression coverage for failure and recovery.
- `read-control` can page beyond the former first 200 items, 50 columns, or 8,000
  text characters. Table pages retain a 2,000-cell cap. `read-item` recovers text
  beyond clipped cell/option summaries. Absolute view indexes and explicit
  continuation fields let callers traverse all available entries.
- Snapshot metadata publishes uniquely addressable read-only outputs and layout
  panes without their values. `read-screen` pages visible labels, dialog messages,
  and text documents, including outputs without unique selectors. It excludes
  hidden tabs/passwords and avoids full-document copies; custom chart pixels
  still use `capture-screen`.
- `edit-text` handles long drafts through bounded insert/replace/delete segments.
  Range checks happen before mutation, normal document filters still run, and
  refused replacements fail instead of being reported as successful edits.
- Structured reads can bind to `expected-context` and return the actual read's
  `contextToken`. Scrollbar-model and viewport notifications now invalidate that
  context, including changes that are subsequently reversed. Old request IDs
  continue to replay old reads; new IDs are required for fresh pages or status.

The 35-route runtime catalog now reads every published supplemental output and
layout selector in addition to checking every interactive selector. The new
runtime tests also use the production Event Services page, exercise policy
denials, and keep modal target resolution inside its active root. This extends
application-level coverage; native input, external account integration, and
assistive-technology limitations listed below remain distinct acceptance work.

Final offline validation passed 279 tests with zero failures, errors, or skips,
including all 69 journeys and the expanded 35-route catalog. This continuation
adds 57 regressions and verifies discovery of 30 desktop actions among 63 total
manifest tools. Evidence: `target/harness-capability-validation.log`; the earlier
focused 93-test pass is in `target/harness-capability-focused.log`.
Checkstyle passed with zero violations and the Medium-or-higher SpotBugs gate
passed with zero findings/errors (`target/harness-capability-quality.log`).
All changes remain local; native installers were not rebuilt or launched.

## Continued local audit — verify application outcomes

The 30-command desktop API now verifies older state-setting operations after
synchronous listeners run. Reverted combo choices, tabs, table/list selections,
sliders, and sort orders report failure. Combo checks compare value equality so
CRM can rebuild its product-choice model using fresh equal records.

The runtime spinner path parses with its installed formatter and validates
standard model constraints before changing the editor. Invalid values leave
drafts and listeners untouched; disabled/read-only editors cannot be bypassed.
The final model value is checked after the ordinary editor commit, catching
DefaultEditor's silent model rejection and application-driven reversion.
The JDK's default integer formatter can narrow `4294967297` to `1` or `2.9`
to `2` before checking its range. An exact decimal preflight rejects such
overflow/truncation and trailing text before touching the draft. It preserves
localized grouping and explicitly overridden custom parsers.

Harvest's actual double-click handler loads the currently selected record.
Previously, a synchronous selection listener could redirect that selection and
the harness would still deliver a double-click. It now checks the selected row,
original table model and model-row mapping, and intervening model notifications
before delivering the mouse event. Tests cover redirect/clear, record changes,
sorting that keeps the same selected view index, failed replay, and recovery.

Observation now tracks each table column's effective default renderer. Without
that binding, a renderer replacement could mix different rendered text across
pages while the old context remained accepted. Column-specific overrides do
not invalidate for unused default changes. Tracking reads no table cells, and
runtime paging verifies stale rejection and a fresh full-cell read.

This continuation adds 29 regressions. Final offline validation passed 308 tests
with zero failures, errors, or skips, including all 69 journeys and the 35-route
catalog (`target/harness-outcome-final-validation.log`). Earlier passes are
recorded in `target/harness-outcome-focused.log` (85 tests) and
`target/harness-outcome-validation.log` (305 tests before the exact integer
preflight and its three additional regressions).
Checkstyle passed with zero violations and the Medium-or-higher SpotBugs gate
passed with zero findings/errors (`target/harness-outcome-quality.log`). All
changes remain local; native installers and external services were not exercised.

## Continued local audit — consistent reads and replay accounting

The shortcut command now falls back from explicit visible input-map bindings to
enabled logical menu accelerators. Production F1/PNW Plant Guide, Dark Mode, and
My access had accelerators but no traversed component input-map binding, making
them unreachable through `invoke-shortcut`. Explicit F5 retains priority over
its menu alias. Disabled bindings are excluded, allowing a usable menu
accelerator to handle the shortcut. Disabled menu ancestry and ambiguous
accelerators or paths are rejected, with normal `doClick` effects, modal scoping,
and MCP replay tests.

All four structured reads now bind both ends of the read to one context and
recheck the active root and attachment. A standard renderer's text-property
callback can synchronously refresh a model: previously the response could
contain old first-row text and new second-row text with a newly valid token.
Such reads now fail before publishing the mixed output. Stable retries still
run, and rejected reads create neither a successful cache entry nor a desktop
action-journal receipt. Lazy Swing binding and accessibility initialization
complete before recording the component revision so ordinary first table reads
remain usable.

The replay cache previously counted entity/DTO objects as 16 bytes, despite
`list-records` returning real populated Item objects. A reproduction retained
more than 10 MB of notes while accounting for only about 80 KB, bypassing the
8 MiB eviction target. Bounded JSON byte accounting now includes these fields,
command inputs, execution messages, and complete snapshots using the configured
serializer. Receipt preservation precedes fallible accounting; sizing failures
receive a conservative oversized weight while the newest receipt stays replayable.
Completion updates also move an incomplete receipt to the newest position before
eviction. Otherwise an older receipt retried after a newer command could evict
itself when accounting fails or the recovered snapshot exceeds the budget,
allowing its completed effect to run again on the next retry.

On September 13, the resumed validation reproduced both receipt-retention
failures before fixing the completion update order. An additional regression
fills the 256-entry cache and confirms ordinary response replay does not refresh
eviction order. All 13 replay-budget tests now pass, including failed accounting
and oversized snapshot recovery. Evidence: `target/harness-resume-replay-red.log`
(12 tests, two failures before the fix) and `target/harness-resume-focused.log`
(87 tests passed).

The final offline regression passed **374 tests with zero failures, errors, or
skips**, including all 69 worker journeys, the 35-route runtime catalog, farm-data
exports, REST/MCP integration, application context, and the Optimus simulator.
The log is `target/harness-resume-validation.log`. These are application-level
checks; native installers, OS input, and external services were not exercised.
Checkstyle and the Medium-or-higher SpotBugs gate passed with zero violations,
findings, or analysis errors (`target/harness-resume-quality.log`). The work
remains uncommitted locally, and GitHub work remains paused.

## Completion audit

This pass checks the current working tree, including Production Studio's new
Production costs route. It closes the following concrete gaps:

- Nested canonical records now enforce their published required fields, types,
  enums, and unknown-field rules before dispatch. Integer overflow/truncation,
  impossible calendar dates, ambiguous source paths, structured scalar cells,
  and duplicate mapped source IDs are rejected. Explicit source key columns
  take precedence over inferred IDs. REST coverage verifies that a failed
  later row rolls back earlier writes and their import receipts.
- Commands snapshot nested arguments for stable replay. A failed observation
  after a completed action no longer allows that action to execute twice.
  Cached sensitive data respects current read policy. Cache accounting includes
  input data as well as results; the newest receipt can exceed the 8 MiB
  eviction target so a large upload remains replayable.
- MCP distinguishes ordinary legacy progress metadata from version metadata,
  preserves typed request identities and canonical object ordering, and rejects
  trailing JSON on a protocol line. Notifications remain silent and protocol
  output stays compact.
- Desktop commands honor disabled menu ancestors, selection-model constraints,
  unsortable columns, repeated nested tab titles, and disabled tab sets. The
  deterministic sweep now fails when the UI never settles instead of recording
  a timeout as successful coverage.
- Live desktop effects now return replayable accepted/completed receipts. A
  modal dialog can remain open while clients observe its controls, submit the
  next action, and poll the original action's status. Status reads omit field
  values and exception text. Queued actions stay bound to their original
  desktop attachment, and busy event loops do not pin the gateway indefinitely.
  Callback completion is distinct from subsequent background worker completion,
  which still requires visible-status and domain read-back checks. Bounded
  reads report both per-value and collection truncation, including selected rows.
- Robot dispatch rechecks lease, deadline, and state freshness after slow reads
  and the durable execution claim. Journal-write fallback uses the same
  monotonic lifecycle normalization as persistent reads, preserving terminal
  states and progress during an outage.
- MCP frames have a 32 Mi-character limit with overflow recovery. Both MCP and
  enabled REST parsing accommodate the full 15 MiB attachment's base64 string;
  the previous default JSON string limit rejected valid maximum-size uploads.
- Farm exports release each serialized page's JPA entities inside their
  repeatable-read transaction, keeping database pagination effective in memory.
- All native packaging jobs obtain the launcher properties and smoke scripts
  they reference. Local packaging runs the AI doctor and ping before archiving.
  Maven's post-package smoke now launches the real executable JAR in addition
  to the existing classpath process check.

## Result

The deterministic desktop harness can address every application-owned input
mechanism currently present in FlowersForever. It recursively visits 35 leaf
routes across primary and nested tabs instead of relying on a manually curated
list. Each visible route is checked for unique accessible selectors and live
button or slider listeners, and each distinct enabled application button is
activated once through its production listener with destructive/native
boundaries safely cancelled.

The route walk now also builds an exact option matrix. On every reachable
surface it selects every initial combo item, every bounded spinner value, every
slider and scroll position, every table row and sortable-column direction,
every list entry, both checkbox states, each radio choice, every tab, editable
text boundaries, and each single-line Enter action. The test requires the
reported exercised choices to equal the choices discovered before the action,
and requires each component's original state to be restored. Oversized numeric
ranges use explicit endpoint/current boundary evidence; sliders or scroll ranges
above the exhaustive safety guard fail and demand a scoped matrix rather than
silently sampling.

The audit found a real Event Services defect: the screen instructed workers to
Ctrl/Cmd-click several services, but shared table configuration reset the table
to single-selection. Construction order is now corrected and a two-service
memorial-media journey proves the production multi-selection model.

The keyboard audit found a second production defect: four Dashboard metric-card
buttons were enabled and mouse-clickable but explicitly excluded from focus
traversal. They now participate in the focus cycle, and every enabled control on
all 35 recursive routes must report itself focusable.

The exhaustive Enter pass found a third production defect: CRM order-line
validation opened a raw modal `JOptionPane` outside the injected dialog
boundary, leaving automation unable to dismiss it. That error now uses the
pluggable dialog adapter, and the same action is safely closed and recorded by
the human harness.

## Human action matrix

| Human action | Harness path and evidence |
|---|---|
| Type or replace single-line, multiline, Unicode, and password text | Accessible-name entry through the real Swing document; boundary-data matrices and login-form coverage |
| Submit with Enter | Normal `JTextField` action event |
| Use installed shortcuts | Production `InputMap`/`ActionMap`; CRM F6 and Ctrl+Enter plus navigator F5 and Alt-number contracts |
| Traverse by keyboard | Every enabled control must be focusable on every recursive route; installed smoke sends native Tab/Shift+Tab and Ctrl+Tab to leave composite tables/text areas |
| Choose fixed or renderer-labelled options | Every initially discovered production combo item plus visible renderer text; exact option evidence and restoration; installed smoke uses native Home/Down/Enter input |
| Check toggles and packing rows | Both checkbox states and each radio choice through normal button/item listeners, with original group state restored |
| Change spinners | Every bounded production spinner value where practical; explicit endpoints/current for oversized numeric domains |
| Move sliders | Every integer position through production change listeners; ranges above the safety guard fail instead of sampling |
| Scroll content | Every reachable horizontal and vertical integer position with the original viewport restored; oversized ranges require a scoped matrix |
| Select one table row | Every visible row through the production selection model, plus exact-column targeted journeys |
| Select several table rows | Every available all-row multiple selection plus native Ctrl/Cmd-click; two-service event brief covered |
| Sort a table | Ascending and descending on every sortable production column plus native header targeting |
| Double-click a table row | Real two-click `MouseEvent`; Harvest and CRM load/edit routes covered |
| Switch tabs | Visible title through the production tab model |
| Discover nested screens | Recursive enumeration of all visible `JTabbedPane` combinations; original selection restored |
| Click application buttons | Normal `doClick` action path with pre-action discovery, route transcript, UI settling, and async exception monitoring |
| Use menus | Full menu-tree audit for path, mnemonic, description, role state, accelerator, and listener; both Dark Mode states restored; non-terminal local actions safely swept |
| Open connector tray | Production tray toggle plus real listener activation of all 21 connector actions through safe connector/file boundaries |
| Complete/cancel modal forms | Scripted production dialog content, visible option text, field edits, copy assertions, and exact returned choice; installed smoke proves real modal focus and native OK dismissal |
| Choose/cancel save files | Production file boundary with title, suggested name, selected destination, overwrite decision, and byte verification; installed smoke proves real chooser focus, suggested filename, native Cancel, and no created file |
| Copy an event brief | Replaceable system-clipboard boundary with exact payload and unavailable-clipboard recovery |
| Wait for background work | Button locks, visible status, table/cell results, latest-request coalescing, stale-result rejection, retry, and uncaught async exception monitoring |
| Change roles | VIEWER/HAND/OWNER transitions across screen, menu, export, connector, mutation, and history-clear permissions |
| Use compact layouts | Rendered contracts and effective clipping measured through every ancestor, viewport, tab, and split pane |
| Use an installed pointer and keyboard | Opt-in real `JFrame` + `Robot` smoke proves native click delivery, typing, combo choice, header sorting, Ctrl/Cmd multi-selection, forward/reverse focus, modal focus transfer, dialog/chooser cancellation, production actions, and pixel capture |

## Surface inventory

No application-owned drag-and-drop, context-menu/right-click, tree control, or
editable table-cell workflow exists in the current desktop source, so there is
no corresponding production interaction to drive. Combo/spinner/scrollbar
arrow buttons installed by the look and feel are deliberately excluded because
they are implementation details of the controls already exercised.

The installed shell's Exit command is audited for a listener, mnemonic, and
safe unfinished-work copy, but the in-process sweep does not execute
`System.exit`. External imports are wired and covered at presenter, connector,
controller, role, and failure boundaries; the menu sweep excludes initiating
real external coordination.

## Installed-app smoke layer

Run the native-input layer only on a workstation whose mouse and keyboard may be
temporarily controlled:

```text
mvn -Pinstalled-desktop-smoke test
```

The profile runs two isolated smokes instead of the ordinary headless suite. The
first opens Event Services in a real always-on-top window, sends native pointer and
keyboard events, walks 14 forward focus transitions and one reverse transition,
uses Ctrl+Tab to leave Swing tables and multiline text, filters occasion/category
with Home/Down/Enter, sorts through the real table
header, Ctrl/Cmd-clicks both memorial-media rows, builds/copies/routes the
two-service brief through safe test boundaries, and writes
`target/installed-desktop-smoke/event-services.png`. Native event probes fail the
run if a physical click lands outside the intended production component.

The second opens the installed `FarmDialogs.swing()` and
`FarmFileChooser.swing()` implementations through native launcher buttons. It
proves focus moves into each modal, inspects the real chooser's suggested file,
dismisses both windows through physical OK/Cancel clicks, proves the cancelled
save creates no file, restores application focus, and captures the complete
windows as `native-dialog.png` and `native-file-chooser.png`. A bounded focus/key
retry records transient desktop focus loss, restores the prior control, and
still fails if the same native action cannot be delivered twice.

The following behavior still belongs to broader manual assistive-technology or
platform coverage and is not claimed by either automated layer:

- native-pointer file approval/overwrite through platform chrome (application
  chooser approval and cancellation are covered through the runtime harness);
- screen-reader speech, multi-monitor DPI, and window restoration across OSes;
- the operating system clipboard service itself (the exact application payload
  and unavailable-service recovery are covered);
- actual process exit/relaunch.

These are explicit limits rather than silent gaps.

## Customer-pluggable runtime layer

The test harnesses above remain deterministic verification tools. A separate
production `HarnessPlugin` SPI now lets a customer connect an AI provider, MCP
adapter, accessibility tool, or script to the live desktop without importing a
model SDK into Swing. The gateway exposes discovery, value-redacted observation,
and typed commands through one token, risk-policy, and request-replay boundary.
The separate headless `farm-data` plugin publishes nested canonical schemas and
calls domain services directly. It supports customer and inventory upserts,
historical harvest/order migration without current-stock effects, ongoing live
records, farm knowledge, task actuals, growing templates, explicit planting
plans, nested floral recipes, and bounded record read-back. A real REST
integration verifies this path independently of the desktop harness. V14
durable import receipts bind stable upstream row identities to target records
and content fingerprints. Coverage proves restart-safe exact replay, same-ID
master-data correction, append-history conflict detection, missing-key
validation, database uniqueness, and paged receipt read-back.
The 1.2 domain plugin extends the same correction and replay contract through
Production Studio. Focused and live REST coverage verify service-layer
validation, multi-ingredient recipe mapping, natural-key creation, durable
same-ID correction, and discovery of all nine mappable operational record types.
The 1.3 intake workflow adds mapping suggestion, non-writing preview, and atomic
mapped import for arbitrary spreadsheet/provider objects. It recognizes common
farm column aliases, accepts explicit dotted paths and defaults, coerces common
spreadsheet values and nested JSON arrays, generates stable composite source
keys, and rejects a whole batch before mutation when any row has structural or
domain issues. Unit and live REST tests cover suggestion confidence, coercion,
nested mapping, row-specific errors, rejection without partial writes, and
same-ID correction through the mapped-import path.
The 1.4 read path replaces whole-table materialization with database-level
offset and limit queries for every canonical type. Its stable per-type ordering,
exact total, `hasMore`, and `nextOffset` preserve the existing contract while
allowing large farms to be traversed in bounded memory. Production-task date
filters are part of the repository query. Unit coverage verifies repository
routing and arbitrary offsets; live REST coverage verifies continuation without
duplicate rows.
The 1.5 export path walks those pages inside a repeatable-read transaction and
publishes immutable, restart-stable manifests and bounded JSON parts. A hashed
stable key supports recovery and exact replay without retaining the raw key;
request fingerprints reject changed replay parameters. Canonical manifest,
file-size, SHA-256, and JSON-count checks detect corruption before data reaches
the caller. Unit coverage exercises restart recovery, replay conflict, default
all-type selection, multi-page chunking, and corruption. Live REST walks every
part of a real all-ten-readable-type export and reconciles the count, while MCP coverage
creates, recovers, and reads the same protocol.
The reference MCP transport now runs the same catalog through a packaged stdio
process. It supports current stateless discovery and legacy initialization,
deterministically filters unavailable tools, forwards calls through the real
gateway, returns compatible text plus structured results, recovers from malformed
input framing, and reserves stdout for protocol messages. Focused protocol,
farm-data integration, and packaged-process smoke coverage exercise that path.
The built-in live adapter covers text, combos, primary/nested tabs, table
single/multiple selection and sorting, lists, spinners, sliders,
buttons/toggles, single-line Enter submission, table double-click listeners,
terminal menu paths, and installed application shortcuts. Each snapshot also
publishes all 35 reachable route paths, and `navigate-route` opens an exact path
through the production navigator and nested tabs. Discovery publishes
every argument's JSON type, array-item type, and description. The manifest
compiles those contracts into common JSON-Schema tools and marks each tool with
the effective operator policy.

Human-visible perception is now a separate, default-denied `SENSITIVE_READ`
tier. It can return bounded renderer-visible control data or a scaled PNG of the
attached application root; it never reads passwords. Captures include size and
SHA-256 metadata and have pixel/byte limits. The gateway uses a 256-entry limit
and an 8 MiB eviction target for retained command data. It always retains the
newest completed receipt, including a single oversized upload; this target is
not a strict heap-memory cap.

Its loopback REST adapter is disabled by default. Read/navigation, drafting,
mutation, and destructive capabilities are distinct operator policies; generic
button activation is deliberately destructive. Focused tests cover every
built-in action, password rejection, field-value omission, renderer labels,
policy escalation, duplicate command retries, conflicting request IDs, and REST
error status, including a real random-port enabled endpoint. Setup and
plugin/transport examples are in
[AI_HARNESS.md](AI_HARNESS.md).

The same discovery boundary now reserves explicit device and robot kinds,
capabilities, typed sensor signals, and physical-world tool annotations. A
separate default-denied physical tier requires all elevated operator gates and
a command expiry within 30 seconds. Gateway contract tests prove typed sensor
validation, sensitive telemetry removal, stale/far-future command rejection,
and one-execution replay behavior for a representative future robot plugin.
This is integration readiness, not a hardware safety claim; watchdogs,
interlocks, collision limits, and emergency stops remain robot-owned.

The disabled-by-default `tesla-optimus` boundary exercises that contract with a
replaceable vendor adapter and an in-process simulator. Focused and random-port
REST tests prove default absence, explicit wiring, typed tool discovery,
redacted observation, bounded high-level task staging, exact plan-token
execution, asynchronous command identity, typed progress, exact active-task
cancellation, physical policy and expiry enforcement, replay safety, and a
latched stop. No undocumented robot transport or raw motion command is present.

The Optimus contract also caps review lifetime at five minutes, refuses silent
plan replacement, derives operational status from five-second-fresh robot state,
and durably claims a plan before the adapter boundary. Clock-controlled tests prove
expired-plan rejection, stale-state blocking, and non-sensitive lifecycle
telemetry without using sleeps.

Adapter identity is now a required contract. The simulator explicitly attests
simulation mode. Hardware mode fails startup without a configured matching
robot ID, and the plugin rejects identity drift after startup. This closes the
wrong-robot/silent-simulator wiring gap without guessing a future vendor
authentication protocol.

The audit also removed fail-open timestamp defaults from robot state, plans, and
receipts. Adapters must now supply timestamps; absent values cannot be silently
substituted with the application clock and presented as fresh telemetry.
Safe-pose dispatch now explicitly cancels any known adapter-side staged task
before starting motion instead of clearing only the harness copy.

Flyway V8 and the 0.5 plugin close the restart-recovery gap with an append-only
robot operation journal. Unit tests cover live and terminal lookup plus a
restarted simulator that safely converts an unreconciled `RUNNING` command to
`UNKNOWN`. Random-port REST coverage proves events persist through JPA and that
the read-only `get-operation` and bounded `list-operations` responses omit
stored robot identity and status detail. Migration tests cover both new and
baselined databases.

The 0.7 plugin adds an incremental `list-operation-events` feed over that same
journal. It returns ordered 1–50 event pages, a string `nextCursor`, and
`hasMore`, enabling AI clients to resume after disconnects without rescanning
each operation. Focused and random-port REST tests cover multi-page traversal,
invalid cursors, size bounds, JPA persistence, and output redaction. The
process-local journal now preserves and de-duplicates the same append history as
the production JPA journal instead of retaining only each operation's latest
state.

The 0.8 plugin and Flyway V10 close the staged-task restart gap. The current
robot/lease/timestamp metadata and plan-token digest persist while task text and
the plaintext plan token do not. A recreated plugin can observe and execute the
same adapter-held plan with the original tokens. Unit coverage recreates the
plugin between stage and execute; random-port REST coverage verifies JPA
creation/removal and confirms the returned plan token is absent from storage.
Lease-expiry coverage also proves a replacement controller can cancel orphaned
staged work without gaining the ability to execute it.

The 0.9 plugin reconciles that durable record with the current adapter. Its new
`staged-plan-adapter-confirmed` observation is true only when both layers agree
that work is staged. If the adapter restarts without retaining the plan,
execution clears the stale record without dispatching and asks the client to
stage again; staging with the active lease can also replace the stale record.
Regression coverage proves both same-adapter resume and adapter-restart recovery.

The 0.10 plugin closes the remaining cleanup asymmetry. Cancellation, lease
release, expiry, restaging, and return-to-pose now share one adapter-aware path.
An idle restarted adapter is not sent a redundant cancellation, while a live
staged adapter is cancelled before its durable record is cleared. Strict mock
coverage exercises each restart cleanup route and verifies that no idle-adapter
cancel call escapes the plugin.

The 0.11 plugin closes the remaining staged-identity ambiguity. Adapter state
now carries an internal fingerprint of its opaque plan token, which must match
the durable digest before `staged-plan-adapter-confirmed` becomes true or
execution can cross the adapter boundary. Regression coverage distinguishes a
matching resume from missing and unrelated staged fingerprints, and verifies
that restaging reconciles an adapter-only draft.

The 0.12 plugin also requires that identity acknowledgement before returning a
new plan token. A fresh post-stage observation must report `STAGED` with the
exact returned token fingerprint. Idle, missing, mismatched, stale, and
future-dated acknowledgement cannot create a durable plan, and the adapter draft
is cancelled before the failure is returned. Regression coverage exercises all
four distinct state-shape failures.

The 0.13 plugin closes failure atomicity around that flow. Every failure after
adapter staging starts now attempts to cancel the adapter draft, including an
ambiguous adapter exception, lease expiry or rotation during staging, and a
durable-store write failure. If cancellation also fails, it is retained as a
suppressed diagnostic without replacing the primary failure. Regression coverage
proves ambiguous acceptance cleanup, lease revalidation cleanup, and dual-failure
diagnostic ordering.

The 0.14 plugin closes the durable-commit acknowledgement gap. It verifies both
the store return and a fresh read-back before exposing the plan token, requiring
exact robot identity, mode, lease, and token digest while accepting only
sub-millisecond database timestamp normalization. Regression coverage rejects a
wrong write result, missing record, wrong read-back digest, and read failure, and
clears unusable metadata after successful adapter cancellation. The REST/JPA
integration proves a genuine committed plan still completes.

The 0.15 plugin and Flyway V11 close the ambiguous execution-handoff gap. The
original harness request ID is atomically attached to the staged record and
read-back verified before adapter dispatch. A timeout before acceptance permits
only that exact request to retry the idempotent plan; a timeout after acceptance
is recovered after restart from a matching originating-plan fingerprint and
command ID without redispatch. Conflicting request IDs are rejected, unresolved
markers stay observable, and the record is cleared only after the receipt reaches
the durable operation journal. Focused coverage exercises both timeout windows,
restart, conflict rejection, exact recovery, and single dispatch after acceptance;
REST coverage exercises the V11 JPA claim and cleanup path.

The 0.16 plugin and Flyway V12 close the response-loss gap after a receipt was
successfully journaled. The operation journal binds each Optimus execution
request ID to a SHA-256 fingerprint of the exact action, plan capability, lease
capability, and original deadline without persisting either plaintext token. A
fresh gateway checks that record before stale-deadline rejection, reconciles the
operation, and returns the same command without redispatch. Regression coverage
recreates both plugin and gateway, retries an already expired exact request,
requires `replayed`/`recovered` output, proves only one adapter execution, and
rejects a changed-command collision. REST/JPA coverage verifies the durable
request lookup and non-plaintext fingerprint.

The 0.17 plugin closes the remaining live-adapter dependency in durable replay.
After startup identity has been established, an owner-API identity/state outage
no longer prevents an accepted exact request from returning through the gateway.
The operation becomes `UNKNOWN`/`UNCONFIRMED`, the response snapshot reports an
unavailable connection with no invented battery, stop, or pose values, and no
second execute call occurs. Focused coverage also proves a new physical command
during the same outage never reaches the adapter. Returned identity drift still
fails instead of being classified as temporary unavailability.

The 0.18 plugin closes a read/write asymmetry in that outage recovery. A journal
can sometimes serve the accepted receipt while rejecting a new reconciliation
append. Replay now returns a locally constructed `UNKNOWN` event with
`reconciliationPersisted=false` in that case, retaining the original receipt for
another attempt. The degraded snapshot independently maps every nonterminal
last-known event to `UNKNOWN`, so it cannot repeat stale `RUNNING` state merely
because the append failed. Focused coverage forces the journal read-only after
acceptance, performs two fresh-gateway retries, and still observes one adapter
execution.

The 0.19 plugin closes the live counterpart. A confirmed adapter command no
longer loses its exact replay response merely because the lifecycle journal has
become read-only after acceptance. The plugin constructs the live result from
the durable receipt plus exact current adapter state, reports
`reconciliationPersisted=false`, and the gateway still attaches a fresh live
snapshot. Focused coverage forces both replay and snapshot status appends to
fail, verifies `LIVE`/adapter-confirmed output, and proves one execution. The
acceptance-receipt write itself remains mandatory.

The 0.20 plugin closes the corresponding operation-read gap. Exact live reads and
locally unconfirmed historical reads remain available when their lifecycle append
fails, and each result exposes `reconciliationPersisted`. The cursor event feed
continues serving only already-durable events, reports a failed optional refresh,
and never invents a cursor for unwritten state. Focused coverage exercises all
three read tools, both `LIVE` and `UNCONFIRMED` paths, retains the accepted receipt,
and proves the adapter executed once.

The 0.21 plugin closes the owner-API dependency in the same read surface.
`get-operation`, `list-operations`, and `list-operation-events` remain available
from the journal while adapter identity or state transport is unavailable.
Nonterminal work is explicitly `UNKNOWN`/`UNCONFIRMED`, terminal history is not
downgraded, and results expose `adapterAvailable=false`. Focused gateway coverage
also verifies the degraded snapshot and proves no second adapter execution; the
real REST/JPA integration remains green.

The 0.22 plugin closes the adapter-reconnect lifecycle regression gap. A shared
normalizer now protects both transient and JPA journals from out-of-order samples,
backward progress, backward nonterminal phases, and terminal resurrection. It
still permits a later real adapter state to recover an `UNKNOWN` operation.
Focused coverage exercises delayed samples, lower progress, phase regression,
recovery, successful completion, and a stale post-terminal reconnect. Existing
cancellation coverage confirms partial progress is retained rather than forced to
100 percent.

Flyway V9 adds one durable, exclusive 15–300 second control lease per stable
robot identity. Staging and normal control require the current capability;
renewal rotates its 256-bit token while only a SHA-256 digest is stored. Expiry,
release, wrong-token use, concurrent acquisition, restart continuity,
staged-plan ownership, and redacted status are regression tested. Emergency
stop intentionally remains lease-independent but still requires the global
physical policy and a fresh command deadline.

The same audit removed the truncated unsalted holder fingerprint from
observation and `get-control-lease`. Low-entropy holder labels made that value
dictionary-guessable despite its earlier “non-reversible” description. Holder
labels now remain only in local lease storage.

A whole-application runtime contract composes the real `FarmTabCatalog`, opens
every published route through the live plugin, and observes its current
controls. It requires every visible selector to be non-blank, unique across
control kinds, and backed by a supported listener/model; adding a route or
control without runtime harness coverage fails the suite.

A separate end-to-end wire test sends JSON through the real controller and
gateway into an attached Swing tree. It proves generated tool schemas, typed
integer and string arrays, sensitive-read policy enforcement, bounded values,
PNG signature/digest metadata, draft entry, Enter, double-click, menu, shortcut,
fresh redacted observation, one real button activation, and an exact retry that
replays the response without clicking twice.

The worker adapter closes the phone/tablet handoff gap without creating a second
farm model. Its responsive `/worker/` shell calls a narrow worker REST surface;
harvests, production-task actuals, and physical stock counts still pass through
the established domain services and become immediately readable through the AI
`farm-data` tools.
Browser-persisted operation keys and V15 transactional result receipts make a
lost response or restart replay exact. Integration coverage proves inventory
effects, task completion, replay conflict, restart recovery, AI read-back, farm
time-zone defaults, and authenticated role enforcement. Live browser passes
verified the field form, responsive layout, a real order handoff and stock
deduction, stale-order retention in the pending panel, explicit device discard,
and clean navigation state.

The stock-count extension uses a bounded field-only projection and V16 durable
receipt action. A pessimistic item lock plus reviewed-quantity comparison blocks
stale phone state from overwriting a harvest, fulfillment, import, or another
worker's count. Typed conflicts preserve the entered device copy for explicit
review/retry or discard; successful counts need no secondary AI synchronization.

The order-handoff extension closes the next field boundary with a database-bounded
Confirmed queue due through the selected date. Each card carries customer contact,
delivery context, instructions, and operational product quantities without price.
V17 adds a durable `COMPLETE_ORDER` action; its opaque review token is checked while
the order and customer are locked, and fulfillment, stock deduction, and receipt
join one transaction. Changed loads remain pending for review, while an unchanged
order completed by another worker reconciles without a second stock deduction.
The shared pending panel now exposes every queued device entry for retry or discard,
and successful state is read back through canonical AI order and inventory pages.

The field-evidence extension closes the photo path across the phone, database,
filesystem, backup boundary, and AI bridge. The task form accepts an optional
camera/file image; IndexedDB retains pending bytes while the existing queue keeps
ordered operation metadata. V18 adds immutable attachment metadata and the
`ADD_ATTACHMENT` receipt action. Server-side signature detection, a 15 MiB
limit, content-addressed storage, and SHA-256 validation protect every HTTP or AI
read. The `farm-data` plugin now pages attachment metadata as its tenth readable
type, reads verified 256 KiB chunks, and writes through the same durable worker
receipt path. Generated-task regeneration retains an open task with evidence.
Automated and live file-backed browser runs both prove photo-before-completion
replay after a server restart.

The packaged-AI audit closes the gap between an in-process MCP handler and a
farm-installable connection. Both native packages now carry a separate
`FlowersForeverAI` launcher with packaged data-directory semantics; Windows uses
an explicit console launcher so an AI client's redirected pipes remain usable.
`--mcp-doctor` boots the same database, migrations, registry, policy, and tool
manifest, then reports connection, reconciliation, and write readiness without
returning its token. A child-process test moves a mapped workbook row through
stdio into the file-backed store, reads it through the canonical page, and finds
its durable receipt. The Windows native image was additionally built locally;
the release smoke launches both platforms, runs the doctor, and exchanges a
real MCP ping before retaining installers.

## Runtime creation and export coverage

The earlier desktop plugin published 21 commands (the current 1.8 plugin has
30). Its editable-option path enters
custom Category and Selling Unit values through the production combo editor
and selection listeners. A gateway journey fills the real Add Item form, saves
through InventoryItemPresenter and InventoryService, reads back the stored
item, and retries the same save without a second repository write. Fixed option
lists still reject unknown values, and the draft policy is enforced.

Application export choosers now have a stable selector and dedicated
`choose-file` / `cancel-file-dialog` actions. A secondary-loop regression opens
the chooser as pending work, reads its state, approves an absolute CSV path,
verifies output bytes, replays without a second export, and cancels another
export without writing. It uses no native window or physical input. Relative
paths, directories in a files-only chooser, and filter mismatches are rejected.

The production control inventory has no application JTree or separate date
picker. Dates use ordinary editable text or spinner editors; checkboxes and
radio buttons use their normal button listeners. Existing table-selection,
sorting, form-entry, spinner, slider, shortcut, and owned-modal routes remain
available through the same gateway.

The focused capability run passed all 12 farm-data REST integration tests,
the new inventory-creation test and two file-dialog tests, and the existing
three Swing plugin and five modal-lifecycle tests. Production availability and
handoff pages bound returned rows but still compute their source service lists
in memory; their returned pages are copied so cached results retain only the
selected rows. Production identifiers preserve the full signed 64-bit range.

## Farmer-entry verification — September 4, 2026

After the V21 schema assertion and Tomcat security update, the final full
verification build passed **1,069 application tests**, both actual
executable-JAR process tests, and every quality gate. The packaged dependency
set and SBOM contain Tomcat core, EL, and WebSocket **10.1.59**.

| Check | Result |
|---|---|
| Full application stage | 1,069 passed; no failures, errors, or skips |
| Backend and entry regressions | 250 passed |
| Final desktop and layout regressions | 217 passed |
| Field forms and device-queue JavaScript | 10 passed |
| Final classpath / actual executable-JAR smoke | 2 / 2 passed |
| Native release contracts | 11 passed |
| Coverage | 86.04% lines; 67.72% branches; both gates passed |
| SpotBugs | 0 findings; 0 analysis errors |
| Checkstyle | 0 violations |
| CycloneDX | XML and JSON generated and validated; 94 components |
| Windows app-image creation | JDK 17.0.20.1 image built; final payload SHA-256 verified |
| Windows native launcher execution | Passed on JDK 17: doctor ready, schema 21, 33 farm tools, stdio ping |
| Java 17 executable-JAR process checks | 2 passed against the final patched JAR |

A live, isolated browser run saved a field note, restored an unfinished draft,
queued a note while the server was stopped, reloaded the page, and recovered
exactly one record after reconnecting. Farm-timezone defaults were checked
against Pacific/Kiritimati. At 390px the entry screen had no horizontal
overflow and primary inputs and buttons were at least 44px tall. Current
desktop Add Item light/dark renders and the compact Harvest Log were inspected;
essential fields remain visible while optional fields scroll.

Earlier fresh JDK 22 and supported JDK 17 images stalled while Windows
reported every JVM thread suspended and the launcher wrapper waited. The
pausing mechanism was not identified or overridden. A later bounded run of
the final JDK 17 image completed the readiness doctor, stdio ping, and clean
EOF shutdown successfully. It used the production smoke assertions with
timestamp-only progress logging and a 300-second per-phase limit; the result
reports schema 21 and all 33 farm tools enabled. A host suspension should be
investigated before treating a startup timeout as an application deadlock.
The native AI configurations select `-Djdk.console=java.base` for ordinary
stdio console behavior, as described by the
[OpenJDK Quality Group](https://inside.java/2023/07/31/quality-heads-up/);
that setting alone does not resolve the observed process suspension.

GitHub's initial JDK 17 and JDK 21 builds also passed all 1,069 tests, and its
browser, Docker, and static-analysis checks passed their execution steps.
Artifact uploads failed because account storage was full, preventing the
native release jobs from receiving their inputs. With owner approval, 20
older preview archives totaling 3.84 GB were removed while retaining the
newest preview and every test report. Future temporary release artifacts now
expire after one day and candidate/report archives after seven days. Quota
recalculation can delay subsequent uploads; the PR carries current CI status.

The subsequent Norton alert identifies `FlowersForeverAI.exe`, `IDP.Generic`,
and Behavioral Protection. Metadata inspection of the retained local image
found both launchers unsigned. That does not establish a false positive or
attribute every earlier pause to Norton. The [antivirus review guide](ANTIVIRUS_REVIEW.md)
documents collection and acceptance without running the blocked artifact.

Local MSI and GitHub candidate MSI/DMG packaging now consume the app image
that passed their smoke gates. Tagged Windows releases also require the MSI
and both launchers to have valid signatures from the same publisher subject.
The focused release contracts pass **14 tests**. The metadata collector was
checked on Windows PowerShell 5.1 for accurate hashes, valid signature details,
invalid executable status, missing launchers/installers, private-data omission,
no inferred build commit, and refusal to overwrite an existing artifact.
PowerShell 7 also collected the retained image's real unsigned metadata into
`target/windows-security-review.json`. No file was submitted to Norton and
no antivirus setting or quarantine was changed.

The first follow-up CI run exposed four test-fixture errors during the window
when UTC and Los Angeles have different dates. Integration harvest fixtures
now use the injected farm profile's timezone, and the closeout unit fixture
explicitly configures the farm timezone used by its expected dates. The
restart acceptance test also preserves the originally captured farm date for
its read-back. All six tests across the four affected classes passed with the
JVM in UTC while Los Angeles was still on the preceding date. These changes
leave production date validation intact.

Local application validation used JDK 22.0.2 with Java 17 compilation targets.

The final full build is `target/farmer-patched-verify.log`; native build and
execution evidence is in `target/farmer-native17-build.log`,
`target/farmer-native17-observed.log`, and `target/farmer-jdk17-jar-smoke.log`.
Earlier focused evidence is in
`target/farmer-release-contract.log` and `target/farmer-sbom.log`.
Detailed process reports are under `target/surefire-reports` and
`target/failsafe-reports`; Node checks run with
`node --test scripts/worker-ui.test.mjs`.

macOS packaging, signing/notarization, actual robot hardware, assistive
technology, and native pointer/keyboard and file approval/overwrite still
require their platform-specific checks. The headless modal tests use real
Swing secondary event loops and mocked owned-window relationships; they do
not establish OS focus or pointer coverage.

## Private preview transport and checksum verification

Actions artifact quota prevented the native jobs from receiving the verified
JAR even after application verification passed. An explicit manual draft mode
now calls a reusable workflow that stores the one Linux-verified JAR, metadata,
and tested Windows/macOS installers in a prepared private unpublished draft.
Source SHA, version, asset IDs, and SHA-256 hashes bind the transfers; a final
completion record requires both platforms. Ordinary PR checks retain read-only
repository permissions, and signed tagged releases keep their existing path.
See the [private preview runbook](PRIVATE_PREVIEW.md).

Backup of the retained August 28 preview exposed a manifest self-checksum bug.
Its five original artifact ZIPs match GitHub's recorded archive digests; all six
payload hashes and the duplicate standalone payloads also match. The original
invalid manifest was preserved as evidence. New candidate/release checksum
gates exclude the manifest itself and execute checksum verification before
retaining outputs. The 19 focused release tests include actual Bash checks of
payload hashes, paths with spaces, an existing-manifest rerun, corruption
rejection, manual-mode isolation, and identical native gates across both paths.
The asset helper passes 31 mocked tests, including the complete transfer,
binary redirects without token forwarding, source/hash mismatches, existing
asset preservation, checksum validation, and failed-upload recovery boundaries.

The first live private-draft run, `33947753440` at `c2fb388`, passed all 1,077
Java tests and both packaged-process tests. Windows completed native doctor,
stdio, runtime smoke, MSI packaging, and private upload. macOS created the
database promptly, then spent 105 seconds reopening it for stdio and exceeded
the runtime smoke deadline on another reopen. That draft remains incomplete.
The macOS smoke helper now waits for each actual process exit and captures a
bounded thread dump during a delayed phase while preserving the same database
and normal mixed-mode settings. Earlier passing macOS builds tested only a
fresh database, so they do not establish that reopening worked.

The diagnostic run `33948586405` at `d5777a5` confirmed the cause: the doctor
exited with no remaining lock, then the second JVM stalled in
`InetAddress.getLocalHost` through H2 `NetUtils.getLocalAddress` and
`Database.startServer`. Its timed thread dump captured that stack before the
90-second gate failed. Bootstrap now defaults H2's listener to `127.0.0.1`
before H2 initializes, preserving explicit JVM overrides and `AUTO_SERVER`.
The process smoke test also keeps two JVMs alive together: one imports data,
the other reads its records and durable receipt, and the first reads again
after the peer exits. Native validation of the fix follows in a fresh draft.
