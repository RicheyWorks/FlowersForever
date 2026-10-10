-- Preserve all existing whole counts while allowing exact measured stock (e.g. 3.5 grams).
ALTER TABLE inventory_items ALTER COLUMN quantity DECIMAL(16, 6) NOT NULL;
ALTER TABLE inventory_items ADD CONSTRAINT chk_inventory_quantity_max
    CHECK (quantity <= 2147483647);

-- Old harvests credited rounded quantities, including weights. Preserve those actual
-- contributions when old harvests are corrected or undone after this upgrade.
ALTER TABLE harvest_log ADD COLUMN inventory_contribution DECIMAL(16, 6);
UPDATE harvest_log SET inventory_contribution = ROUND(quantity);
-- Durable historical imports never credited stock.
UPDATE harvest_log SET inventory_contribution = 0
WHERE id IN (SELECT target_record_id FROM farm_data_import_receipts
             WHERE record_type = 'harvests' AND action = 'import-harvest-history');
-- Direct SQL evidence imports likewise do not adjust inventory.
ALTER TABLE harvest_log ALTER COLUMN inventory_contribution SET DEFAULT 0;
ALTER TABLE harvest_log ALTER COLUMN inventory_contribution SET NOT NULL;
ALTER TABLE harvest_log ADD CONSTRAINT chk_harvest_inventory_contribution
    CHECK (inventory_contribution >= 0 AND inventory_contribution <= 2147483647);
