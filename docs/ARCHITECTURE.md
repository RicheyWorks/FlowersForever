# FlowersForever System Architecture

FlowersForever is one Spring Boot application with five inbound adapters: a
Swing desktop, a responsive worker web app, a REST API, an interactive CLI,
and an MCP stdio server. They
share application services and domain models. Persistence and external
farm-system integrations sit behind explicit boundaries.

## Dependency map

```text
Swing GUI ─────┐
Worker web ────┤
REST controllers ──> application services ──> repository ports ──> JPA adapters
CLI ───────────┘             │                                      │
                             │                                 H2 / SQLite
Connector registry ──────────┘
       │
       └── connector implementations ──> local mirrors / vendor HTTP APIs

AI / MCP / script ──> MCP stdio or REST ──> harness gateway ──> harness plugins
                                                   │
                                      token, risk, replay policy
```

Dependencies point inward. Models know no adapters. Services can depend on
models, shared utilities, and repository ports, but not Spring Data, Swing,
REST, CLI, or connectors. `ArchitectureBoundaryTest` enforces these package
rules from source imports so a cycle fails the build.

| Boundary | Responsibility | Must not own |
|---|---|---|
| `model` | Persisted farm entities and invariants | UI, transport, vendor APIs |
| workflow services | Use cases, validation, transactions, report models | HTTP responses, JPA queries, document layout |
| report collaborators | PDF/CSV/text rendering and file output | Repositories, transactions, farm mutations |
| repository ports | Persistence operations required by use cases | Spring Data pagination/types |
| JPA adapters | Queries, locking, Spring Data translation | Business workflows |
| `controller` | HTTP mapping, request conversion, status/download policy | Business rules |
| `gui` / `cli` | Interaction and presentation | Persistence access |
| `reference` | Immutable, sourced plant and event-service catalogs | Farm mutations, persistence, UI state |
| `connector` | Vendor/local file translation and sync orchestration | JPA access |
| `automation` | Provider-neutral plugin SPI, discovery, policy, and replay-safe commands | Model SDKs, Swing details, farm persistence |
| `config` / `auth` | Bean wiring and access policy | Farm workflows |

## Primary data flows

### Farm mutation

1. Swing, worker web, REST, or CLI calls an application service.
2. The service validates the complete operation and opens the transaction.
3. A repository port loads and, for contested writes, locks the affected row.
4. The service applies domain changes and inventory side effects atomically.
5. REST errors flow through `ApiExceptionHandler`; Swing and CLI present the
   same service exception in their native interaction model.

Harvest logging increments stock. Harvest edit/delete corrects or reverses that
contribution. Order fulfillment locks the order, validates every line, and then
deducts stock. Repeating fulfillment after `FULFILLED` is idempotent.

Event services deliberately do not extend the inventory entity. A completed
order line always represents a physical, inventory-tracked product; wedding or
memorial catering, photography, décor labor, coordination, and other vendor
deliverables live in the order's event brief. `EventServicesTab` can prepare
that brief through `TabHost`, and `CrmTab` accepts it only when it will not
overwrite another unfinished draft. This preserves the order/inventory
invariant while still keeping operational scope beside the customer and date.

Plant-reference handoff follows the same boundary discipline. The immutable
`PnwPlantProfile` catalog can prepare an unsaved `SeasonPlanEntry` form through
`TabHost`, but it does not persist or silently infer a cultivar, bed, schedule,
or yield. Provenance is copied into farm-owned notes for review. Season Planner
then derives a read-only weekly availability outlook from validated harvest
windows; that projection never mutates inventory or reserves customer stock.

Production Studio extends that reviewed-draft pattern with persistent,
farm-owned assumptions. `FarmSeasonSettings` supplies annual frost anchors and
`GrowingTemplate` supplies spacing, maturity, succession, yield, and task
offsets. `ProductionStudioService` can draft without mutation or atomically
commit ordinary `SeasonPlanEntry` records plus plan-linked `ProductionTask`
chains. Every draft also has a deterministic fingerprint covering its inputs,
dates, yield math, and frost warning. The desktop passes that reviewed token at
commit; REST exposes it as an `ETag` and accepts it in `If-Match` or the request
body. A mismatch is a conflict with no partial write. Regeneration deletes only
open generated tasks and preserves completed, skipped, and custom work.

