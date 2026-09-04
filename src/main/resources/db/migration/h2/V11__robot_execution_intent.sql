-- Durable handoff marker between reviewed-plan storage and adapter execution.
-- The original harness request id lets an exact retry resume after restart.
ALTER TABLE robot_staged_plans
    ADD COLUMN execution_request_id VARCHAR(160);

ALTER TABLE robot_staged_plans
    ADD COLUMN execution_requested_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE robot_staged_plans
    ADD CONSTRAINT ck_robot_staged_plan_execution_request
    CHECK ((execution_request_id IS NULL AND execution_requested_at IS NULL)
        OR (execution_request_id IS NOT NULL AND execution_requested_at IS NOT NULL));

CREATE INDEX idx_robot_staged_plan_execution_request
    ON robot_staged_plans(execution_request_id);
