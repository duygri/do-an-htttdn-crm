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
ALTER TABLE order_items DROP CONSTRAINT IF EXISTS order_items_variant_product_fk;
ALTER TABLE order_items ADD CONSTRAINT order_items_variant_product_fk FOREIGN KEY (product_id,variant_id) REFERENCES product_variants(product_id,id);
ALTER TABLE cart_items DROP CONSTRAINT IF EXISTS cart_items_variant_product_fk;
ALTER TABLE cart_items ADD CONSTRAINT cart_items_variant_product_fk FOREIGN KEY (product_id,variant_id) REFERENCES product_variants(product_id,id);
ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS stock_movements_variant_product_fk;
ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_variant_product_fk FOREIGN KEY (product_id,variant_id) REFERENCES product_variants(product_id,id);
ALTER TABLE stocktake_items DROP CONSTRAINT IF EXISTS stocktake_items_variant_product_fk;
ALTER TABLE stocktake_items ADD CONSTRAINT stocktake_items_variant_product_fk FOREIGN KEY (product_id,variant_id) REFERENCES product_variants(product_id,id);
ALTER TABLE supplier_receipt_items DROP CONSTRAINT IF EXISTS supplier_receipt_items_variant_product_fk;
ALTER TABLE supplier_receipt_items ADD CONSTRAINT supplier_receipt_items_variant_product_fk FOREIGN KEY (product_id,variant_id) REFERENCES product_variants(product_id,id);
ALTER TABLE sales_invoice_items DROP CONSTRAINT IF EXISTS sales_invoice_items_variant_product_fk;
ALTER TABLE sales_invoice_items ADD CONSTRAINT sales_invoice_items_variant_product_fk FOREIGN KEY (product_id,variant_id) REFERENCES product_variants(product_id,id);
COMMIT;
