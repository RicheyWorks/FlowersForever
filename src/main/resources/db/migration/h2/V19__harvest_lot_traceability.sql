ALTER TABLE harvest_log ADD COLUMN lot_code VARCHAR(64);
ALTER TABLE harvest_log ADD COLUMN harvest_grade VARCHAR(32) DEFAULT 'UNGRADED' NOT NULL;
ALTER TABLE harvest_log ADD COLUMN loss_quantity FLOAT(53) DEFAULT 0 NOT NULL;
ALTER TABLE harvest_log ADD COLUMN storage_location VARCHAR(128) DEFAULT '' NOT NULL;

UPDATE harvest_log
SET lot_code = 'FFH-' || REPLACE(CAST(harvest_date AS VARCHAR), '-', '')
        || '-' || LPAD(CAST(id AS VARCHAR), 8, '0')
WHERE lot_code IS NULL;

ALTER TABLE harvest_log ALTER COLUMN lot_code SET NOT NULL;
ALTER TABLE harvest_log ADD CONSTRAINT ck_harvest_grade
    CHECK (harvest_grade IN ('UNGRADED', 'SELECT', 'STANDARD', 'PROCESSING'));
ALTER TABLE harvest_log ADD CONSTRAINT ck_harvest_loss_quantity
    CHECK (loss_quantity >= 0 AND loss_quantity <= 2147483647);
CREATE UNIQUE INDEX uq_harvest_lot_code ON harvest_log(lot_code);
CREATE INDEX idx_harvest_storage_location ON harvest_log(storage_location);
