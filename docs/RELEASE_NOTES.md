# FlowersForever private-preview notes

## September 22, 2026 — Recheck customer and packing actions

- Customer keyboard submission follows the same refresh, access, and pending-save
  rules as the buttons. Double-clicking a customer cannot replace its form during
  a running save, deletion, or refresh.
- Customer deletion rechecks the reviewed account, selection, entered fields,
  and access after confirmation. A newer customer write invalidates an older
  deletion decision, and deleting the loaded account explains the form loss.
- Market Day completion rechecks the exact reviewed plan and packed checks after
  confirmation. Changed scope, access, refresh state, or a newer submission
  requires another review.
- A rejected stale packing review keeps completion locked until a successful
  rebuild. Refresh requests during completion wait for its result.

## September 22, 2026 — Keep inventory drafts with their product

- Retains failed counts and edits per saved product and operation, including
  work hidden by filters or missing from a later inventory reload.
- Restores drafts through product renames without attaching them to a same-name
  replacement. Saving one draft preserves other products and the other draft type.
- Adds **Review retained drafts…** with copyable entered values, current saved
  details, a route to the original product, and confirmed single-draft discard.
- Keeps corrections when cancelling a reopened draft, warns when its saved
  baseline changed, and retains duplicate-name notices with the affected edit.
- Rejects older modal save/discard decisions after newer draft changes and
  explains draft loss before deleting a product. Exit warnings include all drafts.

## September 22, 2026 — Reliable inventory recovery and release checks

- Keeps acknowledged inventory counts, edits, and deletions visibly saved when
  their follow-up reload fails. Further inventory writes wait for fresh rows;
  search, selection, and permission changes preserve the recovery warning.
- Queues refreshes behind running inventory writes and rechecks the reviewed
  product and access after modal decisions. Inventory drafts and pending writes
  now participate in the exit and sign-out warning.
- Gives inventory status and report messages full-width wrapping rows so a long
  product name does not crowd the Refresh action out of the header. Compact
  workspaces scroll, with separate stock/report actions and a usable table.
- Isolates every local release build under unique target and dist directories,
  preserving verification evidence and existing native runtimes.
- Requires a fresh desktop smoke marker and successful process exit before
  accepting a Windows runtime or completing local release packaging.

## September 21, 2026 — Keep unfinished harvest work safe

- Gives harvest entry wider fields, complete status messages, reachable history
  and filters on compact screens, and readable history identity columns.
- Asks before loading, copying, or clearing over changed quick-entry fields;
  unchanged saved rows and retained setup remain quick to use.
- Protects existing batch lines before loading examples, keeps crop drafts during
  recent-crop refresh, and blocks history double-click replacement during writes.
- Warns before exit or sign-out with unfinished harvests or pending saves/deletions.
  Running CRM, packing, harvest, and task operations use explicit exit-before-result
  wording so closing never implies undoing records already saved.
- Checks native launcher readiness against required capabilities and missing
  operations, replacing the obsolete Windows check for exactly 33 farm tools.

## September 21, 2026 — Production Studio for everyday laptop use

- Separates planting schedules from daily field work and gives editors full-width,
  vertically scrolling forms, readable tables, associated labels, and visible task context.
- Keeps unsaved frost settings and task actuals during refresh and navigation;
  background loading and task saves leave the interface responsive.
- Retains changed or missing task drafts for review, provides explicit reload/discard
  actions, and warns before closing with unfinished production work.
- Keeps saved outcomes distinct from failed follow-up reads and requires a fresh
  task review before writes resume after an interrupted or failed refresh.
- Brings grower benefits and real product screens forward in the README.

## September 21, 2026 — Close harness and application wiring gaps

- Exposes the harness manifest and live plugin observations as MCP resources,
  including plugins with no action tools and current sensitive-data filtering.
- Connects reviewed inventory counts, reviewed order fulfillment, and durable
  worker receipt recovery to the farm harness; adds saved season-setting readback.
- Reports skipped-task preview issues without marking the preview transaction
  rollback-only, while preserving existing import receipt recovery.
- Checks queued Swing deadlines when callbacks actually begin and refreshes
  Production Studio's dependent readouts after successful writes.
- Requires usable data-directory configuration and all 38 farm tools before the
  MCP readiness doctor reports READY.

## September 21, 2026 — Ecosystem-inspired harness robustness

- Honors optional command deadlines across REST, MCP, and embedded callers while
  preserving receipt replay and stricter physical-action expiry rules.