Production mutations lock their owning frost, template, task, or season-plan row
before changing dependent records. Exact planting blocks are rejected before any
second plan is written, task regeneration verifies the original template link,
and terminal task outcomes must be reopened before changing from done to skipped
or the reverse. The V7 integrity migration backs those service checks with task
state constraints, foreign keys that preserve history with `ON DELETE SET NULL`,
and unique nonblank provider references.
Flyway V13 extends each planting-linked `ProductionTask` with bounded actual labor
minutes, input cost, field observation, and completion time. One locked service
operation writes those actuals and completes the task. Reopening clears the active
completion timestamp but retains the farm evidence; both service regeneration and
the bulk-delete query exclude open tasks that still contain actuals.
Handoff lifecycle validation is type-aware at the shared service boundary:
`ACCEPTED` is a contract outcome, `PAID` is a payment outcome, and marketplace
records stop at sent, failed, or cancelled. The desktop derives its status choices
from the same workflow contract rather than presenting impossible combinations.

`FloralRecipe` and its ingredients are reusable design specifications, not
inventory substitutes. Estimates reconcile the fulfillment week's planned
harvest with current `Item` quantities, allocate farm supply first, round the
remaining shortage to wholesale packs, and keep material, labor, overhead,
profit, margin, and markup visible. `IntegrationHandoff` stores only an outbound
provider reference, status, related record identifiers, amount, currency, and a
bounded operational summary. Card/bank data, tokens, signatures, and legal
contract bodies remain in customer-selected providers.

### Connector synchronization

1. The GUI, CLI, or REST selects an `ExternalConnector` by registry name.
2. The connector translates between inventory models and either an atomic local
   JSON mirror or a remote HTTP API.
3. `ConnectorRegistry` applies imported inventory through `InventoryService`.
4. The registry reduces connector-specific results to neutral audit fields and
   records them through `SyncHistoryService`.

This direction deliberately permits connectors to orchestrate services while
preventing services from importing connector result types. Connector names are
case-normalized, and blank or duplicate registrations fail at startup.

### Reports and downloads

Workflow services assemble immutable report models under read-only transactions.
`HarvestReportGenerator`, `InventoryReportGenerator`, and
`OrderReportGenerator` render those models without repository access. Dedicated
harvest and inventory CSV exporters own filesystem serialization. Existing
service methods remain compatibility façades for Swing, REST, and CLI.

### Phone and tablet field adapter

The static `/worker/` shell calls the narrow `/api/worker` controller rather
than reproducing farm rules in JavaScript. `WorkerWorkflowService` reads due
production work in the configured farm time zone, exposes a bounded stock-count
projection, reads a database-bounded Confirmed-order queue due through the work
date, and delegates harvest, task, count, and handoff mutations to `HarvestService`,
`ProductionStudioService`, `InventoryService`, and `OrderService`.

Before sending a mutation, the browser stores its payload and stable operation
key in a same-browser queue. Flyway V15 adds `worker_action_receipts`; the domain
mutation and its checksummed response are committed in one transaction. An
exact key/payload retry returns that response, including after restart, while a
changed action or payload conflicts. The raw key is not persisted. Successful
records therefore enter the same canonical store read by desktop, REST, and the
AI `farm-data` plugin without a second synchronization path.

Flyway V16 adds physical counts to the durable receipt action set. The inventory
row is locked and its current quantity must equal the quantity reviewed on the
device before replacement. A stale review returns a typed conflict and current
quantity without writing a receipt or changing stock; the browser retains the
entered count for an explicit review/retry.

