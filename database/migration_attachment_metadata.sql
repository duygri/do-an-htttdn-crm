-- Manual migration only; no new tables. Keep customer row as the durable attachment ledger.
-- READY/UPLOADING/DELETING entries are temporary; BOUND entries retain delivery authorization metadata.
ALTER TABLE customers ADD COLUMN IF NOT EXISTS pending_attachments TEXT NOT NULL DEFAULT '[]';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS return_attachments TEXT NOT NULL DEFAULT '[]';
-- support_tickets.attachments is owned by migration_support_experience.sql.
