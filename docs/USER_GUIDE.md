# Flower Farm Manager desktop guide

This guide follows the everyday desktop workflow: check the farm, record the
morning cut, prepare customer orders, pack for market, and close the day.

## Access roles

- **OWNER** can add and change records, run imports and exports, and clear the
  activity history.
- **HAND** can add and change inventory, harvests, customers, and orders. A hand
  can view and export activity history but cannot clear it.
- **VIEWER** can search, filter, review, print, and export. Editing controls are
  disabled and identify the access needed.

Open **Account → My access** at any time to review the current role. In local
mode the app provides full OWNER access.

## Start the morning

1. Open **Dashboard** and choose **Refresh overview**.
   The inventory, harvest, sales, packing, and watering snapshot loads in the
   background. If another app-wide refresh arrives while it is loading, one
   newer snapshot is queued instead of running competing queries. A failed
   refresh keeps the last good values and leaves inline retry guidance.
2. Read **Operations alerts** for low stock, orders ready to pack, beds needing
   attention, and watering guidance.
3. Review the ranked priority queue, then use **Open priority** to jump directly
   to the selected resolving workflow. The most urgent task is selected first;
   the line below the queue explains what to do, where it opens, and whether it
   is urgent, next up, or a follow-up. Priority actions lock while the farm-wide
   snapshot refreshes so an older recommendation cannot be started as if it
   were current. If refresh fails, the last good priority is clearly marked and
   can still be opened for verification in its destination workspace.
   Low-stock priorities carry the named products into a focused Inventory queue
   with **Return to Dashboard**. Draft-order priorities open the complete Draft
   queue in Customers & Orders with the same return route, rather than leaving
   the worker to recreate either filter.
   Market Day priorities keep the current market date, scope, packing list, and
   checked customer loads intact and provide **Return to Dashboard**.
   Watering priorities open the real **Irrigation & Care** workspace—not a stale
   alias—and preserve its forecast mode, field plan, and completed checks behind
   **Return to Dashboard**.
   Missing-harvest priorities open Harvest Log’s **Single entry** workspace with
   crop entry focused. Any unfinished harvest draft remains intact, and **Return
   to Dashboard** restores the ranked task queue.
4. Choose **Morning briefing** for the printable daily checklist. When startup
   work is waiting, its ranked **Start** queue selects the most urgent job first
   but lets you choose any task. The detail line identifies its urgency and
   destination before **Start selected task** opens the resolving workspace. A
   low-stock task carries its exact product queue into Inventory, so the worker
   does not need to recreate the filter. Choose **Return to Dashboard** from the
   focused-queue banner after the stock review, then rebuild the briefing to
   confirm the task is resolved.
   A Market Day task opens the retained packing workspace with the same Dashboard
   return route; it never silently replaces the worker’s current date or scope.
   Briefing preparation runs in the background; Morning briefing and Day
   closeout lock together until the snapshot returns so repeated clicks cannot
   launch overlapping farm-wide queries. The daily-sheet badge keeps the result
   or retry guidance visible. PDF export also runs in the background and writes
   through a temporary file before replacing the selected destination, so a
   renderer or write failure does not silently leave a partial daily sheet.
5. Use the quick actions to open the next relevant screen. **Log harvest** opens
   the retained Single entry form, and **Market packing** opens the retained
   packing scope; both provide **Return to Dashboard** and keep unfinished work.

Open **Irrigation & Care** to turn the current weather recommendation into
field work. Check each item in the **Field checklist** as it is completed; the
progress count makes remaining work visible during a handoff. Refreshing the
same plan keeps its checks for the current app session, while a changed plan
starts a new checklist. If live weather is unavailable, the screen clearly
marks the checklist unavailable and keeps the standing maritime notes visible.
If a refresh fails after a plan has loaded, the last plan and completed checks
stay on screen with a warning to verify current conditions. Repeated refreshes
coalesce so only the newest live-weather or climatology request runs next.

Open **Trend Analysis** when planning next week's harvest capacity. The forecast
uses weekly totals from the real Harvest Log for the crop and unit with the
strongest history. It never substitutes demo quantities. At least three
separate recorded weeks are required; otherwise the screen explains exactly
what history is missing. Use the projected output and planning guidance with
weather, bed conditions, crop maturity, committed orders, and the Market Day
packing plan—not as an automatic production decision.

## Record a harvest

Open **Harvest Log**. For one cut, complete the date, crop, quantity, unit, and
optional bed and notes, then choose **Add harvest**. Quantity starts at `1`, and
inventory increases automatically. The form badge distinguishes a new entry,
a loaded edit, and a copied entry; pressing `Enter` in Notes submits the correct
add-or-save action. **Keep harvest context** is on by default for repeated cuts:
after a save it retains the date, crop, unit, and bed while resetting quantity
to `1` and clearing notes. Turn it off when the next cut is unrelated.

If a crop name has no matching Inventory product, harvest logging creates the
SKU so the cut is not lost, but its price and cost start at zero. The Harvest
Log shows a persistent **new Inventory SKU needs setup** warning for single and
batch entry. Choose **Review new Inventory SKU(s)** to open those exact products,
complete their price, cost, unit, and notes, then return to Harvest Log.

Single-entry adds and edits save in the background from a captured form and edit
target. While the inventory-coupled transaction runs, the single and batch
mutation controls lock so Enter, buttons, or a second entry mode cannot submit
the same cut twice. The form resets only after the harvest and inventory update
succeed. If either fails, every field, the loaded edit target, and **Keep harvest
context** choice remain available for retry.

Invalid single-entry values stay in place and are handled inline: the exact
field is highlighted, keyboard focus moves to it, and the entry badge explains
the correction. Fix the value and submit again; no dismissible error window
interrupts repeated harvest entry.

For a morning cut with several crops, use **Batch entry**. Its visible Date,
Default unit, Default bed / field, and Notes fields apply to every row unless a
line supplies its own unit or bed. Enter one `Crop,Quantity[,Unit][,Bed]` row per
line and review the live row count, totals, and rounded inventory impact before
choosing **Log batch harvest**. After a successful save, the lines clear to
prevent duplicate logging while the visible batch defaults remain ready for the
next group; a failed save preserves the complete batch for retry.

Deleting a harvest also runs in the background because it reverses that cut's
inventory quantity. The selected harvest, entry controls, and report actions
lock until the combined deletion and inventory reversal finishes, preventing a
second reversal. Success clears the deleted target and refreshes harvest and
inventory views. If either part fails, the original row stays selected and the
entry badge reports that no reversal result was received, so the worker can
review the reason before retrying.

Harvest dates may be today or earlier. A future date is rejected before any
harvest row or Inventory quantity changes; correct the highlighted date on the
single-entry form. Batch entry keeps every line in place and disables logging
until that shared date is corrected.

Harvest history records decimals exactly, while inventory is counted in whole
units. Enter at least `0.5`; inventory rounds each saved harvest to the nearest
whole unit. For example, `1.5 lbs` remains `1.5 lbs` in production history and
adds `2 lbs` to inventory.

For a morning batch, use **Batch entry**. Enter one line per crop in this form:

```text
Crop,Quantity[,Unit][,Bed]
Nootka Rose,40,stems,Bed A
```

The readiness badge checks the batch while you type, shows exact line numbers
for problems, and summarizes the rows and quantities that will be logged. When
any row uses a decimal, it also previews the whole-unit inventory increase and
labels that adjustment as rounded by row. The
log button remains unavailable until every row is valid. After a successful
batch, the entry area clears to prevent the same cut from being logged twice.
Logging runs in the background, so the rest of the app remains responsive; the
batch controls lock until it finishes to prevent a duplicate submission. If the
save fails, every entered line stays in place and the readiness badge explains
that nothing was logged and can be tried again.

Use the filters to review a crop, bed, note, or date range. Click a history
column heading to sort the work list, then select or double-click a row to edit
it. The form badge names the exact saved harvest targeted by Load, Duplicate,
Edit, and Delete even while the visible order is sorted. Deleting a harvest asks
for confirmation and removes its amount from inventory. The status area confirms
each inventory adjustment.