- Rejects malformed numeric grouping/currency/percent input before batch writes;
  handles extreme exponents without expanding enormous integer values.
- Redacts and bounds JSON-RPC protocol errors as well as tool failures.
- Adds a clean verification runner, per-class test floors, source/report hashes,
  quality-report checks, and fault injection for misleading pass evidence.
- Supports isolated verification output alongside retained native preview builds.
- Records CSRBT source references and follow-up acceptance criteria in
  [the ecosystem comparison](HARNESS_ECOSYSTEM.md).

## September 13, 2026 — Exact numeric requests and recovery

- Preserves decimal values and request identities through REST, MCP, and mapped
  JSON cells; rejects fractions in integer fields and spreadsheet dates.
- Prevents supported older rounded MCP receipts from silently repeating effects,
  while retaining ordinary-decimal and negative-zero replay identities.
- Recovers accepted robot executions after staged-plan cleanup failures without
  clearing newer plans or losing access to accepted receipts; preserves observed
  completion before another task replaces the adapter's current operation.
- Blocks password-backed Swing editors and masked renderer values through their
  parent controls; discovery marks known password-backed combos unsupported.

## September 13, 2026 — Harness recovery and wire contracts

- Rejects malformed UTF-8 MCP input before dispatch and recovers on the next frame.
- Publishes separate fresh-order and historical-order status schemas.
- Returns consistent import conflicts when a source receipt points to a deleted
  target, including corrected retries across all five upsert types.
- Keeps export filenames and new metadata independent of host locale and application
  JSON settings; recovers supported older formats without rewriting checksums.
- Verifies action-queue saturation, interrupted retry identity, and pending-action
  retention during receipt eviction.

## September 13, 2026 — Deeper harness and API consistency

- Keeps new MCP retry identities stable across equivalent expiry timestamps and
  application JSON formatting; recovers exact older receipts without execution.
- Replays completed physical commands after permission revocation while continuing
  to block fresh actions and revoked sensitive reads.
- Redacts credentials and bounds REST/MCP harness error messages.
- Rejects malformed JSON cells and harvest dates/quantities during mapped preview.
- Runs exports with one database connection while retaining atomic batch imports.
- Rejects changed desktop observations and captures; reads and sorts renamed table
  headers using their current visible values.

## September 13, 2026 — Harness and API request boundaries

- Rejects ambiguous REST command bodies before dispatch and reports unsupported
  media types and missing upload parts as client errors.
- Preserves full 64-bit attachment and target IDs through REST and MCP commands.
- Detects document replacement during text edits and option-model refreshes
  during label matching, preventing false success or selection of another item.

## September 13, 2026 — Preserve receipts during completion retries

- Keeps an older completed action's receipt when a retry encounters failed
  size accounting or an oversized snapshot, preventing the action from running
  again. Ordinary response replay retains its existing eviction order.

## September 8, 2026 — Consistent reads, menu shortcuts, and cache accounting

- Supports closed-menu accelerators such as F1, Dark Mode, and My access through
  the existing shortcut command, preserving explicit application bindings.
- Rejects structured reads if the table, active dialog, or attachment changes
  while the read is running.
- Counts complete farm-record payloads toward the replay cache's byte budget
  while preserving completed-action receipts if accounting fails.

## September 8, 2026 — Verify runtime action outcomes

- Reports failed state changes when application listeners revert an option,
  selection, tab, slider, or sort order.
- Validates spinner text before changing an existing draft and catches silent
  editor/model rejection, integer wraparound, and fractional truncation.
- Stops table double-clicks when selection changes the record being opened.
- Rejects stale paged reads after an effective table renderer is replaced.

## September 8, 2026 — Broader runtime harness coverage

- Expands the desktop API to 30 commands, including scrolling, workspace
  dividers, exact toggle states, selection clearing, and indexed option choices.
- Reads status labels, dialog text, full collection entries, and long reports
  through bounded pages; exposes read-only and layout selectors in discovery.
- Edits long drafts in bounded segments through the normal document filters.
- Supports closed menu toggles through exact paths and binds structured reads
  to observation contexts, preventing changed screens from silently mixing pages.

## September 8, 2026 — Reject actions against a changed desktop

- Publishes an opaque observation token that AI clients can optionally attach
  to queued desktop actions. Changed controls, table models, or active dialogs
  cause rejection before the action runs, so an old selector or row index cannot
  silently target a replacement screen when the token is supplied.
