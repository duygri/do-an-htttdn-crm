import pg from 'pg';
import { getE2EDatabaseSettings } from './database.js';

const { Pool } = pg;
const MANAGER_EMAIL = 'manager-e2e@crm.test';
const MANAGER_PASSWORD_HASH = '$2a$10$tAL0K4zyrX8mctqJC6ldHeAvlO8OMpEDI6HD.mMkb4IMoWGiCLxyW';

export default async function globalSetup() {
  const settings = getE2EDatabaseSettings();
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
      `UPDATE customers
       SET full_name = $1, password_hash = $2, role = 'MANAGER', locked = FALSE, deleted_at = NULL
       WHERE lower(email) = lower($3)`,
      ['E2E Manager', MANAGER_PASSWORD_HASH, MANAGER_EMAIL],
    );
    await pool.query(
      `INSERT INTO customers (email, full_name, password_hash, role, locked, created_at)
       VALUES ($1, $2, $3, 'MANAGER', FALSE, now())
       ON CONFLICT (email) DO NOTHING`,
      [MANAGER_EMAIL, 'E2E Manager', MANAGER_PASSWORD_HASH],
    );
  } finally {
    await pool.end();
  }
}
