# FlowersForever System Architecture

FlowersForever is one Spring Boot application with three inbound adapters: a
Swing desktop, a REST API, and an interactive CLI. They share application
services and domain models. Persistence and external farm-system integrations
sit behind explicit boundaries.

## Dependency map

```text
Swing GUI ─────┐
REST controllers ──> application services ──> repository ports ──> JPA adapters
CLI ───────────┘             │                                      │
                             │                                 H2 / SQLite
Connector registry ──────────┘
       │
       └── connector implementations ──> local mirrors / vendor HTTP APIs
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
| `connector` | Vendor/local file translation and sync orchestration | JPA access |
| `config` / `auth` | Bean wiring and access policy | Farm workflows |

## Primary data flows

### Farm mutation

1. Swing, REST, or CLI calls an application service.
2. The service validates the complete operation and opens the transaction.
3. A repository port loads and, for contested writes, locks the affected row.
4. The service applies domain changes and inventory side effects atomically.
5. REST errors flow through `ApiExceptionHandler`; Swing and CLI present the
   same service exception in their native interaction model.

Harvest logging increments stock. Harvest edit/delete corrects or reverses that
contribution. Order fulfillment locks the order, validates every line, and then
deducts stock. Repeating fulfillment after `FULFILLED` is idempotent.

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

## Runtime profiles

| Profile | UI/server | Persistence | Security |
|---|---|---|---|
| default | Swing + loopback REST | file H2 | local open mode |
| `cli` | interactive CLI, no Tomcat | inherits configured store | local process |
| `sqlite` | inherits adapter | developer-preview SQLite, pool size 1 | inherits security |
| `auth` | Swing + externally bound REST | inherits store | credentials required |
| `demo` | inherits adapter | non-destructive seed | inherits security |
| `container` | headless external REST | mounted file H2 by default | mandatory credentials |

Profiles compose where their responsibilities do not overlap, such as
`auth,sqlite` or `demo,auth`. Profile property resolution and container startup
are covered by tests.

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