Flyway V17 adds `COMPLETE_ORDER` to the durable worker receipt set. The handoff
projection contains customer contact and delivery context plus the complete
operational product load, but omits pricing. An opaque token fingerprints exactly
those displayed fields. `OrderService` locks the live order and customer, checks
the token and lifecycle state, fulfills the order, deducts stock, and joins the
worker receipt transaction. A changed load returns a typed conflict without a
receipt or inventory mutation. If a different worker already fulfilled the same
unchanged load, the operation records a reconciled result without deducting stock
again. All queued device entries are individually visible and discardable.

Flyway V18 adds immutable production-task attachment metadata and the
`ADD_ATTACHMENT` receipt action. Image bytes are detected from their signatures,
bounded to 15 MiB, addressed by SHA-256 beneath the configured data directory,
and revalidated before HTTP or AI reads. The browser retains pending Blobs in
IndexedDB and queues attachment metadata in the existing operation queue; photo
upload precedes task completion, so restart replay preserves causal order.
Generated-task regeneration excludes evidence owners and does not create a
duplicate task type. Attachment metadata is the tenth database-paged/exported
farm record type, while dedicated AI actions upload content and read verified
chunks no larger than 256 KiB.

Flyway V19 turns harvest traceability into canonical data. Every harvest has a
unique normalized lot code plus grade, loss quantity, and storage location;
existing rows receive deterministic lot codes during migration. `HarvestService`
keeps inventory tied only to marketable quantity, enforces lot uniqueness, and
feeds the same fields to Swing, REST, the worker adapter, CSV/report output, and
`farm-data`. `HarvestReportGenerator` creates a 4×6 PDF label with an actual QR
payload containing the versioned lot identifier, while exact REST and MCP lot
lookups resolve the canonical record without scanning an unbounded list.

Flyway V20 adds the season-owned loaded field-labor rate used by production-cost
reporting. `ProductionStudioService` joins task actuals by exact planting-plan ID
and reuses `SeasonPlanService`'s single-best-match harvest allocation, so
overlapping crop windows cannot double-count one harvest. Planting rows and
season totals expose labor minutes/cost, inputs, cost per expected stem, cost per
matched harvested stem, actual-entry coverage, calendar-year unlinked tasks, and
unmatched harvests. The same report is returned by Swing, REST, and the
`farm-data__get-production-costs` MCP tool. Order revenue is deliberately absent:
current order lines identify products, not the planting block that supplied them.

Flyway V21 adds `ADD_FIELD_NOTE` to worker receipts. The mobile note creates an
ordinary Knowledge Log entry and its exact-retry receipt in the same database
transaction. A bounded recent-note query reads that shared record back. Device
capture persists an operation before dispatch, refuses to submit when its queue
cannot be saved, and treats lost or unreadable success responses as uncertain
outcomes requiring an exact-key retry. Notes and harvest drafts survive capture
panel changes and reloads within the same browser profile.

### Desktop interaction adapters

Swing tabs retain layout and dialog policy while `CrmPresenter` and
`HarvestLogPresenter` own input validation, row projection, summaries, and
service orchestration. `FarmTabCatalog` composes the stable feature list;
`FarmTabNavigator` owns refresh, selection, mnemonics, and shortcuts. Connector
result mapping lives in `ConnectorActionPresenter` rather than the frame.

The CLI uses `CliCommandRouter` for its stable 22-command catalog and dispatches
printable workflows through `CliReportCommands`. Both desktop adapters still
enter the same application-service transaction boundaries as REST.

The Farm Knowledge Log is another shared workflow: Swing and `/api/knowledge`
call `KnowledgeService`, which owns validation, grounded plain-language search,
record-link checks, and mutation auditing. The first search layer tokenizes
ordinary farm language, removes conversational filler, recognizes month names,
and matches only farm-entered facts; it does not generate recommendations.

Controllers use `DownloadResponse` for consistent media type, content length,
attachment headers, and filename validation. REST filesystem exports resolve
beneath the configured export root through `ExportPathResolver`.

### AI and automation adapters

`HarnessGateway` is a provider-neutral application boundary. It authenticates
an independent harness token, discovers Spring-owned `HarnessPlugin` beans,
enforces the risk declared in each plugin's action schema, validates required
arguments, and prevents duplicate execution through bounded request-ID replay.
REST is a replaceable adapter over that gateway. The bundled MCP stdio adapter
compiles the same manifest to MCP tools and routes calls back through the
gateway without entering Swing directly. Provider SDKs can still reuse the REST
or embedded boundary.

