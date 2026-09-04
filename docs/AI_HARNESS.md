# AI and automation harness

FlowersForever exposes a provider-neutral automation contract for AI agents,
test runners, accessibility tools, sensors, and future robots. It does not embed a
model SDK or give a model unrestricted access to Swing. A small, typed gateway
sits between every client and each application plugin:

```text
AI provider / MCP client / script / test runner
                 │
      bundled MCP stdio, REST, or another adapter
                 │
         HarnessGateway (token + policy + replay safety)
                 │
         HarnessRegistry (plugin discovery)
                 │
      HarnessPlugin implementations
                 │
      Swing, workflow, device, or robot target
```

The gateway and plugin interface are runtime components. The executable bundles
an MCP stdio server for AI clients, while REST remains available for ordinary
integration services. Neither transport changes the farm-data or desktop plugin.

## Safety defaults

The harness is **off by default**. Its REST controller does not exist unless it
is explicitly enabled, and it is excluded from `cli` and `release-smoke`
profiles. The ordinary desktop server binds to `127.0.0.1`. A headless
`container` deployment may expose the domain-level `farm-data` plugin when the
harness is explicitly enabled; container authentication and the independent
harness token both apply.

Every request needs a separate harness token containing at least 24 characters.
The token is required even when the regular local REST API is in open mode. If
the `auth` profile is active, Spring Security credentials and the harness token
are both required.

Actions carry risk declared by the plugin—not claimed by the caller:

| Risk | Default | Meaning |
|---|---:|---|
| `READ` | allowed | Discover plugins and observe non-value control metadata |
| `SENSITIVE_READ` | blocked | Read visible values or capture application pixels |
| `NAVIGATE` | allowed | Change route or selection without changing farm records |
| `DRAFT` | blocked | Enter temporary field or selector values |
| `MUTATE` | blocked | Change persistent operational data |
| `DESTRUCTIVE` | blocked | Trigger a generic or destructive UI action |
| `PHYSICAL` | blocked | Move or otherwise affect a declared physical-world target |

`DESTRUCTIVE` cannot be enabled unless `MUTATE` is also enabled. `PHYSICAL`
requires all three mutation, destructive, and physical switches. Physical
actions also require a caller-supplied expiry no more than 30 seconds in the
future, so a delayed queue cannot deliver a stale motion command. The Swing
plugin deliberately classifies generic button activation as destructive because
a selector could refer to Save, Delete, Fulfill, or another consequential
button. This is intentionally stricter than guessing from button text.

Snapshots include the visible route and control metadata—kind, selector,
enabled, focusable, and commandable—but never editable field values or password
contents. Visible labels may still contain customer or event information, so
protect the token and treat observations as private farm data.

## Connect an AI through the bundled MCP server

The native installer includes a dedicated `FlowersForeverAI` launcher. It uses
the packaged runtime, opens the same `%LOCALAPPDATA%\FlowersForever\data` store
as the desktop app, starts without Swing or an HTTP listener, and preserves
stdin/stdout for the AI client. Register that executable as a local stdio MCP
server; no separate Java install or `--mcp` argument is needed. Standard output
contains only newline-delimited MCP JSON-RPC, while process diagnostics go to
standard error.

The packaged AI launchers select `-Djdk.console=java.base`. This keeps newer
JDK console providers from initializing an interactive terminal on the AI
client's redirected pipes. The Windows native smoke exercises readiness and
an open-stdin request with the actual packaged setting.

```json
{
  "mcpServers": {
    "flowersforever": {
      "command": "C:\\Program Files\\FlowersForever\\FlowersForeverAI.exe",
      "env": {
        "FLOWERFARM_HARNESS_TOKEN": "replace-with-at-least-24-random-characters",
        "FLOWERFARM_HARNESS_ALLOW_SENSITIVE_READ": "true",
        "FLOWERFARM_HARNESS_ALLOW_MUTATIONS": "true"
      }
    }
  }
}
```

Use the AI client's equivalent local-MCP configuration when its field names
differ, and substitute the actual install path. The macOS launcher is
`/Applications/FlowersForever.app/Contents/MacOS/FlowersForeverAI`. For a
portable JAR, use `java -jar <absolute-jar-path> --mcp` and explicitly set
`FLOWERFARM_DATA_DIR` to the same farm data directory used by the desktop app;
otherwise a client-specific working directory could create an unrelated empty
store. The token is consumed inside the child process; it is never published in
`tools/list`, included in a tool schema, or passed through model-generated
arguments.

Before registering the launcher, run the same executable once with
`--mcp-doctor` in a terminal that has the three environment values above. It
opens the real application graph and emits exactly one JSON result on stdout.
`ready=true` confirms the resolved data directory, database connection, Flyway
schema, `farm-data` plugin, all 33 farm tools, read-back/resume capability, and
write capability. `ready=false` exits with code 2 and lists the exact missing
settings without printing the token. Confirm that `dataDirectory` names the
farm's intended store before connecting the AI.

```text
FlowersForeverAI.exe --mcp-doctor
```

`FLOWERFARM_HARNESS_ALLOW_SENSITIVE_READ=true` is required for canonical
read-back, import receipts, and complete exports. Mutation permission alone can
write records but cannot perform the reconciliation half of a safe migration.

The server supports the current stateless MCP `2026-07-28` protocol, including
`server/discover`, per-request metadata, cache metadata, structured tool output,
and `resultType`. It also supports the `2025-11-25` `initialize` flow for
existing clients. Both expose the same deterministic `plugin-id__action-name`
tools. Only actions enabled in the process environment appear in `tools/list`.
`READ` and navigation actions are available by default;
`FLOWERFARM_HARNESS_ALLOW_MUTATIONS=true` exposes canonical data imports and
ongoing farm-record operations. Read-back, receipts, and export tools also need
`FLOWERFARM_HARNESS_ALLOW_SENSITIVE_READ=true`. The farm-data workflow does not
need the draft, destructive, or physical switches.

MCP calls generate gateway request identities from the JSON-RPC request ID,
tool name, and arguments. Responses provide the complete `HarnessResponse` as
both `structuredContent` and serialized text for client compatibility. Tool
validation and farm-domain errors return `isError=true` with actionable text;
malformed MCP calls and unknown tools use JSON-RPC errors. A physical tool, if
one is separately installed and enabled, adds `_expiresAt` to its MCP input
schema and translates it to the harness command deadline.

The MCP server transports farm tool calls; it does not log into Microsoft 365,
Google, or a previous vendor account. The farm's chosen AI/connectors read those
sources and send their rows through `suggest-field-mapping`,
`preview-mapped-records`, and `import-mapped-records`. This separation keeps
provider credentials outside FlowersForever while preserving one stable import
and operations contract.

## Enable the local REST transport

Use an environment-specific secret rather than committing it to a property
file. This PowerShell example enables observation and navigation only:

```powershell
$env:FLOWERFARM_HARNESS_ENABLED = 'true'
$env:FLOWERFARM_HARNESS_TOKEN = 'replace-with-at-least-24-random-characters'
mvn spring-boot:run
```

Optional policy switches are:

```text
FLOWERFARM_HARNESS_ALLOW_NAVIGATION=true
FLOWERFARM_HARNESS_ALLOW_SENSITIVE_READ=false
FLOWERFARM_HARNESS_ALLOW_DRAFTS=false
FLOWERFARM_HARNESS_ALLOW_MUTATIONS=false
FLOWERFARM_HARNESS_ALLOW_DESTRUCTIVE=false
FLOWERFARM_HARNESS_ALLOW_PHYSICAL_ACTIONS=false
```

