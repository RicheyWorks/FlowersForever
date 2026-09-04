-- Persist the farm's loaded field-labor rate so task actuals can roll up into
-- reproducible planting and season production costs.

ALTER TABLE farm_season_settings
    ADD COLUMN loaded_labor_rate_per_hour FLOAT(53) DEFAULT 0 NOT NULL;

ALTER TABLE farm_season_settings
    ADD CONSTRAINT chk_farm_loaded_labor_rate
        CHECK (loaded_labor_rate_per_hour BETWEEN 0 AND 10000);
