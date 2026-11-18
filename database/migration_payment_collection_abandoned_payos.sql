BEGIN;
-- Explicit COD collection confirmation, PayOS reconciliation review, and abandoned unpaid-link handling.
-- This migration intentionally does not create refund tables or fields.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cod_collected_amount NUMERIC(15,2) CHECK (cod_collected_amount >= 0);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cod_collected_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cod_collected_by BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cod_collection_note VARCHAR(1000);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_review_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_review_reason VARCHAR(120);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_review_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_orders_payment_review ON orders(payment_review_required,order_id);
CREATE INDEX IF NOT EXISTS idx_orders_abandoned ON orders(expires_at,order_id)
    WHERE payment_method='PAYOS' AND status IN ('PENDING','PENDING_PAYMENT') AND payment_status IN ('PENDING','EXPIRED','FAILED','CANCELLED');
COMMIT;
