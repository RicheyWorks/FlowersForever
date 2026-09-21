# Product roadmap: keep the lessons in the ground

## Product promise

**Keep the lessons in the ground. Less waste. More control.**

FlowersForever already connects harvests, inventory, customers, orders, market
packing, reports, and closeouts. The next differentiator is preserving the
practical knowledge behind those records so a farm does not relearn the same
expensive lesson every season—or lose it when an experienced worker leaves.

### September 2026 priority: make daily entry earn its place

The current pass prioritizes visible harvest entry, larger and clearer inventory
fields, custom categories and units, and standalone field notes from a phone.
The harness follows the same create workflows and now exposes production
preview/commit, task status and regeneration, recipe estimates, availability,
and local provider handoffs without requiring an open desktop window.

Next product choices should be tested against real entry and retrieval tasks.
See [eight ideas for the next season](FUTURE_IDEAS.md) for grower scenarios,
dependencies, and acceptance criteria. These ideas are not delivery promises.

## 1.1 — Safe private pilots

- Proprietary distribution and third-party license compliance
- Deterministic, gated releases with matching versions and checksums
- Versioned database migrations and verified backup/restore
- Secret-safe logs and support incident IDs
- Guided Windows-first onboarding for a small group of Washington farms

Success means a pilot farm can install, complete a normal farm day, close the
app, reopen it, upgrade it, and restore a backup without losing its records.

## 1.2 — Farm Knowledge Log

**Private-preview slice implemented.** Growers can record, edit, remove, tag,
follow up, link, and search farm lessons from the desktop or REST API. Search
recognizes ordinary words and month names while keeping the result grounded in
records the farm entered; it does not invent or summarize advice.

The first knowledge release should let a grower record:

- what happened;
- the crop, variety, bed or field, and production stage;
- the problem or opportunity observed;
- the action taken and who took it;
- the result, follow-up date, and confidence in the lesson; and
- reusable tags such as mildew, irrigation, timing, pests, harvest, and market.

Knowledge entries should link to existing harvests, inventory items, orders,
customers, and dates. Search must answer practical questions such as “What
worked for mildew on this rose last August?” without requiring technical query
language.

## 1.3 — Visual season and bed planning

**Planning and actual-performance slice implemented.** Growers can add a planting block once and see its
sowing, growing, and harvest stages across a full-year timeline. The same saved
record tracks crop, variety, bed or field, bed feet, expected stems, status, and
notes, with summary cards for active blocks, occupied beds, planned yield, and
the full harvest window. Recorded stem harvests are allocated to one exact
crop-and-bed block so overlapping successions cannot inflate actual production.
The planner shows actual progress, pace against the expected amount to date,
bed overlaps, week-long harvest gaps, and harvests that need cleaner matching.
Its weekly view distributes every block's expected yield across its harvest
window and compares that plan line with matched actual stem harvests. A selected
block can also become a reviewable next-succession draft with its crop, bed,
capacity, and notes carried forward and every date shifted together. A guided
insights view turns overlap, harvest-gap, behind-pace, and matching counts into
color-coded explanations with direct routes to the records that need review.
Plan-generated work can now retain actual crew minutes, input cost, a field
observation, and completion time on the exact planting-linked task. Reopening
work keeps that evidence, and regeneration will not replace a task that contains
recorded actuals. Those actuals now roll into planting and season production-cost
reports using a persisted loaded labor rate and the same one-to-one harvest
allocation as the season dashboard. Cost per expected and matched harvested stem
is shared by desktop, REST, and AI; unlinked work and unmatched harvests stay
visible, and product sales are not falsely attributed to a bed.

Next planning increments:

- Extend the implemented week-by-week planned-versus-actual chart with demand-aware
  forward projections.
- Add spatial bed/row planning and rotation-family history.
- Add a provable order-line-to-crop/planting allocation before presenting
  production revenue or planting profit.

## 1.4 — Seasonal learning

- Compare recurring problems and successful actions by crop, bed, and season.
- Turn selected lessons into farm playbooks and crew-ready checklists.
- Add reminders when a past lesson becomes relevant again.
- Export a human-readable seasonal knowledge report that belongs to the farm.

## 1.5 — AI data bridge

