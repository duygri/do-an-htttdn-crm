-- Run explicitly after existing inventory/supplier migrations. No stock is allocated automatically.
BEGIN;
CREATE TABLE IF NOT EXISTS product_variants (
 id BIGSERIAL PRIMARY KEY,
 product_id BIGINT NOT NULL REFERENCES products(product_id),
 color VARCHAR(100) NOT NULL CHECK (length(trim(color)) > 0),
 size VARCHAR(100) NOT NULL CHECK (length(trim(size)) > 0),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 stock INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
 UNIQUE(product_id,color,size),
 UNIQUE(product_id,id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_product_variant_normalized ON product_variants(product_id,lower(trim(color)),lower(trim(size)));
ALTER TABLE products ADD COLUMN IF NOT EXISTS variants_allocated BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN IF NOT EXISTS stock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS variant_id BIGINT REFERENCES product_variants(id);
ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS variant_id BIGINT REFERENCES product_variants(id);
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS variant_id BIGINT REFERENCES product_variants(id);
ALTER TABLE stocktake_items ADD COLUMN IF NOT EXISTS variant_id BIGINT REFERENCES product_variants(id);
ALTER TABLE supplier_receipt_items ADD COLUMN IF NOT EXISTS variant_id BIGINT REFERENCES product_variants(id);
ALTER TABLE sales_invoice_items ADD COLUMN IF NOT EXISTS variant_id BIGINT REFERENCES product_variants(id);
ALTER TABLE stocktake_items DROP CONSTRAINT IF EXISTS uq_stocktake_item_product;
DROP INDEX IF EXISTS uq_stocktake_item_product;
CREATE UNIQUE INDEX IF NOT EXISTS uq_stocktake_item_common ON stocktake_items(stocktake_id,product_id) WHERE variant_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_stocktake_item_variant ON stocktake_items(stocktake_id,product_id,variant_id) WHERE variant_id IS NOT NULL;
-- Enforce ownership even for writes outside the application.
DO $$
DECLARE t text;
BEGIN
 FOREACH t IN ARRAY ARRAY['order_items','cart_items','stock_movements','stocktake_items','supplier_receipt_items','sales_invoice_items'] LOOP
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = t || '_variant_product_fk') THEN
   EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (product_id,variant_id) REFERENCES product_variants(product_id,id)', t, t || '_variant_product_fk');
  END IF;
 END LOOP;
END $$;
COMMIT;
