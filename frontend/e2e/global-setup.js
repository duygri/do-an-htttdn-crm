import bcrypt from 'bcryptjs';
import pg from 'pg';
import { getE2EDatabaseSettings } from './database.js';
import {
  ADMIN_EMAIL,
  ADMIN_PASSWORD,
  CRM_FEEDBACK_PRODUCT_NAME,
  MANAGER_EMAIL,
  MANAGER_PASSWORD,
} from './credentials.js';

const { Pool } = pg;

export default async function globalSetup() {
  const settings = getE2EDatabaseSettings();
  const managerPasswordHash = await bcrypt.hash(MANAGER_PASSWORD, 10);
  const adminPasswordHash = await bcrypt.hash(ADMIN_PASSWORD, 10);
  const pool = new Pool({ connectionString: settings.connectionString, connectionTimeoutMillis: 2_000 });
  const deadline = Date.now() + 60_000;

  try {
    let schemaReady = false;
    while (Date.now() < deadline) {
      try {
        const result = await pool.query("SELECT to_regclass('public.customers') AS customers");
        if (result.rows[0].customers) {
          schemaReady = true;
          break;
        }
      } catch {
        // Spring may still be creating the schema or PostgreSQL may still be starting.
      }
      await new Promise((resolve) => setTimeout(resolve, 1_000));
    }

    if (!schemaReady) {
      throw new Error('PostgreSQL or the Spring-created crm_e2e schema was not ready within 60 seconds.');
    }

    await pool.query(
      `INSERT INTO customers (email, full_name, password_hash, role, locked, created_at)
       VALUES ($1, $2, $3, 'MANAGER', FALSE, now())
       ON CONFLICT (email) DO UPDATE SET
         full_name = EXCLUDED.full_name,
         password_hash = EXCLUDED.password_hash,
         role = 'MANAGER',
         locked = FALSE,
         deleted_at = NULL`,
      [MANAGER_EMAIL, 'E2E Manager', managerPasswordHash],
    );

    await pool.query(
      `INSERT INTO customers (email, full_name, password_hash, role, locked, created_at)
       VALUES ($1, 'E2E System Admin', $2, 'ADMIN', FALSE, now())
       ON CONFLICT (email) DO UPDATE SET
         full_name = EXCLUDED.full_name,
         password_hash = EXCLUDED.password_hash,
         role = 'ADMIN',
         locked = FALSE,
         deleted_at = NULL`,
      [ADMIN_EMAIL, adminPasswordHash],
    );

    const existingCategory = await pool.query(
      'SELECT category_id FROM categories WHERE name = $1 ORDER BY category_id LIMIT 1',
      ['E2E CRM'],
    );
    const categoryId = existingCategory.rows[0]?.category_id ?? (
      await pool.query('INSERT INTO categories (name) VALUES ($1) RETURNING category_id', ['E2E CRM'])
    ).rows[0].category_id;
    const existingProduct = await pool.query(
      'SELECT product_id FROM products WHERE name = $1 ORDER BY product_id LIMIT 1',
      [CRM_FEEDBACK_PRODUCT_NAME],
    );
    if (!existingProduct.rows.length) {
      await pool.query(
        `INSERT INTO products
           (category_id, name, color, size, price, quantity_remaining, gender, description,
            material, active, featured, created_at, updated_at)
         VALUES ($1, $2, 'Đen', 'M', 129000, 20, 'NAM', 'E2E-only completed-order fixture',
                 'Cotton', TRUE, FALSE, now(), now())`,
        [categoryId, CRM_FEEDBACK_PRODUCT_NAME],
      );
    }

    const manager = await pool.query(
      'SELECT role, locked, deleted_at, password_hash FROM customers WHERE email = $1',
      [MANAGER_EMAIL],
    );
    const seededManager = manager.rows[0];
    if (
      !seededManager ||
      seededManager.role !== 'MANAGER' ||
      seededManager.locked ||
      seededManager.deleted_at ||
      !(await bcrypt.compare(MANAGER_PASSWORD, seededManager.password_hash))
    ) {
      throw new Error('Could not seed a usable E2E manager account in crm_e2e.');
    }

    const admin = await pool.query(
      'SELECT role, locked, deleted_at, password_hash FROM customers WHERE email = $1',
      [ADMIN_EMAIL],
    );
    const seededAdmin = admin.rows[0];
    if (
      !seededAdmin ||
      seededAdmin.role !== 'ADMIN' ||
      seededAdmin.locked ||
      seededAdmin.deleted_at ||
      !(await bcrypt.compare(ADMIN_PASSWORD, seededAdmin.password_hash))
    ) {
      throw new Error('Could not seed a usable E2E Admin account in crm_e2e.');
    }
  } finally {
    await pool.end();
  }
}