A malformed harvest **From** or **To** date highlights only that field; a range
ending before it starts highlights **To**. The last good history, totals,
selection, and entry work remain visible until the corrected range is applied.

Harvest history, production totals, and recent-crop choices reload together in
the background. If another refresh arrives while one is loading, the older
snapshot is discarded and one newer load runs next. A failed reload keeps the
current rows, totals, filters, selection, and entry details visible with an
inline retry message; row mutations remain locked until the visible snapshot is
current.

The **Reports & exports** menu uses the currently reviewed date range or visible
harvest rows. CSV exports, bed-production grouping, printable harvest-log
assembly, and PDF rendering run in the background. Report controls lock while
one captured job is running, preventing duplicate files or competing database
queries. The header reports progress and the final row/bed count. A failure
keeps the harvest rows, filters, selection, and entry form intact with a visible
retry state; correct a highlighted report date before trying again.

## Keep a farm lesson

Open **Knowledge Log** when something is worth remembering: a problem, an
opportunity, or a practice the crew may want to repeat. Choose **Record a
lesson**, then describe what happened and what you noticed. Crop, variety, bed
or field, production stage, action, action owner, result, and follow-up date add
useful context without turning the entry into a technical report.

Choose confidence honestly:

- **Needs review** means the result is not known yet.
- **Early signal** means it worked once or the evidence is still limited.
- **Promising** means the farm has a useful result worth trying again.
- **Proven on this farm** means repeated farm records support the practice.

Add comma-separated tags the farm already uses, such as `mildew`, `irrigation`,
`timing`, `pests`, `harvest`, or `market`. FlowersForever normalizes tags so the
same idea stays grouped. Optional saved-record numbers can connect a lesson to
one harvest, inventory item, order, or customer. If one of those operational
records is later removed, the farm lesson remains and only its link is cleared.

The search box accepts ordinary language. A question such as **What worked for
mildew on this rose last August?** finds matching words across the crop,
observation, action, result, confidence, and tags, and understands the month as
a date filter. Use **Follow-up due** for lessons whose review date is today or
earlier. Select a result to read the facts, action, result, and confidence in
separate sections.

HAND and OWNER sessions may record, edit, or remove lessons. VIEWER sessions
keep search and reading access but cannot change the farm record. Removing a
lesson never changes its linked harvest, inventory, order, or customer.

## Count physical inventory

Open **Inventory**, search or sort the list into the order you want to count,
and select the first product. Choose **Record stock count** and enter the
quantity physically on hand. The adjustment badge shows whether inventory will
increase, decrease, or remain unchanged before anything is saved.

Inventory reloads run in the background. If an app-wide refresh arrives while
one is already loading, the older result is discarded and one newer reload is
run next. Existing rows, search, filters, and selection remain available after
a failed reload, with an inline retry state; count, edit, and delete actions stay
locked until the displayed snapshot is current.

Low-stock and price-list reports also build in the background from a single
inventory snapshot. While a report or export is running, its controls are
locked to prevent duplicate work, but the current rows, search, sorting, and
selection stay visible. A failed job restores the controls and keeps that
working state intact for retry.

The selected-product badge names the exact product and current quantity targeted
by Count, Edit, and Delete. Selection stays attached to the saved product when
the table refreshes or when two products share the same display name, rather
than relying on a row position that may have changed.

Edits and confirmed deletions run in the background against that captured
product identity. The table, filters, refresh, and mutation actions lock until
the database responds, preventing a double submission or a changed sort from
redirecting the action. A successful edit reloads and reselects the updated
product; a successful deletion clears the selection only after confirmation.
If an edit fails, every edited field is retained and **Edit selected** reopens
the complete draft for retry. If deletion fails, the product remains selected
and the badge explains that no deletion result was received.

Choose **Save & count next** to continue through the visible list without
reselecting each product. The workflow follows the current search and sort, so
workers can count one cooler, category, or market load at a time. Each count is
saved in the background while the captured product and queue position are
locked, preventing a slow save from being submitted twice. The next product
opens only after authoritative inventory reloads successfully. If a save fails,
the same product stays selected and the entered physical quantity is retained
for retry.

## Manage customers and orders

Open **Customers & Orders**.

Customer, filtered-order, and inventory-choice refreshes run in the background.
Customer **Search** (including Enter in the search field) and customer **Show all**,
as well as order **Apply filters**, **Show all**, **Ready to complete**, **Needs
handoff review**, and order handoffs all use that same background path, so a slower
database does not freeze typing or navigation. If a worker changes a customer
search or order filter while a load is already running, CRM queues one newer
snapshot and discards the older result instead of briefly showing stale rows.
If an app-wide refresh arrives while one is loading, the older snapshot is
discarded and one newer load runs next. Existing rows, filters, selection, and
unfinished order-entry details remain available after a failed refresh, with an
inline retry message. Customer and order mutations stay locked until the visible
snapshot is current.
When an empty order result still has a status, handoff, customer, or date filter,
the empty state says that the filters matched nothing and provides **Show all
orders** in place. That one action clears every order filter while preserving the
unfinished new-order and product entry. Press `Alt+L` for the same safe recovery
from anywhere in the Orders workspace. If the unfiltered list is also empty, the
message changes to **No orders yet**, hides the unnecessary reset action, and
directs the worker to select or add a customer before creating the first Draft.
Product, quantity, unit, and price previews use that captured inventory snapshot,
so typing never waits on the database. The preview still updates immediately to
show the line total, exact product match, quantity on hand, or expected shortfall.
Because Inventory stores whole units, each product line must round to at least one
inventory unit. A matched SKU must also use the same unit shown in Inventory;
the form names the required unit and keeps saving disabled until it is corrected.

Use the **Handoff** filter to isolate Pickup, Delivery, Market, or **Needs review**
orders without changing the customer or date search. Choose **Needs handoff
review** for the complete correction queue: CRM selects Open orders, clears
customer and date limits, and shows only Draft or Confirmed legacy orders whose
handoff is still Unspecified. **Show all**, lifecycle queue handoffs, newly created
order selection, and exact-order handoffs reset the handoff filter so required work
cannot remain hidden. The REST equivalent is
`GET /api/orders/filter?status=OPEN&handoff=UNSPECIFIED`.

Use **Ready to complete** for the complete, current fulfillment queue. CRM clears
customer, date, and handoff limits, selects Confirmed, and shows only orders that
have a specific Pickup, Delivery, or Market handoff, at least one saved product,
quantities that round to valid inventory units, matching order and inventory
units, and enough aggregate stock in the captured inventory snapshot. Ready work
is ordered by earliest pickup/delivery/market date, then order ID, and each ready
order reserves its product quantities for the rest of that displayed queue. This
prevents two individually covered orders from both appearing ready when their
combined demand exceeds stock. The result badge also counts confirmed orders that
were excluded. Select **Needs attention**
in the **Status** filter and apply it to review those excluded orders; its
guidance points workers back to handoff, products, quantities, units, and stock.
Selecting an otherwise-valid order that lost coverage to earlier work shows a
**queue stock gap**, names the quantity short after earlier reservations, and
advises completing the earlier ready orders first or replenishing Inventory.
The readiness choice survives background refreshes. Use **Show all**, an exact
order handoff, a lifecycle queue, or **Needs handoff review** to leave the readiness
queue. Refresh Customers & Orders after resolving inventory so readiness uses the
new stock snapshot; transactional completion still performs the final authoritative
check before changing inventory.