- Preserves existing commands and receipt replay. Clients recover from a stale
  context by reviewing a fresh observation and sending a new request ID.

## September 7, 2026 — Harness input bounds and retry behavior

- Checks spinner bounds and numeric types before test-driver input can alter
  a model or notify application listeners; date and list spinners are covered.
- Freezes the first published desktop-action receipt for concurrent retries,
  while status polling continues to report current completion or failure.
- Rejects a live AI record page that would return the same continuation offset
  indefinitely, with a clear retry message instead of a misleading result.

## September 7, 2026 — Harness visibility and selector accuracy

- Makes desktop journey actions and success-message waits respect hidden
  ancestors and inactive tabs, and prevents the test driver from selecting
  disabled tabs. Hidden copies of controls can no longer mask missing navigation.
- Makes the runtime desktop snapshot account for selector collisions with
  component names and button text, so an ambiguous control is not advertised
  to AI clients as commandable. An existing unique component name can keep a
  control addressable when its accessible label is shared with another component.
- Fixes the Market Day order handoff to open the nested Orders workspace,
  making the selected order and its handoff message immediately visible.
- Collects selector failures across every published desktop route in one
  catalog check, making multiple addressability defects visible together.

## September 4, 2026 — macOS shared-database startup

- Avoids a machine-hostname lookup that stalled H2 startup on macOS by binding
  its local shared-database listener to loopback before initialization. Desktop
  and AI processes retain concurrent access; explicit JVM overrides are preserved.
- Adds bounded native diagnostics and verifies that separate processes can
  read the same committed records while both remain open.

## September 4, 2026 — Preview storage and checksum integrity

- Adds an explicit manual [private draft preview path](PRIVATE_PREVIEW.md) for
  native packaging when Actions artifact storage is exhausted. Both platforms
  consume the same verified JAR; files remain in an unpublished private draft
  and existing archives are preserved.
- Excludes the output manifest from its own checksum list and verifies all
  payload checksums before retaining candidate or release assets. Executable
  regressions cover existing-manifest reruns and corrupted payloads.

## September 4, 2026 — Windows antivirus follow-up

- Packages preview MSI/DMG installers and the local MSI from their tested app
  images, preserving the launcher bytes that passed smoke checks.
- Requires the signed Windows MSI and both launchers to have the same verified
  publisher, while allowing short-lived signing certificates to rotate.
- Adds a Windows PowerShell 5.1-compatible metadata collector and a
  [Norton review guide](ANTIVIRUS_REVIEW.md). The collector never runs artifacts,
  reads farm records, restores quarantine, or uploads samples. The observed
  `IDP.Generic` alert still requires review and a clean-workstation acceptance check.
- Aligns integration-test harvest dates with the configured farm timezone and
  gives the closeout fixture an explicit timezone, so UTC runners exercise the
  same farm day even when Los Angeles is on the previous date.

## September 4, 2026 — Farmer entry and connected production

- Moves Harvest Log entry above history and gives Inventory larger paired fields,
  practical examples, persistent field guidance, and a clearer save area.
- Adds prominent **Log a harvest** and **Add a field note** actions on the phone
  screen. Field notes enter the shared Knowledge Log and use the existing device
  queue plus a durable server receipt; Flyway V21 permits that receipt type.
- Enables custom categories and selling units through the desktop harness,
  and adds explicit file-dialog approval/cancellation for real export workflows.
- Adds eight farm-data tools for reviewed planting schedules, task status and
  regeneration, recipe estimates, availability, and local provider handoffs.
  The headless bridge now exposes 33 tools; the desktop bridge exposes 21.
- Selects the standard JDK console provider in packaged AI launchers for
  redirected stdin/stdout. The final Windows JDK 17 native image passes
  readiness and protocol checks; earlier host process pauses are documented
  in the harness audit.
- Updates embedded Tomcat to 10.1.59 for the remaining dependency advisories
  and reduces retention of temporary CI inputs and preview downloads.
- Reworks the README around grower outcomes across seasons and adds a grounded
  idea backlog with explicit dependencies and acceptance criteria.

## Next — AI and future-device harness contract

- Adds a shared production-cost report across desktop, REST, and MCP. It joins
  planting-linked task labor/input actuals to the season planner's deterministic
  harvest allocation, calculates per-planting and season cost per expected and
  harvested stem, and keeps unlinked tasks, missing actuals, and unmatched
  harvests visible instead of hiding them in totals.
