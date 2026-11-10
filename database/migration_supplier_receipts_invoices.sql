-- Nhà cung cấp: phiếu nhập hàng và hóa đơn bán hàng.
-- Phiếu nhập hàng chỉ ghi nhận chi phí mua hàng; không cộng tồn kho.
BEGIN;

CREATE TABLE IF NOT EXISTS supplier_receipts (
    id BIGSERIAL PRIMARY KEY,
    supplier_id BIGINT NOT NULL REFERENCES suppliers(id),
    invoice_number VARCHAR(80) NOT NULL,
    invoice_date DATE NOT NULL,
    total_amount NUMERIC(15,2) NOT NULL CHECK (total_amount >= 0),
    note VARCHAR(1000),
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_supplier_receipts_supplier_date
    ON supplier_receipts(supplier_id, invoice_date DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_supplier_receipts_date
    ON supplier_receipts(invoice_date DESC, id DESC);

CREATE TABLE IF NOT EXISTS supplier_receipt_items (
    id BIGSERIAL PRIMARY KEY,
    receipt_id BIGINT NOT NULL REFERENCES supplier_receipts(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(product_id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_cost NUMERIC(15,2) NOT NULL CHECK (unit_cost >= 0),
    line_total NUMERIC(15,2) NOT NULL CHECK (line_total >= 0)
);
CREATE INDEX IF NOT EXISTS idx_supplier_receipt_items_receipt
    ON supplier_receipt_items(receipt_id, id);

CREATE TABLE IF NOT EXISTS sales_invoices (
    id BIGSERIAL PRIMARY KEY,
    invoice_number VARCHAR(80) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL UNIQUE REFERENCES orders(order_id),
    customer_id BIGINT NOT NULL REFERENCES customers(customer_id),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    seller_name VARCHAR(150) NOT NULL,
    seller_address VARCHAR(1000),
    seller_phone VARCHAR(40),
    seller_tax_code VARCHAR(40),
    buyer_name VARCHAR(150) NOT NULL,
    buyer_email VARCHAR(255),
    buyer_phone VARCHAR(40),
    delivery_address VARCHAR(1000),
    subtotal NUMERIC(15,2) NOT NULL,
    discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(15,2) NOT NULL,
    payment_method VARCHAR(30),
    payment_status VARCHAR(30),
    note VARCHAR(1000),
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sales_invoices_customer
    ON sales_invoices(customer_id, issued_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS sales_invoice_items (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES sales_invoices(id) ON DELETE CASCADE,
    product_id BIGINT REFERENCES products(product_id),
    product_name VARCHAR(150) NOT NULL,
    size VARCHAR(1000),
    color VARCHAR(1000),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(15,2) NOT NULL,
    line_total NUMERIC(15,2) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sales_invoice_items_invoice
    ON sales_invoice_items(invoice_id, id);

COMMIT;