Enable only the capability needed for the current supervised session, then
remove the environment variables and restart the application when finished.
Do not expose the port to the public internet or place the harness token in a
URL, prompt, source file, screenshot, or chat transcript.

## Fetch the tool manifest

An adapter can bootstrap itself from one authenticated document:

```powershell
$headers = @{ 'X-FlowersForever-Harness-Token' = $env:FLOWERFARM_HARNESS_TOKEN }
$manifest = Invoke-RestMethod -Headers $headers `
  -Uri 'http://127.0.0.1:8080/api/harness/manifest'
$manifest.tools | Select-Object name, risk, allowed
```

The manifest includes protocol version `1.0`, the 256-command replay limit and
8 MiB eviction target, strict-argument behavior, the effective operator policy for
every risk, the 30-second physical-command TTL ceiling, the plug-in descriptors,
and one common JSON Schema per action. Descriptors identify an `APPLICATION`,
`WORKFLOW`, `DEVICE`, or `ROBOT`, list machine-readable capabilities, and may
declare typed observation signals with units and sensitivity. Tool definitions
include action effects plus common AI/MCP-style read-only, destructive,
idempotent, and open-world hints, along with explicit physical-world and
freshness flags. Tool
names use `plugin-id__action-name`, contain only provider-safe letters, numbers,
hyphens, and underscores, and stay within 64 characters. An OpenAI, MCP, or
local-model adapter can use each tool's `description` and `inputSchema`
directly, then route a call with its published `pluginId` and `action`.
Array arguments publish an `items.type` as well as their outer `array` type, so
providers can distinguish integer row collections from string menu paths.
Record arrays also publish their nested object properties, required fields,
types, and enums, allowing an AI to map spreadsheet columns without a
FlowersForever-specific prompt. The gateway validates these nested schemas
before dispatch, including required fields and unknown properties. Canonical
commands reject fractional integers and implicit scalar coercion; spreadsheet
conversion belongs in the mapped preview/import workflow.

`allowed=false` is an instruction to omit or disable that tool for the current
session; it is not merely advisory. The gateway independently enforces the
same policy if a client submits the command anyway.

## Discover and observe

The discovery endpoint returns installed plugins and their typed actions:

```powershell
Invoke-RestMethod -Headers $headers -Uri 'http://127.0.0.1:8080/api/harness'
```

The built-in plugin is `swing-desktop`. Observe its current route and visible
controls with:

```powershell
Invoke-RestMethod -Headers $headers `
  -Uri 'http://127.0.0.1:8080/api/harness/swing-desktop/observe'
```

When the desktop has not attached yet, the snapshot returns `ready=false`
instead of inventing a UI state.
When attached, snapshot metadata publishes `routes` as every reachable primary
and nested tab path, plus `routeCount` and terminal `menuPaths`. Route and menu
paths contain exact visible labels, never editable field values. The current
`route` and visible controls update after every command.

## Send typed commands

Each command requires a caller-generated `requestId`. Retrying the exact same
command ID returns the cached response with `replayed=true` and does not operate
the UI twice. Reusing that ID with different command contents is rejected. The
current-process replay cache retains at most 256 completed commands. It evicts
older entries when estimated argument, output, and observation data exceeds
8 MiB. The newest completed receipt is retained even when it alone exceeds that
target, preserving retry protection for a large attachment upload. This is an
eviction target, not a strict heap-memory limit. Durable job orchestration should
keep its own audit and retry state. Nested command arguments are copied into
immutable JSON values so later caller edits cannot change a receipt's identity.
A completed effect is retained even if the following observation fails; an exact
retry attempts observation again without repeating the effect. Revoked sensitive
read permission also applies to cached observations and sensitive-read responses.

Non-physical commands may omit `expiresAt`. A provider adapter submitting a
tool whose `annotations.freshnessRequired` is true must add an ISO-8601
`expiresAt` timestamp within the manifest's `physicalCommandMaxTtlSeconds`.
The expiry belongs to the command envelope, not the model-generated action
arguments. Exact retries remain safe after expiry because the gateway returns
the cached result rather than operating the target again.

```powershell
$body = @{
  requestId = [guid]::NewGuid().ToString()
  action = 'navigate-route'
  arguments = @{ path = @('Reference & Planning', 'Event services') }
} | ConvertTo-Json -Depth 4

Invoke-RestMethod -Method Post -Headers $headers -ContentType 'application/json' `
  -Uri 'http://127.0.0.1:8080/api/harness/swing-desktop/commands' `
  -Body $body
```

## Migrate and operate farm records directly

The built-in `farm-data` workflow plugin is the stable domain bridge for an AI.
It calls the same transactional application services as the desktop and REST
controllers; it does not need a visible Swing window or imitate taps and
keystrokes. This makes it suitable for a farm AI serving workers on phones,
tablets, or computers, including a headless container deployment.

The AI's Excel, Google Sheets, CSV, or previous-app reader remains upstream of
FlowersForever. Column interpretation and canonical validation are now built
into the farm-data plugin. The normal migration flow is:

1. Fetch `/api/harness/manifest`, then call `get-data-catalog`.
2. Read source rows through the AI's existing connector and pass their column
   names to `suggest-field-mapping`.
3. Call `preview-mapped-records` with sample or complete rows, adjust any
   ambiguous mappings/defaults, and resolve its row-specific issues. This step
   does not change farm data.
4. Call `import-mapped-records` with the same mapping, a stable `source-system`,
   and either a mapped source ID or `source-key-fields`. The import is atomic.
5. Import customers and current inventory first.
6. Import old harvests and orders with the history behavior, then import farm
   knowledge. History actions preserve the record without changing today's
   inventory.
7. Import growing templates, explicit planting blocks, and floral recipes so
   future work, expected supply, and design requirements move with the records.
8. Use `list-import-receipts` to resume or audit the source-to-farm mappings,
   then use `list-records` to page the saved records back and reconcile counts.
9. Call `create-record-export` with a stable export key when the AI needs a
   complete, restart-safe farm snapshot; recover it by key and read each
   checksummed part until `hasMore` is false.
10. For ongoing work, use the operational actions so live harvests, orders, task
    actuals, and production-task photos follow FlowersForever's normal rules.

