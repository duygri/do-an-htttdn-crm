// @vitest-environment jsdom
import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import ManagerApp from './ManagerApp';
import { adminApi, adminDownload, adminRefreshSession } from './manager-api';

vi.mock('./manager-api', async importOriginal => ({
  ...(await importOriginal()), adminApi: vi.fn(), adminDownload: vi.fn(), adminRefreshSession: vi.fn(),
}));

const supplier = { id: 5, code: 'NCC-01', name: 'Nhà cung cấp A', active: true, createdAt: '2026-10-01T02:00:00Z' };
const detail = {
  ...supplier,
  productCount: 2,
  receiptCount: 1,
  totalPurchase: 100000,
  totalPaid: 20000,
  outstanding: 80000,
  lastReceiptDate: '2026-11-01',
  products: [{ id: 7, name: 'Áo sơ mi', stock: 2, price: 150000, active: true }],
  receipts: [{ id: 9, invoiceNumber: 'HD-9', invoiceDate: '2026-11-01', totalAmount: 100000, paidAmount: 20000, outstanding: 80000 }],
};
const page = content => ({ content, totalElements: content.length, totalPages: content.length ? 1 : 0 });

beforeEach(() => {
  vi.resetAllMocks();
  window.history.replaceState({}, '', '/manager/suppliers');
  adminRefreshSession.mockResolvedValue({ manager: { fullName: 'Quản lý' } });
  adminDownload.mockResolvedValue(undefined);
  adminApi.mockImplementation(async (url, options = {}) => {
    if (url === '/api/manager/suppliers/5' && !options.method) return { ...detail };
    if (url === '/api/manager/suppliers/5/status') return { ...detail, active: options.body.active };
    if (url.endsWith('/payments') && options.method === 'POST') return { id: 31, ...options.body };
    if (url === '/api/manager/suppliers/5/payments') return page([]);
    if (url.includes('/api/manager/suppliers?')) return page([supplier]);
    return page([]);
  });
});
afterEach(cleanup);

it('opens the supplier detail with payables and archives the supplier', async () => {
  render(<ManagerApp />);
  fireEvent.click(await screen.findByRole('button', { name: 'Chi tiết' }, {timeout:10000}));
  expect(await screen.findByRole('heading', { name: 'NCC-01 · Nhà cung cấp A' })).toBeTruthy();
  expect(screen.getAllByText('80.000 đ').length).toBeGreaterThan(0);
  expect(screen.getByText('Áo sơ mi')).toBeTruthy();
  expect(screen.getByText('HD-9')).toBeTruthy();

  fireEvent.click(screen.getByRole('button', { name: 'Ngừng hợp tác' }));
  await waitFor(() => expect(adminApi).toHaveBeenCalledWith(
    '/api/manager/suppliers/5/status', { method: 'PATCH', body: { active: false } },
  ));
  expect(await screen.findByRole('button', { name: 'Kích hoạt lại' })).toBeTruthy();
});

it('records a payment for a receipt and downloads the payables CSV', async () => {
  render(<ManagerApp />);
  fireEvent.click(await screen.findByRole('button', { name: 'Chi tiết' }, {timeout:10000}));
  await screen.findByRole('heading', { name: 'NCC-01 · Nhà cung cấp A' });

  const amount = screen.getByLabelText('Số tiền HD-9');
  expect(amount.value).toBe('80000');
  fireEvent.change(amount, { target: { value: '50000' } });
  fireEvent.change(screen.getByLabelText('Phương thức HD-9'), { target: { value: 'BANK_TRANSFER' } });
  fireEvent.change(screen.getByLabelText('Ghi chú HD-9'), { target: { value: 'Chuyển khoản' } });
  fireEvent.click(screen.getByRole('button', { name: 'Ghi nhận thanh toán HD-9' }));
  await waitFor(() => expect(adminApi).toHaveBeenCalledWith('/api/manager/suppliers/5/payments', {
    method: 'POST',
    body: {
      receiptId: 9, amount: 50000, paidAt: expect.any(String), method: 'BANK_TRANSFER', note: 'Chuyển khoản',
    },
  }));

  fireEvent.click(screen.getByRole('button', { name: 'Xuất CSV công nợ' }));
  expect(adminDownload).toHaveBeenCalledWith('/api/manager/suppliers/5/reports/summary.csv', 'cong-no-ncc.csv');
});
