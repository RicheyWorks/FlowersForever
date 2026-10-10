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

## 1.6 — Farm types beyond cut flowers

**Farm setup and workspace navigation implemented in this local preview.** An
operation can identify its state, primary and additional enterprises,
specialties, cut-flower offerings, and a desktop workspace: **Rose & events**,
**Wildflower farm**, **Flower shop**, **Cannabis farm**, **Potato farm**,
**Apple orchard**, **Berry farm**, **Nursery farm**, **Livestock farm**,
**Poultry & eggs**, **Dairy farm**, **Vineyard farm**, **Hay & forage**, **Hops farm**, **Grains farm**, **Seed farm**, **Tree nut orchard**, **Vegetable farm**, **Aquaculture farm**, **Christmas tree farm**, **Apiary farm**, **Mushroom farm**, **Cranberry farm**, **Pumpkin patch**, or
**Custom farm**.
The **Combine farm types…** action saves two or more selected farm types and a
main type separately from the enterprise list. The combined overview opens first;
the main type sets context and shared defaults. It brings each selected
specialty tool forward while showing shared records once. A potato, cannabis,
and apple operation can keep independent Field plans, Cultivation, and Orchard
blocks in the same navigation. Adding or removing a type changes tool visibility
without deleting saved plans. The selection preserves one set of farm records.
Custom farm can join the mix for unlisted work and can be the main type;
it uses the shared Harvest Log, Inventory, and customer records without adding
a separate production plan. Saved specialty crop names appear in its overview.
A first cannabis planning notebook records cultivation batches,
areas, stages, and plant counts; products and orders use the existing generic
Inventory and Customers & Orders tools.
Saved cannabis batches can move directly to a planning stage by dragging a row
or by selecting and clicking a stage; an open draft must be resolved first.
Initial potato field and apple orchard
block notebooks record named areas, optional variety and acreage, dates, stage,
and notes. A separate berry notebook records named blocks and crops, optional
cultivar, acreage and planting date, first and last planned pick, intended fresh,
processing, U-pick, mixed, or undecided channel, stage, and notes. These plans do
not create harvest lots, stock, or order links. A nursery growing-batch notebook
records living plants, plant form, area, start and planned ready dates, optional
starting count, intended sales channel, stage, and notes. It does not create
stock or orders or reconcile plant losses. Grading, packout, bin and cold
storage records, U-pick booking, weight conversion, produce traceability,
nursery licensing, inspections, and shipment certification remain future work.
Saved nursery batches can move directly to a planning stage by dragging a row
to **Move by stage**, or by selecting and clicking a stage; a draft must be
resolved first. A livestock notebook saves editable herd groups with species,
headcount, location, purpose, next check date, and notes. Each is a current
planning snapshot, without individual animals, health or breeding events,
milk or egg production, stock or sales links, or regulatory traceability.
The Poultry & eggs workspace saves dated collection entries by flock or coop,
with collected and unusable counts and a calculated usable count. Collections
can be edited and repeated for a flock on the same day. They do not create egg
inventory or sales orders, grade or pack eggs, or track flock health.
The Dairy farm workspace saves editable dated milk collections by herd or group
and milking session, with an amount and explicit lb, gal, or L unit. Multiple
sessions can be saved for one group on a day. The entered unit is retained;
there is no unit conversion. These entries do not change Herd groups, create
milk inventory or sales orders, track quality or processor delivery, or serve
as food-safety or compliance records.
The Vineyard farm workspace saves editable seasonal grape block plans with a
block name and variety, optional location and acreage, planned harvest window,
intended destination, planning stage, and notes. These plans do not record
picked grape weights, create harvest lots, stock, or orders, or track grape
quality, crush, wine production, contracts, or regulatory events.
The Hay & forage workspace saves editable field cutting plans with forage
crop, cutting number, optional acreage, planned cut and bale dates, stage,
and notes. A saved cutting can move to a planning stage by drag or click after
any form draft is resolved. Plans do not measure actual yield, count or weigh
bales, calculate feed rations, grade forage, update stock or orders, or serve
as compliance records. A dairy or livestock farm can include the cutting
notebook in its mix without linking those records automatically.
The Hops farm workspace saves editable yard and variety plans with optional
acreage, a planned stringing date and harvest window, stage, and notes. A saved
yard can start a next-season draft with its dates cleared, or move to a planning
stage by drag or click after any form draft is resolved. Plans do not measure
actual yield, record drying or pellets, post stock, fulfill contracts, link
orders, or serve as regulatory records. A mixed operation can include Hop yards
beside its other specialty tools without linking those records automatically.
The Grains farm workspace saves editable field and crop plans with optional
variety, acres, previous crop, planned seeding date, planned harvest end, stage,
and notes; a planned harvest start is required. **Plan next rotation** starts a
new draft for the saved field and acres, moves its crop into **Previous crop**,
and clears the next crop, variety, dates, and notes. A saved field can move to a
planning stage by drag or click after any form draft is resolved. These plans
do not record actual yield, grade, moisture, storage, contracts, stock, or
orders. Mixed farms can include Grain fields beside their other tools without
linking those records automatically.
The Seed farm workspace saves editable seed-crop field plans with optional
variety, acres, previous crop, planned sowing and flowering dates, planned
harvest end, intended use, manual isolation notes, stage, and general notes; a
planned harvest start is required. **Plan next season** starts a draft with the
field, crop, variety, and acres carried forward and planned dates cleared. A
saved field can move to a planning stage by drag or click after any form draft
is resolved. These plans do not calculate isolation or certify seed. They do
not record actual yield, cleaned seed lots, tests, stock, or order links. Mixed
farms can include Seed fields beside their other tools without linking those
records automatically.
The Tree nut orchard workspace saves editable nut block plans with a required
block name, nut crop, and planned harvest start; optional variety, pollinizer
variety, acres, tree count, planned bloom start, floor preparation date,
harvest end, intended destination, stage, and notes. **Plan next season**
starts a draft with the saved block, crop, variety, pollinizer, acres, tree
count, and destination carried forward, while dates and notes are cleared.
Saved blocks can move to a planning stage by drag or click after the form draft
is resolved. These plans do not check pollinizer compatibility or record
actual yield, cleaning, drying, grading, stock, or order links. Mixed farms can
include Nut blocks beside their other tools without connecting those records
automatically.
The Vegetable farm workspace saves editable field or bed succession plans with
a crop, succession number, planned harvest start, and optional variety,
previous crop, acres, sowing and transplant dates, planned harvest end,
intended channel, stage, and notes. **Plan next succession** carries the
saved field, crop, variety, acres, previous-crop note, and channel into a new
draft, increments the succession number, and clears dates and notes. A saved
planting can move to a planning stage by drag or click after its form draft is
resolved. Dates are grower-entered; the notebook does not predict harvest
readiness, record actual yield, grade, wash or pack produce, manage storage,
certify food safety, or link stock and orders. Mixed farms can include
Vegetable plantings beside their other tools without automatic links.
The Aquaculture farm workspace saves editable aquatic cohort plans with a
name, species, growing site, and planned harvest start; optional culture
method, planned stocking date, harvest end, intended destination, stage, and
notes. Saved cohorts can move to a planning stage by drag or click after an
open draft is resolved. **Plan next cohort** starts a draft with species,
growing site, culture method, and intended destination carried forward;
cohort name, dates, and notes cleared; and stage reset to Planned. These
plans do not check licenses, site approval, area-open status, water quality,
or harvest authorization, and do not issue tags, provide regulatory
traceability, record actual harvest, or link stock and orders. Mixed farms can
include Aquatic cohorts beside their other tools without automatic links.
The Christmas tree farm workspace saves editable Tree blocks with a required
block name, tree species, and target harvest year; optional planting year,
acres, planted tree count, target height, next shearing date, planned cut
window, intended channel, stage, and notes. **Plan replacement planting**
starts a draft with block, species, acres, target height, and channel carried
forward, while years, count, dates, and notes clear and stage resets to
Planned. Saved blocks can move to a planning stage by drag or click after an
open draft is resolved. These plans do not record actual cut counts, post
stock or orders, manage choose-and-cut bookings, or verify licensing,
inspection, or export clearance. Mixed farms can include Tree blocks without
automatic links to their other records.
The Apiary farm workspace saves editable, dated Hive checks with a required
apiary/site, hive label, and check date; optional purpose, beekeeper-entered
status, next check date, observations, and actions. **Start next check** carries
the site, hive, and purpose into a new draft while clearing dates, status,
observations, and actions. A saved check can move to a status by drag or click
after its open draft is resolved. Checks do not diagnose or treat colonies,
verify registration, manage pollination contracts, record honey extraction,
or link hive records to stock and sales. Mixed farms can include Hive checks
beside orchard, berry, and other tools without automatic links.
The Mushroom farm workspace saves editable Mushroom batches with a required
batch name and species; optional growing method, substrate, growing area,
planned inoculation date, planned fruiting window, intended channel, stage,
and notes. **Plan next batch** carries species, growing method, substrate,
growing area, and intended channel into a new draft while clearing its name,
dates, and notes and resetting stage to Planned.
Saved batches can move to a planning stage by drag or click after an open
draft is resolved. These plans do not forecast fruiting, record actual yield
or harvest, control growing conditions, certify food safety, or link stock
and orders. Mixed farms can include Mushroom batches beside other tools
without automatic links.
The Cranberry farm workspace saves editable Cranberry beds with a required bed
name; optional variety, acres, planned Wet, Dry, Mixed, or Undecided harvest
method, planned harvest dates, intended channel, stage, and notes. **Plan next
season** carries bed, variety, acres, method, and channel into a new draft;
dates and notes clear and stage returns to Planned. Saved beds can move through
planning stages by drag or click after an open draft is resolved. The board
does not control water, record actual harvest, grade fruit, provide food-safety
traceability, or link stock and orders. Mixed farms can include Cranberry beds
beside other tools without automatic links. [OSU Extension describes perennial
beds and wet versus dry harvest](https://extension.oregonstate.edu/catalog/pub/em-9212-integrated-pest-management-strategic-plan-oregon-washington-cranberries).
The Pumpkin patch workspace saves editable Pumpkin patches with a required
patch name; optional variety, acres, planned planting and picking dates,
planned pick-your-own, farm stand, wholesale, mixed, or undecided sales mode,
visitor-area notes, stage, and general notes. **Plan next season** carries
patch, variety, acres, mode, and visitor notes into a new draft; all dates
and general notes clear and stage returns to Planned. Saved patches can move
through planning stages by drag or click after an open draft is resolved.
The board does not issue tickets or bookings, verify visitor arrangements,
record actual harvest, or link stock and sales. Mixed farms can include
Pumpkin patches beside other tools without automatic links. [OSU Extension
lists pumpkin patches among agritourism activities](https://extension.oregonstate.edu/catalog/em-9909-adding-agritourism-developing-your-plan-working-your-county).
The cut-flower workflow remains the only deep production specialization.
A selected workspace does not convert
stem plans, flower recipes, or the Kitsap irrigation defaults into another
crop's production model. See [farm types and scope](FARM_TYPES.md).

Mixed farms can edit supported specialty plans, named crops, and products as
their operations change. A combination does not supply arbitrary custom fields,
automatic cross-crop stock movement, or links from plans to harvests and orders.

Build each specialty on shared farm identity, customers, orders, inventory,
harvest lots, and knowledge, with explicit units and a production model suited
to the enterprise:

1. **Rose and other cut-flower farms:** retain variety and bed plans, stem
   harvests, bouquet recipes, wedding services, and sales by the stem or design.
   Test mixed retail, wholesale, and event orders against one stock ledger.
2. **Wildflower farms and flower shops:** validate how wildflower growers plan,
   harvest, and sell named crops, and how shops receive stock, assemble designs,
   and fulfill orders without a growing plan. Keep the shop's retail needs
   distinct from the grower's production needs.
3. **Central Washington potato farms:** validate the initial field planning
   notebook, then add seed and planting records, harvest weight, grade, storage
   loss, packout, and sales by weight or pack.
   Planned yield and actual harvest must use the same selected unit, never a
   stem conversion.
4. **Central Washington apple orchards:** validate the initial block planning
   notebook, then add harvest bins, packout and grade, cold storage, and sales
   by the bin, case, or weight.
   Keep fresh and processed product forms distinct.
5. **Washington and Oregon berry farms:** validate the first block and picking-window
   plans across crops such as blueberries, raspberries, and blackberries. Study
   actual picking, fresh versus processing outlets, U-pick operations, and
   packout before linking plans to harvests or claiming those workflows.
6. **Cannabis cultivators:** validate the first batch/area/stage/plant-count
   planning notebook with licensed operators. Add harvest and finished-product
   inventory, product forms, and sales reconciliation as distinct increments;
   current generic orders do not reconcile to cultivation batches. State
   license, tracking, reporting, and transfer integrations require separate
   jurisdiction-specific design and verification before any compliance claim.
7. **Washington and Oregon nurseries:** validate growing-batch plans for live
   starts, potted plants, bare-root plants, bulbs, and cuttings with growers.
   Design plant count adjustments, purchasing and stock conversion separately;
   verify licensing, inspection, quarantine, and shipment needs before making
   any compliance claim.
8. **Washington and Oregon livestock farms:** validate herd group planning for
   cattle, sheep, and goats with growers. Design count history, individual
   animal records, health and breeding events, product sales, and traceability
   as separate workflows before presenting them as supported.
9. **Washington and Oregon poultry farms:** validate dated egg collection entries
   and usable-count calculations with growers. Design flock management,
   grading, packing, stock conversion, and sales links as separate workflows;
   do not treat the current log as a food-safety or regulatory record.
10. **Washington and Oregon dairy farms:** validate dated milking-session entries,
    group labels, and the units growers actually use. Design herd integration,
    milk quality, processor delivery, stock conversion, and sales links as
    separate workflows; the current log is not a food-safety or compliance record.
11. **Washington and Oregon vineyard farms:** validate grape block names,
   varieties, harvest windows, and intended destinations with growers. Design
   actual grape picks, weights, fruit quality, winery handoffs, contracts,
   stock, and sales links as separate workflows; a block plan does not imply
   these are supported.
12. **Washington and Oregon hay and forage farms:** validate field cutting
   plans, cutting numbers, and seasonal timing with growers. Design actual
   harvest weights, bale inventory, forage quality, feed allocation, and
   customer sales as separate workflows; a cutting plan does not supply them.
13. **Washington and Oregon hop farms:** validate hop yard, variety, stringing,
   and harvest-window plans with growers. Design picked yield, drying, pellets,
   stock, contracts, and order links as separate workflows; a seasonal yard
   plan does not supply them.
14. **Washington and Oregon grain farms:** validate field names, grain crops,
   previous-crop rotation context, and seeding and harvest windows with growers.
   Design actual yield, grade and moisture, grain storage, contracts, stock, and
   sales as separate workflows; a field plan does not supply them.
15. **Washington and Oregon seed farms:** validate field, crop or species,
   variety, intended use, flowering and harvest windows, and manual isolation
   notes with seed growers. Design isolation checks, certification, actual yield,
   cleaning, lot identity, testing, stock, and order links separately; the
   planning notebook does not supply them.
16. **Washington and Oregon tree nut orchards:** validate block, crop,
   pollinizer, tree count, bloom, floor preparation, harvest window, and
   destination fields with growers. Design compatibility checks, actual
   harvested yield, cleaning, drying, grading, stock, and sales links as
   separate workflows; a block plan does not supply them.
17. **Washington and Oregon vegetable farms:** validate field or bed names,
   crop succession numbers, previous-crop context, sowing and transplant
   dates, harvest windows, and intended channels with growers. Design harvest
   forecasts, actual yield, grading, wash/pack, storage, food-safety records,
   stock, and order links separately; a planting plan does not supply them.
18. **Washington and Oregon aquaculture farms:** validate cohort, species,
   growing site, culture method, stocking and planned harvest windows, and
   intended destination with operators. Design species-specific production,
   site and license checks, water-quality and area status, harvest controls,
   tags, traceability, actual harvest, stock, and sales separately; a cohort
   plan does not supply them.
19. **Washington and Oregon Christmas tree farms:** validate block, species,
   planting year and count, target harvest year and height, shearing and cut
   windows, intended channel, and replacement planting details with growers.
   Design actual cut counts, stock, order links, choose-and-cut booking, and
   licensing, inspection, and export clearance workflows separately; a block
   plan does not supply them.
20. **Washington and Oregon apiaries:** validate apiary/site names, hive labels,
   dated checks, beekeeper-entered status, next-check dates, and observation and
   action notes with operators. Design colony history, treatment verification,
   pollination contracts, honey extraction, stock, sales links, and applicable
   registration checks separately; a hive check does not supply them.
21. **Washington and Oregon mushroom farms:** validate batch, species,
   growing method, substrate, growing area, inoculation date, planned fruiting
   window, and intended channel with growers using different log and indoor
   methods. Design actual harvest, grade, food-safety, environmental control,
   stock, and order links separately; a batch plan does not supply them.
22. **Washington and Oregon cranberry farms:** validate named perennial beds,
   variety, acreage, wet and dry harvest planning, intended channel, and
   seasonal dates with growers. Design water controls, actual harvest, grading,
   food-safety records, stock, and order links separately; a bed plan does not
   supply them.
23. **Washington and Oregon pumpkin patches:** validate patch names, variety,
   acres, planting and picking windows, pick-your-own, farm stand and wholesale
   modes, and visitor-area notes with growers. Design visitor ticketing,
   bookings, visitor checks, actual harvest, stock, and sales links separately;
   a seasonal patch plan does not supply them.
24. **Other Washington and Oregon enterprises:** use the same common record
   foundation, then validate the production and sales details with growers in
   each group.

California follows Washington and Oregon workflow validation. Farms that span
several enterprises need one farm identity and stock ledger with each crop's
unit, grade, and product form preserved. Specialty data should migrate through
versioned schema changes and remain recoverable with the farm database backup.

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