1. Add or select a customer in the upper workspace.
   Customer names are unique regardless of capitalization. If a matching account
   already exists, no duplicate is created and every entered customer detail stays
   in the form. Choose **Review existing customer** to find and select that exact
   account without discarding the draft, or enter a unique name and retry.
   In the order workspace, **Customer & delivery context** immediately shows the
   selected new-order customer's account type, contact person, phone, email, and
   shortened account note. A missing-contact message does not block Pickup or Market,
   so walk-up customers remain valid. A Delivery order cannot be confirmed or completed
   when the customer has no contact person, phone, email, or account delivery note and
   the order also has no saved delivery instruction. When **Delivery** is selected, this
   panel shows live **Delivery readiness**: it names customer contact, the account delivery
   note, or the new-order instruction as the source making the handoff ready, and changes
   immediately as the instruction is typed. If every source is absent it shows **Missing**
   before the Draft is saved, so the worker knows exactly what confirmation will require.
   Draft creation remains available so incomplete phone orders can be retained safely.
   Choose **Open customer** to add
   usable account context, or save an explicit destination or handoff instruction on
   the order, then refresh and review it again. The context and customer selection
   remain attached to the unfinished new-order draft across refreshes and while a
   saved order is temporarily selected. Choose **Open draft customer** (`Alt+V`)
   to select that exact account in Customer accounts before adding a missing phone,
   email, contact, or delivery note. The handoff never creates an order and preserves
   the draft customer, date, handoff, instructions, product, quantity, unit, and
   price. A contextual banner remains above Customer search while the account is
   reviewed or saved. Choose **Return to unfinished order entry** (`Alt+R`) to go
   straight back and focus Product; manually opening Orders also dismisses the
   completed return handoff.
2. Create a draft order with its first product in the lower workspace. Desktop
   orders always start as Draft so the complete saved order can be reviewed before
   it becomes eligible for packing; the read-only **Starting status** control makes
   this explicit.
   Choose the required **Handoff** type: **Pickup**, **Delivery**, or **Market**.
   New desktop orders default to Pickup, so change it before saving when a driver
   or market load is responsible. Older orders created before this field existed
   remain compatible and appear as **Needs review** in the order table and
   Unspecified in historical handoff documents. They cannot be confirmed or
   completed until Pickup, Delivery, or Market is saved.
   Set **Order date** to the actual pickup, delivery, or market date; it defaults
   to today and accepts `YYYY-MM-DD`. This date determines which delivery window
   and Market Day packing list includes the order. The date and **Notes** are
   checked while you type: an invalid date or more than 2,000 instruction
   characters disables **Create & select order**, preventing a mouse submission.
   Enter or `Ctrl+Enter` highlights the exact field without starting a save and preserves
   the customer, handoff, first product, quantity, unit, price, and instructions
   for correction. Historical dates remain available for corrections, but the
   confirmation review explicitly warns when the handoff date is in the past.
   Before saving, CRM checks in the background for Draft or Confirmed orders
   belonging to that customer on the same handoff date. If one exists, the review
   names its order number, status, handoff, product count, and value. Choose
   **Open selected order** to bind the unsaved first product to that exact order,
   reload its current details, and review it before adding anything. When several
   active orders match, select the intended order from the review. Choose **Create
   separate order** only for an intentional split; **Keep draft** creates nothing
   and preserves the customer, date, handoff, instructions, and complete
   first-product entry. The final save locks that customer and rechecks the exact
   active-order set reviewed by the worker. If another workstation creates or
   changes the same-customer/date queue between the review and save, no order is
   created, the complete draft stays available, and CRM refreshes for another
   duplicate review. An intentional split is accepted only while the exact orders
   shown in its review remain current, including their customer contact, account
   delivery note, status,
   handoff, date, instructions, products, quantities, units, and prices.
   Choose an inventory product
   to fill its exact name, unit, and current price, or enter a custom product
    manually. The line preview shows the total and
   warns about missing SKUs or likely stock shortfalls before you save. Enter a
   unit price of `0` for a complimentary sample or donation; the preview shows
   `$0.00` and still checks the physical quantity against Inventory. Blank,
   negative, or out-of-range prices keep the save action disabled with a clear
   correction message. Product validation is field-specific while you type: the
   exact product, quantity, unit, or price field receives an error outline and an
   accessible explanation without moving keyboard focus. Product names may contain
   up to 255 characters, units up to 64, quantities must be greater than zero and
   no more than 2,147,483,647, and unit prices must be between $0 and
   $1,000,000,000. Correcting one value immediately clears its marker or moves the
   marker to the next value needing attention; no submission is required to see it.
3. Choose **Create & select order**. The saved order remains selected, so use
   **Add product to selected order** for each additional product without finding
   the order again. The target badge names the selected order, customer, status,
   handoff type, product count, and total. After the exact saved order reloads,
   keyboard focus moves to the re-enabled Product field—not while it is still
   locked by refresh—and its accessible instruction announces that `Ctrl+Enter`
   submits the next reviewed product. **New order details** and **Product entry** are kept
   visually separate. While a Draft or Confirmed order is selected, its product
   editor stays active but the new-order customer, date, status, and notes are
   visibly locked; those retained draft values are not applied to the selected order. Choose
   **Start another order** to unlock them and return to new-order mode intentionally.
   Unfinished product entry is also target-bound: selecting another saved order
   pauses the current product values and opens that order's own entry. Reselect the
   original order to restore its exact product, quantity, unit, price, and any saved-
   line correction. **Start another order** does not silently carry a selected
   order's product into the separate new-order draft.
   Each later-product save is also bound to the exact displayed customer, order
   date, status, instructions, and complete saved-product list. If another worker
   changes or deletes that order after entry begins, the product is not added and
   inventory is unchanged. CRM refreshes the saved order, keeps the entered
   product for that order when it is still available, and asks the worker to
   compare the current load before choosing **Add product** again.
   If the order was deleted while the product was saving, CRM shows **Order #…
   missing · product preserved** and leaves every entered product value visible.
   Create/Add and `Ctrl+Enter` remain locked so the entry cannot silently become
   a different order. Choose **Move product to new order** to deliberately reuse
   it, then review the customer, delivery date, and instructions before creating
   the replacement. **Clear product entry**, then **Discard product entry**, removes
   it intentionally instead. If a separate
   new-order product draft already existed, it is paused and restored after the
   recovered entry is saved or cleared.
   Completed and Cancelled orders are read-only: their inventory picker and product,
   quantity, unit, and price fields are disabled while their saved products remain
   available for review. If another worker completes or cancels an order while a
   product draft is open, the draft remains visible and cannot be submitted to that
   immutable order. Choose **Move product to new order** to transfer it deliberately,
   then verify the new customer and delivery context, or choose **Clear product
   entry** to discard it. A pre-existing new-order product draft is paused and
   restored afterward.
   After the first product is saved, CRM focuses the order list on that exact
   Draft customer and date before reloading it. This keeps future-dated and
   previously filter-hidden orders visible and selected for the next product.
    While this exact-order reload is pending, the status explicitly says the order
    is already saved so it must not be submitted again. If reload fails or the
    exact customer-and-date view does not contain it yet, CRM shows a durable
    **saved once** receipt and locks the cleared new-order and product fields.
    **Refresh CRM** is the only available recovery action and repeats the same
    instruction for assistive technology; never recreate the order.
   The customer context directly below the target shows the account type, contact
   person, phone, email, and a shortened account note. It remains copyable on a
   compact screen. Choose **Open customer** to review the full account without
   losing the selected order or unfinished product entry. If that account is
   already visible, CRM selects it immediately while refreshing the full customer
   list in the background. If a customer search had hidden it, CRM opens the
   customer workspace first and selects the exact linked account when the background
   refresh returns. Keyboard focus moves to the selected customer row, and the
   customer badge announces the exact account and preserved order work. If the
   refreshed search still excludes the account, focus moves to **Customer search**
   and the badge directs the worker to **Show all**. A failed reload keeps the order
   and product draft intact, announces the exact account target, focuses **Refresh
   CRM**, and retains the handoff for that refresh to retry. The contextual
   **Return to order #…** button (`Alt+R`) restores the originating saved-order row
   and keyboard focus without clearing its unfinished product entry.
   While adding another product, the line preview includes matching quantities
   already saved on the selected order. For example, `6 already saved + 4 entered`
   is checked as `10 required`, so a repeated product cannot appear covered when
   only the new line fits. The preview also subtracts inventory reserved by earlier
   completion-ready Confirmed orders in the complete fulfillment queue, following
   handoff date and then order number. This reservation snapshot is deliberately
   separate from the visible CRM filters: an earlier commitment still reserves its
   stock when a customer, date, handoff, or status filter hides its row. The preview
   names the reserved inventory units beside the resulting
   coverage or shortfall, so a line cannot appear available merely because another
   customer load has not yet deducted its committed stock. The product can still be
   saved as a future commitment, but resolve the named shortfall before completion.
   It also warns **Already saved on this order** and names
   the existing line count. The first click or `Ctrl+Enter` pauses without writing
   and changes the action to **Add separate product line**. Review **Saved products**,
   then choose that explicit action again only for an intentional price, grade, or
   allocation split. Any edit to product, quantity, unit, or price, a different
   selected order, or refreshed saved-order details cancels the approval, so stale
   or accidental second submissions cannot reuse it. When
   correcting a saved product, the old quantity is
   replaced rather than counted twice. A shortfall remains a planning warning—the
   product can be saved for a future commitment—but it must be resolved before
   completion deducts inventory.
   If **Refresh CRM** finds that the selected order changed while a new product
   entry was unfinished, the product stays bound to that order and the Add control
   changes to **Review changed order before adding**. The review shows the originally
   reviewed and current customer, contact, account delivery note, date, handoff,
   status, instructions, and
   saved products. Acknowledging the review does not add anything or change inventory;
   choose **Add product to selected order** afterward only when the preserved product
   still belongs on the changed order.
    After a product saves, CRM reloads and reselects that exact order, clears the saved
    entry, and returns focus to Product for the next line; **Ctrl+Enter** adds the next
    reviewed line without reaching for the mouse. If the saved order is outside the
    refreshed filters or cannot reload, the cleared form and warning explicitly say the
    product was saved once. Product entry and every order-changing action stay locked,
    including Confirm and Complete, so stale pre-save order data cannot be used. Choose
    **Show all** or correct the filters, then use **Refresh CRM**, whose accessible
    guidance repeats the saved-once warning. Do not enter the product again, which would
    create a duplicate line.
   The order-stock badge checks all saved lines together, including repeated
   products, against the captured inventory snapshot. A cumulative shortage or
   missing inventory match locks completion. Choose **Resolve order stock** to
   open Inventory with only the blocking products, correct their counts, then
   choose **Return to order #…** from the focused-queue banner and **Refresh CRM**
   to verify the order again. Older imported orders can contain a quantity below
   one rounded inventory unit. This appears as an order packing issue and locks
   completion; choose **Correct saved quantity** to load the exact saved line,
   enter a valid quantity, and save it. If a saved order unit differs from the
   matching Inventory unit, the badge names both values and offers **Correct order
   unit**. It loads the first affected saved line, prefills Inventory's canonical
   unit, and leaves the correction unsaved for review; choose **Save product
   changes** only after confirming that unit is right. If several lines disagree,
   repeat the exact correction after each refreshed order. Genuine count shortages
   and products with no Inventory match continue to use **Resolve order stock**.
   The transactional fulfillment check also
   rejects such a line if the UI is bypassed. Confirmation remains
   available for future commitments; only inventory-deducting completion is gated.
