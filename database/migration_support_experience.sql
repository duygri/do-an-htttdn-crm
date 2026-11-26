BEGIN;
-- Nullable SLA fields deliberately preserve unknown history; no backfill.
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS priority VARCHAR(10) CHECK(priority IN ('URGENT','NORMAL','LOW'));
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS due_at TIMESTAMPTZ;
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS first_response_at TIMESTAMPTZ;
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS priority_reason VARCHAR(1000);
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS csat_rating INTEGER CHECK(csat_rating BETWEEN 1 AND 5);
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS csat_comment VARCHAR(1000);
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS csat_request_key VARCHAR(36);
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS csat_at TIMESTAMPTZ;
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS attachments TEXT NOT NULL DEFAULT '[]';
CREATE INDEX IF NOT EXISTS idx_support_due ON support_tickets(due_at,id) WHERE first_response_at IS NULL AND status <> 'RESOLVED';
CREATE INDEX IF NOT EXISTS idx_support_resolved ON support_tickets(resolved_at);
COMMIT;