| Action | Purpose | Data effect |
|---|---|---|
| `get-data-catalog` | Discover record types, schemas, and write behavior | none |
| `suggest-field-mapping` | Match unfamiliar source columns to canonical fields | none |
| `preview-mapped-records` | Coerce and validate source rows with row-level issues | none |
| `import-mapped-records` | Atomically map and import a validated batch | canonical records and receipts |
| `list-records` | Read a bounded page for AI context, export, or verification | none |
| `read-attachment-content` | Read one verified bounded photo chunk | none |
| `lookup-harvest-lot` | Read one harvest by exact printed/scanned lot code | none |
| `get-production-costs` | Join task actuals and matched harvests for one season; optionally override labor rate | none |
| `preview-plantings` | Review frost-aware succession drafts and receive a fingerprint | none |
| `commit-plantings` | Save exactly the reviewed planting schedule and its work | planting blocks and tasks |
| `regenerate-tasks` | Replace open generated work while retaining actuals and completed work | generated tasks |
| `update-task-status` | Mark work TODO, DONE, or SKIPPED | production task status |
| `estimate-recipe` | Calculate flower needs, pack rounding, labor, and margin | none |
| `get-availability` | Page planned weekly stems and live stock | none |
| `list-handoffs` | Page local provider briefs | none |
| `create-handoff` | Record a contract, payment, or marketplace handoff locally | local handoff record; no external action |
| `list-import-receipts` | Page durable source-row mappings for resume and audit | none |
| `create-record-export` | Create or replay an immutable, complete farm-data snapshot | immutable export files |
| `get-record-export` | Recover a completed snapshot using its stable caller key | none |
| `read-record-export` | Verify and read one bounded snapshot part | none |
| `upload-attachment` | Attach a retry-safe base64 photo to a production task | task evidence and worker receipt |
| `upsert-inventory` | Insert or update stock by exact case-insensitive name | current inventory |
| `upsert-season-settings` | Save frost anchors and loaded field-labor rate by season year | season settings |
| `upsert-customers` | Insert or update a customer by exact case-insensitive name | customer master data |
| `upsert-growing-templates` | Insert or update reusable crop assumptions by template name | production templates |
| `upsert-planting-plans` | Insert or update dated crop and bed allocations | season plans |
| `upsert-floral-recipes` | Insert or update designs and nested ingredient requirements | floral recipes |
| `import-harvest-history` | Append a historical harvest | no current-stock adjustment |
| `log-harvests` | Record current harvest work | increments current inventory |
| `import-order-history` | Append an order in any final or active state | no current-stock adjustment |
| `create-orders` | Create a new draft or confirmed order | normal order workflow |
| `record-knowledge` | Record farm observations and lessons | knowledge log |
| `complete-tasks` | Save labor, input cost, and field results | production task actuals |

Batch writes accept at most 500 records per request. Record reads and receipt queries return
at most 200 records per page and include paging metadata. Unknown fields,
invalid enums, malformed dates, missing required fields, and invalid domain
values fail the request instead of being silently discarded. A batch and its
source receipts run in one transaction.

Harvest records expose `lotCode`, `grade`, `lossQuantity`, and
`storageLocation` in addition to the marketable `quantity`. A blank lot code is
assigned uniquely by FlowersForever. Loss/culls remain in production history
and gross-yield reporting but never increase sellable inventory. Use
`lookup-harvest-lot` after reading a label; its result includes the canonical
harvest and `/api/harvest/{id}/label.pdf` path for reprinting the QR label.

Use `upsert-season-settings` to save a season's two frost anchors, source, and
loaded field-labor rate through MCP; repeated writes replace the same season
instead of creating duplicates. Call `get-production-costs` with `season-year`.
By default it uses the loaded
hourly labor rate saved with that season's frost settings. Pass
`labor-rate-per-hour` for a non-persisted scenario calculation. The report keeps
missing task actuals, unlinked tasks, and unmatched harvests explicit. It does
not include revenue because product-level order lines do not currently prove
which planting block supplied a sale.

### Headless production workflow

Farm-data version 1.9 carries the Production Studio workflow through MCP and
REST using the same application services as the desktop:

1. Save season settings and a growing template with the existing upsert tools.
2. Call `preview-plantings` with `template-id`, `season-year`, `bed-or-field`,
   `bed-feet`, and `successions`. Review the returned `drafts`, including frost
   warnings. This call is a sensitive read and saves nothing.
3. Call `commit-plantings` with the same arguments plus `preview-fingerprint`
   copied from `output.previewFingerprint`. Changed dates or yield assumptions
   invalidate the preview. A duplicate planting is rejected. Retry the exact
   command with its original request ID while the gateway retains its receipt.
4. Use `update-task-status` with `task-id` and `status` to reopen, skip, or finish
   work. Use `complete-tasks` to record labor, input cost, and observations.
   `regenerate-tasks` takes `plan-id` and `template-id`; finished work, actuals,
   and attached evidence remain protected by the production service.
5. Call `estimate-recipe` with `recipe-id`, `count`, and `event-date`, or
   `get-availability` with `week-of`. Estimates do not reserve or deduct stock.
   Availability keeps planned stems and live inventory separate.
6. Use `create-handoff` to record a reviewed local brief. Required arguments are
   `handoff-type` (CONTRACT, PAYMENT, MARKETPLACE), `provider`, and `status`.
   Optional fields link an order/recipe and retain amount, currency, summary,
   or an external reference. Status values are DRAFT, READY, SENT, FAILED, and
   CANCELLED; CONTRACT also accepts ACCEPTED and PAYMENT accepts PAID. A sent or
   completed status requires an external reference. This action does not send
   messages, publish listings, sign contracts, or process a payment.

`get-availability` and `list-handoffs` accept `offset` and `limit` (1–200,
default 100), returning `total`, `hasMore`, and `nextOffset`. Their current
service calculations materialize the underlying lists before returning a page;
they are not database-streamed exports. Read tools require the sensitive-read
policy. The four modifying production tools require the mutation policy.

These eight tools use the ordinary process-local gateway replay cache. They do
not add durable cross-restart request receipts. After a restart or evicted
receipt, read current plans/tasks/handoffs before issuing a new mutation. A
duplicate planting conflict prevents a second schedule from being saved, but
it is not a replay of the original response. Give fresh reads new request IDs.

The mapping object runs from canonical field name to source column or dotted
provider path—for example `{"name":"Product Name","quantity":"Stock.OnHand"}`.
Common farm, inventory, customer, planting, and production aliases are suggested
deterministically with confidence and a reason; ambiguous fields remain
unmapped. `defaults` fills blank or absent optional values. Spreadsheet money,
percent, integer, yes/no, common date, and delimited string-list cells are
coerced into the canonical type. Nested order lines and recipe ingredients may
be supplied as provider arrays or JSON array cells. Unknown destinations,
unknown source paths, malformed nested JSON, invalid domain values, and missing
required fields appear as row-specific preview issues. Calendar dates are
validated strictly (for example, February 30 is rejected). Exact source path
names take precedence; a normalized name matching several source fields must
be disambiguated by supplying the exact spelling. Structured objects and arrays
cannot silently become names or source IDs.

`source-key-fields` can name one source ID or several columns that together
identify a row. Explicit key fields override inferred or mapped source IDs.
The plugin writes the resulting stable key into `sourceKey`;
long composite keys are represented by a deterministic SHA-256 key. A mapped
import requires a source key on every row, preventing a resumable migration
from silently falling back to process-local request replay. Preview rejects
duplicate keys within a batch and keys beyond the receipt store's length limit.

For example, an adapter can write normalized inventory rows without knowing the
Java model:

```powershell
$body = @{
  requestId = [guid]::NewGuid().ToString()
  action = 'upsert-inventory'
  arguments = @{ 'source-system' = 'legacy-excel'; records = @(
    @{ sourceKey = 'Inventory!row-184';
       name = 'Cafe au Lait Dahlia'; category = 'Cut flowers';
       quantity = 140; unit = 'stems'; price = 4.5; cost = 1.1 }
  ) }
} | ConvertTo-Json -Depth 6

Invoke-RestMethod -Method Post -Headers $headers -ContentType 'application/json' `
  -Uri 'http://127.0.0.1:8080/api/harness/farm-data/commands' `
  -Body $body
```