4. To change who handles a saved commitment, select the order, choose its exact
   **Selected handoff type**, and choose **Save handoff type**. Use Pickup for a
   customer collection, Delivery for a farm delivery, and Market for a market
   load. Legacy Unspecified orders can be corrected here. Unspecified is display-only:
   it cannot be saved onto new work or used to advance an order. To move the commitment,
   correct **Selected order date** and choose **Save order date**. Draft and
   Confirmed orders can be corrected; Completed and Cancelled handoff details
   remain historical and read-only. Either save refreshes Market Day, invoices,
   packing documents, dashboard summaries, and other farm views without changing
   inventory.
5. To correct pickup, delivery, packing, or customer instructions, select the
   order, edit **Pickup / packing instructions**, and choose **Save order
   instructions**. This editor always loads the selected order's saved value; the
   **Notes** field in the new-order form remains a separate unfinished draft and
   is never used to update an existing order. Draft and Confirmed instructions
   can change, while Completed and Cancelled instructions remain historical and
   read-only. The selected date, handoff, instructions, saved-products, and more-actions
   labels are explicitly associated with their controls for screen readers and other
   assistive technology. If you inspect another order before saving a handoff, date, or
   instruction correction, the app keeps that draft with its original order and restores it
   when you return. Saving refreshes invoices, packing lists, Market Day, and other farm
   handoffs without changing inventory. Each save compares the locked database
   value with the handoff type, date, or instructions originally loaded into the editor. If
   an ordinary CRM refresh finds a newer saved value while your correction is unfinished,
   your draft and its original reviewed value stay together. The relevant Save control
   changes to **Review newer saved date**, **Review newer saved handoff**, or **Review
   newer saved instructions** and shows the exact saved value beside your preserved draft.
   That review click only acknowledges the newer value; it does not save anything. The
   normal Save control returns afterward, so choose it only if your draft should replace
   the value you just reviewed. After a date, handoff, or instruction save succeeds,
   CRM reloads the exact order before allowing any other order change. If that reload
   fails, both order badges retain the exact value with a durable **saved once** receipt.
   Product entry, further corrections, confirmation, completion, cancellation, and
   deletion remain locked; **Refresh CRM** is the only safe retry action. Do not save
   the correction again or act from the retained pre-save row. A successful reload
   restores the authoritative saved value and unlocks the workspace without changing
   inventory. If
   another worker saved a newer value first, FlowersForever refuses to overwrite
   it, refreshes the order table, and keeps your entered correction for comparison.
   The error names the newer saved handoff, date, or instructions and confirms that no order
   or inventory value changed. Review both values before choosing whether to save
   your correction again.
6. If a saved quantity, unit, product, or price is wrong, choose it under **Saved
   products**, select **Edit selected product**, correct the entry fields, then
   choose **Save product changes**. To abandon the unsaved correction, choose
   **Clear product entry** once to review the consequence, then choose **Discard
   product entry** while every field and target is still unchanged. Any edit or
   order-selection change cancels that discard approval. Pressing Enter in Unit
   price submits the same context-aware action:
   create a new order, add a product, or save the selected product edit. To discard
   a line instead, choose **Remove selected product**. Editing and removal are
   available for Draft and Confirmed orders before completion. A Confirmed order
   must retain at least one product, so add the replacement first when removing
   its only line. Both actions refresh the order total, stock check, Market Day,
   and other farm views in the background but do not add inventory back—the stock
   was never deducted. Wait for the destination's current-status badge before
   acting on the refreshed data. Completed and
   Cancelled orders remain read-only. Product edits are bound to both the exact saved
   line and the complete order the worker reviewed, including customer, date, handoff,
   status, instructions, account context, and all saved products. Removals use the same
   complete-order binding and their confirmation names the exact quantity, unit, price,
   customer, date, handoff, status, and instructions. If another worker changes the line
   or order context first, nothing is overwritten or removed and inventory remains
   unchanged. CRM refreshes the order while preserving the unfinished correction in
   the entry fields. Compare
   the current customer, delivery context, instructions, and **Saved products**, then
   save again only if the correction is still intended for that refreshed order.
   For removal conflicts, review the refreshed product and choose **Remove selected
   product** again only if that newer line should still be deleted.
   After a correction or removal succeeds, CRM reloads that exact order before any
   other order change is allowed. If the reload fails, the saved-product status keeps
   a durable **updated once** or **removed once** receipt even though the old row may
   still be visible. Product entry, correction, removal, confirmation, completion,
   and the other order-changing controls stay locked. Use **Refresh CRM** as the only
   safe retry action; do not re-enter the correction or remove the product again.
   Once the exact refreshed order returns, the controls unlock and the receipt confirms
   that inventory was not changed. If the refreshed filters hide the order, the receipt
   still confirms the one committed operation; use **Show all** or adjust the filters
   before reviewing it.
