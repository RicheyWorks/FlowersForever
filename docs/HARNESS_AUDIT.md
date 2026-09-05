# Human-interaction harness audit

Audit date: 2026-09-04

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

The desktop plugin publishes 21 commands. Its editable-option path now enters
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