For restart-safe migration, every write command may include `source-system`;
when it does, every row must include a nonblank `sourceKey`. The V14 database
receipt binds that pair and canonical record type to the saved FlowersForever
record and a SHA-256 content fingerprint. An exact row retry can use a new
request ID after cache eviction or application restart and is returned in the
`replayed` count without applying the domain effect again. Changed inventory,
customer, growing-template, planting-plan, or floral-recipe rows update the same
target ID, which permits corrections and renames. Without a source key,
templates use exact case-insensitive names, plans use season/crop/variety/bed/sow
identity, and recipes use design type plus name as their natural keys. Harvests,
orders, knowledge entries, and completed-task actuals are treated as historical
events: reusing their source key with changed content or a different action
returns HTTP 409 instead of silently duplicating or rewriting history. A
receipt whose target was removed also returns 409 so the operator can reconcile
it deliberately.

`list-import-receipts` uses database-backed paging and can filter by
`source-system` and canonical `record-type`. Its response includes the target
record ID, action, fingerprint, and import/update timestamps. An adapter should
still checkpoint how far it has read the external file or API; the durable
receipt answers whether each encountered row reached FlowersForever. Omitting
both source fields remains supported, but then only the gateway's bounded,
process-local request-ID replay applies.

`list-records` also pages at the database for every canonical type; it does not
load a whole inventory, customer list, history table, planting program, or
recipe catalog before returning a slice. Supply `offset` and `limit` (maximum
200). The response returns the deterministic page as `records`, the database
`total`, `hasMore`, and `nextOffset`. When `hasMore` is true, pass `nextOffset`
as the next request's `offset`; when it is false, the traversal is complete.
Production-task pages additionally accept `from` and `to`, and those filters are
applied inside the database query. For example:

```json
{
  "record-type": "inventory",
  "offset": 0,
  "limit": 200
}
```

A non-final response includes continuation metadata alongside the real rows:

```json
{
  "recordType": "inventory",
  "offset": 0,
  "limit": 200,
  "nextOffset": 200,
  "total": 483,
  "hasMore": true,
  "records": ["... first 200 records ..."]
}
```

A client should use the returned `nextOffset` rather than inventing
`offset + limit`; this also handles a short final page correctly.

Production-task photo metadata is available as the `attachments` record type.
Metadata pages expose the target task ID, original filename, detected media
type, byte size, SHA-256, caption, and timestamps; storage paths are never part
of the contract. Use `read-attachment-content` with an attachment ID, byte
offset, and `max-bytes` no greater than 262,144. The result includes base64
bytes, `nextOffset`, and `hasMore`, and FlowersForever verifies the complete
file size and SHA-256 before releasing any chunk.

Use `upload-attachment` for AI-supplied task evidence. It requires a stable
`operation-key`, `target-type` of `PRODUCTION_TASK`, an existing task ID,
filename, media type, and base64 image bytes; caption and capture time are
optional. JPEG, PNG, WebP, HEIC, and HEIF are accepted up to 15 MiB after
signature detection. The same operation key and content recover the committed
result after restart, while changed reuse conflicts. This action intentionally
shares the worker receipt path instead of maintaining a second AI-only upload
ledger.

```json
{
  "operation-key": "north-bed-photo-2026-09-02-1",
  "target-type": "PRODUCTION_TASK",
  "target-id": 42,
  "filename": "north-bed.png",
  "media-type": "image/png",
  "caption": "Support checked after wind",
  "captured-at": "2026-09-02T15:30:00Z",
  "content-base64": "iVBORw0KGgo..."
}
```

For a complete handoff or long-running AI job, `create-record-export` builds an
immutable snapshot over those same database-backed pages. `record-types` is
optional and defaults to all ten readable/exportable types. `chunk-records` defaults to
100 and is bounded from 1 through 200; a one-megabyte byte ceiling may make a
part smaller. Optional `task-from` and `task-to` filters must be supplied
together and apply only when production tasks are included.

The export uses a dedicated repeatable-read transaction and releases each
serialized page from the JPA persistence context, so a complete farm snapshot
does not retain every loaded entity until the final page.

The caller supplies an `export-key` that is stable for one logical snapshot.
FlowersForever stores only a deterministic opaque export ID, not the raw key.
An exact create retry returns the original manifest with `replayed=true`, even
after restart or ordinary gateway replay eviction. Reusing the key with changed
types, chunk size, or task dates returns HTTP 409. `get-record-export` recovers
the manifest by key without reading the database again.

The manifest returns per-type counts, `totalRecords`, `partCount`, creation time,
and a snapshot SHA-256. Read parts from zero with `read-record-export`; each
response names its record type and offset, includes a part SHA-256, and returns
`hasMore` plus `nextPart`. The service verifies the manifest and selected file's
size, checksum, and JSON record count before returning data. Export files live
beneath the configured FlowersForever export directory, so normal data-directory
backup retains them. A zero-record snapshot has no parts and its manifest is the
complete result.

The stdio transport accepts one JSON document per line, with at most 32 Mi
characters per frame. It drains an oversized frame, reports the limit, and
continues with the next line. Its parser allowance includes a full 15 MiB
attachment encoded as base64; enabled REST harness parsing uses the same
attachment allowance. Existing larger REST parser limits are retained.

The bundled MCP transport exposes these actions directly to OpenAI, Anthropic,
local-model, and other MCP-capable clients. Provider-specific spreadsheet or
previous-app readers still supply source rows; they no longer need custom
column-mapping or farm-domain conversion code.

The Swing plugin publishes 21 commands through discovery. Every
argument also includes its JSON type, description, and required flag so an AI
tool can derive its own function/tool schema without hard-coded knowledge:

| Action | Required arguments | Risk |
|---|---|---|
| `navigate` | `tab` | `NAVIGATE` |
| `navigate-route` | `path` (string array) | `NAVIGATE` |
| `replace-text` | `selector`, `value` | `DRAFT` |
| `choose-option` | `selector`, `value` | `DRAFT` |
| `select-tab` | `selector`, `tab` | `NAVIGATE` |
| `select-row` | `table`, `row` | `NAVIGATE` |
| `select-rows` | `table`, `rows` | `NAVIGATE` |
| `sort-table` | `table`, `column`, `direction` | `NAVIGATE` |
| `select-list-row` | `selector`, `row` | `NAVIGATE` |
| `set-spinner` | `selector`, `value` | `MUTATE` |
| `set-slider` | `selector`, `value` | `MUTATE` |
| `activate-button` | `selector` | `DESTRUCTIVE` |
| `submit-field` | `selector` | `DESTRUCTIVE` |
| `double-click-row` | `table`, `row` | `DESTRUCTIVE` |
| `activate-menu` | `path` (string array) | `DESTRUCTIVE` |
| `invoke-shortcut` | `shortcut` | `DESTRUCTIVE` |
| `action-status` | `request-id` (the original desktop action ID) | `READ` |
| `read-control` | `selector` | `SENSITIVE_READ` |
| `capture-screen` | none | `SENSITIVE_READ` |
| `choose-file` | `selector`, `path` (absolute local path) | `DESTRUCTIVE` |
| `cancel-file-dialog` | `selector` | `NAVIGATE` |

`choose-option` accepts the exact visible text of a fixed option. When
`read-control` reports `editable=true`, it also accepts custom text and commits
the editor value through the normal combo-box selection listener. This lets a
client add an inventory item with a new category or selling unit. Use
`replace-text` for its name, prices, quantity, and notes, then activate
`Add item to inventory` or submit `Inventory quantity`. Retain the save request
ID for retries and confirm the background save through farm-data read-back.
Fixed option lists continue to reject unknown values.

### Dialogs and action completion