7. Confirm a draft when it is ready for packing. The confirmation review names
   the exact order, customer, pickup or delivery date, customer contact, account
   delivery note, and every
   saved product's quantity, unit, unit price, and line total, plus the order
   total, instructions, and current stock state. The review is scrollable so a
    large multi-product order remains complete and usable on a compact screen.
   The order table includes a **Handoff** column so Pickup, Delivery, Market, and
   **Needs review** commitments can be distinguished before selection. Confirmation
   stays locked for **Needs review** until an exact operational handoff is saved.
   Confirming makes the order eligible
   for packing and Market Day date windows but does not deduct inventory. The
   database compares the exact reviewed customer/contact and account delivery note,
   status, date,
   instructions, and canonical product list under the order's write lock. If
   another worker changes any reviewed detail while the confirmation is open,
   the order stays Draft and the badge requires a CRM refresh and fresh review;
   unseen changes are never confirmed.
   A successful confirmation produces a durable **confirmed once · ready to pack**
   receipt. If the refreshed Confirmed order remains visible, CRM keeps it selected.
   If a Draft-only queue correctly hides it, the receipt directs workers to **Show
   all** or the Confirmed filter. If the exact-order reload fails, retry **Refresh
   CRM** only. Confirmation never deducts inventory, and the saved status has already
   changed—never confirm the order again.
   A legacy, imported, or partially corrected draft with no saved products stays
   locked in Draft status. The order badge shows **0 products**, and the Confirm
   action directs the worker to enter and save at least one product first. The
   service also rejects new empty Confirmed orders if another interface bypasses
   the desktop checks. If older imported data already contains one, Market Day
   labels it as an empty confirmed order, disables its packed check and completion,
   and provides **Open empty order #…** to correct that exact order. Batch completion
   performs the same check before changing any order or inventory.
   Confirmation and completion
   stay locked while the selected order has an unsaved product entry, product
   correction, handoff change, date change, or instruction change. Use the matching Save action,
   restore the saved handoff, date, or instructions, or use the two-step **Clear
   product entry** / **Discard product entry** action. Confirm and Complete remain
   locked after the first click, and any field or target change cancels approval.
   The button guidance names the unfinished work; nothing is silently discarded.
8. Choose **Complete order** only when the products leave the farm; inventory
   is deducted at that point. If its saved handoff date is in the past, the first
   click pauses with **Overdue handoff · completion paused** and changes the action
   to **Review overdue completion**. Nothing is written. Verify that this exact
   pickup, delivery, or market handoff really happened late and still belongs on
   the selected order. Otherwise correct the date or cancel the abandoned order.
   A product entry, date/handoff/instruction edit, selection change, refresh, or
   saved-order change cancels the approval. A second unchanged action opens the
   normal completion dialog with an overdue warning.
   If its saved handoff date is still in the future,
   the first click pauses with **Future handoff · completion paused** and changes
   the action to **Review early completion**. Nothing is written. Verify that the
   physical pickup, delivery, or market load actually happened early, or correct
   the date. Any product entry, date/handoff/instruction edit, selection change,
   refresh, or saved-order change cancels approval. Only a second unchanged action
   opens the normal completion dialog, which repeats the early-completion warning.
   Before the write, a scrollable review names the
   exact customer, contact, account delivery note, pickup or delivery date,
   instructions, every saved
   product and price, order total, current stock coverage, and the rounded number
   of inventory units that each line will deduct. Cancel if the physical handoff
   or any saved detail is not exact. Completion then runs in the background, and
   the chosen order plus its filters lock until the result returns so a slow
    inventory update cannot be submitted twice. Live stock is rechecked before
    inventory changes. The same locked snapshot protection used for confirmation
   rechecks the customer/contact, account delivery note, status, date, instructions,
   and canonical
   product list before any decrement. If another worker changed the reviewed
   load, the order stays Confirmed and no inventory changes. CRM automatically
   reloads that exact selected order and current inventory, then the badge says
   **Order changed · review refreshed order**. A live-stock rejection follows the
   same recovery path: stale availability is replaced before Complete can become
   available again, and the refreshed stock badge explains the current shortage.
   Other completion failures keep the order selected, reload current guidance,
   and retain the exact reason in the badge. If that automatic reload fails, use
   **Refresh CRM** before attempting completion again.
   A successful completion produces a durable **completed once · inventory
   deducted** receipt. If the refreshed Completed order remains visible, CRM keeps
   it selected. If a Confirmed-only queue correctly hides it, the receipt says the
   order is outside the current view and directs workers to **Show all** or the
   Completed filter. If the exact-order reload fails, retry **Refresh CRM** only.
   The Refresh CRM control repeats that safe recovery instruction for keyboard and
   assistive-technology users. In every case, inventory was already deducted
   once—never choose Complete or deduct the order again.
   The service applies the same handoff and Delivery-context gates to desktop, REST,
   and batch lifecycle calls, so an Unspecified order—or a Delivery with no customer
   contact, account delivery note, or explicit order instruction—cannot change status
   or deduct inventory even if the interface is bypassed. A context-free confirmed
   Delivery is also rejected during direct creation before the order is saved. REST
   order creation accepts `handoffType` as
   `PICKUP`, `DELIVERY`, or `MARKET`; it is required when creating a Confirmed order.
   Correct an open Draft or Confirmed order with `PATCH /api/orders/{id}/handoff`
   and a body such as `{"handoffType":"DELIVERY"}` before advancing it.
9. Save an invoice, customer statement, or filtered order export as needed.

Customer statements, invoice PDFs, and filtered or full order CSVs use captured
customer/order snapshots and run in the background. The document badge reports
preparation, row/order counts, saved location, and retry guidance; document
controls lock until the current job finishes so a slow report cannot be started
twice. A failure keeps customer and order rows, filters, selection, and unfinished
entry work intact. If the chosen destination already exists, the app asks before
replacing it.
Order CSVs are operational handoff files, not just accounting totals. Every row
includes the pickup/delivery/market date, structured handoff type, customer,
contact person, phone, email, account delivery note,
status, complete product summary with quantities, prices, total, and handoff
instructions. Invoice text and PDFs use the same explicit date and handoff labels
and place the account delivery note with the bill-to / ship-to context.
This lets a packing or delivery worker identify the load and contact the customer
without reopening CRM. Because these exports contain customer contact details,
store and share them as customer data.

Customer names and order dates are required. Email is optional; when supplied,
it must look like an email address. An invalid order date is highlighted with a
`YYYY-MM-DD` example and is never sent to the database. A rejected customer,
date, or product line stays in the form so only the highlighted field needs
correction. These expected checks stay inline without opening a blocking error
window. Changing the highlighted customer or order value clears the stale error
and marks that draft ready to retry.

Order-list date filters use the same recovery pattern. A malformed **From** or
**To** date highlights only that field, while a reversed range highlights **To**.
The current order results remain visible until the corrected range is applied.

Adding or editing a customer, creating an order with its first product, and
adding each later product to the selected order run in the background. While a
save is underway, that entry form and its submit actions lock so pressing Enter
or clicking again cannot create a duplicate. For later products, the selected
order also locks so the line cannot drift to a different customer. The status
badge identifies the customer, order, or product being saved. After success,
the form clears and the saved order is selected only when the database confirms
the write. If saving fails, every entered field and the selected order remain
intact, the badge explains the reason and retry state, and the controls unlock
for another attempt. A **changed · product not added** result is not a generic
retry: review the refreshed customer, date, lifecycle status, instructions, and
saved products first. This protects against adding a product from an older screen
after another worker confirms, corrects, cancels, fulfills, or deletes the order.

After a customer add or edit commits, CRM must reload authoritative customer data
before another customer mutation is allowed. If that reload fails, the customer
badge keeps a durable **saved once · reload required** receipt and locks add, edit,
delete, and form changes. Choose **Refresh CRM** only; do not submit the customer
again. A successful reload selects the saved account when it is visible, or reports
that it is outside the current customer search without repeating the write.

