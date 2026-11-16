// @vitest-environment jsdom
import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import ManagerApp from './ManagerApp';
import { adminApi, adminDownload, adminRefreshSession } from './manager-api';

vi.mock('./manager-api', async importOriginal => ({
  ...(await importOriginal()), adminApi: vi.fn(), adminDownload: vi.fn(), adminRefreshSession: vi.fn(),
}));

const lowRow = { productId: 7, name: 'Áo sơ mi', category: 'Áo', supplierId: 2, stock: 2, threshold: 5, updatedAt: '2026-11-01T02:00:00Z' };
const movement = { id: 11, productId: 7, productName: 'Áo sơ mi', type: 'SALE', quantity: -3, stockBefore: 5, stockAfter: 2, referenceType: 'ORDER', referenceId: 9, note: 'Bán đơn #9009', actorName: 'Quản lý', createdAt: '2026-11-01T03:00:00Z' };
const page = content => ({ content, totalElements: content.length, totalPages: content.length ? 1 : 0 });

beforeEach(() => {
  vi.resetAllMocks();
  window.history.replaceState({}, '', '/manager/inventory');
  adminRefreshSession.mockResolvedValue({ manager: { fullName: 'Quản lý kho' } });
  adminDownload.mockResolvedValue(undefined);
  adminApi.mockImplementation(async (url, options = {}) => {
    if (options.method === 'PATCH') return { ...lowRow, threshold: options.body.threshold };
    if (url.includes('/inventory/low-stock')) return page([lowRow]);
    if (url.includes('/inventory/movements')) return page([movement]);
    return page([]);
  });
});
afterEach(cleanup);

it('shows low-stock rows, switches to the ledger and updates a threshold', async () => {
  render(<ManagerApp />);
  expect(await screen.findByRole('heading', { name: 'Tồn kho' })).toBeTruthy();
  expect(await screen.findByText('Áo sơ mi')).toBeTruthy();
  expect(adminApi.mock.calls.some(([url]) => url.includes('/api/manager/inventory/low-stock'))).toBe(true);

  fireEvent.click(screen.getByRole('tab', { name: 'Thẻ kho' }));
  expect(await screen.findByText('Bán đơn #9009')).toBeTruthy();
  const ledgerUrls = adminApi.mock.calls.map(([url]) => url).filter(url => url.includes('/api/manager/inventory/movements'));
  expect(ledgerUrls.at(-1)).toContain('page=0');
  expect(ledgerUrls.at(-1)).toContain('size=20');
  expect(screen.getByText('Bán hàng', { selector: 'td' })).toBeTruthy();

  fireEvent.click(screen.getByRole('tab', { name: 'Sắp hết hàng' }));
  fireEvent.click(await screen.findByRole('button', { name: 'Sửa ngưỡng' }));
  const input = screen.getByLabelText('Ngưỡng Áo sơ mi');
  fireEvent.change(input, { target: { value: '2' } });
  fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
  await waitFor(() => expect(adminApi).toHaveBeenCalledWith(
    '/api/manager/products/7/stock-threshold', { method: 'PATCH', body: { threshold: 2 } },
  ));
});

it('clears a threshold when the field is left empty', async () => {
  render(<ManagerApp />);
  await screen.findByText('Áo sơ mi');
  fireEvent.click(screen.getByRole('button', { name: 'Sửa ngưỡng' }));
  fireEvent.change(screen.getByLabelText('Ngưỡng Áo sơ mi'), { target: { value: '' } });
  fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
  await waitFor(() => expect(adminApi).toHaveBeenCalledWith(
    '/api/manager/products/7/stock-threshold', { method: 'PATCH', body: { threshold: null } },
  ));
});

it('keeps the row editor open and shows the error when saving the threshold fails', async () => {
  const normal = adminApi.getMockImplementation();
  adminApi.mockImplementation((url, options = {}) => (options.method === 'PATCH'
    ? Promise.reject(new Error('Không lưu được ngưỡng'))
    : normal(url, options)));
  render(<ManagerApp />);
  await screen.findByText('Áo sơ mi');
  fireEvent.click(screen.getByRole('button', { name: 'Sửa ngưỡng' }));
  fireEvent.change(screen.getByLabelText('Ngưỡng Áo sơ mi'), { target: { value: '3' } });
  fireEvent.click(screen.getByRole('button', { name: 'Lưu' }));
  expect((await screen.findByRole('alert')).textContent).toContain('Không lưu được ngưỡng');
  expect(screen.getByLabelText('Ngưỡng Áo sơ mi').value).toBe('3');
});

