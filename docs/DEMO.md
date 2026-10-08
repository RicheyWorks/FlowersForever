# FlowersForever — Presenter demo runbook

**Use:** RicheyWorks presenters and developers run a local build to guide growers through the sample farm. External pilot installation follows [signed-release approval](PILOT_ACCESS.md).

**Time:** ~5 minutes for the sample-farm tour after startup; use the optional stops for a longer conversation.

## The 30-second pitch

"FlowersForever opens with the farm you run. Pick a specialty or combine several, and keep customers, stock, orders, and notes together as your mix changes. Cut-flower teams can follow a stem from planting to customer handoff today. Other farm types open focused planning boards, collection logs, or hive checks, so a grower can plan a pumpkin patch beside cranberry beds and orchard blocks without splitting the farm's records."

For a grower, show the decision each screen supports before explaining the feature. Keep **planned stems**, **on-hand stock**, and **confirmed orders** distinct: the app does not turn a forecast into a buyer commitment. The cut-flower tour below shows the deepest connected workflow today; the other specialty notebooks do not yet link plans to harvests, stock, or orders.

---

## 0. Build once

```bash
cd FlowersForever
mvn clean verify
```

All tests green → development JAR under `target/flowerfarm-manager-1.2.0-SNAPSHOT.jar`.
GitHub Actions runs the same command on every push to `main` (see CI badge in the README).  
Approved stable tags publish only when the tag, POM, verified JAR, and both native installers agree on the exact version.

---

## 1. Start (pick a profile)

| Goal | Command |
|------|---------|
| Default GUI + REST + H2 | `mvn spring-boot:run` |
| Same via JAR | `java -jar target/flowerfarm-manager-1.2.0-SNAPSHOT.jar` |
| **Demo seed** (empty CRM/harvest/knowledge) | `java -jar … --spring.profiles.active=demo` |
| SQLite file DB | `java -jar … --spring.profiles.active=sqlite` |
| Barn multi-user | Set `FLOWERFARM_AUTH_USERS`, then use `--spring.profiles.active=auth` |
| Demo + auth | Set `FLOWERFARM_AUTH_USERS`, then use `--spring.profiles.active=demo,auth` |
| Auth + SQLite | Set `FLOWERFARM_AUTH_USERS`, then use `--spring.profiles.active=auth,sqlite` |
| CLI only | `java -jar … --cli` (menu includes Kitsap irrigation advice) |

**Demo profile:** on an empty demo database, seeds Kitsap Blooms, Saturday Market Stall, bed harvests (Bed A / Tunnel 1), and **today’s CONFIRMED** orders so Market Day + Morning briefing work immediately. It also seeds a Draft order and farm lessons. Seeding does not wipe existing records. Set `FLOWERFARM_DATA_DIR` to a separate, empty demo folder before starting; do not clear a farm's working `./data` folder for a presentation.

For a fresh portable-JAR demo from PowerShell in the repository root:

```powershell
$env:FLOWERFARM_DATA_DIR = Join-Path $PWD ("demo-data-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
java -jar target/flowerfarm-manager-1.2.0-SNAPSHOT.jar --spring.profiles.active=demo
```

App opens Swing GUI and Tomcat on **http://localhost:8080**.

---

## 2. Five-minute sample-farm tour

Start with the **demo** profile already running against a separate demo data folder. The seed supplies today's confirmed Pickup and Market orders, so the tour does not need a new customer or order. Use a small sample harvest to show what changes. Market Day completes the confirmed loads together, so do that step only when both simulated handoffs are finished.