`McpProtocolHandler` supports the stateless MCP `2026-07-28` request metadata,
`server/discover`, cacheable tool lists, structured results, and protocol errors,
plus the legacy `2025-11-25` initialization flow. `McpStdioServer` applies the
standard one-JSON-message-per-line framing, keeps stdout protocol-only, and exits
on stdin EOF. The `mcp` profile removes Swing, Tomcat, and the REST harness;
authentication stays process-local, and only currently enabled tools are listed.

Native packages add a second `FlowersForeverAI` launcher rather than asking an
AI client to drive the desktop executable. Its default `--mcp` argument, headless
JVM, packaged data-directory property, and Windows console subsystem preserve
redirected stdin/stdout and point at the desktop store. `--mcp-doctor` replaces
the blocking stdio runner with a one-shot inspection of that same application
graph. It reports the resolved store, database product, Flyway version, installed
plugins, enabled tools, and readiness for mapping, reconciliation, and writes;
missing configuration exits nonzero without exposing the process token.

The authenticated manifest compiles every action into a common JSON-Schema tool
definition and pairs it with the effective operator policy. Stable
`plugin-id__action-name` identifiers let provider adapters generate their tool
catalog without hard-coded Swing or FlowersForever knowledge. Existing list
discovery remains a smaller backward-compatible endpoint.

`FarmDataHarnessPlugin` is the domain-level AI integration. It publishes nested
canonical schemas and uses `InventoryService`, `CustomerService`,
`HarvestService`, `OrderService`, `KnowledgeService`, and
`ProductionStudioService` directly, so an AI can migrate records from a sheet or
previous system and continue operating the farm without driving Swing. Master
data, growing templates, planting plans, and nested floral recipes use
natural-key upserts; append-only records use transactional batches.
Historical harvest and order imports intentionally preserve old activity
without changing current inventory, while live harvest and order actions retain
the normal stock effects. `FarmDataPageService` executes bounded, deterministically
ordered database queries for all ten readable record types, preserving arbitrary offsets
and returning totals and continuation metadata without materializing full tables.
Flyway V14 adds durable `FarmDataImportReceipt` mappings keyed by source system,
canonical record type, and source row key. Exact source-row retries survive
gateway cache eviction and application restart. Changed inventory, customer,
template, plan, and recipe rows update their original target ID; changed
append-history rows fail with a conflict. Database-backed receipt pages let an
adapter resume and reconcile a large import without treating a request ID as a
permanent migration ledger.

`FarmDataMappingService` is the provider-neutral intake layer in front of those
canonical writes. It deterministically suggests canonical-field-to-source-path
mappings from spreadsheet/provider headers, resolves dotted object paths,
applies explicit defaults, converts common spreadsheet scalar/date/list forms,
and validates nested arrays against the catalog schema. The read-only preview
returns canonical draft rows plus row/field issues. The mapped-import action
requires durable source keys, validates the entire batch—including domain model
construction—before calling the established write action inside one transaction.
The service reads no files and owns no provider credentials; CSV, Excel, MCP, or
model adapters only transport source objects into this stable boundary.

Plugin descriptors also publish whether the target is an application, workflow,
device, or robot; declare observation, command, perception, sensing, actuation,
and emergency-stop capabilities; and may type stable snapshot signals with units
and sensitivity. Tool annotations expose read-only, destructive, idempotent,
open-world, physical-world, and freshness hints without trusting a model to
classify its own action. Physical effects have an independent default-denied
policy tier and require a command expiry within 30 seconds. The gateway validates
sensor types, removes sensitive signals when sensitive read is disabled, and
rejects plugin/snapshot identity mismatches. Device and robot snapshots are a
closed telemetry contract: undeclared metadata is rejected rather than passed
through without a sensitivity decision. Hardware plugins remain responsible
for local watchdogs, interlocks, collision limits, and emergency stops.

