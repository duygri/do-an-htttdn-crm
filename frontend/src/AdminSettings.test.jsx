// @vitest-environment jsdom
import React, { StrictMode } from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import SystemAdminApp from './SystemAdminApp';
import { adminApi, adminRefreshSession } from './api';

vi.mock('./api', async (importOriginal) => ({
  ...(await importOriginal()),
  adminApi: vi.fn(),
  adminRefreshSession: vi.fn(),
  adminSignIn: vi.fn(),
  adminSignOut: vi.fn(),
}));

const wrapper = ({ children }) => <StrictMode>{children}</StrictMode>;
const settings = {
  'invoice.seller.name': 'Anh Lon Shop',
  'invoice.seller.address': '',
  'invoice.seller.phone': '',
  'invoice.seller.taxCode': '',
  'inventory.lowStockDefault': '5',
  'loyalty.pointsPer10000': '1',
  'loyalty.redeemPoints': '100',
  'loyalty.redeemValue': '20000',
};

beforeEach(() => {
  vi.resetAllMocks();
  window.history.replaceState({}, '', '/admin/settings');
  window.localStorage.clear();
  adminRefreshSession.mockResolvedValue({ admin: { fullName: 'Quản trị', email: 'admin@example.com' } });
  adminApi.mockImplementation(async (url, options = {}) => {
    if (url === '/api/admin/overview') return { accounts: 1, products: 2, suppliers: 3 };
    if (url === '/api/admin/settings' && options.method === 'PUT') return { ...settings, ...options.body };
    if (url === '/api/admin/settings') return settings;
    return { content: [] };
  });
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

it('loads system settings and saves edited values', async () => {
  render(<SystemAdminApp />, { wrapper });
  expect(await screen.findByRole('heading', { name: 'Cấu hình' })).toBeTruthy();
  const redeem = await screen.findByLabelText('Ngưỡng tồn thấp mặc định');
  expect(redeem.value).toBe('5');
  fireEvent.change(redeem, { target: { value: '150' } });
  fireEvent.click(screen.getByRole('button', { name: 'Lưu cấu hình' }));
  await waitFor(() => expect(screen.getByText('Đã lưu cấu hình.')).toBeTruthy());
  const call = adminApi.mock.calls.find(([url, options]) => url === '/api/admin/settings' && options?.method === 'PUT');
  expect(call).toBeTruthy();
  expect(call[1].body['inventory.lowStockDefault']).toBe('150');
  expect(call[1].body['loyalty.redeemPoints']).toBeUndefined();
  expect(call[1].body['invoice.seller.name']).toBe('Anh Lon Shop');
});

it('shows an error when saving fails', async () => {
  adminApi.mockImplementation(async (url, options = {}) => {
    if (url === '/api/admin/overview') return { accounts: 1, products: 2, suppliers: 3 };
    if (url === '/api/admin/settings' && options.method === 'PUT') throw new Error('Cấu hình không hợp lệ.');
    if (url === '/api/admin/settings') return settings;
    return { content: [] };
  });
  render(<SystemAdminApp />, { wrapper });
  await screen.findByLabelText('Ngưỡng tồn thấp mặc định');
  fireEvent.click(screen.getByRole('button', { name: 'Lưu cấu hình' }));
  expect(await screen.findByRole('alert')).toBeTruthy();
});