Confirmed customer deletion runs in the background against the captured account
ID. The customer table and form lock until the database responds, so changing a
selection or clicking twice cannot delete another account. Success clears the
selection and any edit form loaded for that customer only after database
confirmation. If linked orders or another storage rule rejects deletion, the
account remains selected, the loaded form remains intact, and the customer badge
explains what must be reviewed before retrying. If deletion commits but CRM cannot
reload, a durable **deleted once · reload required** receipt keeps all customer
mutations locked. Use **Refresh CRM** only; never repeat the deletion.

Confirming, cancelling, saving notes, and deleting an order also use one
captured-order background action. Selection, filters, entry fields, and all
lifecycle actions lock until the database responds, preventing a slow save from
being repeated or applied to a newly selected order. Successful actions refresh
the order and keep it selected when it still exists; deletion clears the target
only after confirmation from the database. A failure leaves the same order and
unfinished notes in place with the reason and retry action in the completion
badge. A stale confirmation specifically reports that no status changed, keeps
the exact draft selected, and automatically reloads its current customer,
handoff, date, instructions, products, and stock guidance. Open a new confirmation
review only after the badge says **review refreshed draft**. If that automatic
reload fails, Confirm stays locked until **Refresh CRM** succeeds.

After cancellation or deletion commits, CRM must reload before another order
change is allowed. If that reload fails, the completion and selected-order badges
retain a durable **cancelled once** or **deleted once** receipt and **Refresh CRM**
is the only safe retry. A retained pre-cancellation row or deleted row is stale:
do not confirm, complete, cancel, delete, or otherwise change it. Cancellation
unlocks when the refreshed Cancelled order returns or a successful filtered reload
confirms it is outside the current view. Deletion unlocks only after a successful
reload removes the permanently deleted order. Neither action changes inventory.

**Cancel selected order** and **Delete selected order** stay locked while the
selected order has an unsaved product entry, date, handoff type, or instruction
change. Save each intended change or explicitly restore or clear it first. The
destructive review uses saved order data only, so it never silently treats a
visible draft as part of the order being cancelled or deleted. Once the visible
work is resolved, either action opens
a scrollable consequence review containing the exact customer, contact, account
delivery note, status,
pickup or delivery date, instructions, products, prices, and total. Cancellation
explains that the order leaves active packing and Market Day work without an
inventory adjustment; deletion explains that every line is permanently removed
and cannot be recovered. The database locks and compares that complete reviewed
snapshot immediately before either action. If anything changed, the order is
neither cancelled nor deleted, inventory remains unchanged, and the current order
refreshes for a new review. The badge says **review refreshed order** only after
the exact customer, status, handoff, date, instructions, products, prices, and
total are current. If that reload fails, the matching Cancel or Delete action
stays locked until **Refresh CRM** succeeds. Never repeat the destructive action
until every refreshed detail has been checked in a new consequence review.

Lifecycle rules are shown before a destructive review can start. A completed
order cannot be cancelled or deleted because its inventory deduction and sales
history must remain intact. An already-cancelled order cannot be cancelled a
second time, but it may still be permanently deleted after the exact consequence
review. The disabled **Run action** control explains the applicable rule and
routes completed-order research to its invoice or activity history instead of a
doomed database action.

Friendly status labels—Draft, Confirmed, Completed, and Cancelled—are used
throughout the desktop even though saved data remains compatible with older
versions.

## Prepare market day

Open **Market Day**, choose the market date and delivery window, and select
**Build packing list**. The summary warns about product shortfalls. If nothing
is ready, the summary uses a neutral **no work in scope** state rather than claiming
that stock is OK, and explains how to create, preview, or confirm an order. Choose
**Review draft orders** to open Customers & Orders with every Draft visible and
date limits cleared; confirm eligible work, then return and rebuild the list.
**Include unconfirmed drafts** adds upcoming demand to the planning value and aggregate
pick list, but a Draft-only shortage is labeled as preview demand and never locks
completion of covered confirmed orders. **Include completed orders** is historical
reference only: completed products are not counted again as active pipeline or
stock demand. The status line separately reports visible orders, active pipeline,
and the confirmed count and value actually ready to complete.

Packing-list builds run in the background, so a slow order or inventory query
does not freeze data entry. If another build is requested while one is loading,
the app discards the older result and applies one snapshot using the newest
date and scope. A failed refresh keeps the reviewed plan, packed checks, scope,
and shortfall guidance available for retry. Exports and completion use that
displayed snapshot; their controls stay locked until the current build finishes.
After a list has loaded, changing the market date, window days, Draft scope, or
completed-order scope immediately marks that list **Packing scope changed ·
rebuild required**. The previous list and packed checks remain visible for
reference, but its checkboxes, shortage handoff, export, and completion controls
lock so staff cannot act on the old window under the newly displayed scope. A
malformed date is highlighted inline and disables **Build packing list**. Correct
it using `YYYY-MM-DD`, then rebuild; unchanged loads retain their checks while
new, changed, and out-of-scope loads are reviewed again.

As each customer load is physically packed, check that order in the
**Confirmed load-out** checklist. Completion stays locked until every confirmed
customer is checked. Loads are grouped in a stable working sequence: **Needs
handoff review**, **Market**, **Delivery**, then **Pickup**. Within each group,
the service orders work by handoff date, customer, and order number. **Next
unpacked** follows that same sequence. Each row shows that customer's products and delivery notes;
it also names the structured Pickup, Delivery, Market, or legacy Unspecified type
and the exact handoff date, which is especially important
when the packing scope covers several days. When the customer record includes
them, it also shows the contact person, phone,
and email so pickup or delivery questions can be handled without leaving the list.
Multi-product loads show one product per line. Their customer card grows to its
complete product, contact, and instruction content inside the scrollable queue
instead of clipping it to a fixed height. **Open order #…** stays below the
details, so it remains reachable without forcing the checklist wider on a compact
screen.
The progress badge shows what remains. A legacy Unspecified confirmed order is a
blocking packing issue because staff cannot safely decide where its load belongs.
Every confirmed customer card has a **Ready to pack** or **Blocked** badge. A
blocked card names the exact missing handoff, empty order, invalid quantity, unit
mismatch, or aggregate confirmed-stock gap affecting that customer's products.
Delivery is also blocked when the customer has no contact or account delivery note
and the order has no saved instruction. Choose **Add delivery context #…** to open
that exact order, add usable customer or destination context, return to Market Day,
and rebuild the list. The service repeats this check immediately before batch
completion, so a legacy or imported context-free Delivery cannot bypass the
checklist or deduct inventory.
Its packed checkbox stays disabled, while unrelated customer loads with clear
checks remain available so useful packing work can continue. **Next unpacked**
skips blocked loads and the progress badge separately counts packed, ready
remaining, and blocked work.
On compact screens, the checklist heading, progress badge, and action buttons use
separate stacked rows, so **Next unpacked** and **Clear checks** remain available
without a wrapped toolbar being clipped.
Choose **Review handoff #…**, save Pickup, Delivery, or Market on that exact order,
return to Market Day, and rebuild the list. A packing-issue summary names the
affected products and the first exact correction. If a saved quantity rounds
below one inventory unit, choose **Correct quantity #…** to open that order,
automatically select the affected saved product, and load its correction fields.
Correct and save its quantity; the save starts a fresh Market Day snapshot. Return
and wait for the packing-list status to say it is current. Use **Build packing
list** only if that refresh failed or you changed the packing scope. This remains
exact even when the order contains the same product more than once.
For an order/Inventory unit mismatch, choose **Review unit #…** to open the exact
order with the affected saved product selected and loaded into the correction
fields. Compare its saved unit with the Inventory unit named in the guidance:
correct the saved product when the order is wrong, or return and correct Inventory
when the catalog is wrong. Returning without changing those automatically loaded
fields clears the untouched edit; a correction you actually typed remains saved as
an unsaved per-order draft. Draft-preview quantity and unit issues route to their
exact Draft without locking otherwise-ready confirmed completion. Genuine stock
shortfalls still use **Resolve stock shortfalls**, which opens Inventory as a
focused queue containing only the affected products. Availability uses an exact,
case-insensitive SKU name match—the app never treats stock for a similarly named
product such as **Rose** as stock for **Spray Rose**. Demand uses the same
per-line whole-unit rounding as final fulfillment and combines every occurrence
of an SKU before comparing it with stock. Confirmed-order quantity or unit issues
block only affected loads and completion until the corrected list is rebuilt.
The same contact context—including the customer account delivery note and the
order-specific instruction—is included in packing PDFs, plain-text plans, JSON,
and CSV exports. The desktop preserves that account delivery note in the immutable
background-export snapshot, so a destination reviewed on screen cannot disappear
from the saved handoff file. A printed plan also carries the context-free Delivery warning.
Treat exported packing files as customer data and store them accordingly.
The queue survives the
inventory reload, selects the first affected product, and clears when you type
a normal search, choose **Low stock only**, or use **Clear search**. When the
count or replenishment work is finished, choose **Return to Market Day** in the
focused-queue banner; the packing list and its reviewed checks remain available.
If a requested product has no Inventory record, the missing-product warning
names it instead of showing an unexplained empty queue. Select the missing SKU
and choose **Add missing product**. Add Item prepares that name only when its
form is blank, keeps any unfinished item draft intact, and provides **Return to
stock queue** after the catalog item is saved.
If a refresh introduces a stock or order issue after a load was checked, that
affected check resets; unchanged, still-ready customer loads keep their checks.
Known stock gaps lock order completion and prevent affected loads from being
marked packed.
After counting or replenishing the queued products, return to Market Day and
**Build packing list** again. Completion unlocks only when that current stock
check is clear and every confirmed load is checked.
If a customer load needs clarification, choose **Open order #…** beside that
customer. Customers & Orders opens with the exact order selected and clears
filters that could hide it; review or correct its date, instructions, or products,
then choose **Return to Market Day**. The current packing scope and checks remain
available so you can rebuild the list only when the order actually changed.
Rebuilding the same list keeps packed checks for orders whose details have not
changed. Changing only the handoff type also resets the packed check, preventing a
load moved between Pickup, Delivery, and Market from remaining incorrectly marked
packed. New, changed, or out-of-scope work is never treated as packed: its check
is reset and the checklist explains what was retained or removed. If an order
changes after the checklist was built, the app requires a fresh review instead
of completing newly changed work silently. Immediately before the first batch
write, the app rechecks every reviewed customer load, the full confirmed-order
scope for that Market Day window, and aggregate inventory against live data. If
a new order was confirmed in the window, or if a product, quantity, price, date,
instruction, customer contact, account delivery note, status, or stock count changed,
no order is
completed. The completion
badge says **Packing review changed · rebuild required**, retains the current
checks for reference, and directs the worker to **Build packing list**; unchanged
orders keep their checks while changed loads must be checked again.