The optional `tesla-optimus` plugin is the first concrete robot boundary. It
accepts bounded high-level task drafts, returns an adapter-issued plan token,
and requires that exact token plus a fresh physical command before execution.
`OptimusRobotAdapter` isolates the future owner API from the provider-neutral
harness; the only current implementation is an explicitly enabled in-process
simulator. Enabling the plugin without an adapter fails startup. This prevents
an unconfigured hardware deployment from silently selecting simulated behavior
and lets an official/customer transport be added without changing AI tools,
gateway policy, or replay semantics.

Robot state has a five-second maximum age and two-second future-skew allowance.
The plugin publishes derived operational and staged-plan status, rejects
not-ready or stopped state, prevents replacement of adapter-confirmed staged
work, and caps plan review lifetime at five minutes. It separately reports
whether durable staged metadata is confirmed by the current adapter. Execution
durably claims the reviewed plan before the adapter call and removes the claim
only after its command receipt is journaled. Typed lifecycle,
monotonic progress, and status detail flow back through observation; targeted
graceful cancellation is separate from staged-plan cancellation and emergency
stop. This keeps a long robot job from masquerading as a synchronous API call.

Every adapter declares stable simulator/hardware mode, robot ID, model, software
version, and coordinate frame. Hardware initialization requires a configured
matching robot ID, and identity drift after startup fails closed. An ambiguous
transport failure cannot be blindly redelivered; the adapter must independently
make its plan token idempotent and reconcile its command ID with robot state.

Robot operation recovery is durable and append-only. Flyway V8 creates
`robot_operation_events`; accepted receipts and changed lifecycle observations
append events without updating or deleting prior ones. The read-only
`get-operation` tool reconciles one exact command ID with current adapter state;
`list-operations` returns one latest event for up to 25 recent operation IDs.
For reconnecting or long-running clients, `list-operation-events` pages through
the ordered append log in batches of 1–50 using an opaque string cursor and a
`hasMore` continuation flag. The process-local test journal mirrors the same
append, de-duplication, recency, and cursor behavior as the JPA implementation.
Fresh matching state is `LIVE`, terminal history remains queryable, and a
nonterminal operation absent after restart becomes `UNKNOWN`/`UNCONFIRMED`
instead of being guessed complete. Generic gateway replay remains a bounded
process-local duplicate-delivery guard. Optimus execution receipts additionally
bind request IDs to exact command fingerprints in this journal so completed
physical dispatches can replay after a process restart.

Robot control authority is separately durable and exclusive. Flyway V9 creates
`robot_control_leases`, keyed by adapter mode plus stable robot ID. At most one
15–300 second lease can be active for that identity; pessimistic row locking,
the primary key, and JPA versioning prevent silent controller replacement.
Capability tokens contain 256 random bits and only their SHA-256 digests are
stored. Renewal retains the lease ID so an already reviewed staged plan remains
owned, but rotates the capability token. Reacquisition after release or expiry
uses a new lease ID, so it cannot execute another controller's staged plan.
Staging and normal control require the lease; observation and durable operation
queries do not. Emergency stop deliberately remains lease-independent.

Flyway V10 adds `robot_staged_plans`, one restart-resume record per stable robot
identity. It stores the owning lease ID, plan timestamps, and only a SHA-256
digest of the adapter plan token. The task text and plaintext token stay with
the AI and adapter. A recreated plugin can therefore validate the tokens it is
given and continue the same staged task when the adapter still retains it.
Observation distinguishes that live adapter confirmation from the mere presence
of a durable record. If the adapter has restarted without the plan, execution
clears the stale record instead of dispatching and staging can replace it for
the active lease. Flyway V11 attaches the original request ID and execution
timestamp before adapter dispatch. A new lease may cancel an expired controller's leftover plan,
but its different lease ID cannot execute it.

Plugin 0.10 applies that reconciliation to every cleanup route. Explicit draft
cancellation, lease release, expiry, restaging, and return-to-pose skip the
adapter cancellation call when the current adapter is already idle. When the
adapter still reports `STAGED`, its cancellation succeeds before the durable
record is cleared, preserving a retryable record if the transport fails.

