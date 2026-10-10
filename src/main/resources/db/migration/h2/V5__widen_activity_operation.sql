-- Activity names now include farm workflows such as SEASON_PLAN_CREATE.
-- Keep enough room for readable, stable operation identifiers during upgrades.
ALTER TABLE IF EXISTS sync_history ALTER COLUMN operation VARCHAR(64) NOT NULL;
