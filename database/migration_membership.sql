-- Membership snapshot: no historical discount or tier backfill.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS member_tier VARCHAR(10) CHECK(member_tier IN ('BRONZE','SILVER','GOLD','DIAMOND'));
ALTER TABLE orders ADD COLUMN IF NOT EXISTS member_discount_percent INTEGER NOT NULL DEFAULT 0 CHECK(member_discount_percent IN (0,3,5,10));
ALTER TABLE orders ADD COLUMN IF NOT EXISTS member_discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK(member_discount_amount>=0);
