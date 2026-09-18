-- Schema cho database PostgreSQL htttdn.
-- Phần đầu giữ nguyên mô hình categories/products/customers/admins/orders
-- của dự án; các cột/bảng mở rộng phục vụ luồng customer storefront.

CREATE TABLE IF NOT EXISTS categories (
    category_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_name ON categories(name);

CREATE TABLE IF NOT EXISTS products (
    product_id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES categories(category_id),
    name VARCHAR(150) NOT NULL,
    color VARCHAR(1000),
    size VARCHAR(1000),
    price NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    quantity_remaining INTEGER NOT NULL DEFAULT 0 CHECK (quantity_remaining >= 0)
);

ALTER TABLE products ADD COLUMN IF NOT EXISTS gender VARCHAR(20) NOT NULL DEFAULT 'NAM';
ALTER TABLE products ADD COLUMN IF NOT EXISTS description VARCHAR(4000);
ALTER TABLE products ADD COLUMN IF NOT EXISTS material VARCHAR(255);
ALTER TABLE products ADD COLUMN IF NOT EXISTS sale_price NUMERIC(12,2);
ALTER TABLE products ADD COLUMN IF NOT EXISTS image_url VARCHAR(2000);
ALTER TABLE products ADD COLUMN IF NOT EXISTS badge VARCHAR(80);
ALTER TABLE products ADD COLUMN IF NOT EXISTS featured BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE products ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE products ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS customers (
    customer_id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    age SMALLINT,
    preferences VARCHAR(4000),
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE customers ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS phone VARCHAR(40);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';
ALTER TABLE customers ADD COLUMN IF NOT EXISTS locked BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_email ON customers(email) WHERE email IS NOT NULL;

CREATE TABLE IF NOT EXISTS admins (
    admin_id BIGSERIAL PRIMARY KEY,
    role VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
    order_id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(customer_id),
    total_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
);

ALTER TABLE orders ADD COLUMN IF NOT EXISTS order_code BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_status VARCHAR(30) DEFAULT 'PENDING';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(30);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_address VARCHAR(1000);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE orders ADD COLUMN IF NOT EXISTS stock_released BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_order_code ON orders(order_code) WHERE order_code IS NOT NULL;

CREATE TABLE IF NOT EXISTS order_items (
    order_item_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(product_id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL
);

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS size VARCHAR(1000);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS color VARCHAR(1000);

CREATE TABLE IF NOT EXISTS cart_items (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(customer_id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(product_id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    size VARCHAR(1000),
    color VARCHAR(1000)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_customer_product_variant
    ON cart_items(customer_id, product_id, COALESCE(size, ''), COALESCE(color, ''));

CREATE TABLE IF NOT EXISTS feedback (
    feedback_id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(customer_id),
    admin_id BIGINT REFERENCES admins(admin_id),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE feedback ADD COLUMN IF NOT EXISTS product_id BIGINT REFERENCES products(product_id);
ALTER TABLE feedback ADD COLUMN IF NOT EXISTS rating INTEGER NOT NULL DEFAULT 5 CHECK (rating BETWEEN 1 AND 5);
ALTER TABLE feedback ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'NEW';
ALTER TABLE feedback ADD COLUMN IF NOT EXISTS admin_response VARCHAR(4000);
ALTER TABLE feedback ADD COLUMN IF NOT EXISTS processed_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uq_feedback_customer_product
    ON feedback(customer_id, product_id) WHERE product_id IS NOT NULL;

-- survey_definitions là mẫu khảo sát mà frontend hiển thị cho khách hàng.
CREATE TABLE IF NOT EXISTS survey_definitions (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(4000),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS survey_questions (
    id BIGSERIAL PRIMARY KEY,
    survey_id BIGINT NOT NULL REFERENCES survey_definitions(id) ON DELETE CASCADE,
    text VARCHAR(1000) NOT NULL,
    type VARCHAR(30) NOT NULL DEFAULT 'TEXT',
    display_order INTEGER NOT NULL DEFAULT 0,
    options_json VARCHAR(4000),
    required BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS survey_responses (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(customer_id),
    survey_id BIGINT NOT NULL REFERENCES survey_definitions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(customer_id, survey_id)
);

CREATE TABLE IF NOT EXISTS survey_answers (
    id BIGSERIAL PRIMARY KEY,
    response_id BIGINT NOT NULL REFERENCES survey_responses(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES survey_questions(id),
    answer VARCHAR(4000) NOT NULL
);

-- Database cũ có thể còn các khóa ngoại survey_id trỏ nhầm sang bảng surveys.
-- Backend hiện dùng survey_definitions nên chỉ giữ khóa ngoại đúng này.
DO $$
DECLARE constraint_row RECORD;
BEGIN
    FOR constraint_row IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'survey_questions'::regclass
          AND contype = 'f'
          AND to_regclass('public.surveys') IS NOT NULL
          AND confrelid = to_regclass('public.surveys')
    LOOP
        EXECUTE format('ALTER TABLE survey_questions DROP CONSTRAINT %I', constraint_row.conname);
    END LOOP;

    FOR constraint_row IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'survey_responses'::regclass
          AND contype = 'f'
          AND to_regclass('public.surveys') IS NOT NULL
          AND confrelid = to_regclass('public.surveys')
    LOOP
        EXECUTE format('ALTER TABLE survey_responses DROP CONSTRAINT %I', constraint_row.conname);
    END LOOP;

    FOR constraint_row IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'survey_responses'::regclass
          AND contype = 'f'
          AND to_regclass('public.users') IS NOT NULL
          AND confrelid = to_regclass('public.users')
    LOOP
        EXECUTE format('ALTER TABLE survey_responses DROP CONSTRAINT %I', constraint_row.conname);
    END LOOP;
END $$;

-- Bổ sung sequence cho database cũ nếu cột id của survey_questions chưa có default.
CREATE SEQUENCE IF NOT EXISTS survey_questions_id_seq;
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'survey_questions'
          AND column_name = 'id'
          AND column_default IS NULL
          AND is_identity = 'NO'
    ) THEN
        ALTER TABLE survey_questions ALTER COLUMN id SET DEFAULT nextval('survey_questions_id_seq');
        PERFORM setval('survey_questions_id_seq', COALESCE((SELECT MAX(id) FROM survey_questions), 0) + 1, false);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(customer_id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    family_id VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    revoke_reason VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE REFERENCES orders(order_id),
    amount NUMERIC(15,2) NOT NULL,
    payment_link_id VARCHAR(255),
    checkout_url VARCHAR(2000),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS payment_webhook_events (
    id BIGSERIAL PRIMARY KEY,
    event_key VARCHAR(255) NOT NULL UNIQUE,
    order_code BIGINT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_products_category ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_catalog ON products(active, gender, category_id);
CREATE INDEX IF NOT EXISTS idx_orders_customer ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_customer_created ON orders(customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_product ON order_items(product_id);
CREATE INDEX IF NOT EXISTS idx_feedback_customer ON feedback(customer_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_hash ON refresh_tokens(token_hash);