Plugin 0.11 upgrades staged confirmation from lifecycle-only to exact plan
identity. `RobotState` may carry a SHA-256 fingerprint of the adapter's opaque
plan token. The plugin compares it with the digest already stored by Flyway V10
without publishing either value to AI clients. Missing or mismatched
fingerprints cannot dispatch the stored plan, and authorized restaging
reconciles an adapter-only draft before creating the replacement.

Plugin 0.12 closes the acknowledgement gap at initial staging. After an adapter
returns a plan, the plugin requires a fresh observation of `STAGED` with that
exact fingerprint before it persists or returns the token. Idle, missing,
mismatched, stale, future-dated, and failed observations cancel the new adapter
draft and leave no durable staged record.

Plugin 0.13 makes adapter staging transactional across the remaining local
boundaries. Any failure after `stage(...)` begins—including an ambiguous adapter
exception, plan validation, lease revalidation, or durable-store write—attempts
adapter cancellation. Rollback errors are suppressed onto the primary failure,
preserving the actionable cause while leaving adapter-only work visible for the
existing reconciliation path.

Plugin 0.14 verifies the durable commit before releasing a plan token. The
store's write result and a separate read-back must both carry the exact robot,
mode, lease, and token digest, plus timestamps equivalent at database precision.
Missing, malformed, mismatched, or failed read-back triggers adapter rollback and
removal of the unusable record after adapter cancellation succeeds. This keeps
successful AI responses aligned with the state a recreated plugin can actually
resume.

Plugin 0.15 closes the ambiguous execution-handoff gap. An atomic, read-back
verified execution claim survives adapter timeouts, invalid receipts, journal
failures, and plugin restart. Only the same harness request may resume it. If the
adapter still reports the exact staged fingerprint, the same idempotent plan
token is retried; if the adapter reports a command with that originating
fingerprint, the operation is journaled and returned without redispatch. A
different request cannot take over, and unresolved claims remain visible through
dedicated observation signals instead of being erased.

Plugin 0.16 and Flyway V12 close the post-dispatch response-loss gap. The first
accepted execution receipt stores the original request ID and a SHA-256 digest
over the exact command, including both opaque capabilities and its deadline; no
plaintext token enters the journal. Before rejecting a stale deadline, a
recreated gateway asks the plugin for an exact durable match. It reconciles the
operation with the adapter when possible and returns the original command
without dispatching again. A request ID reused for another action or changed
argument is rejected.

Plugin 0.17 makes that durable response independent of a later owner-API state
outage. Adapter-call failures produce a degraded observation from local identity,
lease, staged-plan, and operation records: connection is `UNAVAILABLE`, readiness
is false, unknown sensors are omitted, and a nonterminal replay is journaled and
returned as `UNKNOWN`/`UNCONFIRMED`. The same outage still prevents every new
physical dispatch because command execution continues to require live adapter
state. An actual returned identity mismatch is not treated as an outage.

Plugin 0.18 separates durable response availability from the secondary
reconciliation append. During adapter unavailability, a last-known nonterminal
event is rendered as `UNKNOWN` immediately. If appending that transition fails,
the replay builds the same unconfirmed result from the already-read receipt and
reports `reconciliationPersisted=false`; it neither erases the accepted receipt
nor calls the adapter again. A later request can retry persistence and live
reconciliation. Terminal history remains authoritative.

Plugin 0.19 covers the live form of the same write outage. Exact current adapter
state can supply an ephemeral reconciled event when the secondary lifecycle
append fails, producing `LIVE` plus `reconciliationPersisted=false`. Fresh
observation also remains available if its journal enrichment fails. The initial
execution receipt is still a required durable write; only later status
enrichment is best-effort, so this path cannot manufacture an accepted command.

Plugin 0.20 applies the same distinction to read-only operation access.
`get-operation` and `list-operations` construct truthful live or unconfirmed
results when a secondary state append is unavailable and expose whether that
reconciliation persisted. The cursor event feed returns only journal-backed
events, but a failed optional live refresh no longer prevents reading its existing
page and is surfaced as `reconciliationPersisted=false`.

