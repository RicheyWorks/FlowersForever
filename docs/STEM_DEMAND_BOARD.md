# Stem demand board

**File → Stem demand board…** (Rose & events, Wildflower farm, and Flower shop
workspaces) totals the stems your upcoming orders need, by crop and date, in one
buying and cutting list. **File → Buying list by event…** opens the same board
on its **By event** tab: the same dates, split by event.

## Where the numbers come from

There is no separate event record: an event is an order with its fulfillment
date. The board only reads saved records.

| Column | Source |
| --- | --- |
| Stems needed | **Confirmed** orders dated in the window (drafts only when *Include draft orders* is ticked). |
| From cooler | Live inventory in stems (`Per Stem`, `Per Unit`, each, or blank units). Shared across all orders, never counted twice. |
| To cut (forecast) | The season plan's projected harvest (not complete) from the first date up to each need date, after earlier needs. A forecast: confirm before promising. |
| Short | What neither covers: buy or substitute. |

Confirmed orders are covered before drafts, and earlier dates before later ones,
so a draft never takes stock a confirmed order needs.

## Which order lines count

- A line sold **in stems** counts directly (40 stems of Nootka Rose = 40 stems).
- A line **counted as a saved recipe** (Production Studio) counts each
  ingredient: stems per design × quantity ordered × (1 + recipe waste %),
  rounded up. Use **Count line as recipe…** on a *Not counted* line, for
  example "Centerpiece × 12" or "Bridal bouquet × 1".
- Anything else (bunches, buckets, designs without a recipe) is listed under
  **Not counted** with the reason. The board never guesses stems per bunch.

**Stop counting** removes a recipe link. Renaming the product on an order line
also removes its link. Deleting a recipe keeps the order and shows the line as
not counted. Each link change is recorded in sync history (`ORDER_LINE_RECIPE`).

## Buying list by event

The **By event** tab lists each event (an order on its date) with one row per
crop: stems needed, from cooler, to cut (forecast), short, and the action. The
*For* column says where the stems come from, such as `Centerpiece × 12` or
`Stems ordered`. Lines the board does not count stay with their event, marked
*Not counted*; select one and use **Count line as recipe…**.

The events split the same totals as *Stems by crop and date*; nothing is
counted twice. Cooler stock and planned harvest go to earlier dates first,
confirmed orders before drafts, and the lower order number first on the same
date. A draft event shows as `(draft)` and only appears when *Include draft
orders* is ticked.

## Export

- **Copy list**: plain text with the buy list first, then the cut list, then
  the full table and anything not counted.
- **Save CSV…**: `stem-demand-<from>-to-<to>.csv` with columns
  `Date,Crop,Stems needed,Draft stems,From cooler,To cut (forecast),Short,Action,Needed for`.
  Not-counted lines follow with Action `Not counted`. Cells that start with
  `=`, `+`, `-`, or `@` are prefixed with `'` so spreadsheets show them as text.
- **Copy by event**: plain text, one block per event with what to pull from
  the cooler, cut, and buy, then its not-counted lines.
- **Save by event CSV…**: `buying-list-by-event-<from>-to-<to>.csv` with columns
  `Date,Event,Status,Crop,Stems needed,From cooler,To cut (forecast),Short,Action,For`.
  Status is `Confirmed` or `Draft`; not-counted lines follow each event with
  Action `Not counted`.

The [florist availability export](FLORIST_AVAILABILITY_EXPORT.md) stays the
buyer-facing stock list; this board is the grower's own buying and cutting list.

## REST

Same roles as other `/api/**` reads and writes.

- `GET /api/production-studio/stem-demand?from=2026-10-12&days=14&includeDrafts=false`
  returns the board as JSON (from defaults to today; days 1–120, default 14).
- `GET /api/production-studio/stem-demand/csv` returns the same CSV with the same parameters.
- `GET /api/production-studio/stem-demand/events` returns the buying list by
  event as JSON (also in the board JSON as `events`), and
  `GET /api/production-studio/stem-demand/events/csv` the by-event CSV, with
  the same parameters.
- `PUT /api/production-studio/stem-demand/orders/{orderId}/lines/{lineId}/recipe`
  with `{"recipeId": 5}` counts a draft or confirmed order line as that recipe;
  `{"recipeId": null}` stops counting it.

## Not yet

Not built yet ([future ideas](FUTURE_IDEAS.md) #2 and #6): supplier names and
pack rounding (products have no supplier or pack size to round to), crop or
lot reservations (the board still reserves nothing), cooler lot age,
substitutions with recalculated cost, stems-per-bunch conversions (bunches stay
*Not counted*), recipe revision snapshots and printed workbench sheets, linking
recipes from the CRM order editor, and a harness or MCP tool for the board.
