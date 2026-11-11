import { expect, test } from '@playwright/test';
import { MANAGER_EMAIL, MANAGER_PASSWORD } from './credentials.js';

const apiResponse = (page, path, method = 'POST') =>
  page.waitForResponse((response) => {
    const request = response.request();
    return new URL(response.url()).pathname === path && request.method() === method;
  });

test('customer can register and keep a session after a reload', async ({ page }) => {
  const runId = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  const email = `e2e-${runId}@example.test`;
  const fullName = `Khách E2E ${runId}`;

  await page.goto('/dang-ky');
  await page.getByLabel('HỌ VÀ TÊN').fill(fullName);
  await page.getByLabel('SỐ ĐIỆN THOẠI').fill('0900000000');
  await page.getByLabel('EMAIL').fill(email);
  await page.getByLabel('MẬT KHẨU').fill('Customer123');

  const registerResponse = apiResponse(page, '/api/auth/register');
  const loginResponse = apiResponse(page, '/api/auth/login');
  await page.getByRole('button', { name: 'TẠO TÀI KHOẢN →' }).click();

  expect((await registerResponse).status()).toBe(201);
  expect((await loginResponse).status()).toBe(200);
  await expect(page).toHaveURL(/localhost:5173\/$/);

  await page.goto('/tai-khoan');
  await expect(page.getByRole('heading', { name: fullName })).toBeVisible();
  await expect(page.getByText(email)).toBeVisible();

  const refreshResponse = apiResponse(page, '/api/auth/refresh');
  await page.reload();
  expect((await refreshResponse).status()).toBe(200);
  await expect(page.getByRole('heading', { name: fullName })).toBeVisible();
  await expect(page.getByText(email)).toBeVisible();
});

test('manager can sign in and load the live dashboard', async ({ page }) => {
  await page.goto('http://localhost:5175/manager/login');
  await page.getByLabel('EMAIL').fill(MANAGER_EMAIL);
  await page.getByLabel('MẬT KHẨU').fill(MANAGER_PASSWORD);

  const loginResponse = apiResponse(page, '/api/manager/auth/login');
  const reportPaths = [
    '/api/manager/reports/revenue',
    '/api/manager/reports/users',
    '/api/manager/surveys/stats',
  ];
  const reportResponses = reportPaths.map((path) => apiResponse(page, path, 'GET'));
  await page.getByRole('button', { name: /ĐĂNG NHẬP QUẢN LÝ/i }).click();

  expect((await loginResponse).status()).toBe(200);
  await expect(page.getByText('CHỈ SỐ HOẠT ĐỘNG TOÀN HỆ THỐNG')).toBeVisible();
  const reports = await Promise.all(reportResponses);
  expect(reports.map((response) => response.status())).toEqual([200, 200, 200]);
});