Desktop actions run through a process-local action journal. A quick callback
returns `status=COMPLETED`; an action still running after the short dispatch
grace period returns `accepted=true`, `completed=false`, `status=PENDING`, and
`actionRequestId`. A successful acceptance does not mean the farm operation is
complete. This allows an action to open a real modal dialog without holding the
gateway lock until the dialog closes. `COMPLETED` means the command callback
returned successfully. A background SwingWorker started by that callback may
still be running; verify its visible status and resulting farm data separately.

Poll `action-status` with the returned `actionRequestId` in
`arguments["request-id"]` (for MCP, this differs from the JSON-RPC request ID). Give each
poll a fresh envelope `requestId` so the gateway does not replay an older poll.
The response reports `PENDING`, `COMPLETED`, or `FAILED`; failures expose only
their exception class. Status reads omit control values and prior result text.
Retrying the original action returns its original accepted receipt and never
clicks twice while that receipt is retained. Up to 256 receipts and 16 pending
actions are retained; pending actions are not silently evicted. The journal is
process-local, so domain receipts remain necessary for restart-safe mutations.

Observation exposes `modalActive` and `pendingActions`. While an owned modal is
visible, selectors, bounded control reads, and screenshots target the deepest
visible modal belonging to the attached application. Use its published controls
to edit, confirm, or cancel it; primary-route navigation waits until it closes.
Unrelated application windows are outside this attachment. If the event loop
is busy, observation returns `ready=false` and pending action IDs instead of
waiting indefinitely. A queued action cannot move into a newly attached
desktop session.

Export dialogs publish the stable `Farm export file chooser` selector
(`farm.exportFileChooser` also works). With sensitive reads enabled,
`read-control` reports the dialog title, current directory, and selected path.
`choose-file` sets an absolute path and calls the chooser's approval callback;
it rejects relative paths, incompatible file/directory selections, and files
outside the active filter. The original export action then resumes. Use
`cancel-file-dialog` to resume it without approving a file. These commands use
the same pending-action and replay journal as other dialog actions and do not
depend on operating-system pointer or keyboard input.

### Sensitive perception

Ordinary observation stays value-redacted. If an operator explicitly enables
`FLOWERFARM_HARNESS_ALLOW_SENSITIVE_READ=true`, `read-control` returns a bounded,
JSON-compatible view of one visible non-password control. Text is capped at
8,000 characters; option collections and table cells have smaller item and
count limits and report `truncated=true` when either a collection or an individual
value is clipped. Selected rows and tab titles are also bounded. Tables use their current
sorted view and renderer-visible cell text. Password controls are rejected even
when this policy is enabled.

`capture-screen` returns an `image/png` payload encoded as base64 with width,
height, and SHA-256 metadata. It captures the attached FlowersForever
application root or its active owned modal, scales to at most 2,048 pixels per side and two million
pixels, and rejects PNG output over 4 MiB. It does not write an image to the
server filesystem. For example, a supervised PowerShell client can save one
response locally:

```powershell
$body = @{
  requestId = [guid]::NewGuid().ToString()
  action = 'capture-screen'
  arguments = @{}
} | ConvertTo-Json
$capture = Invoke-RestMethod -Method Post -Headers $headers `
  -ContentType 'application/json' `
  -Uri 'http://127.0.0.1:8080/api/harness/swing-desktop/commands' `
  -Body $body
[IO.File]::WriteAllBytes('flowersforever.png',
  [Convert]::FromBase64String($capture.output.data))
```

Values and pixels can contain customer, order, event, or farm information.
Enable this gate only for a supervised session and ensure the connected model,
transport, logs, and transcripts have an acceptable retention policy.

Selectors come from accessible names, production component names, or visible
button text and must be unique across all control kinds on the current screen.
Buttons without a production action or item listener are not advertised as
commandable. Combo choices are matched
to renderer-visible text; table columns and tabs use their exact displayed
titles. Table rows are zero-based indexes in the current sorted view. Password
fields cannot be written through this plugin or advertised as commandable.
Commands run on Swing's event-dispatch thread and return a fresh snapshot after
execution. Enter submission uses the field's production action listener;
double-click dispatches a two-click mouse event after selecting the row; menu
commands follow the observed visible-label path; and shortcut strings use Swing
keystroke syntax such as `F5` or `ctrl ENTER`. These generic activation routes
are destructive-policy operations because their application effects cannot be
safely inferred from their labels or keys.

Use `navigate-route` with one exact path from snapshot `metadata.routes` to open
any published leaf surface atomically. The bridge validates the complete route
before changing tabs, selects its primary workflow through the production
navigator, then selects each uniquely matching visible nested tab. A missing,
disabled, ambiguous, or partially selected path fails instead of leaving the
caller with an invented route.

The live contract covers application-owned text, selectors, primary and nested
tabs, single and multiple table selection, table sorting, list selection,
spinners, sliders, buttons/toggles, single-line Enter submission, table
double-click routes, terminal menus, and installed application shortcuts.
Native dialogs, file choosers, the OS clipboard, arbitrary pointer coordinates,
and process exit remain explicit plugin or transport extensions because they
need platform-specific focus and approval policy. The installed-desktop test
profile covers native pointer, keyboard, focus, and pixels independently.

## AI adapter and future robot contract

An AI adapter should build its offered tools only from the authenticated
manifest. Omit `allowed=false` tools, retain the published annotations, generate
a unique request ID outside the model, and stamp a fresh expiry for physical
tools. The gateway remains authoritative if a client ignores those hints.
Provider prompts and tool names therefore do not become a security boundary.

Device and robot plugins use the same `HarnessPlugin` interface. Declare kind
`DEVICE` or `ROBOT`, add `PHYSICAL_SENSING` and/or `PHYSICAL_ACTUATION`, and
describe stable sensor keys with `HarnessObservationSpec`. The gateway checks
published signal types, removes signals marked sensitive unless
`SENSITIVE_READ` is enabled, and rejects a snapshot whose plugin ID does not
match its descriptor. Device and robot snapshots also reject undeclared metadata
keys so a new camera, location, or diagnostic field cannot bypass sensitivity
review. A physical action must use both `HarnessRisk.PHYSICAL`
and `HarnessActionEffect.PHYSICAL_WORLD`; descriptor validation rejects any
other pairing or a plugin missing `PHYSICAL_ACTUATION`.

A minimal future robot descriptor looks like this:

```java
new HarnessPluginDescriptor(
        "field-robot", "Field robot", "Observe and move a supervised cart.", "0.1",
        HarnessPluginKind.ROBOT,
        Set.of(HarnessPluginCapability.OBSERVATION,
                HarnessPluginCapability.COMMANDS,
                HarnessPluginCapability.PHYSICAL_SENSING,
                HarnessPluginCapability.PHYSICAL_ACTUATION,
                HarnessPluginCapability.EMERGENCY_STOP),
        List.of(
                new HarnessObservationSpec("battery-percent", HarnessValueType.NUMBER,
                        "Remaining battery charge.", "%", false),
                new HarnessObservationSpec("pose", HarnessValueType.OBJECT,
                        "Pose in the plugin-declared coordinate frame.", null, false)),
        List.of(new HarnessActionSpec(
                "move-to", "Move to a reviewed pose.", HarnessRisk.PHYSICAL,
                List.of(new HarnessArgumentSpec("pose", HarnessValueType.OBJECT,
                        "Target pose in the declared frame.", true)))));
