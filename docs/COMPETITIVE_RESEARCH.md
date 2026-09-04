# FlowersForever competitive, event-service, and plant-reference research

Research snapshot: 2026-09-01. This is a product-planning review, not a claim
that every competitor feature was independently performance-tested. Feature
claims below come from each product's official site. University plant guidance
was synthesized into short original field notes; source titles and URLs remain
visible in the application.

## Competitive landscape

### September 4, 2026 workflow refresh

Rechecked the official pages for [Tend flower-farm management](https://www.tend.com/solutions/flower-farm-management),
[Farmbrite crops](https://www.farmbrite.com/crops),
[EveryStem features](https://www.everystem.com/features), and
[Flower Farm Planner](https://flowerfarmplanner.com/). This refresh concerns
published workflows, not competitor performance, pricing, or independently
verified customer outcomes. Earlier source entries below retain their original
September 1 snapshot.

| Published expectation | Concrete FlowersForever response in this pass | Remaining product work |
|---|---|---|
| Tend and Farmbrite let growers add their own crop details. | Farmers can enter custom inventory categories and selling units; desktop automation can now use the same editable controls. Inventory and harvest entry receive clearer grouping and larger fields. | Broader farm-defined fields and spatial crop layouts. |
| Farmbrite emphasizes recording field work while offline. | The worker screen adds a standalone field note, saves it to the existing Knowledge Log, and uses a durable operation receipt with the device retry queue. | Richer note attachments, crew assignments, and deployment-specific identity. |
| Tend carries growing templates into planting schedules and tasks. | Existing frost-aware preview, reviewed commit, status updates, and task regeneration are now available as dedicated headless farm-data tools. | Demand-aware planning and rotation history. |
| EveryStem carries saved recipes through costing and buying calculations. | Dedicated recipe-estimate and availability tools expose existing application calculations to an AI without a desktop window. | Supplier-level consolidation across events and printable design production sheets. |
| Flower Farm Planner presents planning, harvesting, selling, and learning as connected work. | The README leads with the grower's daily workflow and the value of keeping reusable records across seasons. | Season comparisons, durable sales-to-planting allocation, and crew playbooks. |

These changes close concrete entry and integration gaps. They do not establish
feature parity with every product below. Payments, contracts, marketplaces,
farm mapping, and multi-event purchasing remain distinct work streams. Proposed
next steps and measurable acceptance criteria are in [Future ideas](FUTURE_IDEAS.md).

| Product | Officially presented strengths | Implication for FlowersForever |
|---|---|---|
| [Tend](https://www.tend.com/solutions/flower-farm-management) | Flower-specific crop library, growing templates, visual crop plans, automatic tasks, projected yield/revenue, live inventory, orders, ecommerce, and availability calendars | The deepest direct competitor. FlowersForever should connect plant profiles to bed plans, target stems, tasks, and sales rather than stop at a reference encyclopedia. |
| [Farmbrite](https://www.farmbrite.com/crops) | Broad crop/livestock operations, scheduling, harvests, treatments, inventory, accounting, mapping, reporting, and integrations | FlowersForever can remain much more focused and approachable for small cut-flower teams. Structured harvest traceability and planting/season production-cost joins are now closed; farm mapping remains a gap. |
| [LiteFarm](https://www.litefarm.org/tutorials) | Free/open-source farm management, a catalog of more than 250 crop varieties, repeatable crop plans, tasks, mapping, and sustainability records | Local-first ownership is not unique by itself. FlowersForever's defensible angle is fast flower-farm workflow plus regional, attributable horticultural context. |
| [GrowVeg Garden Planner](https://www.growveg.com/garden-planner-intro.aspx) | Drag-and-drop spatial plans, spacing, local planting calendars, succession planning, crop rotation, companion guidance, and reminders | Add a bed/row layout and turn region + frost dates into sow/transplant/harvest windows. These are high-value planning gaps. |
| [From Seed to Spoon](https://www.seedtospoon.net/) | Visual garden planning, localized planting dates, plant/pest identification, companion guidance, organic treatments, predicted harvests, and reminders | Beginner-friendly explanations and location-specific timing matter. FlowersForever should keep sources visible and avoid opaque AI advice. |
| [Gardenize](https://gardenize.com/smarter-gardening/) | Searchable plant database, personal garden history, notes/photos, care advice, calendar, and reminders across devices | The Knowledge Log can become a plant-linked field journal: a strong route from general advice to the farm's own evidence. |
| [PictureThis](https://www.picturethisai.com/app) | Photo identification, problem diagnosis, care information, toxicity identification, and reminders | Camera workflows are a future mobile opportunity, but image identification should not displace Extension-based diagnosis and explicit uncertainty. |
| [Bloom Manager](https://app.bloommanager.com/) | Satellite bed mapping, flower presets, spacing and days-to-maturity data, frost-aware schedules, succession previews, stems-per-week projections, printable plans, and Florlist handoff | Spatial planning and frost math are real gaps, but the immediate lesson is the connected handoff: a crop choice must become a plan and the plan must become qualified availability. |
| [Flower Farm Planner](https://flowerfarmplanner.com/) | A flower-specific chain from plant library, beds, successions, tasks and harvests through bouquets, products, customers, sales, costs, profit, and next-season decisions | This is the clearest whole-product benchmark. FlowersForever should keep joining its already-strong operational records instead of adding isolated encyclopedias or dashboards. |
| [MyFlowerFarm](https://www.myflowerfarm.io/) | Farm layouts, automatic season events, multi-season yield and revenue analysis, recipes, harvest tracking, QR labels, and location-specific guidance | QR lot labels and mobile field capture are now implemented. Farm-generated schedules, spatial layouts, and season-over-season evidence remain higher-value than generic reminders. |
| [Florlist](https://florlist.com/florists) | Live and future weekly availability, stem counts, photos, bunch pricing, buyer-specific terms, order-ahead, multi-farm baskets, and availability notifications | A planning calendar becomes commercially useful only when planned supply is clearly separated from confirmed live stock and buyer commitments. |
| [Farmers to Florists](https://www.farmerstoflorists.com/) | Crop plans and projected harvest windows connected to florist recipes, future demand, current availability, orders, and direct grower-buyer communication | FlowersForever can differentiate locally by connecting plan, inventory, CRM, and events without operating a marketplace; an export or connector is preferable to a shallow marketplace clone. |

### Wedding, catering, décor, photography, and memorial landscape

| Product | Officially presented strengths | Implication for FlowersForever |
|---|---|---|
| [Curate](https://curate.co/) | Event records that connect contacts, locations, timelines, contracts, recipes, proposals, purchasing, production, payments, and client communication for florists and caterers | Keep one concise event brief, then connect approved floral work to physical recipes and purchasing without pretending outside-vendor services are farm inventory. |
| [Floranext](https://floranext.com/beautiful-professional-wedding-event-proposals/) | Wedding/event proposals with floral and supply components, visual inspiration, markups, shopping lists, profitability, payments, and a proposal calendar | Add source-aware visual proposals and flower recipes later; now, preserve service categories, locations, dates, scope, photos, and approval prompts in the event record. |
| [Tripleseat](https://tripleseat.com/platform/book/) | Branded proposals, contracts, banquet event orders, food-and-beverage minimums, rooms, rentals, add-ons, deposits, floor plans, and guest menu choices | Catering cannot be one free-text afterthought. Track headcount, dietary/allergy notes, licensed provider, service timing, kitchen, rentals, setup, cleanup, and payment checkpoints. |
| [HoneyBook for florists](https://www.honeybook.com/software-for-florists) | Inquiries, questionnaires, proposals, contracts, invoices, payments, scheduling, documents, and shared client/vendor communication | Turn repeat discovery questions and approvals into reusable briefs while keeping legal contract language outside the bundled reference catalog. |
| [Details Flowers](https://info.detailsflowers.com/faqs) | Recipe and item libraries, design worksheets, stem/bunch counts, markup and labor pricing, proposals, payment schedules, e-signatures, RFQs, event pipeline, and production documents | Recipe math and production instructions are a larger differentiator than decorative proposal output. They belong after crop-plan availability and inventory costs can ground them. |
| [EveryStem](https://www.everystem.com/features) | Flower libraries, reusable event recipes, wholesale pack rounding, vendor-separated orders, live markup/labor/profit summaries, extra-stem visibility, and consolidated multi-event purchasing | A focused internal production calculator can be valuable without becoming a CRM. FlowersForever should eventually connect recipes to its own crop availability before adding wholesale purchasing. |
| [Tribute Management Software](https://www.tributetech.com/tribute-management-software) | Funeral-service case scheduling, staff, rooms, vehicles, forms, service planning, and family coordination | Memorial work needs a named family decision maker, funeral-home contact, service/visitation/interment locations, culturally respectful approvals, and a low-burden change path. |
| [Tribute Video](https://www.tributetech.com/tribute-video) | Family photo/video uploads, themes and music, previews, approval, sharing, and keepsake delivery | Memorial media needs explicit authority, private upload and preview, rights checks, excluded moments, final family approval, delivery, and no public reuse by default. |

## Deep refresh: where category leaders create leverage

The current official product pages converge on one operating chain. The
products vary in scope, but the leverage comes from carrying structured data
forward instead of asking a grower or designer to retype it:

| Decision | Strong official examples | FlowersForever position after this pass |
|---|---|---|
| Choose what to grow | Tend crop library; Bloom Manager flower presets; Flower Farm Planner plant library | 94 sourced PNW profiles retain public provenance and can now open an editable Season Planner draft. |
| Turn the choice into a farm plan | Tend growing templates; Bloom Manager spacing/frost math; LiteFarm repeatable crop plans | The selected common name and full source context carry forward, while bed, cultivar, dates, spacing proxy, and yield remain explicit farm decisions. |
| Read supply over time | Tend and Bloom Manager availability projections; Florlist planned-versus-live weekly supply | A new weekly crop/variety availability outlook spreads expected stems over real harvest windows and labels past, current, and upcoming plan weeks. |
| Commit actual supply | Tend live inventory; Florlist confirmed stem counts; Farmers to Florists ordering | Existing inventory and CRM remain the commitment boundary. The copied outlook explicitly says to confirm live stock before promising stems. |
| Convert flowers into designs | Details, EveryStem, Curate, and Floranext recipes, consolidated stem counts, worksheets, and purchasing | The internal recipe layer now consumes season availability and inventory costs, and its canonical AI schema can migrate nested designs without creating a second disconnected flower library. |
| Protect margin | Tend crop costs; Farmbrite breakeven analysis; Details/EveryStem markup, labor, waste, and profit summaries | Task labor/inputs now join exact planting IDs and matched harvests into per-stem production costs; recipe yield/waste and order revenue still lack a provable planting-block attribution. |
| Win and manage the client | HoneyBook and Curate proposals, approvals, contracts, payments, timelines, and communication | FlowersForever owns operational CRM, event briefs, invoices, packing, and fulfillment. Contracts, e-signatures, and payment custody should be integrated deliberately, not imitated shallowly. |

### Wire-gap audit and product decisions

1. **Closed now — reference to plan.** `Plan selected crop` opens the ordinary
   validated Season Planner form. It copies the common crop name and a concise
   source block into notes, does not mislabel the scientific name as a cultivar,
   and does not infer dates or yields that the source does not provide.
2. **Closed now — plan to availability.** `Availability outlook` groups every
   planting into Monday-based crop weeks, allocates expected stems in proportion
   to overlapping harvest days, combines matching crop/variety blocks, and
   copies a tab-separated planning table. It never changes inventory or implies
   that a buyer allocation has been confirmed.
3. **Closed now — farm-owned growing templates.** Editable spacing, germination,
   frost-relative sow/transplant dates, maturity, succession, stems-per-plant,
   yield, and task offsets remain separate from immutable reference guidance.
4. **Closed now — frost-aware schedules and work.** Saved farm frost anchors
   produce reviewable succession drafts. A commit impact review prevents an
   accidental duplicate, while generated work can be completed, skipped,
   reopened, or safely regenerated without rewriting finished work.
5. **Closed at the internal-production layer — recipes and economics.** Recipes
   join planned and live flowers, inventory cost, wholesale pack rounding, labor,
   waste, overhead, markup, margin, break-even price, and event quantity. Vendor
   consolidation, substitutions, and printable production sheets remain useful
   follow-on work rather than reasons to split the internal flower records.
6. **Integrate rather than clone.** Marketplace discovery, card processing,
   legal contracts, e-signatures, and client portals carry operational and legal
   obligations. Use explicit connectors or exports after the internal records
   are strong enough to remain the system of operational truth.

## 2026-09-01 automation and robot-orchestration deep refresh

The harness was also compared with current official robot and farm-operations
contracts. This is architecture research, not a claim that FlowersForever can
control Optimus before Tesla publishes and grants access to an owner API.

| Official benchmark | Current contract worth carrying forward | Result in FlowersForever |
|---|---|---|
| [ROS 2 actions](https://docs.ros.org/en/rolling/Concepts/Basic/About-Actions.html) and its [interface guidance](https://docs.ros.org/en/ros2_documentation/rolling/Concepts/Basic/Interfaces-Topics-Services-Actions.html) | Long-running behavior has a goal, feedback, result, and cancellation instead of pretending to be a quick synchronous RPC | **Closed in the Optimus plugin:** execution returns a command ID and `RUNNING`; typed status, monotonic progress, and status detail arrive through observation. |
| [Viam machine APIs](https://docs.viam.com/reference/apis/robot/) and [module model](https://docs.viam.com/build-modules/overview/) | Machines expose current operations and cancellation; replaceable drivers have explicit configuration, dependencies, and versioning | **Closed at the port and recovery boundaries:** `OptimusRobotAdapter` owns the vendor transport, while the AI contract can observe, durably query, and cancel the exact active command without knowing that transport. |
| [Viam API-key scopes](https://docs.viam.com/organization/api-keys/) and [RBAC](https://docs.viam.com/organization/rbac/) | Machine identity and least-privilege scope are explicit | **Closed for local exclusive control:** stable robot identity is startup-verified and Flyway V9 persists one short-lived capability lease per robot. Tokens are robot-bound, rotated on renewal, and stored only as hashes. External IdP role mapping remains an owner-adapter concern. |
| [FarmBot REST resources](https://developer.farm.bot/v15/docs/web-app/rest-api.html) and [FarmBot OS](https://github.com/FarmBot/farmbot_os) | Stored plans are separate from device execution; operations expose telemetry/progress and a robot-owned emergency stop | **Closed for the safe common subset:** reviewed plans, state freshness, exact replay, graceful cancellation, and independently latched emergency stop are separate. Raw joints, trajectories, velocity, and reset-stop remain intentionally absent. |
| [Open-RMF fleet adapters](https://osrf.github.io/ros2multirobotbook/integration_fleets_adapter_tutorial.html) and [task dispatch](https://osrf.github.io/ros2multirobotbook/task.html) | A vendor adapter reports robot identity/state/current work while orchestration stays capability-oriented | **Closed for one future robot:** identity, battery, readiness, lifecycle, and high-level task state are vendor-neutral. Multi-robot bidding, traffic, and charging are deliberately out of scope until the farm has more than one supported robot. |
| [Tend task management](https://www.tend.com/feature/task-management) and [visual crop planning](https://www.tend.com/feature/visual-crop-planner) | Planned work is reconciled with actual labor, yield, task feedback, and downstream inventory | **Closed at task capture:** every planting-linked production task can retain actual crew minutes, input cost, field observation, and completion time. Reopened work keeps those actuals and regeneration excludes it from replacement. Planting/season aggregation remains a follow-on. |
| [Farmbrite harvest records](https://help.farmbrite.com/help/harvests) | Harvests carry batches, grades, storage, losses, and traceability | **Closed end to end:** every harvest now has a unique lot, grade, marketable and loss quantities, storage location, exact AI/REST lookup, CSV/report fields, and a printable QR label across desktop and mobile capture. |

### Harness wire-gap decisions

1. **Closed — command lifecycle.** Robot execution no longer returns a false
   synchronous `COMPLETED`. It returns a stable command ID and `RUNNING`, and
   observation exposes `IDLE`, `STAGED`, `RUNNING`, `SUCCEEDED`, `FAILED`,
   `CANCELLED`, `STOPPED`, or `UNKNOWN` with progress.
2. **Closed — targeted graceful cancellation.** `cancel-active-task` requires
   the exact active command ID, fresh state, the physical policy gates, and a
   short command expiry. It is distinct from both cancelling a staged draft and
   emergency stop.
3. **Closed — adapter and robot identity.** Simulator and hardware modes cannot
   be confused in telemetry. Hardware startup requires a configured, matching
   robot ID, and runtime identity drift fails closed.
4. **Closed — durable operation recovery.** Flyway V8 adds an append-only local
   event journal for accepted robot commands and meaningful lifecycle changes.
   `get-operation` reconciles an exact command ID with fresh adapter state;
   `list-operations` discovers up to 25 recent operations, and
   `list-operation-events` drains ordered lifecycle changes in 1–50 item pages
   from a durable string cursor. Point queries report `LIVE`,
   `TERMINAL_HISTORY`, or `UNCONFIRMED`. After restart, a
   nonterminal command the adapter no longer reports becomes `UNKNOWN` rather
   than remaining falsely `RUNNING`. Generic gateway response replay remains
   bounded and process-local; exact Optimus execution replay is separately
   durable as described below.
5. **Closed — local operator leases and robot-scoped authorization.** Flyway V9
   stores one exclusive 15–300 second lease per stable robot identity. Acquiring
   requires the mutation policy; staging, cancellation, execution, and safe-pose
   control require the unguessable lease capability. Renewal rotates the token,
   only its SHA-256 digest is stored, and expired leases fail closed. Emergency
   stop intentionally bypasses lease ownership while retaining the physical
   policy and freshness gates. The holder label stays in local storage and is not
   exposed through observation or status; federated per-user roles remain
   deferred to the released owner interface.
6. **Closed — staged-task reconnect and state reconciliation.** Flyway V10
   retains one staged task's robot identity, owning lease, timestamps, and token
   digest without storing task text or the plaintext token. A recreated plugin
   can resume the task when the adapter still holds it. If the adapter restarts
   without that plan, observation reports the mismatch, execution clears the
   stale record without dispatching, and the active lease can stage again. A
   replacement lease can cancel stale work after expiry but cannot execute the
   previous lease's plan. All cleanup routes now skip redundant cancellation
   against an idle restarted adapter and retain the durable record when a needed
   adapter cancellation fails. Internal plan fingerprints also distinguish the
   exact persisted review from an unrelated adapter-held staged task; lifecycle
   state alone no longer authorizes resume. Initial staging now has the same
   identity guarantee: the harness returns a token only after a fresh adapter
   observation acknowledges that exact plan, and cleans up an unacknowledged
   adapter draft. Staging rollback now also covers ambiguous adapter exceptions,
   lease changes during the call, and durable-store failures while preserving
   both the primary and cleanup diagnostics. Before a token is returned, the
    durable write result and a fresh read-back must also agree on the exact robot,
    lease, and plan digest so restart metadata cannot silently diverge.
7. **Closed — ambiguous execution handoff.** Flyway V11 claims the exact staged
   plan with the original harness request ID before adapter dispatch. If the
   adapter response is interrupted before acceptance, only that request may
   retry the idempotent token. If acceptance occurred, the adapter's retained
   originating-plan fingerprint and command ID let a restarted plugin journal
   and return the operation without a second dispatch. Conflicting requests and
   unmatchable adapter state preserve an observable recovery marker instead of
   guessing or erasing the handoff.
8. **Closed — successful dispatch with lost client response.** Flyway V12 binds
   the accepted Optimus receipt to the original request ID and a SHA-256 digest
   of the exact execution command. A restarted gateway can replay the same
   operation before the original deadline check without another adapter call;
   changed arguments or actions collide. The plan and lease capabilities remain
   plaintext-free in persistence.
9. **Closed — owner API unavailable during durable replay.** Once startup has
   established robot identity, an exact accepted request can return from local
   durable records even when later adapter identity/state calls fail. The result
   is explicitly `UNKNOWN`/`UNCONFIRMED`, its observation is `UNAVAILABLE`, and
   unknown sensor values are omitted. This recovery path cannot dispatch a new
   physical command, while a returned mismatched identity still fails.
10. **Closed — reconciliation journal temporarily read-only.** Exact replay no
    longer depends on appending a secondary `UNKNOWN` event after its durable
    receipt was read. The response remains `UNKNOWN`/`UNCONFIRMED`, explicitly
    reports that reconciliation was not persisted, and retains the accepted
    receipt for retry without another physical dispatch. Unavailable snapshots
    also map last-known nonterminal state to `UNKNOWN` directly.
11. **Closed — live status journal temporarily read-only.** When exact adapter
    state confirms the accepted command, replay can return that `LIVE` state and
    a fresh snapshot even if later lifecycle appends fail. The response exposes
    `reconciliationPersisted=false`; only status enrichment is best-effort, while
    the original durable acceptance receipt remains required.
12. **Closed — operation reads blocked by status enrichment.** `get-operation`
    and `list-operations` now return live or explicitly unconfirmed results when a
    secondary lifecycle append fails, with persistence disclosed per result. The
    cursor event feed still returns its durable page, marks the failed refresh, and
    never assigns a cursor to unwritten live state.
13. **Closed — operation history blocked by owner API outage.** Durable single,
    recent, and cursor-paginated operation reads now remain available while the
    adapter transport is offline. Nonterminal work becomes explicitly unconfirmed,
    terminal history is preserved, adapter availability is exposed, and no robot
    command is dispatched by the fallback.
14. **Closed — stale adapter lifecycle regression.** One lifecycle normalizer now
    protects transient and database journals from delayed samples, decreasing
    progress, backward nonterminal phases, and terminal resurrection. Partial
    progress is retained for cancellation/failure/stop, while a later valid sample
    can recover an explicitly unknown operation.
The gap analysis intentionally did not invent a Tesla endpoint, authentication
scheme, coordinate system, reset procedure, or low-level motion primitive. Those
details must come from the released owner interface and stay inside the adapter.

## Event-service position that emerges

FlowersForever should coordinate a farm's full event contribution without
claiming to replace a caterer, photographer, venue, funeral director, or
contract/payment platform. The delivered Event Services workspace therefore
separates two kinds of work:

1. Physical flowers, plants, vessels, and other stocked goods remain CRM order
   lines and deduct inventory only when the order is completed.
2. Catering, décor installation, photography, media, venue, coordination,
   setup, strike, approvals, and vendor responsibility stay in a compact event
   brief. They never become fake stock or create an accidental shortage.

The bundled catalog ships wedding, memorial/funeral, and all-event starters
across floral design, catering, décor and rentals, photography and media,
logistics, and coordination. Workers can combine services, build an editable
brief, copy it, or prepare it in CRM without overwriting an unrelated draft.
The same catalog is available through `/api/event-services` and its source
library through `/api/event-services/sources`.

### Event safety and dignity policy

- Catering prompts require an appropriately licensed provider and explicitly
  leave food safety, allergen controls, permits, and insurance with that vendor.
- Photography and video prompts separate coverage permission from portfolio or
  social-media reuse. Memorial media defaults to private and requires a named
  family decision maker and venue/funeral-home approval.
- Memorial starters never infer faith, culture, relationship, symbols, or
  preferred wording. Those decisions stay with the family.
- The catalog provides operational questions, not quotes, contracts, legal
  advice, permit determinations, or promises that a provider is available.
- Venue access, attachment, flame, egress, weather, accessibility, delivery,
  installation, strike, rentals, damage, and final destinations must be
  confirmed for each event.

## Product position that emerges

FlowersForever is strongest when it behaves as the operating notebook for a
small Pacific Northwest flower farm: local-first records, season plans, harvest,
inventory, customers, packing, paperwork, and farm lessons in one workflow.
The new plant guide adds a second differentiator: every profile exposes its
public provenance instead of presenting unattributed care text.

The competitive review suggests this sequence:

1. **Delivered now:** a searchable 94-profile PNW catalog, 18 public research
   resources, sourced profile-to-plan handoff, weekly planning availability,
   event-service catalog and CRM brief handoff, farm-owned crop templates,
   frost-aware succession and work generation, recipes, planned-versus-live
   allocation, purchasing math, production costs and design estimates, and planting-linked
   labor, input-cost, and field-result capture.
2. **Next:** spatial bed/row planning, rotation families, consolidated multi-event
   purchasing, substitutions, and printable
   field/design production sheets.
3. **Mobile capture now; further work later:** task photos, harvests, counts,
   order handoffs, and standalone field notes are implemented. Reminders, weather-aware adjustments,
   and carefully qualified identification/diagnostic assistance.

## Regional research base

The catalog emphasizes plants and crops useful in Washington and Oregon, with
special attention to wet winters, dry summers, cool western sites, and the
Port Orchard/Kitsap context. It does not imply that every listed plant fits every
PNW microclimate.

Core university and Master Gardener sources include:

- Washington State University Extension:
  [Home Vegetable Gardening in Washington](https://pubs.extension.wsu.edu/product/home-vegetable-gardening-in-washington-home-garden-series/),
  [Berries and Small Fruits](https://extension.wsu.edu/pnw-gardeners-handbook/chapter-9-berries-and-small-fruits/),
  [King County Pollinator-Friendly Gardening](https://extension.wsu.edu/king/mg-home/gardening-resources/tip-sheets/tip-sheet-12),
  and [Kitsap Master Gardener resources](https://extension.wsu.edu/kitsap/master-gardeners/gardening-resources/clinics-gardens-and-resources/).
- Oregon State University Extension:
  [Gardening with Oregon Native Plants West of the Cascades](https://extension.oregonstate.edu/catalog/ec-1577-gardening-oregon-native-plants-west-cascades),
  [woodland native plants](https://extension.oregonstate.edu/gardening/flowers-shrubs-trees/pacific-northwest-native-plants-woodland-garden),
  [Growing Your Own](https://extension.oregonstate.edu/catalog/em-9027-growing-your-own),
  and [Dahlias in Oregon](https://extension.oregonstate.edu/catalog/fs-95-dahlias-oregon).
- University of Washington Botanic Gardens:
  [Climate-Ready Plants](https://botanicgardens.uw.edu/science-conservation/climate-ready-plants/),
  the [Miller Library Native Plant Finder](https://depts.washington.edu/hortlib/website/native-plant-finder/),
  and a [Pacific crabapple profile](https://botanicgardens.uw.edu/about/blog/category/plant-profiles/page/7/).
- The Evergreen State College:
  [Ethnobotanical Gifts Garden](https://www.evergreen.edu/longhouse/ethnobotanical-gifts-garden),
  included as a teaching-garden resource for habitat-based native planting and
  respectful cultural context.

## Editorial and safety policy

- Profiles paraphrase and synthesize public guidance; they do not copy
  publication prose or images.
- A source is attached to every plant, and supplemental regional libraries are
  visible alongside the catalog.
- Native status is regional, not universal. Restoration work should use local
  provenance and project-specific guidance.
- Edibility is never established by this catalog. Positive identification,
  toxicity checks, and current Extension advice remain required.
- Pesticide rates and treatment recipes are intentionally excluded because
  labels, registrations, pests, and recommendations change.