After the physical load-out is checked, choose **Complete confirmed orders** and
review the exact, scrollable customer-load list. It names every checked confirmed
order, pickup or delivery date, contact, account delivery note, product quantity
and price, instruction, and the confirmed-only total. This keeps the destination
visible in the final irreversible review when an account note is the Delivery
order's only usable handoff context. Draft and already-completed orders included for
reference are explicitly excluded from that total and completion. Cancel if any
detail does not match the physical load. Completion runs in the background while
the rest of the app remains responsive; the captured scope and packing checks
lock until it finishes so the same load cannot be submitted twice. The completion
transaction also locks and rechecks each order against the exact customer,
contact, account delivery note, date, instructions, status, and canonical product
snapshot captured by
the final preflight. This closes the gap where another worker changes a later
order while an earlier order is completing. That changed order fails without a
status or inventory change and remains named in the result handoff; orders that
already completed remain saved. The completion badge reports completed and failed
counts. Successful orders stay completed,
while any failed confirmed orders remain available for a targeted retry. A compact
completion-result handoff remains visible under the controls and lists every
customer outcome and failure reason without truncating longer batches. Completed
orders are already saved and leave the active list after refresh; failed confirmed
orders retain their packed checks. Correct each named reason, rebuild the list if
order or stock details changed, and complete only the confirmed orders still shown.

If completion was saved but the refreshed list fails or still contains the
pre-completion confirmed loads, completion stays locked. The retained receipt says
the orders completed once and inventory was deducted once. Use **Build packing
list** only; never complete the retained checklist again. The lock clears only
when a refreshed confirmed queue proves those exact completed order IDs left it;
another order with the same queue count cannot clear the lock. After a partial
result, only the failed confirmed orders remain eligible. These identities come
from the saved batch outcome itself rather than wording displayed in the receipt.

Packing lists can be saved as text, CSV, or PDF by every role. **Complete
confirmed orders** is a write action: review the confirmation carefully because
it completes every eligible order and deducts inventory.

Each packing export uses the currently reviewed list as a fixed snapshot and
runs in the background, so workers can keep checking customer loads while the
file is prepared. Export controls lock until that one file finishes to prevent
duplicates. Existing files require confirmation before replacement, and a
failed export keeps both the previous destination and every packed check intact
for retry.

## Inventory and reports

Use **Inventory** to search stock, edit a selected product, review low-stock
quantities, create a price list, or export the visible rows. Use **Add Item** for
a new product; price and cost start at `0.00`, and quantity starts at `0`. Edit
opens the complete item in one dialog, so a validation error never loses changes
already made to other fields.

Add and Edit enforce the same domain limits before saving. An out-of-range price
or cost, overlong name/category/unit/notes value, or invalid quantity highlights
the exact field while every other entered value remains in place for correction.
Add Item reports these checks inline without opening a blocking error window.
Changing the highlighted value clears its stale error state and marks the draft
ready to retry; submit again when the correction is complete.

**Export visible CSV** captures the current filtered and sorted rows before it
starts writing, so later screen changes cannot alter the file halfway through.
Commas, quotation marks, and line breaks in product data are quoted correctly.
Reports and exports ask before replacing an existing destination file.

When entering several similar crops, bouquets, or supplies, select **Keep setup
for next item** before saving. Category, selling unit, price, and cost remain for
the next record; item name, variety, quantity, and notes always clear so product-
specific facts are not copied accidentally. **Reset form** still clears every
field back to the safe defaults.

Adding an item runs in the background from one captured draft. The form and its
submit shortcuts lock until that save finishes, preventing an Enter key and
button click from creating two products. A successful save clears the form only
after Inventory accepts the item. If saving fails, every field and the **Keep
setup for next item** choice remain exactly as entered for retry.
Product names are unique without regard to capitalization. If **Add Item** finds
an existing name, it saves nothing, highlights the name, and preserves every
entered field. Choose **Open existing item** to open that exact Inventory row,
then record a stock count, replenish it, or edit its product details instead of
creating an ambiguous duplicate SKU.
Renaming an existing product follows the same rule. A conflicting edit keeps the
original row unchanged and retains the complete edit draft. Choose **Edit
selected** to enter a unique name, or **Review existing SKU** to focus the row
that already owns that product name.

Choose **Low stock only** to build a replenishment/count queue for products with
10 or fewer on hand. The lowest quantity is selected first; **Record stock
count** and **Save & count next** then move through only the visible low-stock
products. Search can narrow that queue further without turning the mode off.

Open **Reports** to create the standard seven-day report or a custom inclusive
date range. Dates use `YYYY-MM-DD`. The live period summary confirms the number
of included days and keeps **Create selected report** unavailable until both
dates form a valid range of no more than ten years. Report generation runs in
the background, so other farm tabs stay responsive. Failures remain visible in
the report workspace with a retry instruction. A missing `.pdf` extension is
added automatically, and replacing an existing file requires confirmation;
cancelling the save or replacement never overwrites the existing file.

