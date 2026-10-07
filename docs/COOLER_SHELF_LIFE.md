# Cooler lot shelf-life review

Private-pilot helper for **which bucket to pack first**. It builds on harvest lot
codes, grade, loss, storage location, and the existing printable QR lot label.

## Grower path

1. Open **Help > Cooler lot review…**.
2. Lots with marketable quantity sort **overdue → due today → still OK**, then by
   harvest date (oldest first).
3. Select a lot to:
   - **Record review…** — recounted marketable stems, quality grade, additional
     cull/loss (inventory adjusts once through the normal harvest edit path), and
     an optional note.
   - **Save QR lot label…** — same 4×6 PDF as Harvest Log.
   - **Crop review interval…** — days between cooler checks for that crop, plus
     conditioning (`FRESH_CUT` 4d, `CONDITIONED` 7d, `WOODY` 10d). Crop-specific
     day overrides win over conditioning defaults. Farm default is 5 days.
4. A due or overdue review is a **farm reminder**, not a promise of vase life.

## Boundaries

- Finished lots with zero marketable stems are not kept on Harvest Log; write them
  off there after packing out. Cooler review always leaves at least one inventory
  unit on the lot.
- Lot-to-sale allocation (customer-level traceability) remains a later increment.
- Split/merge of cooler buckets is not part of this slice.

## Reminders

Overdue cooler lots also appear in **Help > Today’s reminders…** when present.