```

The gateway is an orchestration boundary, not a certified robot safety system.
Every physical plugin must still fail safe locally and own hardware emergency
stop, watchdog timeout, collision/occupancy checks, travel and speed limits,
coordinate-frame validation, actuator interlocks, loss-of-link behavior, and
human exclusion rules. Advertise `EMERGENCY_STOP` only when the plugin has a
real independently tested stop path. Do not treat token access, replay caching,
or a model's confirmation as a substitute for those controls.

### Optimus development boundary

The optional `tesla-optimus` plugin is a high-level integration boundary, not a
claim of compatibility with unreleased hardware. As of 2026-09-01, Tesla's
public AI and Optimus materials describe autonomy, perception, navigation,
interaction, and motion-planning goals but do not publish a customer robot SDK
or command API. FlowersForever therefore does not guess an endpoint, wire
protocol, credential scheme, joint model, or coordinate frame.

`OptimusRobotAdapter` is the replaceable vendor port. The included
`SimulatedOptimusRobotAdapter` makes the complete AI-to-harness flow testable
now. When an official owner integration becomes available, implement that port
against the documented customer API and keep authentication, device identity,
connection health, hardware limits, task acknowledgement, progress, graceful
cancellation, and stop behavior in the adapter. The harness plugin should remain
vendor-transport agnostic.

Both pieces are absent from the ordinary runtime. Enable the simulator only in
a supervised development session:

```powershell
$env:FLOWERFARM_HARNESS_ENABLED = 'true'
$env:FLOWERFARM_HARNESS_TOKEN = 'replace-with-at-least-24-random-characters'
$env:FLOWERFARM_HARNESS_ALLOW_DRAFTS = 'true'
$env:FLOWERFARM_HARNESS_ALLOW_MUTATIONS = 'true'
$env:FLOWERFARM_HARNESS_ALLOW_DESTRUCTIVE = 'true'
$env:FLOWERFARM_HARNESS_ALLOW_PHYSICAL_ACTIONS = 'true'
$env:FLOWERFARM_OPTIMUS_ENABLED = 'true'
$env:FLOWERFARM_OPTIMUS_MODE = 'simulator'
mvn spring-boot:run
```

Enabling the Optimus plugin without a configured `OptimusRobotAdapter` fails
application startup. This is deliberate: an unconfigured real robot must not
silently fall back to the simulator.

Every adapter must also return a stable `RobotIdentity`. A simulator explicitly
reports `SIMULATOR`. A hardware adapter refuses to initialize unless
`FLOWERFARM_OPTIMUS_EXPECTED_ROBOT_ID` is set and exactly matches the
adapter-reported ID; a later identity change also fails closed. Model, adapter
software version, coordinate frame, and the explicit `simulated` flag remain
observable, while the robot ID is sensitive telemetry.

The adapter must return a timestamped `RobotState` and a timestamped `TaskPlan`.
The plugin derives an `operational` signal and refuses staging or motion when
state is more than five seconds old, more than two seconds in the future, stop
is latched, or `ready-for-command` is false. A staged plan is visible through
`staged-plan`, `staged-plan-expired`, `staged-plan-expires-at`, and
`staged-plan-adapter-confirmed`, without exposing its token. Interrupted adapter
handoffs are separately visible as `execution-recovery-pending` and
`execution-adapter-confirmed`. The staged confirmation
signal is true only while the current adapter reports `STAGED` and its
`stagedPlanFingerprint` exactly matches the persisted plan-token digest. The
fingerprint stays inside the adapter/plugin boundary and is never included in AI
observation metadata. Plans expire no later than five minutes after staging and
cannot be silently replaced while the adapter confirms that exact plan; cancel
or execute the existing plan first.
State, plan, and command-receipt timestamps are required; the harness never turns
a missing adapter timestamp into an apparently fresh local time.

Flyway V10 stores the active plan's robot identity, lease ID, timestamps, and
plan-token SHA-256 digest in `robot_staged_plans`; the plaintext plan token and
task text are not persisted. An AI that retained the original lease token and
plan token can reconnect to a recreated plugin and execute while the adapter
still reports the same staged task. If the durable record survives but the
adapter restarts without its staged state, `staged-plan-adapter-confirmed` is
false. Execution then clears the stale record without dispatching, and
`stage-task` can replace it for the active lease. A newly active lease may
cancel staged work left behind by an expired lease, but it cannot execute that
old plan.

An adapter should populate `RobotState.stagedPlanFingerprint` with
`OptimusRobotAdapter.planFingerprint(planToken)` whenever it reports `STAGED`,
and retain that fingerprint on the originating running or recent task so an
interrupted execution response can be reconciled exactly.
A missing fingerprint or the fingerprint of a different adapter-held plan is
unconfirmed and cannot execute the stored token. If the adapter has staged work
but no matching durable record, the next authorized `stage-task` cancels that
adapter-only draft before staging the requested task.

`stage-task` also observes the adapter immediately after `stage(...)` returns.
The plugin returns the plan token only when that observation is fresh, reports
`STAGED`, and carries the exact new plan fingerprint. Idle, missing, mismatched,
stale, future-dated, or unreadable acknowledgement is rejected and the adapter
draft is cancelled, so an AI client is not handed a token the robot has not
demonstrably retained.

Once adapter staging begins, every unsuccessful exit attempts to cancel the
adapter draft. This includes an ambiguous exception from `stage(...)`, invalid
plan data, failed acknowledgement, an expired or rotated lease during the call,
and durable-store failure. If that rollback also fails, the original staging
failure remains primary and the cleanup failure is attached as a secondary
diagnostic. An adapter-only draft remains observable and can be reconciled by a
later authorized `stage-task` attempt.

Before returning the token, `stage-task` verifies both the staged-plan store's
write result and a fresh read-back. Robot identity, simulator mode, lease ID,
and plan-token digest must match exactly; staging and expiry timestamps must
match within the database's sub-millisecond normalization. A missing, malformed,
mismatched, or unreadable commit cancels the adapter draft and, after
cancellation succeeds, clears the unusable stored record.

Flyway V11 extends that record with a durable execution handoff marker. Before
calling `executeStagedTask`, the plugin atomically stores the original harness
request ID and verifies it with a fresh read-back. The marker remains if the
adapter throws, returns an invalid receipt, or the operation journal cannot be
written. An exact retry after plugin restart then either retries the still-staged
idempotent plan or recovers the matching adapter command without redispatch. A
different request ID cannot take over the pending execution. The marker is
removed only after a valid receipt is durably journaled.

Flyway V12 makes that accepted receipt an exact durable response-replay record.
It stores the request ID beside a SHA-256 fingerprint of the complete execution
command: request ID, action, plan token, lease token, and original deadline. The
plaintext plan and lease tokens are never copied into the journal. After a lost
HTTP response or application restart, the Optimus plugin checks this receipt
before command-expiry validation. An exact retry therefore returns the same
operation, marked `replayed` and `recovered`, even when its original deadline is
now past, without executing the physical task again. Reusing that request ID for
another action or any changed argument is rejected as a collision.

Plugin 0.17 removes live owner-API availability from that replay path. If robot
identity was established when the plugin started but a later identity or state
call is unavailable, an exact durable retry still returns the accepted command
with `status=UNKNOWN`, `reconciliation=UNCONFIRMED`, and
`adapterConfirmed=false`. Its response snapshot reports
`connection-state=UNAVAILABLE` and omits sensor values that cannot be known.
Normal physical commands continue to require a live state and do not reach the
adapter during the outage. When the owner API recovers, the next observation or
operation query resumes live reconciliation.

Plugin 0.18 also removes the best-effort reconciliation append from the response
critical path. An unavailable snapshot immediately renders any last-known
nonterminal operation as `UNKNOWN` instead of repeating stale `RUNNING` state.
If the durable receipt can be read but the journal cannot append that `UNKNOWN`
event, the exact replay still succeeds from the original receipt and returns
`reconciliationPersisted=false`. The stored accepted receipt is left intact so a
later retry can reconcile again; terminal durable history is not downgraded.

Plugin 0.19 applies the same separation when the adapter is live. If current
state exactly confirms the accepted command but its lifecycle append fails, the
replay returns the adapter-confirmed status with `reconciliation=LIVE` and
`reconciliationPersisted=false`. The gateway can still attach the fresh adapter
snapshot even while its best-effort status append is unavailable. This does not
relax acceptance: the original command receipt must still be durably written
before any execution response can succeed or replay.

Plugin 0.20 extends that read continuity to the operation tools. `get-operation`
and every `list-operations` item now report `reconciliationPersisted`; live state
or a locally derived `UNKNOWN` result remains readable when only its secondary
lifecycle append fails. `list-operation-events` continues returning its already
durable cursor page and reports `reconciliationPersisted=false` when its optional
live-state refresh could not be appended. No synthetic cursor event is emitted,
so reconnecting clients never advance past state that was not durably journaled.

Plugin 0.21 removes live owner-API availability from those read tools. When the
adapter is temporarily unavailable, `get-operation` and `list-operations` use
durable history, preserve terminal results, and render nonterminal work as
`UNKNOWN`/`UNCONFIRMED`. Each operation reports `adapterAvailable=false`;
`list-operation-events` returns its durable cursor page with the same availability
signal and does not attempt a live refresh. These fallbacks are read-only with
respect to the robot and cannot dispatch a physical command.

Plugin 0.22 makes the durable lifecycle monotonic across reconnects and delayed
adapter samples. Both transient and JPA journals ignore observations older than
their latest event, clamp progress so it cannot move backward, convert a backward
nonterminal phase into `UNKNOWN`, and retain terminal history instead of allowing
it to return to `RUNNING` or `STAGED`. A later forward adapter sample can reconcile
an `UNKNOWN` event normally. Cancellation, failure, and stop receipts preserve the
latest known progress; only `SUCCEEDED` implies 100 percent.

Every staged-plan cleanup path reconciles the durable record with current
adapter state. Explicit cancellation, lease release, plan expiry, restaging,
and return-to-pose call the adapter's staged-task cancellation only when it
currently reports `STAGED`; an already-idle restarted adapter needs only local
record cleanup. When adapter cancellation is required, it completes before the
durable record is removed so a transport failure remains retryable. A pending
execution record cannot be silently discarded when adapter state is running or
unresolved; emergency stop dispatches and journals first, then removes it.

Normal control also requires a short-lived, exclusive capability lease bound to
the adapter mode and stable robot ID. `acquire-control-lease` accepts an operator
or agent label and a 15–300 second duration. It returns a 256-bit capability
token once; only the SHA-256 digest is stored in Flyway V9
`robot_control_leases`. Renewal keeps the lease ID (so its reviewed plan remains
owned) but rotates the token. Release or expiry invalidates the token, and a new
lease cannot execute a plan staged by the old lease. Read-only observation and
operation queries need no lease. Emergency stop also remains lease-independent
so another caller that passes the physical policy and command-deadline checks
can stop the robot. Holder labels remain only in the local lease record and are
not returned through AI observation or lease status; they are audit context, not
federated human identity.

Robot work is an asynchronous operation, not a synchronous REST fiction.
`execute-staged-task` returns a stable command ID and `RUNNING`. Subsequent
observations publish the typed task status, monotonic progress percentage, and
status detail. The command ID and detail are sensitive; status and progress are
not. Terminal status is one of `SUCCEEDED`, `FAILED`, `CANCELLED`, or `STOPPED`.

Accepted command receipts and meaningful lifecycle changes are also written to
an append-only local operation journal. The generic gateway response cache is
bounded and process-local. Optimus execution responses additionally use the
Flyway V12 exact-request receipt so they can replay after cache eviction or an
application restart without duplicate physical delivery. The journal also
supports recovery and status lookup. `get-operation` reconciles a command ID with current
adapter state and returns `LIVE`, `TERMINAL_HISTORY`, or `UNCONFIRMED`. A
nonterminal operation that the current adapter no longer reports is recorded as
`UNKNOWN`; it is never assumed to have completed. `list-operations` exposes the
latest event for up to 25 recent operation IDs so a reconnecting client does not
need to retain every prior response. `list-operation-events` provides the
complementary incremental stream: pass the previous `nextCursor` as
`after-cursor` to read the next 1–50 append-only lifecycle changes in order.
Cursors are JSON strings so large database IDs are not rounded by JavaScript;
clients should store and replay them unchanged. A response also reports
`hasMore`, allowing a reconnecting AI to drain a backlog without polling each
operation separately.

The plugin publishes observation plus thirteen bounded actions:

| Action | Risk | Behavior |
|---|---|---|
| `get-control-lease` | `READ` | Read only active/expiry status without returning a lease ID, token, holder label, or guessable holder fingerprint |
| `acquire-control-lease` | `MUTATE` | Acquire exclusive control of this robot for 15–300 seconds and receive the one-time capability token |
| `renew-control-lease` | `MUTATE` | Extend the same lease ID and rotate its capability token |
| `release-control-lease` | `MUTATE` | Invalidate the capability and discard any currently recorded staged plan |
| `stage-task` | `DRAFT` | With a valid lease, stage a high-level task, named work area, duration up to 900 seconds, and optional constraints; returns a one-time plan token and expiry no more than five minutes away |
| `cancel-staged-task` | `DRAFT` | With the active lease, remove the staged task without motion, including work orphaned by an expired lease |
| `execute-staged-task` | `PHYSICAL` | With the owning lease, start only the exact staged plan token and return its command ID with `RUNNING` status |
| `get-operation` | `READ` | Read one durable command ID and reconcile it with current adapter state or offline history; reports adapter availability and whether reconciliation persisted, without status message or robot ID |
| `list-operations` | `READ` | List and reconcile 1–25 recent operation IDs from live or offline history, returning adapter availability and reconciliation persistence per operation without status detail or robot identity |
| `list-operation-events` | `READ` | Read 1–50 ordered durable lifecycle changes after an optional string cursor; returns `nextCursor`, `hasMore`, adapter availability, and live-refresh persistence without status detail or robot identity |
| `cancel-active-task` | `PHYSICAL` | With the current lease, gracefully cancel the exact running command ID using fresh state; separate from emergency stop |
| `return-safe-pose` | `PHYSICAL` | With the current lease, start the adapter-owned safe-pose routine as a cancellable operation |
| `emergency-stop` | `PHYSICAL` | Request the adapter's independent, idempotent stop path without requiring lease ownership |

For example, acquire control, stage, and then execute a simulator task:

```powershell
$leaseBody = @{
  requestId = [guid]::NewGuid().ToString()
  action = 'acquire-control-lease'
  arguments = @{
    holder = 'packing-shed-supervisor'
    'duration-seconds' = 300
  }
} | ConvertTo-Json -Depth 4
$lease = Invoke-RestMethod -Method Post -Headers $headers `
  -ContentType 'application/json' `
  -Uri 'http://127.0.0.1:8080/api/harness/tesla-optimus/commands' `
  -Body $leaseBody

$stageBody = @{
  requestId = [guid]::NewGuid().ToString()
  action = 'stage-task'
  arguments = @{
    task = 'Carry one empty harvest tote to the wash station'
    'work-area' = 'Packing shed'
    'max-duration-seconds' = 120
    constraints = @('Keep clear of people', 'Do not handle tools')
    'lease-token' = $lease.output.leaseToken
  }
} | ConvertTo-Json -Depth 5
$stage = Invoke-RestMethod -Method Post -Headers $headers `
  -ContentType 'application/json' `
  -Uri 'http://127.0.0.1:8080/api/harness/tesla-optimus/commands' `
  -Body $stageBody

