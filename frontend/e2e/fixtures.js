import pg from 'pg';
import { getE2EDatabaseSettings } from './database.js';
import { CRM_FEEDBACK_PRODUCT_NAME } from './credentials.js';

const { Pool } = pg;

export async function createCompletedOrderForCustomer(email, orderCode) {
  const settings = getE2EDatabaseSettings();
  const pool = new Pool({ connectionString: settings.connectionString, connectionTimeoutMillis: 2_000 });
  const client = await pool.connect();

  try {
    await client.query('BEGIN');
    const customerResult = await client.query(
      `SELECT customer_id FROM customers
       WHERE email = $1 AND role = 'CUSTOMER' AND deleted_at IS NULL`,
      [email],
    );
    if (customerResult.rows.length !== 1) {
      throw new Error(`Expected one active E2E customer for completed order; found ${customerResult.rows.length}.`);
    }

    const productResult = await client.query(
      'SELECT product_id, price FROM products WHERE name = $1 AND active = TRUE ORDER BY product_id LIMIT 1',
      [CRM_FEEDBACK_PRODUCT_NAME],
    );
    if (productResult.rows.length !== 1) {
      throw new Error(`E2E feedback product "${CRM_FEEDBACK_PRODUCT_NAME}" was not seeded.`);
    }

    const customerId = customerResult.rows[0].customer_id;
    const product = productResult.rows[0];
    const orderResult = await client.query(
      `INSERT INTO orders
         (customer_id, order_code, total_amount, status, payment_status, payment_method,
          updated_at, discount_amount, return_status, stock_released, payment_review_required)
       VALUES ($1, $2, $3, 'COMPLETED', 'PAID', 'COD', now(), 0, 'NONE', TRUE, FALSE)
       RETURNING order_id`,
      [customerId, orderCode, product.price],
    );
    await client.query(
      `INSERT INTO order_items (order_id, product_id, quantity, unit_price)
       VALUES ($1, $2, 1, $3)`,
      [orderResult.rows[0].order_id, product.product_id, product.price],
    );
    await client.query('COMMIT');
    return { orderId: orderResult.rows[0].order_id, productId: product.product_id };
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
    await pool.end();
  }
}
