# Stem demand board

**File → Stem demand board…** (Rose & events, Wildflower farm, and Flower shop
workspaces) totals the stems your upcoming orders need, by crop and date, in one
buying and cutting list.

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

## Export

- **Copy list**: plain text with the buy list first, then the cut list, then
  the full table and anything not counted.
- **Save CSV…**: `stem-demand-<from>-to-<to>.csv` with columns
  `Date,Crop,Stems needed,Draft stems,From cooler,To cut (forecast),Short,Action,Needed for`.
  Not-counted lines follow with Action `Not counted`. Cells that start with
  `=`, `+`, `-`, or `@` are prefixed with `'` so spreadsheets show them as text.

The [florist availability export](FLORIST_AVAILABILITY_EXPORT.md) stays the
buyer-facing stock list; this board is the grower's own buying and cutting list.

## REST

Same roles as other `/api/**` reads and writes.

- `GET /api/production-studio/stem-demand?from=2026-10-12&days=14&includeDrafts=false`
  returns the board as JSON (from defaults to today; days 1–120, default 14).
- `GET /api/production-studio/stem-demand/csv` returns the same CSV with the same parameters.
- `PUT /api/production-studio/stem-demand/orders/{orderId}/lines/{lineId}/recipe`
  with `{"recipeId": 5}` counts a draft or confirmed order line as that recipe;
  `{"recipeId": null}` stops counting it.

## Not yet

This is the first slice of the multi-event buying list
([future ideas](FUTURE_IDEAS.md) #2 and #6). Still to come: supplier and pack
rounding on the combined need, explicit crop/lot reservations, cooler lot age,
approved substitutions with recalculated cost, stems-per-bunch conversions,
per-event grouping, recipe revision snapshots with printed workbench sheets,
and linking recipes from the CRM order editor.