- Adds Flyway V20 and a season-owned loaded labor rate. Existing farms migrate
  at a zero rate, the desktop makes the assumption editable beside frost dates,
  REST accepts a compatible optional field, and the AI `get-production-costs`
  tool can use the saved value or an explicit scenario override. Revenue is not
  attributed to plantings until order data can prove that relationship.
- Adds structured harvest traceability across desktop, phone/tablet, REST, CSV,
  PDF, mapped migration, and MCP. Harvests now keep a unique normalized lot
  code, grade, marketable quantity, loss/culls, storage location, and gross
  quantity while only marketable quantity changes sellable inventory.
- Adds Flyway V19 with deterministic lot codes for existing harvest history,
  exact REST and AI lot lookup, and printable 4×6 PDF labels containing a real
  QR payload for the canonical lot identifier.
- Adds an authenticated responsive `/worker/` field app for phones and tablets.
  It lists overdue/due-today production tasks, records labor/cost/observations,
  and logs harvests through the same domain services used by desktop and AI.
- Adds a browser-persisted pending queue plus Flyway V15 durable worker-action
  receipts. Stable operation keys make retries exact across lost responses and
  application restarts; changed reuse returns a conflict instead of duplicating
  a harvest or task completion. Network failure, auth interruption, manual
  retry, pending-task locking, and farm-time-zone defaults are explicit.
- Adds end-to-end field workflow coverage proving inventory effects, task
  completion, exact replay/recovery, role enforcement, and immediate AI
  `farm-data` read-back. The responsive UI was exercised at a 390×844 viewport.
- Adds searchable mobile physical stock counts with a bounded field-only
  projection. Count writes compare the quantity reviewed on the device under a
  database row lock, so harvest, fulfillment, import, and concurrent count
  changes cannot be silently overwritten.
- Adds Flyway V16 count receipts, exact response-loss replay, typed stale-count
  conflicts, on-device review/retry/discard controls, and immediate canonical
  inventory read-back for connected AI clients.
- Adds a database-bounded **Due handoffs** queue to the mobile field app. Workers
  can review customer contact, delivery context, instructions, and complete
  product quantities before fulfilling a Confirmed Pickup, Delivery, or Market
  order and deducting its inventory.
- Adds Flyway V17 `COMPLETE_ORDER` receipts, operational review tokens, typed
  changed-load conflicts, same-order multi-worker reconciliation without a second
  stock deduction, per-entry pending-queue discard controls, and immediate AI
  order/inventory read-back.
- Adds optional production-task photos to the mobile completion form. JPEG, PNG,
  WebP, HEIC, and HEIF files up to 15 MiB are stored by SHA-256 beneath the farm
  data directory; metadata and bytes are validated on every read.
- Adds IndexedDB-backed offline photo bytes, ordered photo-before-completion
  replay, V18 `ADD_ATTACHMENT` receipts, exact restart recovery, explicit
  oversized-file responses, and regeneration protection for tasks that own
  evidence.
- Extends `farm-data` to ten readable/exportable types and 25 tools with
  attachment metadata paging, bounded verified content reads, and retry-safe AI
  uploads. Normal data-directory backup includes attachment bytes.

- Adds a dedicated `FlowersForeverAI` native launcher to Windows and macOS
  packages. It carries the packaged runtime/data behavior and, on Windows, the
  console subsystem required for redirected MCP stdin/stdout.
- Adds `--mcp-doctor`, a one-line machine-readable preflight over the real
  application graph. It verifies the resolved farm store, database and Flyway
  schema, installed plug-ins, all 33 farm tools, read-back/resume permission,
  and mutation permission; incomplete setup exits with actionable guidance and
  never emits the token.
- Adds child-process mapping/import/read-back/receipt coverage and requires both
  unsigned candidates and signed releases to exercise the native doctor and a
  real stdio protocol ping before an installer is retained.

- Adds a bundled, headless MCP stdio server over `HarnessGateway`. It supports
  the current stateless `2026-07-28` discovery/request model and the legacy
  `2025-11-25` initialize flow, lists only currently enabled tools, returns both
  structured and text-compatible results, and keeps stdout protocol-only.
- Adds `--mcp` runtime wiring with no Swing, Tomcat, or REST listener. The local
  process consumes the independent harness token from its environment without
  exposing credentials in model-visible tool arguments.
