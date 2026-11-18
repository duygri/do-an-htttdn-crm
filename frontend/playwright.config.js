import { defineConfig } from '@playwright/test';
import { getE2EDatabaseSettings } from './e2e/database.js';
import { ADMIN_BASE_URL, API_BASE_URL, CUSTOMER_BASE_URL, MANAGER_BASE_URL } from './e2e/credentials.js';

const database = getE2EDatabaseSettings();
const backendEnv = {
  ...process.env,
  DB_URL: database.jdbcUrl,
  DB_USERNAME: database.username,
  DB_PASSWORD: database.password,
  JWT_SECRET: 'playwright-e2e-only-secret-at-least-32-bytes',
  SERVER_PORT: '8182',
  SPRING_JPA_HIBERNATE_DDL_AUTO: 'create-drop',
  CORS_ORIGINS: `${CUSTOMER_BASE_URL},${ADMIN_BASE_URL},${MANAGER_BASE_URL}`,
  SECURE_REFRESH_COOKIE: 'false',
};
const frontendEnv = {
  ...process.env,
  VITE_API_BASE_URL: API_BASE_URL,
  VITE_USER_ORIGIN: CUSTOMER_BASE_URL,
  VITE_MANAGER_ORIGIN: MANAGER_BASE_URL,
};
const backendCommand = 'mvn -B -ntp -f ../backend/pom.xml spring-boot:run';

export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.js',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    baseURL: CUSTOMER_BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { browserName: 'chromium' } },
    { name: 'firefox', use: { browserName: 'firefox' } },
    { name: 'webkit', use: { browserName: 'webkit' } },
  ],
  webServer: [
    {
      command: backendCommand,
      url: `${API_BASE_URL}/api/auth/password-recovery`,
      env: backendEnv,
      timeout: 240_000,
      reuseExistingServer: false,
    },
    {
      command: 'npm run dev:user -- --port 5183',
      url: CUSTOMER_BASE_URL,
      env: frontendEnv,
      timeout: 60_000,
      reuseExistingServer: false,
    },
    {
      command: 'npm run dev:admin -- --port 5184',
      url: `${ADMIN_BASE_URL}/admin/login`,
      env: frontendEnv,
      timeout: 60_000,
      reuseExistingServer: false,
    },
    {
      command: 'npm run dev:manager -- --port 5185',
      url: MANAGER_BASE_URL,
      env: frontendEnv,
      timeout: 60_000,
      reuseExistingServer: false,
    },
  ],
});
