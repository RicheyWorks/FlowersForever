-- Exact restart replay for a robot execution whose client response was lost.
-- Only a SHA-256 fingerprint of the full command is stored.
ALTER TABLE robot_operation_events
    ADD COLUMN request_id VARCHAR(160);

ALTER TABLE robot_operation_events
    ADD COLUMN request_fingerprint VARCHAR(64);

ALTER TABLE robot_operation_events
    ADD CONSTRAINT ck_robot_operation_request_replay
    CHECK ((request_id IS NULL AND request_fingerprint IS NULL)
        OR (request_id IS NOT NULL AND LENGTH(request_id) > 0
            AND request_fingerprint IS NOT NULL
            AND LENGTH(request_fingerprint) = 64));

CREATE UNIQUE INDEX idx_robot_operation_request
    ON robot_operation_events(plugin_id, request_id);