- Adds the headless `farm-data` AI plugin with detailed nested JSON schemas for
  inventory, customers, harvests, orders, farm knowledge, and production-task
  actuals. AI clients can discover the contract instead of hard-coding models.
- Adds bounded transactional master-data upserts, historical imports, live farm
  operations, and paged record reads. Historical harvests and fulfilled orders
  do not alter today's stock; live harvests retain normal inventory effects.
- Adds Flyway V14 durable import receipts using a stable source-system and
  per-row source key. Exact row retries survive request-ID changes and restarts,
  inventory/customer corrections retain the same target ID, and changed
  append-history rows return a conflict instead of duplicating farm history.
- Adds database-backed `list-import-receipts` paging so an AI can audit and
  resume legacy-app, Excel, CSV, or sheet migrations by source and record type.
- Replaces `list-records` in-memory slicing with true database-level paging for
  all ten readable/exportable record types. Arbitrary offsets remain compatible, every
  type has deterministic ordering, production-task date filters execute in the
  query, and `nextOffset` gives AI clients an unambiguous continuation value.
- Adds restart-stable complete farm-data exports. An AI can create an immutable
  all-type or selected-type snapshot, recover or exactly replay it by a stable
  caller key, and consume bounded JSON parts with manifest, size, record-count,
  and SHA-256 validation. A reused key with changed parameters returns a
  conflict instead of silently substituting a different dataset.
- Extends the discoverable `farm-data` catalog with growing templates, planting
  plans, and floral recipes. All three support canonical read-back, natural-key
  upserts, durable source-key correction, and transactional batches; recipes
  retain their nested ingredient quantities, pack sizes, costs, and notes.
- Adds a guided source-record intake workflow for AI-owned Excel, CSV, sheet,
  and previous-app readers. New tools suggest deterministic field mappings,
  preview canonical rows with row-level validation, coerce common spreadsheet
  values and nested JSON, create stable composite source keys, and atomically
  import only fully valid batches through the existing canonical actions.
- Makes the opt-in harness transport available in authenticated container
  deployments. Container credentials and the independent harness token are both
  required, and the headless plugin does not depend on Swing.
- Adds end-to-end JSON/REST tests for schema discovery, replay, migration,
  ongoing operations, record read-back, and container dual authentication.
- Extends provider-neutral discovery with application, workflow, device, and
  robot kinds; machine-readable capabilities; and typed observation signals.
- Adds AI/MCP-friendly effect annotations for read-only, destructive,
  idempotent, open-world, physical-world, and freshness behavior.
- Adds an independent default-denied `PHYSICAL` tier. Physical commands need
  the mutation, destructive, and physical operator gates plus an expiry within
  30 seconds; exact retries remain replay-safe.
- Validates sensor JSON types, strips declared sensitive telemetry unless the
  sensitive-read gate is enabled, rejects undeclared device/robot metadata, and
  rejects snapshot/plugin identity drift.
- Documents the boundary clearly: hardware watchdogs, interlocks, collision
  limits, loss-of-link behavior, and emergency stops stay inside the robot.
- Adds a disabled-by-default `tesla-optimus` high-level plugin, replaceable
  `OptimusRobotAdapter`, and deterministic simulator. Tasks use a reviewed
  stage/token/execute flow; no undocumented Tesla transport or raw motion API is
  assumed.
- Hardens reviewed robot plans with a five-minute maximum lifetime, exact
  non-replacement semantics, fresh/ready state checks, derived operational
  telemetry, and fail-closed consumption before the adapter execution boundary.
- Upgrades the Optimus boundary to an asynchronous operation contract: execution
  returns a stable command ID and `RUNNING`, while typed status, progress, and
  detail are observable through completion, failure, cancellation, or stop.
- Adds exact active-command cancellation as a fresh, expiring physical action,
  distinct from staged-draft cancellation and the independent emergency stop.
- Requires stable adapter identity. Hardware mode needs a configured matching
  robot ID and fails closed on startup mismatch or later identity drift;
  simulator mode is explicit in discovery telemetry.
- Adds a V8 append-only robot-operation journal plus read-only `get-operation`
  and bounded `list-operations` tools. Accepted commands and lifecycle changes
  survive replay eviction and application restarts; missing nonterminal adapter
  state becomes `UNKNOWN` instead of being guessed complete.
- Upgrades the Optimus plugin to 0.5 with Flyway V9 durable, exclusive
  robot-scoped control leases. Lease capabilities expire after 15–300 seconds,
  rotate on renewal, and are stored only as SHA-256 digests. All normal staging
  and physical control requires the lease; emergency stop remains deliberately
  lease-independent.