$executeBody = @{
  requestId = [guid]::NewGuid().ToString()
  action = 'execute-staged-task'
  expiresAt = [DateTimeOffset]::UtcNow.AddSeconds(10).ToString('o')
  arguments = @{
    'plan-token' = $stage.output.planToken
    'lease-token' = $lease.output.leaseToken
  }
} | ConvertTo-Json -Depth 4
$execution = Invoke-RestMethod -Method Post -Headers $headers `
  -ContentType 'application/json' `
  -Uri 'http://127.0.0.1:8080/api/harness/tesla-optimus/commands' `
  -Body $executeBody

$cancelBody = @{
  requestId = [guid]::NewGuid().ToString()
  action = 'cancel-active-task'
  expiresAt = [DateTimeOffset]::UtcNow.AddSeconds(10).ToString('o')
  arguments = @{
    'command-id' = $execution.output.commandId
    'lease-token' = $lease.output.leaseToken
  }
} | ConvertTo-Json -Depth 4
Invoke-RestMethod -Method Post -Headers $headers `
  -ContentType 'application/json' `
  -Uri 'http://127.0.0.1:8080/api/harness/tesla-optimus/commands' `
  -Body $cancelBody

$statusBody = @{
  requestId = [guid]::NewGuid().ToString()
  action = 'get-operation'
  arguments = @{ 'command-id' = $execution.output.commandId }
} | ConvertTo-Json -Depth 4
Invoke-RestMethod -Method Post -Headers $headers `
  -ContentType 'application/json' `
  -Uri 'http://127.0.0.1:8080/api/harness/tesla-optimus/commands' `
  -Body $statusBody
```

