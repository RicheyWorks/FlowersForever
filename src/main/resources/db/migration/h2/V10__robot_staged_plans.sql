-- Restart continuity for the single reviewed task staged on each robot.
-- Only the SHA-256 digest of the adapter plan token is persisted.
CREATE TABLE robot_staged_plans (
    robot_key VARCHAR(160) PRIMARY KEY,
    robot_id VARCHAR(120) NOT NULL,
    simulated BOOLEAN NOT NULL,
    lease_id VARCHAR(80) NOT NULL,
    plan_token_hash VARCHAR(64) NOT NULL,
    staged_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT ck_robot_staged_plan_expiry
        CHECK (expires_at > staged_at),
    CONSTRAINT ck_robot_staged_plan_token_hash
        CHECK (LENGTH(plan_token_hash) = 64)
);

CREATE INDEX idx_robot_staged_plan_expiry
    ON robot_staged_plans(expires_at);
