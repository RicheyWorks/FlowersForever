# Ten-pass coding campaign

The owner requested ten focused passes on the highest coding concerns, then added
farm-specific product names and quantities: cannabis products need named varieties
and weight quantities rather than flower-stem defaults. This campaign starts from
`01367a26986b7db1fff62cfd25818a4f9670376a` in an isolated worktree.

Implemented passes:

| Pass | Change | Evidence |
| --- | --- | --- |
| 1. Stock integrity | Inventory uses decimal storage and exact arithmetic, up to six decimal places. Counted units require whole numbers. V49 preserves existing stock and records the stock contribution of older harvests. | `InventoryQuantityTest`, `MeasuredQuantityMigrationTest` |
| 2. Harvest and sales | Harvest add/edit/delete reverses its actual inventory contribution; historical imports contribute no stock. Measured sales deduct exact amounts, with transactional rollback and repeat-fulfillment protection. | `MeasuredFarmOperationsIT`, `HarvestServiceTest`, `OrderServiceTest` |
| 3. Quantity boundaries | Desktop, API, worker, CLI, CSV, Excel and connector mappings retain fractions. Malformed quantities are refused. Shopify outbound whole-count requirements are enforced without rounding. | `SpreadsheetQuantityTest`, `FieldMapperTest`, API and packing tests |
| 4. Farm vocabulary | All 25 farm types have product examples, categories and suitable units. Cannabis starts with named flower products and grams, with additional weight, plant and clone choices. | `FarmProductProfileTest` |
| 5. Entry screens | Inventory, add-item, harvest and phone capture use the selected farm profile. Combined farms retain each starter's own category and unit. Custom names and units remain editable. | All-farm Swing form tests and worker browser tests |
| 6. Samples and reports | Production inventory starts empty unless records are explicitly imported; sample stock is opt-in. Inventory and harvest summaries group by unit. PDF/CSV output preserves precise quantities. | `InventoryServiceTest`, `HarvestReportQuantityTest`, `ReportServiceTest` |
| 7. Recovery | Integrity and restore probes read every row and column in records tables. Failed restores receive unique recovery filenames even at the same clock time. | `IntegrityRestoreSafetyTest`, `RestoreStartupTest`, real backup/reopen/restore workflow |
| 8. Farm time | Services and report date presets use the farm's configurable, injectable clock and time zone. | `FarmReportClockTest` at opposite sides of a UTC date boundary |
| 9. Retry safety | Physical-count requests retain the unit reviewed; unit changes are refused under a transaction lock. Whole-count receipt fingerprints remain compatible. Precise counts replay once after reopening and restoring. | `MeasuredFarmOperationsIT`, worker queue tests |
| 10. Maintenance | Removed obsolete integer parsers and rounded-quantity copy; documented farm defaults, measured stock, sample opt-in and remaining domain limits. | Full Java verification, quality gates, Node and Python suites |

The real-data integration check uses separate temporary databases: harvest,
historical import, loss, sale, repeat fulfillment, physical count, backup, reopen,
restore and replay are exercised together. It does not restore over the owner's farm.

Compatibility and scope:

- Existing quantities are retained; changing a product's unit does not convert its
  amount. Grams and kilograms must be entered in the intended unit.
- V49 identifies historical harvest imports through durable import receipts.
  Older rows without those receipts cannot be distinguished from operational
  harvests; their reversal contribution follows the previous rounded-credit rule.
- Farm-specific notebooks and vocabulary are supported. Flower recipe and stem-demand
  tools still apply to flower products; specialized livestock, dairy, cannabis
  processing or regulatory workflows are not implied by a named starter.
- Legacy numeric analytics fields remain for compatibility. Unit-aware summaries
  should be used when products with different units are compared or totaled.
- Worker writes with durable receipts have tested replay protection. This does not
  establish universal crash-proof behavior for every generic automation mutation.

Verification results are recorded below after the final build completes. Real MSI
installation, production restore, signed release publication and hardware power-loss
acceptance are separate from these coding checks. The currently open sample pilot
uses the previously verified JAR; source edits do not modify that running app.