- Removes the AI-facing lease-holder fingerprint. Its unsalted truncated hash
  was guessable for low-entropy labels; holder labels now remain only in the
  local audit record.
- Rejects missing adapter timestamps for robot state, staged plans, and command
  receipts, and rejects future-dated receipts instead of manufacturing fresh
  local timestamps.
- Cancels a known adapter-side staged plan before starting the safe-pose routine,
  keeping the harness and robot draft state aligned.
- Upgrades the Optimus plugin to 0.7 with a cursor-paginated
  `list-operation-events` feed for AI reconnect and long-running job tracking.
  Pages preserve append order, expose `nextCursor`/`hasMore`, and use string
  cursors to avoid JavaScript integer rounding. The process-local journal now
  mirrors production append, de-duplication, and recency behavior.
- Upgrades the Optimus plugin to 0.8 with Flyway V10 staged-task resume storage.
  Plugin recreation no longer loses the active plan's lease and expiry metadata;
  only the plan-token digest is stored, and execution removes the record before
  adapter dispatch. A new active controller can explicitly cancel staged work
  left by an expired lease.
- Upgrades the Optimus plugin to 0.9 with staged-state reconciliation. A new
  `staged-plan-adapter-confirmed` observation distinguishes a live adapter-held
  plan from a durable resume record. If an adapter restarts without its plan,
  execution clears the stale record without dispatching and the active lease can
  stage the task again; same-adapter restart resume remains supported.
- Upgrades the Optimus plugin to 0.10 with unified staged-plan cleanup. Explicit
  cancellation, lease release, plan expiry, restaging, and return-to-pose no
  longer send redundant cancellation to an idle restarted adapter. If the
  adapter still reports `STAGED`, cancellation completes before durable metadata
  is removed so transport failures remain retryable.
- Upgrades the Optimus plugin to 0.11 with exact staged-plan identity. Adapter
  state carries an internal SHA-256 fingerprint of its opaque plan token;
  `staged-plan-adapter-confirmed` and execution now require it to match the
  durable digest. Missing or unrelated fingerprints cannot dispatch, and
  authorized restaging reconciles adapter-only staged work.
- Upgrades the Optimus plugin to 0.12 with verified staging acknowledgement. A
  plan token is persisted and returned only after a fresh adapter observation
  reports `STAGED` with that exact fingerprint. Idle, missing, mismatched, stale,
  future-dated, or failed acknowledgement cancels the adapter draft and returns
  the original diagnostic without creating resumable metadata.
- Upgrades the Optimus plugin to 0.13 with complete staging rollback. Ambiguous
  adapter exceptions, invalid returned plans, lease expiry or rotation during
  staging, acknowledgement failure, and durable-store failure all attempt to
  cancel the adapter draft. A cleanup failure is retained as a suppressed
  diagnostic without replacing the primary staging error.
- Upgrades the Optimus plugin to 0.14 with durable commit verification. The
  staged-plan write result and a fresh read-back must both match the exact robot,
  adapter mode, lease, and plan-token digest before the token is returned.
  Missing, malformed, mismatched, or unreadable commits roll back the adapter,
  then clear the stored record after cancellation succeeds; sub-millisecond
  database timestamp normalization is supported.
- Upgrades the Optimus plugin to 0.15 with Flyway V11 durable execution intent.
  The original request ID is committed before adapter dispatch and retained
  across ambiguous failures. An exact retry can safely redeliver a still-staged
  idempotent plan or recover the matching running command without redispatch;
  conflicting request IDs cannot take over, and intent clears only after the
  command receipt reaches the operation journal.
- Upgrades the Optimus plugin to 0.16 with Flyway V12 durable exact-request
  receipts. If a successful execution response is lost, the identical request
  can recover the same command after a full application restart—even after its
  original deadline—without redispatch. Changed-action and changed-argument
  collisions are rejected, and only a SHA-256 command fingerprint is persisted;
  plan and lease tokens remain plaintext-free.
- Upgrades the Optimus plugin to 0.17 with outage-tolerant durable replay. After
  startup identity is established, an accepted exact execution can still return
  while the owner API is unavailable. It is marked `UNKNOWN`/`UNCONFIRMED`, the
  snapshot reports `UNAVAILABLE` without invented sensor values, and no physical
  command is redispatched. New physical commands continue to require live state.