The simulator starts a task in `RUNNING`, supports deterministic progress from
its test driver, and supports exact graceful cancellation; it does not control
hardware. Its emergency stop is latched and can be cleared only by restarting
the simulator. A real adapter must require a robot-local reset and must never
expose an AI-callable reset-stop action. No raw joint, arbitrary trajectory,
velocity, or unbounded free-form motion command is part of this contract.

The plugin writes a durable execution marker before asking an adapter to execute.
If the transport fails ambiguously after that boundary, retry the exact original
request ID and plan token. The harness reconciles a matching running or terminal
adapter task, or safely redelivers the same token when the adapter still reports
the exact reviewed plan. Do not retry with a new request ID. A hardware adapter
must make its plan token idempotent and retain the originating plan fingerprint
because it alone can distinguish an acknowledgement lost after acceptance.

## Add another application plugin

Implement `HarnessPlugin` and register it as a Spring bean. The registry will
discover it automatically and will fail startup on duplicate plugin IDs or
duplicate action names. Plug-in IDs and action names are lowercase
letter/number/hyphen slugs of 1–30 characters; this keeps their combined tool
name stable and portable across common AI providers.

```java
@Component
final class GreenhouseHarnessPlugin implements HarnessPlugin {
    @Override
    public HarnessPluginDescriptor descriptor() {
        return new HarnessPluginDescriptor(
                "greenhouse", "Greenhouse controller",
                "Observe zones and draft an irrigation plan.", "1.0",
                List.of(new HarnessActionSpec(
                        "draft-plan", "Draft a reviewed plan",
                        HarnessRisk.DRAFT, List.of(new HarnessArgumentSpec(
                                "zone", HarnessValueType.STRING,
                                "Greenhouse zone identifier.", true)))));
    }

    @Override
    public HarnessSnapshot observe() {
        return new HarnessSnapshot("greenhouse", Instant.now(),
                List.of("zones"), List.of(), Map.of("ready", true));
    }

    @Override
    public HarnessExecution execute(HarnessCommand command) {
        if (!"draft-plan".equals(command.action())) {
            throw new IllegalArgumentException("Unsupported action");
        }
        String zone = command.text("zone");
        return HarnessExecution.success(
                "Drafted a reviewed plan", Map.of("zone", zone));
    }
}
```

Keep domain authorization and validation inside the target service as well as
at the harness boundary. A risk flag is a gateway permission, not a replacement
for inventory, role, transaction, or safety rules.

## Add another transport or AI provider

Inject `HarnessGateway` into an MCP, stdio, WebSocket, or provider-specific
adapter and map only these four operations:

1. `manifest(token)`
2. `discover(token)`
3. `observe(token, pluginId)`
4. `execute(token, pluginId, HarnessCommand)`

Do not call Swing components directly from the transport. Keeping provider code
outside `HarnessPlugin` preserves one policy, action schema, replay rule, and
test surface for every model. Provider authentication, rate limits, prompt
approval, network egress, and transcript retention remain responsibilities of
that adapter and its operator.

## Verification

`mvn verify -Pquality` runs the ordinary unit, REST, and desktop journey suite,
then runs the MCP process smoke again through Failsafe against the freshly
repackaged executable JAR. The latter uses `java -jar`, checks doctor readiness,
imports a mapped row over stdio, reads the canonical row and durable receipt,
and verifies clean protocol EOF. Reports are in `target/surefire-reports` and
`target/failsafe-reports`; coverage, SpotBugs, and Checkstyle run in the same
verification command. Native packaging jobs obtain their matching launcher
properties and smoke scripts before using the verified JAR. The local release
script also runs the AI launcher doctor and ping before archiving its app image.

Focused contract tests cover authentication, policy escalation, replay and ID
collision behavior, typed schema discovery, required/unknown/type-invalid
arguments, REST status mapping, field-value omission, password rejection,
renderer-visible option selection, and every built-in command type. A real
random-port integration test proves the independent token boundary and live
manifest endpoint. A domain integration sends JSON through the live controller
and gateway into `farm-data`, verifies nested record schemas and replay, writes
customers, inventory, growing templates, planting plans, and multi-ingredient
recipes, distinguishes historical imports from live inventory effects, creates
orders, records knowledge and task actuals, and pages the saved records back. A
container-profile test proves normal HTTP authentication
and the harness token are both required. A complete JSON → controller → gateway
→ live Swing test
proves schema generation including typed array items, default-denied sensitive
policy, bounded structured values, a PNG signature and digest, draft entry,
Enter, double-click, menu, shortcut and button activation, fresh observation,
and duplicate-request replay without replacing any layer with a mock. The full
application-context smoke proves that the Swing and farm-data plugins are
registered while the REST transport stays absent by default. The separate
installed-desktop profile remains the evidence for real OS pointer, keyboard,
focus, and pixel behavior;
see the [human-interaction harness audit](HARNESS_AUDIT.md). A production-catalog
contract independently publishes all 35 current primary/nested routes, opens
each through `navigate-route`, observes its controls, and requires every visible
runtime selector to be non-blank, globally unique, and backed by a supported
interaction.
