import { expect, test } from '@playwright/test';
import { ADMIN_BASE_URL, ADMIN_EMAIL, ADMIN_PASSWORD } from './credentials.js';

test('Admin creates, assigns a role to, and deactivates an internal account', async ({ page }) => {
  const runId = `${test.info().project.name}-${Date.now()}`;
  const email = `internal-${runId}@crm.test`;
  const fullName = `Nhân viên E2E ${runId}`;

  await page.goto(`${ADMIN_BASE_URL}/admin/login`);
  await page.getByLabel('Email').fill(ADMIN_EMAIL);
  await page.getByLabel('Mật khẩu').fill(ADMIN_PASSWORD);

  const loginResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname === '/api/admin/auth/login' && response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Đăng nhập Admin' }).click();
  expect((await loginResponse).status()).toBe(200);

  await expect(page.getByRole('heading', { name: 'Tổng quan', level: 1 })).toBeVisible();
  await page.getByRole('link', { name: 'Tài khoản nội bộ', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tài khoản nội bộ', level: 1 })).toBeVisible();

  await page.getByRole('button', { name: 'Thêm mới' }).click();
  await page.getByLabel('Họ tên').fill(fullName);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mật khẩu (để trống nếu không đổi)').fill('Employee123');
  await page.getByLabel('Vai trò').selectOption('MANAGER');
  await page.getByRole('button', { name: 'Lưu', exact: true }).click();

  const accountRow = page.getByRole('row').filter({ hasText: email });
  await expect(accountRow).toBeVisible();
  await expect(accountRow.getByText('MANAGER', { exact: true })).toBeVisible();

  page.once('dialog', (dialog) => dialog.accept());
  await accountRow.getByRole('button', { name: 'Xóa mềm' }).click();
  await expect(accountRow).toHaveCount(0);
});