- Upgrades the Optimus plugin to 0.18 with read-resilient reconciliation. An
  unavailable snapshot now renders last-known nonterminal work as `UNKNOWN`
  immediately. If the accepted receipt is readable but its `UNKNOWN` append
  fails, exact replay still succeeds with `reconciliationPersisted=false`, keeps
  the receipt for a later retry, and never redispatches the physical command.
- Upgrades the Optimus plugin to 0.19 with live read-resilient reconciliation.
  Exact adapter-confirmed state and its fresh gateway snapshot remain available
  when secondary lifecycle appends fail, with
  `reconciliationPersisted=false`. The original accepted receipt is still a
  mandatory durable write, and the physical command is never redispatched.
- Upgrades the Optimus plugin to 0.20 with read-tool outage continuity.
  `get-operation`, `list-operations`, and the cursor event feed remain readable
  when optional lifecycle enrichment cannot be appended, disclose persistence,
  and never publish a cursor for unwritten state.
- Upgrades the Optimus plugin to 0.21 with offline durable operation reads. The
  three operation-query tools fall back to journal history during owner-API
  outages, expose `adapterAvailable=false`, preserve terminal results, and mark
  nonterminal work `UNKNOWN` without dispatching another robot command.
- Upgrades the Optimus plugin to 0.22 with monotonic lifecycle reconciliation.
  Transient and JPA journals now ignore delayed samples, prevent progress and
  phase regression, retain terminal results across stale reconnects, and preserve
  partial progress for cancellation, failure, and stop receipts.

## Next — Production Studio

- Adds farm-owned annual frost anchors and reusable crop templates for spacing,
  rows, sow/transplant offsets, maturity, succession, germination, yield, and
  standard-work offsets.
- Builds reviewable frost-aware succession drafts, then commits normal Season
  Planner blocks and dependent soil-prep, sow, transplant, support,
  harvest-prep, and first-harvest tasks.
- Adds reusable bouquet, event, subscription, and wholesale design recipes.
- Reconciles recipe stems with planned weekly crops and live inventory, applies
  waste, rounds only shortages to full packs, and exposes leftovers and supply
  gaps.
- Calculates material, labor, overhead, revenue, profit, margin, and markup in
  one estimate.
- Adds provider-neutral contract, payment, and marketplace handoff records that
  retain status and references without storing card data, tokens, signatures,
  or legal contract bodies.
- Adds a versioned H2 V6 migration, REST routes under
  `/api/production-studio`, six recursive desktop/harness routes, and focused
  service and schema coverage.
- Polishes the desktop workflow with automatic frost reload, selection-preserving
  refreshes, double-click editing, persistent in-page feedback, and an explicit
  impact review before planting blocks and generated work are committed.
- Adds open/done/skipped work filters, overdue/today timing, skip and reopen
  actions, per-design break-even economics, duplicate-ingredient protection, and
  sellable availability that excludes completed plans and empty stock rows.
- Adds a V7 integrity migration and service-level conflict handling: exact
  planting duplicates are blocked before mutation, frost/template/plan/task rows
  are locked during dependent updates, regeneration verifies template ownership,
  and in-use templates retain their reproducible history.
- Binds every planting commit to the exact reviewed draft with a deterministic
  preview fingerprint; desktop confirmation carries it automatically and REST
  publishes an `ETag` accepted through `If-Match` or `previewFingerprint`.
- Hardens external handoffs with valid related-record checks, positive payment
  amounts, three-letter currency validation, required references for sent/final
  states, and database-enforced unique provider references and workflow states.
- Polishes Production Studio state safety: unsaved seasons cannot retain stale
  frost dates, changed planting inputs visibly invalidate old previews, task and
  library actions follow selection and role, regeneration requires an impact
  confirmation, ingredient errors identify their line, and handoff statuses are
  scoped to contract, payment, or marketplace workflows.
- Adds planting-linked field-work actuals. Production tasks now retain bounded
  crew minutes, input cost, field observation, and completion time through the
  desktop and `PUT /api/production-studio/tasks/{id}/actuals`.
- Adds Flyway V13 for the new actual fields. Completion is one locked write;
  reopened tasks retain their farm evidence, and regeneration preserves any task
  containing recorded actuals instead of silently replacing it.

## Next — Visual Season & Bed Planner foundation

- Refreshes the complete desktop visual system with botanical color washes,
  richer semantic cards, colorful sections, clearer action hierarchy, tinted
  tables, and coordinated light/dark theme colors.