Plugin 0.21 also decouples those read tools from live owner-API availability.
Adapter transport failure falls back to the journal: terminal operations remain
terminal, nonterminal operations become `UNKNOWN`/`UNCONFIRMED`, and each result
reports `adapterAvailable=false`. The ordered event feed serves its durable page
without attempting or inventing a live refresh. Identity drift remains a hard
failure rather than being classified as an outage.

Plugin 0.22 routes both operation-journal implementations through one internal
lifecycle normalizer. Out-of-order samples are ignored, progress is nondecreasing,
backward nonterminal phases become `UNKNOWN`, and terminal events are immutable
against later adapter state. Receipt progress also distinguishes successful
completion from cancellation, failure, and stop so partial work is not rewritten
as 100 percent.

The built-in `SwingHarnessPlugin` delegates to an instance-owned bridge attached
to the live frame. Observations contain the current route, the complete
primary/nested route catalog, and visible control metadata but omit field and
password values. Terminal menu paths are observable, and array schemas declare
their item types. An exact `navigate-route` command validates a published path
and opens it through the production navigator and nested tab models. Commands
are otherwise typed, selector-based, and
marshalled onto the event-dispatch thread; Enter, double-click, menus, and
installed shortcuts dispatch through production Swing listeners. Generic
activation is conservatively destructive. A separate default-denied sensitive
read tier exposes bounded control values and scaled application-root PNGs;
passwords remain unreadable. Both the entry-count and output-byte replay bounds
are published in the manifest. The REST controller is absent by default,
requires explicit enablement plus a strong token, and is excluded from CLI and
release-smoke profiles. The container profile can expose only headless-capable
plugins and keeps its mandatory HTTP authentication in front of the harness.
See [AI_HARNESS.md](AI_HARNESS.md) for the extension contract.

The runtime route contract is checked against the composed `FarmTabCatalog`, not
a test-only list: all 34 current leaf routes must be published and reachable,
and every visible control on every route must have one globally unique,
supported selector. Adding a workflow without live-harness coverage fails that
contract test.

## Persistence, transactions, and concurrency

Services define application transaction boundaries; repository adapters own
Spring Data details. The ports are `InventoryRepository`, `HarvestRepository`,
`CustomerRepository`, `CustomerOrderRepository`, `KnowledgeRepository`, and
`SyncHistoryRepository`.
Their `Jpa*Repository` adapters delegate to the low-level Spring Data
interfaces.

| Workflow | Transaction policy | Concurrency policy |
|---|---|---|
| reads and reports | `readOnly = true` | bounded date/range inputs |
| inventory mutation | required | pessimistic item lock |
| harvest edit/delete | required | harvest and affected inventory locks |
| customer/order mutation | required | affected aggregate lock |
| knowledge mutation | required | locked lesson; linked record ids validated |
| one order fulfillment | `REQUIRES_NEW` | locked order; idempotent terminal state |
| market-day batch | orchestration plus one transaction per order | one failure does not undo prior successes |
| sync audit record/clear | required | repository-owned ordering/pagination |

H2 is the supported persistent desktop store. Flyway owns its versioned schema
and runs before Hibernate validates the entity mapping; Hibernate never mutates
the customer schema automatically. A non-empty pre-1.1 database is baselined at
V1 and then upgraded in place. SQLite remains a developer-preview compatibility
profile, uses a one-connection pool, and is not approved for customer data.
V3 adds the Knowledge Log and tag tables; optional links use `ON DELETE SET
NULL` so operational cleanup cannot erase a lesson.
`spring.jpa.open-in-view=false` keeps lazy database access out of presentation
adapters.

## Security and failure handling

Default desktop mode binds REST to loopback and grants the local Swing session
OWNER capability. The `auth` profile binds externally and enables stateless HTTP
Basic roles: OWNER, HAND, and VIEWER. The `container` profile is fail-closed: it
requires strong non-demo credentials and at least one OWNER. Health remains
public; farm data follows role policy.