| Time | Show | Say what the grower can decide |
|---|---|---|
| 0:00–0:45 | **Today → Morning briefing.** Open a priority and point to today's confirmed packing work. | “What needs attention before we leave for market?” |
| 0:45–1:45 | **Harvest Log.** Add today's `Nootka Rose`, `10` `stems`, `Bed A`; wait for the saved result. | “The cut is recorded once and adds to matching on-hand stock.” |
| 1:45–3:45 | **Market Day → Build packing list.** Compare the confirmed Pickup and Market lines with stock and review both customer handoffs. After loading each ready order, check it or drag its card to **Packed**. If demonstrating completion, choose **Complete confirmed orders…**, review the dialog, then choose **Complete checked orders**. | “The Draft stays out of the pack. A confirmed order is a commitment; stock is deducted when the handoff is completed.” |
| 3:45–4:30 | **Today → Day closeout.** Show completed sales if the handoffs were done, or the still-open work if they were not. | “The next person can start from the same records.” |
| 4:30–5:00 | **Knowledge Log.** Open the seeded mildew lesson and point to its observation, action, result, and confidence. | “The farm keeps the reason behind a decision for next season.” |

**Presenter check:** If a step is not ready, show the current status and move on. Do not check a load that has not been packed or complete orders with a stock or handoff warning. If you skip completion, use Day closeout to show the still-open work rather than a completed sale. The demo profile adds sample records only where the relevant data is empty, so a reused demo folder may no longer match this script.

### Optional deeper stops

Choose these when the audience asks for detail. They are not part of the five-minute tour.

#### Pick and combine farm types (~2 minutes)

1. Open **File → Choose or combine farm types…**. Search for a farm type by its crop or business, then open **Combine farm types…**.
2. Drag **Potato farm**, **Apple orchard**, and **Cannabis farm** into **Your mix**. Drop one card on **Main focus**, or choose it from the menu. Point to the preview: Field plans, Orchard blocks, and Cultivation appear alongside shared records.
3. Save the combination and enter the combined overview. Show that each specialty notebook has its own editable plan details, while customers, inventory, orders, and notes remain shared.
4. Back on the farm menu, choose **Add to mix…** on **Custom farm** to open the editor with that type in the draft. Save it to show the shared Harvest Log for an unlisted crop alongside the named specialty tools. Give that crop a name in **Farm setup…** to display it in the overview. The saved mix changes only after **Save combination**; removing a type hides its tools and keeps its saved plans.