- Adds a persistent Season Planner with fast planting-block entry, edit, and
  removal while preserving existing harvest records.
- Maps sowing, growing, and expected harvest stages on a full-year bed timeline.
- Summarizes active blocks, occupied beds, total bed feet, expected stems, and
  the season's harvest window.
- Adds sortable plan records, viewer-safe permissions, demo planting data,
  a versioned database migration, and visual regression coverage.
- Connects stem-unit harvests to one exact crop-and-bed block, preventing a
  harvest from being counted twice when expected windows overlap.
- Shows actual stems, full-plan completion, expected-to-date pace, bed overlap
  warnings, week-long harvest gaps, and harvest records that need matching.
- Adds a scrollable weekly harvest-pace chart with actual stem bars, a planned
  production line, a current-week marker, and an expected-by-today summary.
- Adds **Plan succession** to turn a selected planting block into a reviewable
  unsaved draft with its dates shifted forward 14 days and farm data preserved.
- Adds a colorful **Plan insights** view that names the exact bed overlaps,
  harvest gaps, pace shortfalls, and unmatched harvest totals, with actions that
  jump directly to the relevant planting records or Harvest Log.

## 1.2.0 — Farm Knowledge Log preview

- Adds a desktop Knowledge Log for observations, problems, opportunities,
  actions, action owners, results, follow-up dates, confidence, and reusable tags.
- Adds grounded plain-language search that recognizes ordinary farm terms and
  month names without generating diagnoses or advice.
- Lets a lesson optionally link to a saved harvest, inventory item, order, or
  customer while preserving the lesson if the operational record is removed.
- Adds role-aware `/api/knowledge` CRUD/search routes and `/api/knowledge/tags`.
- Adds Flyway V3 so fresh and existing H2 pilot databases receive the knowledge
  tables without replacing existing farm records.
- Extends the demo profile with three realistic lessons for mildew, harvest
  timing, and market demand.

## 1.1.0 — Private-pilot hardening

The 1.1 hardening work remains part of the 1.2 private preview. No current build
is approved for customer distribution. The previous 1.0.19 artifacts are retired.

## Release safety

- One fully tested executable JAR feeds both Windows and macOS installers.
- Pull requests that change release packaging build both installers, launch the
  packaged runtime on clean Windows and macOS runners, require a successful
  database-startup marker, and preserve a private candidate bundle without
  creating a release.
- Tags, POM versions, JAR names, and installer versions must match exactly.
- A release is created only after tests, SBOM, license policy, and both native
  packaging jobs pass.
- SHA-256 values cover every installer, the JAR, SBOM, and third-party notice.
- Weka and its GPLv3 distribution issue were removed; transparent ordinary
  least-squares forecasting is implemented directly in FlowersForever.
- Runtime third-party notices are generated, bundled, and attached to releases.
- An unsigned Windows app image triggered Norton's `IDP.Generic` heuristic during
  local validation. The runtime completed its controlled startup and shutdown,
  but antivirus blocks must not be bypassed. Code signing is now a pilot release
  gate, and suspected false positives should be reviewed by the security vendor.
- Tagged publishing now depends on a valid Artifact Signing signature for both
  the Windows launcher and MSI plus a Developer ID-signed, Apple-notarized,
  stapled, Gatekeeper-approved DMG. Enrollment credentials stay in the protected
  `release-signing` environment; pull requests never receive them.

## Data and security

- H2 schemas now use versioned Flyway migrations followed by Hibernate
  validation. Existing 1.0 databases are baselined and upgraded without
  replacing their farm records.
- A migration acceptance test verifies both fresh installation and an in-place
  legacy order upgrade.
- Credential-shaped values are redacted from API and connector logs.
- Unexpected API failures include a support incident ID without exposing the
  underlying secret or exception detail.
- Authentication no longer compiles a demo password fallback into production.

## Existing production workflow

- Stable packaged data under `%LOCALAPPDATA%\FlowersForever\data`
- Checksum-validated backup and recoverable restore
- Harvest, inventory, CRM, order, market-day, reports, briefing, and closeout
- Role-aware desktop and REST controls
- Offline-first connector mirrors and guarded live connector modes

Review [private pilot access](PILOT_ACCESS.md), [deployment](DEPLOYMENT.md),
[backup and restore](BACKUP_RESTORE.md), and [known limitations](KNOWN_LIMITATIONS.md)
before approving a pilot workstation.