In **Reference → Rose Visualizer**, the inventory-product selector reloads in
the background so an app-wide refresh does not interrupt other farm work. If a
new refresh is requested while one is loading, only the newest product snapshot
is applied. A failed refresh keeps the current product choices, linked growth
preview, and variety presets available; choose **Refresh products** to retry.
An empty selector explains that a flower or rose product must first be added in
Inventory, while the built-in variety presets remain usable.

In **Reference → Rose Varieties**, **Add sample Nootka Rose** first checks the
current inventory in the background. A slow database cannot freeze the variety
guide or submit the sample twice. If Nootka Rose already exists, no duplicate is
created; use **Open Inventory** to review or edit that product. A failed save
keeps the current guide search and filters and restores the sample action for
retry.

## Close the day and recover mistakes

Return to **Dashboard** and choose **Day closeout**. Review completed sales,
confirmed work, harvest totals, low stock, and remaining priorities before
saving the sheet. When operational follow-up remains, the ranked **Resolve**
queue lists confirmed orders first, then the harvest log, low stock, and
cancelled-order follow-up. Select any item to see exactly which workspace opens,
then choose **Open selected follow-up**. A low-stock follow-up opens Inventory
with the exact products already queued; **Return to Dashboard** preserves the
handoff after the count is finished. A cancelled-order follow-up opens
Customers & Orders with **Cancelled** selected and date limits cleared, so older
customer commitments remain visible. Rebuild closeout after resolving the item
to check readiness. Closeout preparation stays off the interface thread,
so the dashboard remains responsive while its farm-wide snapshot is assembled.

Open **Activity History** to trace imports, exports, harvest changes, and order
actions. Filter **Needs attention** when an operation failed, then select a row
to read its complete message, diagnostic details, and recovery guidance. For
harvest, inventory, order, market-day, irrigation, and report activity, **Open
related workflow** jumps directly to the screen where the cause can be reviewed.
When audit detail contains a saved harvest or order ID, the destination clears
filters that could hide it and selects that exact record. After the destination
checks current data, the status bar confirms the selection or explains that the
saved record is no longer available. Exact harvest and order reviews also show
**Return to Activity History**, preserving the filtered activity list and selected diagnostic.
An order-status entry
that records a cancellation still opens the complete Cancelled order queue so
related commitments can be reviewed together.
Status-focused queues opened from Dashboard, Market Day, or Activity History also
show a contextual return button. Customer and date filters are cleared for the
complete queue, while the originating checklist or diagnostic view stays intact.
Connector-only failures instead identify the credentials, network, mapping, or
destination checks required before retrying. OWNER can clear the entire history
after a confirmation; HAND and VIEWER retain read and export access.

CSV and PDF exports capture the currently filtered activity rows before running
in the background. The PDF preview is built from that same fixed snapshot.
Export controls lock to prevent duplicate files, but filters, diagnostics, and
related-workflow navigation remain available. Existing destinations require
replacement confirmation; a failed export preserves both the previous file and
the reviewed activity state for retry.

Timeline rows, summary counts, source choices, and activity choices reload as
one background snapshot. A newer filter or app-wide refresh supersedes an older
in-flight result instead of issuing competing UI-thread queries. Failed reloads
keep the existing timeline, filters, selection, and diagnostic inspector visible
with inline retry guidance. Export and OWNER clear actions stay locked until the
displayed snapshot is current.

When a form cannot be saved, read the highlighted field's tooltip, correct the
value, and retry. Errors preserve the current form wherever possible. Destructive
actions name the affected record and require confirmation.

Closing the desktop or choosing **Sign out** checks Customers & Orders for
unfinished product entries (including drafts paused on other orders), unsaved
saved-order date, handoff, or instruction corrections, unfinished new-order
customer, date, handoff, or instructions, and writes still running in the background. The warning lists the
remaining work and defaults to **Keep working**. Choose **Discard and exit** or
**Discard and sign out** only when losing those unsaved fields is intentional;
otherwise return to CRM and save or explicitly clear each draft first.
The same guard includes checked Market Day loads that have not completed, a
batch completion still running, and a packing export still writing. Successful completed-order IDs are not reported
as unfinished, while checks retained for failed or not-yet-completed loads remain
protected. When both workspaces contain unfinished work, the prompt lists both.
To intentionally abandon local Market Day progress, choose **Clear checks**. The
first click keeps every checkbox and explains the consequence; **Discard checks**
clears only the unchanged unfinished load checks. It never clears completion
evidence for orders already saved, and it does not change orders or Inventory.
In new-order mode, **Reset new-order details** provides a guarded two-step discard
for changed customer, date, handoff, and instructions. The first click keeps the values and
explains the consequence; **Discard new-order changes** restores the last accepted
customer, date, and handoff and clears instructions while preserving every
product-entry value. If that accepted customer was removed during refresh, reset
returns to no customer instead of silently substituting another account. Use the separate **Clear product entry** action when
the product draft itself should also be discarded.

## Keyboard use

- `F5` refreshes the desktop.
- `Alt+1` through `Alt+9` opens the corresponding navigation item.
- Underlined field labels expose `Alt` keyboard mnemonics.
- In Customers & Orders, `F6` moves between the order list and Product entry.
  Each destination is scrolled into view on compact screens and announced in the
  app status. In the list, use the arrow keys to choose an order; press `F6` again
  to return to Product. When no orders are visible, `F6` goes directly to the
  new-order Product field. During a save or refresh, `F6` uses whichever destination
  remains enabled. If both are locked, focus stays where it is and the status says
  to wait for the current result before pressing `F6` again.
  `Ctrl+Enter` submits the current product context from anywhere in the Orders
  workspace: it creates the first order, adds a later product, or saves a product
  correction. If several values need attention, it highlights, selects, and reveals
  the first invalid field in entry order so it remains visible on compact screens.
  Correct it and press `Ctrl+Enter` again. Repeating the shortcut while a save is
  running is safely ignored. After any order write has committed but its exact CRM
  reload is still pending or has failed, `Ctrl+Enter` cannot validate or submit
  retained fields. It repeats the saved-once, **Refresh CRM only** receipt instead,
  including for created orders, added or corrected products, order corrections,
  confirmations, cancellations or deletions, and completions.
- Order action mnemonics include `Alt+N` for a new order, `Alt+A` to add a
  product, `Alt+M` to confirm, `Alt+P` to complete, `Alt+W` to start another
  order, `Alt+Y` for the Ready to complete queue, `Alt+G` for Needs handoff
  review, `Alt+V` to open the draft customer, `Alt+T` to save a selected order's
  date, `Alt+F` to save its handoff, `Alt+B` to open the current blocking stock,
  quantity, or unit correction, `Alt+L` to clear filters and show all orders without
  losing entry, `Alt+E` to edit the selected saved product, `Alt+O` to open the exact
  product-removal review, and `Alt+X` to clear the product entry.
  These stay distinct from `Alt+R` for Product and `Alt+H` for the new-order
  Handoff selector. The four primary create/add/confirm/complete actions use a
  two-row grid so their full labels remain usable on compact screens. The usual
  validation, pending-work locks, and completion confirmation still apply.
- In Market Day, `Alt+D` focuses Market date and `Alt+W` focuses Window days.
  Use `Alt+B` to build, `Alt+N` to move focus to the next unpacked customer,
  and Space to check that load after it is physically packed. Repeat `Alt+N`,
  Space through the load-out sequence. `Alt+V` opens the current stock/order
  correction handoff, `Alt+R` reviews Drafts, `Alt+E` exports, and `Alt+C` opens
  the final completion review. Scope shortcuts are `Alt+U` for unconfirmed
  Drafts and `Alt+O` for completed-order reference. These actions retain the
  same stale-scope, shortage, confirmation, and duplicate-submit protections as
  mouse actions.
- Press `Enter` in supported search and date fields to apply. In Add Item,
  Quantity submits; in Harvest or Customer forms, the last field adds or saves
  according to whether an existing row is loaded. In Customers & Orders, Unit
  price creates an order, adds a product, or saves product changes according to
  the selected order and product-edit state.
- In tables, use the arrow keys to move between rows. Actions apply to the
  selected row; double-click loads a row on screens that support editing.
