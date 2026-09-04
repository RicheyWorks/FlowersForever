ALTER TABLE worker_action_receipts DROP CONSTRAINT ck_worker_action_type;

ALTER TABLE worker_action_receipts ADD CONSTRAINT ck_worker_action_type CHECK (
    action IN ('LOG_HARVEST', 'COMPLETE_TASK', 'COUNT_INVENTORY', 'COMPLETE_ORDER')
);
