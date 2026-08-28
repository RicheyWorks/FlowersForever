# Product roadmap: keep the lessons in the ground

## Product promise

**Keep the lessons in the ground. Less waste. More control.**

FlowersForever already connects harvests, inventory, customers, orders, market
packing, reports, and closeouts. The next differentiator is preserving the
practical knowledge behind those records so a farm does not relearn the same
expensive lesson every season—or lose it when an experienced worker leaves.

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

Next planning increments:

- Add week-by-week planned-versus-actual trend charts and forward projections.
- Turn overlap and succession-gap warnings into guided corrective actions.
- Add reusable crop timing templates without hiding or inventing farm inputs.
- Turn selected planting windows into crew-ready task lists and reminders.

## 1.4 — Seasonal learning

- Compare recurring problems and successful actions by crop, bed, and season.
- Turn selected lessons into farm playbooks and crew-ready checklists.
- Add reminders when a past lesson becomes relevant again.
- Export a human-readable seasonal knowledge report that belongs to the farm.

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
