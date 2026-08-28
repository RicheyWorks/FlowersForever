-- Added in 1.1 for pickup, delivery, and market-day fulfillment planning.
-- IF NOT EXISTS also supports databases briefly opened by a pre-migration
-- 1.1 development build where Hibernate may already have added the column.

ALTER TABLE customer_orders
    ADD COLUMN IF NOT EXISTS handoff_type VARCHAR(16);

UPDATE customer_orders
SET handoff_type = 'UNSPECIFIED'
WHERE handoff_type IS NULL OR TRIM(handoff_type) = '';