Expected validation failures become stable 4xx envelopes. Persistence races
become `409 DATA_CONFLICT`. Unexpected failures receive a correlation ID;
credential-shaped values are redacted before logging and details are not
returned. Connector errors use the same redactor before reaching responses or sync history.
Local mirrors replace files atomically, and download/export filenames reject
control characters and traversal.

The optional automation gateway adds its own constant-time token check and
plugin-declared capability policy. With `auth`, normal REST roles and the
harness token both apply. Elevated harness policy never bypasses domain service
validation or transactional rules.

## Runtime profiles

| Profile | UI/server | Persistence | Security |
|---|---|---|---|
| default | Swing + loopback REST | file H2 | local open mode |
| `cli` | interactive CLI, no Tomcat | inherits configured store | local process |
| `sqlite` | inherits adapter | developer-preview SQLite, pool size 1 | inherits security |
| `auth` | Swing + externally bound REST | inherits store | credentials required |
| `demo` | inherits adapter | non-destructive seed | inherits security |
| `container` | headless external REST | mounted file H2 by default | mandatory credentials |
| `mcp` | local stdio MCP, no Swing or HTTP | inherits configured store | process token |
| `mcp,mcp-doctor` | one-shot MCP/store readiness report | same store as MCP | token diagnosed, never returned |

Profiles compose where their responsibilities do not overlap, such as
`auth,sqlite` or `demo,auth`. Profile property resolution and container startup
are covered by tests. The REST automation controller remains opt-in in every
server profile. When enabled in `container`, the headless `farm-data` plugin is
available and both normal container authentication and the harness token are
required; the Swing plugin is naturally absent. The `mcp` profile enables the
same gateway only inside the local stdio child process and requires its token.

`FarmDataExportService` turns the farm plugin's bounded database pages into an
immutable file snapshot beneath the configured export directory. One
`REQUIRES_NEW`, repeatable-read transaction spans snapshot creation. A
deterministic hash of the caller's stable export key locates the result without
persisting the key itself; a request fingerprint prevents the same key from
naming different selections. Creation writes into a validated staging
directory and publishes the completed manifest and JSON parts by same-volume
move. Reads recalculate the canonical manifest digest and validate each selected
part's path, byte count, SHA-256, and JSON record count before returning it.
This makes complete handoff finite and restart-stable while keeping every read
bounded; it does not turn the local embedded database into a shared store.

## Build and deployment

The Maven build targets Java 17, produces one executable JAR and runs unit,
integration, architecture, transaction, and profile tests. JaCoCo gates line and
branch coverage. The `quality` profile adds zero-baseline Medium+ SpotBugs and
project Checkstyle gates. CI also builds and smoke-tests the authenticated
container. Candidate release CI builds both native installers and launches a
temporary app image on clean Windows and macOS runners without publishing.
Stable version tags may publish the JAR, SBOM, container, and desktop installers
only after those same gates pass.

## Deliberate constraints and evolution triggers

1. **A shared production database requires a separate concurrency and operations design.**
   Versioned H2 migrations now protect one local customer store, but they do not
   turn the desktop database into a shared server. Do not add a shared or
   externally managed database profile until its migration dialect, deployment,
   backup, concurrency, and rollback paths have dedicated acceptance tests.
2. **Move report DTOs only when independently useful.** They remain nested in
   compatibility services so existing callers keep the same source-level API.

Desktop identity now lives in the Spring-owned `DesktopSession`; tabs receive
capabilities and display identity through `TabHost`. It remains intentionally
separate from Spring Security's REST authorization state.

All maintained connectors now use component scanning with constructor property
injection. This gives the registry one discovery and lifecycle policy while
keeping direct constructors available for focused connector tests.

Architecture decisions are recorded under [`docs/adr`](adr/). Backend detail is
in [BACKEND_ARCHITECTURE.md](BACKEND_ARCHITECTURE.md), and Swing composition is
in [GUI_ARCHITECTURE.md](GUI_ARCHITECTURE.md).