**Core migration and operations slice implemented.** A provider-neutral
`farm-data` plugin now publishes nested canonical schemas, imports customer and
inventory master data, imports historical harvests and orders without corrupting
current stock, records live harvests and orders with normal stock behavior,
writes farm knowledge and production-task actuals, migrates growing templates,
planting plans, and nested floral recipes, and pages records back for
verification. It works without a desktop window, including an authenticated
headless deployment, so a farm's existing AI can map Excel or prior-app data and
continue serving phone, tablet, and computer workflows. Stable source-system
and row keys now create database-backed migration receipts: exact retries
survive restarts, master-data corrections retain their target IDs, and changed
historical rows surface as explicit conflicts.
The bridge now also suggests mappings for unfamiliar source columns, previews
canonical rows without writing, coerces common spreadsheet values and nested
JSON cells, reports row-level issues, builds stable composite source keys, and
atomically commits a fully valid batch. A bundled MCP stdio adapter now exposes
the effective tool catalog and structured results to both current stateless and
legacy-initialize MCP clients without putting harness credentials in tool
arguments. Canonical read-back now uses bounded database queries for ten readable
record types instead of loading full tables before slicing; deterministic order,
totals, `hasMore`, and `nextOffset` let an AI traverse large farms reliably.
The bridge can also freeze all ten types, or a requested subset, into immutable
checksummed chunks. A stable caller key recovers or exactly replays the same
snapshot across restarts, while a changed request produces an explicit conflict.
This gives handoffs and long-running AI jobs a finite, verifiable dataset rather
than a moving sequence of live pages.

**Core mobile field slice implemented.** The authenticated `/worker/` web app
uses the same production and harvest services as desktop, REST, and MCP. It
lists overdue/due-today tasks, records task actuals, and captures harvests on
phone or tablet. Device-persisted operation keys, transactional V15 receipts,
exact replay, restart recovery, and visible retry state prevent a lost response
or ordinary reconnect from duplicating work. AI clients see the resulting
canonical records without a separate synchronization layer.

**Concurrency-safe mobile stock counting implemented.** Workers can search the
scoped count view without exposing cost or notes, review current on-hand stock,
and queue a physical count from a phone. The V16 receipt action commits the
count and replay result together. A row lock plus reviewed-quantity comparison
rejects a stale count instead of erasing a harvest, fulfillment, import, or
another worker's change. The entered count stays on-device for review/retry,
and the canonical inventory update is immediately readable by an AI client.

**Retry-safe mobile order handoff implemented.** The field screen now lists a
database-bounded queue of Confirmed Pickup, Delivery, and Market orders due
through the work date, including customer contact, delivery context, instructions,
and complete operational product quantities without pricing. A V17 action receipt
commits with the locked fulfillment and inventory deduction. A review token blocks
changed customer/load data, a second worker's unchanged completion reconciles
without another deduction, conflicted entries remain reviewable on-device, and AI
clients immediately read the resulting Fulfilled order and stock quantities.

**Durable mobile field evidence implemented.** A worker can take or choose an
optional production-task photo before completion. The browser stores pending
image bytes in IndexedDB and queues the photo before the task update, while V18
stores immutable metadata and content-addressed bytes in the farm data folder.
Exact operation-key replay survives restart, image signatures and SHA-256 are
verified, oversized files return a typed response, task regeneration retains an
open task that already owns evidence, and the AI bridge can page metadata, read
bounded content chunks, or upload the same retry-safe record type.

**Harvest lot traceability implemented.** Desktop, phone/tablet, REST, mapped
imports, and AI operations now write one canonical harvest shape with a unique
lot code, grade, marketable quantity, loss/culls, and storage location. Existing
history receives deterministic codes in Flyway V19. Inventory changes only by
marketable quantity; gross yield and loss remain reportable. Exact REST/MCP lot
lookup, expanded CSV/PDF output, and printable 4×6 QR labels complete the path
from field capture to cooler identification and AI read-back.

**Packaged AI connection slice implemented.** Windows and macOS packages now
ship a dedicated `FlowersForeverAI` stdio launcher using the packaged runtime
and the same local store as the desktop. A one-shot `--mcp-doctor` verifies the
resolved data directory, H2/Flyway state, farm plug-in, all 38 farm tools, and
the permissions needed for read-back/resume and writes. Child-process coverage
performs mapping, durable import, canonical read-back, and receipt lookup against
one file-backed store, plus MCP resource reads and inventory-count receipt replay
between separate JVMs. Release jobs also exercise the native doctor and a real
redirected protocol ping before retaining either installer.

Next integration increments:

- Validate client-specific MCP configuration and reconnect behavior against each
  AI client selected by pilot farms; the native launcher/process path is now gated.

## Later — Carefully earned assistance

Automated summaries or recommendations come only after farms have enough clean,
consented data. Any future AI-assisted feature must show its sources, separate
recorded facts from suggestions, protect farm ownership of data, and allow a
grower to correct or reject its conclusion.

## Pilot learning plan

Start with two or three independent Washington cut-flower farms. Observe their
actual workflow, measure repeated entry and time saved, and prioritize the
smallest knowledge feature they will use every week. The working commercial
hypothesis—setup support plus a monthly farm subscription—should be validated
with pilots before public pricing is promised.
