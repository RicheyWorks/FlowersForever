-- Durable, exclusive control authority for one stable robot identity.
-- Only SHA-256 token digests are persisted; plaintext capability tokens are never stored.
CREATE TABLE robot_control_leases (
    robot_key VARCHAR(160) PRIMARY KEY,
    robot_id VARCHAR(120) NOT NULL,
    simulated BOOLEAN NOT NULL,
    lease_id VARCHAR(80) NOT NULL,
    holder VARCHAR(80) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    released_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT ck_robot_control_lease_expiry
        CHECK (expires_at > issued_at),
    CONSTRAINT ck_robot_control_lease_token_hash
        CHECK (LENGTH(token_hash) = 64)
);

CREATE INDEX idx_robot_control_lease_expiry
    ON robot_control_leases(expires_at);