Describe the scope as you show it: these notebooks organize planning for each enterprise. They do not yet turn those plans into harvest lots or finished stock, and the cannabis notebook does not provide state tracking or reporting. [Farm-type scope](FARM_TYPES.md#choose-a-workspace-for-the-days-work) has the current boundaries.

In **Cultivation**, drag a saved batch to a stage under **Move planning stage**, or select its row and click a stage. The planning label saves immediately. Save or discard an open batch draft first; the stage move does not create a harvest or stock record.

#### Show a field science source beside a farm finding (~1 minute)

1. Open **Field science** in the combined workspace. Point to the tool cards chosen for its farm types, including **Subcool's Super Soil** for Cannabis farm.
2. Select a previously reviewed entry in **Saved field science exports** and wait for its text to load. Choose **Record finding from selected…**. In the Knowledge Log draft, show the read-only instrument, archive ID, and fingerprint, then write a brief observation from the farm and save.
3. Select the saved lesson and choose **Open linked export**. The exact archived text opens by ID, while the archive's search and selected row remain as they were. A VIEWER can follow this read-only link.

Describe the two records plainly: the archive holds reviewed tool text; the Knowledge Log holds the grower's own observation and a link to that source. The original browser file stays separate.

#### Show a mixed livestock plan (~1 minute)

1. Search for **Livestock farm** in the farm picker, add it to a combination, and save. Open **Herd groups** from the combined overview.
2. Create a group such as `Spring calves` with species `Cattle`, a headcount, a current pasture, a purpose, and a next check date. Select the saved group to show that these details can be edited.
3. Explain that this is a current planning snapshot. It does not identify individual animals, record health or breeding events, move animals through a traceability ledger, or turn headcount into stock or sales.

#### Show a mixed poultry collection (~1 minute)

1. Add **Poultry & eggs** to the combination and open **Egg collections** from the combined overview. Other selected specialty tools and the shared customer, stock, and order records remain available.
2. Save a collection for `Layer flock` on today's date: 84 collected, 3 unusable, and an optional coop location. Point to 81 usable eggs calculated from that entry. Select the saved row to edit its details.
3. Explain that this is a dated collection log. It does not create 81 eggs in Inventory or link them to an order, grade or pack eggs, or track flock health.

#### Show a mixed dairy collection (~1 minute)

1. Add **Dairy farm** to a combination with **Livestock farm** or **Poultry & eggs** and open **Milk collections**. The other specialty tools and shared records remain available.
2. Save a collection for `Milking herd`, `Morning`, and today's date with `125.50` `lb` and optional `Tank A`. Save a second session for the same group and date, then select a saved entry to edit it. Point out that each entry retains its chosen lb, gal, or L unit without conversion.
3. Explain that these are local milking notes. They do not change Herd groups, post milk to Inventory, link a sale, track quality or processor delivery, or serve as food-safety or compliance records.

#### Show a mixed vineyard plan (~1 minute)

1. Add **Vineyard farm** to a combination with another crop type, then open **Vineyard blocks**. Point out that the other specialty tools and shared records remain available.
2. Save a block such as `South slope` with a grape variety, optional location and acreage, first and last planned harvest dates, intended destination, and notes. Select the saved block and change its planned window to show that the grower can edit the plan.
3. Drag the saved row to a stage under **Move a saved block**; select the row and click a stage to show the same action without dragging. The stage saves immediately after an open draft has been saved or discarded.
4. Explain that this is a seasonal block plan. It does not record a picked weight, create a harvest lot or stock, link a contract or order, or manage crush and wine production.

#### Show a mixed hay cutting plan (~1 minute)

1. Add **Hay & forage** to a combination with **Dairy farm** or **Livestock farm**, then open **Hay cuttings**. The milk or herd tools remain available alongside the shared records.
2. Save a cutting for `North field`, forage crop `Alfalfa`, cutting number `1`, a planned cut date, and an optional bale date and acreage. Select the saved entry to edit its timing and notes.
3. Select that saved entry and choose **Plan next cut**. The new draft retains the field, crop, and acres, increases the cutting number to `2`, and leaves both planned dates blank. Enter its next planned cut date before saving. An open draft must be saved or discarded first; VIEWER cannot use this action, and cutting 99 has no next number.
4. Drag a saved row to a stage under **Move a saved cutting**, or select the row and click a stage. Finish or discard an open form draft first; the stage move saves immediately.
5. Explain that this is a cutting plan. It does not count or weigh bales, calculate feed rations, post stock, link an order, or record forage quality or compliance.

#### Show a mixed hop yard plan (~1 minute)

1. Add **Hops farm** to a combination with another crop type, then open **Hop yards**. The other specialty tools and shared records remain available.
2. Save a yard such as `East trellis` with variety `Cascade`, optional acreage, a planned stringing date, a planned first and last harvest date, stage, and notes. Select the saved yard to edit its plan.
3. Select that yard and choose **Plan next season**. The new draft retains yard, variety, and acreage, and leaves its planned dates blank. Enter a new planned harvest start date before saving. Finish or discard an open draft first.
4. Drag a saved yard to a stage under **Move a saved yard**, or select the row and click a stage. The stage move saves immediately after an open form draft is resolved.
5. Explain that the yard entry is a plan. It does not record picked yield, drying or pellets, post inventory, link contracts or orders, or serve as a regulatory record.

#### Show a mixed grain field rotation (~1 minute)

1. Add **Grains farm** to a combination with **Potato farm** or another crop type, then open **Grain fields**. The other specialty tools and shared records remain available.
2. Save `North field` with grain crop `Wheat`, an optional variety and acreage, previous crop `Potatoes`, a planned seeding date, planned harvest start and optional end date, stage, and notes. Select the saved field to edit its plan.
3. Select the saved field and choose **Plan next rotation**. The new draft retains the field and acres, sets **Previous crop** to `Wheat`, and clears the next grain crop, variety, seeding date, and harvest dates. Choose a new crop and planned harvest start before saving. Finish or discard an open draft first.
4. Drag a saved field to a stage under **Move a saved field**, or select the row and click a stage. The stage move saves immediately after an open form draft is resolved.
5. Explain that the field entry is a rotation plan. It does not record harvested yield, grade, moisture, storage, contracts, stock, or order links.

#### Show a mixed seed field plan (~1 minute)

1. Add **Seed farm** to a combination with **Grains farm** or another crop type, then open **Seed fields**. The other specialty tools and shared records remain available.
2. Save `South seed field` with seed crop `Ryegrass`, an optional variety and acreage, previous crop, a planned harvest start and optional sowing, flowering, and harvest end dates. Choose an intended use, and enter any isolation observations as manual notes. Select the saved field to edit it.
3. Select that field and choose **Plan next season**. The new draft retains the field, crop, variety, and acres while clearing planned dates. Enter a new planned harvest start before saving. Finish or discard an open draft first.
4. Drag a saved field to a stage under **Move a saved field**, or select the row and click a stage. The stage move saves immediately after an open form draft is resolved.
5. Explain that this is a seasonal seed-crop plan. It does not calculate isolation, certify seed, record harvested yield or cleaned lots, track tests, post stock, or link orders.

#### Show a mixed tree nut block plan (~1 minute)

1. Add **Tree nut orchard** to a combination with **Apple orchard** or another crop type, then open **Nut blocks**. Other specialty tools and the shared records remain available.
2. Save `West hazelnut block` with nut crop `Hazelnut`, a variety, optional pollinizer variety, acreage, tree count, planned bloom and floor preparation dates, planned harvest start and optional end, intended destination, stage, and notes. Select the saved block to edit its plan.
3. Select the block and choose **Plan next season**. The new draft carries the block, crop, variety, pollinizer variety, acreage, tree count, and destination forward while clearing dates and notes. Enter a new planned harvest start before saving. Finish or discard an open draft first.
4. Drag a saved block to a stage under **Move a saved block**, or select it and click a stage. The stage move saves immediately after an open form draft is resolved.
5. Explain that the pollinizer is a grower-entered note, not a compatibility check. Nut blocks do not record actual harvested yield, cleaning, drying, grading, stock, or orders.

#### Show a mixed vegetable succession (~1 minute)

1. Add **Vegetable farm** to a combination with **Potato farm** or another crop type, then open **Vegetable plantings**. Other specialty tools and the shared records remain available.
2. Save `South bed` with crop `Broccoli`, variety, succession number `1`, previous crop `Peas`, optional acres, grower-entered sowing, transplant, and planned harvest dates, intended channel, stage, and notes. Select the saved planting to edit its plan.
3. Select that planting and choose **Plan next succession**. The draft carries its field or bed, crop, variety, previous crop, acres, and intended channel forward, increases the succession number to `2`, and clears dates and notes. Enter the new planned harvest start before saving. Finish or discard an open draft first.
4. Drag a saved planting to a stage under **Move a saved planting**, or select the row and click a stage. The stage move saves immediately after an open form draft is resolved.
5. Explain that these are grower-entered dates. The notebook does not predict harvest readiness, record actual yield, grade or wash and pack produce, manage storage, certify food safety, post stock, or link orders.

#### Show a mixed aquatic cohort plan (~1 minute)

1. Add **Aquaculture farm** to a combination with another type, then open **Aquatic cohorts**. The other specialty tools and shared customer, stock, and order records remain available.
2. Save a named oyster cohort with its species, growing site, optional culture method and stocking date, planned harvest start and optional end, intended destination, stage, and notes. Select the saved cohort to edit its plan.
3. Select the saved row and choose **Plan next cohort**. Species, site, culture method, and intended destination carry forward; cohort name, dates, and notes clear; and stage returns to Planned. Enter a new name and planned harvest start before saving. Finish or discard an open draft first.
4. Drag a saved cohort to a stage under **Move a saved cohort**, or select the row and click a stage. The stage move saves immediately after an open form draft is resolved.
5. Explain that the dates and stages are only plans. The notebook does not check a license, site approval or area-open status, monitor water quality, authorize harvest, issue shellfish tags, provide regulatory traceability, record actual harvest, post stock, or link orders.

#### Show a mixed Christmas tree block plan (~1 minute)

1. Add **Christmas tree farm** to a combination with **Nursery farm** or another type, then open **Tree blocks**. The other specialty tools and shared customer, stock, and order records remain available.
2. Save `North fir block` with a tree species, target harvest year, optional planting year, acres, planted tree count, target height, next shearing date, planned cut start and end in the target year, intended channel, stage, and notes. Select the saved block to edit its plan.
3. Choose **Plan replacement planting**. The draft carries block name, species, acres, target height, and intended channel forward; years, tree count, dates, and notes clear; and stage returns to Planned. Enter the new target harvest year before saving. Finish or discard an open draft first.
4. Drag a saved block to a stage under **Move a saved block**, or select the row and click a stage. The stage move saves immediately after an open form draft is resolved.
5. Explain that this is a plan, not an actual cut count or a booking. It does not post stock or orders, manage choose-and-cut visits, or verify licensing, inspection, or export clearance.

#### Show a mixed Apiary farm hive check (~1 minute)

1. Add **Apiary farm** to an **Apple orchard** or **Berry farm** combination, then open **Hive checks**. The crop tools and shared customer, stock, and order records remain available.
2. Save a check for `West apiary`, hive `H-04`, with a check date, optional purpose, beekeeper-entered status, next check date, observations, and actions. Select the saved check to edit its entry.
3. Choose **Start next check**. The draft carries apiary/site, hive label, and purpose forward but clears both dates, status, observations, and actions. Enter a new check date before saving. Finish or discard any open draft first.
4. Drag a saved check to a status target, or select its row and click a status. The move saves immediately after an open form draft is resolved.
5. Explain that this is the beekeeper's observation, not an automated diagnosis. It does not verify treatments or registration, manage pollination contracts, extract honey, or connect hive records to stock and sales.

#### Show a mixed mushroom batch plan (~1 minute)

1. Add **Mushroom farm** to a combination with **Nursery farm** or another type, then open **Mushroom batches**. Other specialty tools and the shared customer, stock, and order records remain available.
2. Save `Shiitake log batch` with species `Shiitake`, growing method `Outdoor logs`, substrate `Alder logs`, a growing area, and optional planned inoculation and fruiting window, intended channel, stage, and notes. Select the saved batch to edit it.
3. Choose **Plan next batch** to start another draft from it. Species, method, substrate, growing area, and intended channel carry forward; batch name, dates, and notes clear; and stage returns to Planned. Enter a new name and this batch's own planned dates when known before saving. Finish or discard an open draft first.
4. Drag a saved batch to a stage under **Move a saved batch**, or select its row and click a stage. The stage move saves immediately after an open form draft is resolved.
5. Explain that the dates are grower-entered plans. They do not forecast fruiting, record actual harvest, control growing conditions, certify food safety, post stock, or link orders.

#### Show a mixed cranberry bed plan (~1 minute)

1. Add **Cranberry farm** to a combination with **Berry farm** or another type, then open **Cranberry beds**. The other selected tools and shared records remain available.
2. Save `South bed` with a bed name, optional variety and acres, **Wet** as the planned harvest method, grower-entered harvest dates, intended channel, stage, and notes. Select the saved bed to edit it.
3. Choose **Plan next season**. Bed name, variety, acres, harvest method, and intended channel carry forward; dates and notes clear; and stage returns to Planned. Enter the next season's own dates before saving.
4. Drag a saved bed to a target under **Move a saved bed**, or select its row and click a stage. Resolve an open form draft first; the stage move then saves immediately.
5. Explain that Wet and Dry are planning labels. The board does not control water, record picked fruit, grade it, supply food-safety traceability, or post inventory and orders.

#### Show a mixed pumpkin patch plan (~1 minute)

1. Add **Pumpkin patch** to a combination with **Vegetable farm** or another type, then open **Pumpkin patches**. The other selected tools and shared customer, stock, and order records remain available.
2. Save a patch named `North field` with optional variety and acres, planned planting and picking dates, **Pick your own** as the planned sales mode, visitor-area notes, stage, and general notes. Select the saved patch to edit it.
3. Choose **Plan next season**. Patch name, variety, acres, sales mode, and visitor-area notes carry forward; all dates and general notes clear; and stage returns to Planned. Enter the next season's dates when known before saving.
4. Drag a saved patch to a target under **Move a saved patch**, or select its row and click a stage. Resolve an open form draft first; the stage move then saves immediately.
5. Explain that this is a seasonal plan. It does not issue visitor tickets, take bookings, verify visitor arrangements, record actual pumpkin harvest, or link inventory and sales.

#### Build an event-service brief by drag or click (~1 minute)

1. Open **Reference → Event services**. Select one or more catalog rows, using Ctrl/Cmd-click for several, then drag the selection onto **↓ Drop selected services here to build an empty brief**. The same action is available through **Build selected-service brief**.
2. Review and edit the resulting brief. If it already contains text, the drop leaves that text alone; choose **Clear brief** before building another. **Copy brief** is available for handoff, while a CRM order brief uses the reviewed text as instructions rather than stock lines for services.

For a nursery grower, open **Growing batches** and drag a saved batch to a stage under **Move by stage**. The stage changes immediately; clicking a stage provides the same action. Finish or discard a form draft before moving a saved batch so the new stage cannot overwrite unsaved details.

Narrate as a Kitsap / Port Orchard grower:

1. **Dashboard**  
   - Point at 5 KPIs: SKUs, inventory value (+ cost basis), low stock, **Week Harvest**, **Week Revenue**.  
   - Hover sparklines → day-by-day tooltip.  
   - Note **realized revenue** = FULFILLED only; pipeline under the card.  
   - **Morning briefing** → priority actions + export PDF (pack / beds / water / stock).  
   - After market → **Day closeout** → fulfilled sales, leftover pipeline, low stock, next-day steps (PDF).

2. **Harvest Log**  
   - Date today, crop `Nootka Rose`, qty `120`, unit `stems`, bed `Bed A`.  
   - **Add harvest** → inventory increases; status mentions `HARVEST_LOG`.  
   - Or **Batch log**:  
     ```
     Nootka Rose,40
     Damask Rose,25,bunches
     Dahlia mix,15,stems,Bed C
     ```  
   - Double-click a row → tweak qty → **Save edit** (`HARVEST_EDIT`).  
   - Filter crop/bed/notes; **This week**; **Export filtered CSV**.  
   - **Bed production…** → rank beds by qty with crop mix; export CSV or **PDF**.  
   - **Harvest log PDF…** → chronological cut list for the cooler (filter dates or last 7 days).

3. **Dashboard again**  
   - Week Harvest KPI / sparkline updated.  
   - Optional: **Low-stock reorder** (Inventory) → threshold sheet + PDF for the cooler door.  
   - Optional: **Price list…** → in-stock market/wholesale catalog PDF for the booth table.

4. **CRM**  
   - Select the seeded `Kitsap Blooms` customer (type FLORIST); do not create a duplicate.
   - To show order entry, create a new Draft with a Pickup handoff and `Nootka Rose` product line; review it, then **Confirm**.
   - **Complete** after the handoff → inventory drops; audit under CRM.
   - Select order → **Invoice PDF…** (bill-to sheet for florist / wholesale).  
   - Select customer → **Statement PDF…** (account rollup, default last 90 days; uses order filter dates if set).  
   - Filter **Pipeline only**; export orders CSV.

5. **Market Day**  
   - With today's seeded confirmed orders, choose **Build packing list**.
   - Pick list shows aggregate stems vs stock (shortfalls flagged).  
   - Per-customer pack slips; export TXT / CSV / **PDF** for the van.  
   - After every confirmed load in the displayed window is packed and handed off, check each one, choose **Complete confirmed orders…**, and review the batch deduction and audit.

6. **Farm Knowledge Log**
   - Open **Knowledge Log** and select the seeded mildew lesson.
   - Point out the separated observation, action, result, follow-up, and confidence.
   - Search `What worked for mildew on this rose last August?`—no query syntax required.
   - Filter the reusable `mildew` tag, then show **Record a lesson** and its farm-language prompts.

7. **Day closeout** (Dashboard quick action)
   - End-of-day sheet: fulfilled orders + revenue, harvest today, open CONFIRMED leftover, low stock.  
   - Export PDF for the barn board; REST: `/api/closeout` and `/api/closeout/report.pdf`.

8. **Sync History**
   - Show HARVEST_LOG / ORDER_FULFILL / connector rows.  
   - Failures only filter; export audit CSV or **Export PDF…** (FAIL rows highlighted).

9. **Reports**
   - Weekly PDF (GUI or `curl -o weekly.pdf http://localhost:8080/api/reports/weekly.pdf`).

10. **Connectors (offline dual-mode)**
   - **Export Farmbrite** → `data/farmbrite-mirror.json`.  
   - **Sync Shopify** / **Export Square** with default local mirrors (no API keys).  
   - Same for Sheets / Airtable / Webhook dry-run payload file.

11. **Irrigation & Care**
   - **Refresh (try live weather)** → Open-Meteo for Port Orchard (or climatology offline).  
   - Priority badge + 7-day precip / ET when live; active beds from recent harvests.  
   - Dashboard may show a **WATER** alert in dry months.

12. **Rose Visualizer**
   - Grow L-System, mutate, save ruleset, optional PNG — “fun technical depth.”

---

## 3. Auth demo (optional, ~2 min)

The authenticated profile intentionally has no built-in passwords. In PowerShell,
generate temporary credentials for this run and keep the demo bound to this
workstation:

```powershell
$ownerPassword = [Convert]::ToBase64String(
  [Security.Cryptography.RandomNumberGenerator]::GetBytes(18))
$handPassword = [Convert]::ToBase64String(
  [Security.Cryptography.RandomNumberGenerator]::GetBytes(18))
$viewerPassword = [Convert]::ToBase64String(
  [Security.Cryptography.RandomNumberGenerator]::GetBytes(18))
$env:FLOWERFARM_AUTH_USERS = "owner:$ownerPassword:OWNER,hand:$handPassword:HAND,viewer:$viewerPassword:VIEWER"

Write-Host "owner password:  $ownerPassword"
Write-Host "hand password:   $handPassword"
Write-Host "viewer password: $viewerPassword"

java -jar target/flowerfarm-manager-1.2.0-SNAPSHOT.jar `
  --spring.profiles.active=demo,auth `
  --server.address=127.0.0.1
```

| User | Password | Show |
|------|----------|------|
| `owner` | Value printed for `$ownerPassword` | OWNER — full; clear audit allowed |
| `hand` | Value printed for `$handPassword` | HAND — write ops; cannot clear history |
| `viewer` | Value printed for `$viewerPassword` | VIEWER — connectors + harvest/CRM/inventory writes blocked |

**Account → Who am I?** / **Switch user…**  
REST from the same PowerShell session:

```powershell
$pair = "owner:$ownerPassword"
$b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair))
Invoke-RestMethod http://localhost:8080/api/auth/me -Headers @{
  Authorization = "Basic $b64"
}
```

---

## 4. REST smoke (PowerShell)

```powershell
# Health
Invoke-RestMethod http://localhost:8080/actuator/health

# Dashboard snapshot
Invoke-RestMethod http://localhost:8080/api/dashboard | ConvertTo-Json -Depth 5

# Harvest week series
Invoke-RestMethod http://localhost:8080/api/harvest/week

# Order week revenue (realized + pipeline)
Invoke-RestMethod http://localhost:8080/api/orders/week

# Connectors + dual-mode flags
Invoke-RestMethod http://localhost:8080/api/connectors | ConvertTo-Json -Depth 4

# Audit (failures)
Invoke-RestMethod "http://localhost:8080/api/connectors/history?success=false&limit=20"

# Plain-language farm knowledge search
Invoke-RestMethod "http://localhost:8080/api/knowledge?q=What%20worked%20for%20mildew%20on%20this%20rose%20last%20August"

# Kitsap irrigation advice (climatology = no network)
Invoke-RestMethod "http://localhost:8080/api/irrigation/advice?live=false" | ConvertTo-Json -Depth 4

# Market day packing list (CONFIRMED orders for today)
Invoke-RestMethod "http://localhost:8080/api/market-day" | ConvertTo-Json -Depth 5
# Printable packing PDF
Invoke-WebRequest "http://localhost:8080/api/market-day/packing.pdf" -OutFile market-pack.pdf

# Bed / field production (last 7 days)
Invoke-RestMethod "http://localhost:8080/api/harvest/beds?week=true" | ConvertTo-Json -Depth 5
Invoke-WebRequest "http://localhost:8080/api/harvest/beds/report.pdf?week=true" -OutFile bed-prod.pdf

# Morning briefing (offline-safe by default)
Invoke-RestMethod "http://localhost:8080/api/briefing" | ConvertTo-Json -Depth 4
Invoke-WebRequest "http://localhost:8080/api/briefing/report.pdf" -OutFile morning-briefing.pdf

# Weekly PDF
Invoke-WebRequest http://localhost:8080/api/reports/weekly.pdf -OutFile weekly.pdf
```

With auth, reuse the generated owner password from section 3:

```powershell
$pair = "owner:$ownerPassword"
$b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair))
Invoke-RestMethod http://localhost:8080/api/auth/me -Headers @{ Authorization = "Basic $b64" }
```

Or use `scripts/demo-rest.ps1` from the repo root (app must already be running).

---

## 5. Talking points (why this exists)

- **Know what the farm can do today.** The dashboard points to work that needs attention; harvests update matching stock, and the market pick list compares confirmed orders with what is on hand.
- **Carry records forward.** A completed handoff deducts stock. Saved harvest and order records feed invoices, briefings, closeouts, and an activity trail instead of asking the grower to reconstruct the day from memory.
- **Keep the growing lesson.** Planting plans and expected stems stay distinct from actual harvests. Field notes, task actuals, and production history give the farm material to review next season.
- **Fit the pilot honestly.** Core desktop records work locally. The optional phone field screen connects to a running authenticated instance, and live connectors need provider credentials. The supported customer setup uses one farm workstation, not a shared multi-workstation database.

For a technical audience, the build uses Spring Boot 3, JPA, Swing with FlatLaf, JFreeChart, OpenPDF, and Flyway. Offline connector demos use local mirrors; live connector mode requires credentials. OWNER, HAND, and VIEWER roles are available when authentication is configured.

---

## 6. Data files after a full demo

| Path | Created by |
|------|------------|
| `data/flowerfarm.mv.db` | H2 default |
| `data/*-mirror.json` | Dual-mode connector exports |
| `data/webhook-last-payload.json` | Webhook dry-run |
| `weekly.pdf` | Report download |

For a repeatable first-run presentation, use a separate empty demo data folder next time. Preserve any folder containing working farm records.

---

## See also

- [README.md](../README.md) — setup & API  
- [CONNECTORS_SANDBOX.md](CONNECTORS_SANDBOX.md) — local mirrors + live keys  
- [GUI_ARCHITECTURE.md](GUI_ARCHITECTURE.md) — tab design history  
