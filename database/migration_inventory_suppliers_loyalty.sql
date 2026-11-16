-- Inventory ledger, stocktakes, supplier payables, loyalty points and internal notices.
-- Idempotent: safe to run on existing databases and on fresh ones.
BEGIN;

CREATE TABLE IF NOT EXISTS app_settings (
  setting_key VARCHAR(60) PRIMARY KEY,
  setting_value VARCHAR(2000) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT
);

ALTER TABLE products ADD COLUMN IF NOT EXISTS low_stock_threshold INTEGER
  CHECK (low_stock_threshold IS NULL OR low_stock_threshold >= 0);

CREATE TABLE IF NOT EXISTS stock_movements (
  id BIGSERIAL PRIMARY KEY,
  product_id BIGINT NOT NULL REFERENCES products(product_id),
  movement_type VARCHAR(30) NOT NULL,
  quantity INTEGER NOT NULL,
  stock_before INTEGER NOT NULL,
  stock_after INTEGER NOT NULL CHECK (stock_after >= 0),
  reference_type VARCHAR(30),
  reference_id BIGINT,
  note VARCHAR(1000),
  actor_id BIGINT,
  actor_name VARCHAR(150),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_stock_movements_product
  ON stock_movements(product_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_stock_movements_type
  ON stock_movements(movement_type, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_stock_movements_created
  ON stock_movements(created_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS stocktakes (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(40) UNIQUE,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  note VARCHAR(1000),
  created_by BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_by BIGINT,
  completed_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS stocktake_items (
  id BIGSERIAL PRIMARY KEY,
  stocktake_id BIGINT NOT NULL REFERENCES stocktakes(id) ON DELETE CASCADE,
  product_id BIGINT NOT NULL REFERENCES products(product_id),
  system_quantity INTEGER NOT NULL,
  counted_quantity INTEGER,
  difference INTEGER,
  note VARCHAR(500)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_stocktake_item_product
  ON stocktake_items(stocktake_id, product_id);

CREATE TABLE IF NOT EXISTS supplier_payments (
  id BIGSERIAL PRIMARY KEY,
  supplier_id BIGINT NOT NULL REFERENCES suppliers(id),
  receipt_id BIGINT NOT NULL REFERENCES supplier_receipts(id),
  amount NUMERIC(15,2) NOT NULL CHECK (amount > 0),
  paid_at DATE NOT NULL,
  method VARCHAR(30) NOT NULL,
  note VARCHAR(500),
  created_by BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_supplier_payments_supplier
  ON supplier_payments(supplier_id, paid_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_supplier_payments_receipt
  ON supplier_payments(receipt_id);

CREATE TABLE IF NOT EXISTS point_transactions (
  id BIGSERIAL PRIMARY KEY,
  customer_id BIGINT NOT NULL REFERENCES customers(customer_id),
  order_id BIGINT REFERENCES orders(order_id),
  txn_type VARCHAR(20) NOT NULL,
  points INTEGER NOT NULL CHECK (points <> 0),
  note VARCHAR(500),
  created_by BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_point_transactions_customer
  ON point_transactions(customer_id, created_at DESC, id DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_point_earn_order
  ON point_transactions(order_id) WHERE txn_type='EARN' AND order_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS internal_notices (
  id BIGSERIAL PRIMARY KEY,
  recipient_id BIGINT NOT NULL REFERENCES customers(customer_id),
  notice_type VARCHAR(40) NOT NULL,
  title VARCHAR(150) NOT NULL,
  content VARCHAR(2000) NOT NULL,
  link VARCHAR(200),
  dedupe_key VARCHAR(120) NOT NULL,
  read_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (recipient_id, dedupe_key)
);
CREATE INDEX IF NOT EXISTS idx_internal_notices_recipient
  ON internal_notices(recipient_id, created_at DESC, id DESC);

COMMIT;
