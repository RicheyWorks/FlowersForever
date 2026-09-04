-- Keep actual field work connected to the plan-generated task that prompted it.

ALTER TABLE production_tasks ADD COLUMN actual_minutes INTEGER;
ALTER TABLE production_tasks ADD COLUMN input_cost FLOAT(53) DEFAULT 0 NOT NULL;
ALTER TABLE production_tasks ADD COLUMN field_observation VARCHAR(1000);
ALTER TABLE production_tasks ADD COLUMN completed_at TIMESTAMP(6) WITH TIME ZONE;

ALTER TABLE production_tasks ADD CONSTRAINT chk_production_task_actual_minutes
    CHECK (actual_minutes IS NULL OR actual_minutes BETWEEN 0 AND 10080);
ALTER TABLE production_tasks ADD CONSTRAINT chk_production_task_input_cost
    CHECK (input_cost BETWEEN 0 AND 1000000);