it('lists the inventory section in the manager navigation', async () => {
  render(<ManagerApp />);
  const nav = await screen.findByRole('navigation', { name: 'Chức năng quản lý' });
  const button = within(nav).getByRole('button', { name: 'Tồn kho' });
  fireEvent.click(button);
  await waitFor(() => expect(window.location.pathname).toBe('/manager/inventory'));
});

const draftDetail = {
  id: 6, code: 'KK-6', status: 'DRAFT', note: '',
  items: [{ id: 61, productId: 7, productName: 'Áo sơ mi', systemQuantity: 10, countedQuantity: null, difference: null }],
};

function stocktakeApi(url, options = {}) {
  if (options.method === 'PATCH' && url.includes('/stocktakes/6/items')) {
    return { ...draftDetail, items: [{ ...draftDetail.items[0], countedQuantity: 8, difference: -2 }] };
  }
  if (options.method === 'POST' && url === '/api/manager/stocktakes') return draftDetail;
  if (options.method === 'POST' && url === '/api/manager/stocktakes/6/complete') {
    return { ...draftDetail, status: 'COMPLETED', items: [{ ...draftDetail.items[0], countedQuantity: 8, difference: -2 }] };
  }
  if (url === '/api/manager/stocktakes/6') return draftDetail;
  if (url.includes('/api/manager/stocktakes?')) {
    return page([{ id: 6, code: 'KK-6', status: 'DRAFT', itemCount: 1, countedCount: 0, createdAt: '2026-11-01T02:00:00Z' }]);
  }
  if (url === '/api/manager/reports/inventory') {
    return {
      totalValue: 60000, totalUnits: 5, missingCostProducts: 0, lowStockCount: 1,
      categories: [{ category: 'Áo', units: 5, value: 60000 }],
      products: [{ productId: 7, name: 'Áo sơ mi', category: 'Áo', stock: 5, threshold: 5, unitCost: 12000, value: 60000, lowStock: true }],
    };
  }
  return null;
}

it('creates, counts and completes a stocktake', async () => {
  const normal = adminApi.getMockImplementation();
  adminApi.mockImplementation((url, options) => stocktakeApi(url, options) ?? normal(url, options));
  render(<ManagerApp />);
  fireEvent.click(await screen.findByRole('tab', { name: 'Kiểm kê' }));
  expect(await screen.findByText('KK-6')).toBeTruthy();

  fireEvent.click(screen.getByRole('button', { name: 'Tạo phiếu kiểm kê' }));
  expect(await screen.findByText('Phiếu KK-6 — Đang mở')).toBeTruthy();
  await waitFor(() => expect(adminApi).toHaveBeenCalledWith('/api/manager/stocktakes',
    { method: 'POST', body: { note: '', productIds: [] } }));

  fireEvent.change(screen.getByLabelText('Số đếm Áo sơ mi'), { target: { value: '8' } });
  fireEvent.click(screen.getByRole('button', { name: 'Lưu số đếm' }));
  await waitFor(() => expect(adminApi).toHaveBeenCalledWith('/api/manager/stocktakes/6/items', {
    method: 'PATCH', body: [{ itemId: 61, countedQuantity: 8, note: null }],
  }));

  fireEvent.click(screen.getByRole('button', { name: 'Hoàn tất kiểm kê' }));
  await waitFor(() => expect(adminApi).toHaveBeenCalledWith('/api/manager/stocktakes/6/complete', { method: 'POST' }));
  expect(await screen.findByText('Phiếu KK-6 — Hoàn tất')).toBeTruthy();
});

it('shows inventory value with category totals and downloads CSV files', async () => {
  const normal = adminApi.getMockImplementation();
  adminApi.mockImplementation((url, options) => stocktakeApi(url, options) ?? normal(url, options));
  render(<ManagerApp />);
  fireEvent.click(await screen.findByRole('tab', { name: 'Giá trị tồn' }));
  expect(await screen.findByText('60.000 đ', { selector: 'strong' })).toBeTruthy();
  expect(screen.getByText('Áo sơ mi')).toBeTruthy();
  expect(screen.getByText('Sắp hết')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Xuất CSV' }));
  expect(adminDownload).toHaveBeenCalledWith('/api/manager/reports/inventory.csv', 'ton-kho.csv');

  fireEvent.click(screen.getByRole('tab', { name: 'Thẻ kho' }));
  await screen.findByText('Bán đơn #9009');
  fireEvent.click(screen.getByRole('button', { name: 'Xuất CSV' }));
  expect(adminDownload).toHaveBeenCalledWith(expect.stringContaining('/api/manager/inventory/movements.csv?'), 'the-kho.csv');
});
