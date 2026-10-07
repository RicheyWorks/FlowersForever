-- The visible desktop workspace is a UI choice, separate from business enterprises.
-- Upgraded installations retain the existing rose/flower interface by default.
ALTER TABLE farm_operation_settings
    ADD COLUMN workspace_code VARCHAR(32) DEFAULT 'ROSE_AND_EVENTS' NOT NULL;

-- Existing nonflower setups must remain valid on upgrade. The previous primary
-- enterprise is the best available signal for a starting workspace.
UPDATE farm_operation_settings
SET workspace_code = CASE primary_enterprise
    WHEN 'CUT_FLOWERS' THEN 'ROSE_AND_EVENTS'
    WHEN 'CANNABIS' THEN 'CANNABIS_FARM'
    ELSE 'CUSTOM_FARM'
END;

ALTER TABLE farm_operation_settings
    ADD CONSTRAINT ck_farm_operation_workspace CHECK (
        workspace_code IN ('ROSE_AND_EVENTS', 'WILDFLOWER_FARM',
                           'FLOWER_SHOP', 'CANNABIS_FARM', 'CUSTOM_FARM'));
