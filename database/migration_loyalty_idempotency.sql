-- Run before starting the new backend. No historical balances are changed.
BEGIN;
ALTER TABLE point_transactions ADD COLUMN IF NOT EXISTS request_key VARCHAR(36);
ALTER TABLE point_transactions ADD COLUMN IF NOT EXISTS redemption_result TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS uq_point_customer_request
    ON point_transactions(customer_id, request_key) WHERE request_key IS NOT NULL;
COMMIT;
