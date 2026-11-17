// @vitest-environment jsdom
import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import AdminStatisticsPage, { AdminStatisticsWidget } from './AdminStatistics';
import { adminApi } from './api';

vi.mock('./api', async (importOriginal) => ({
  ...(await importOriginal()),
  adminApi: vi.fn(),
}));

const report = {
  collected: '500000.00',
  purchaseCost: '200000.00',
  paidToSuppliers: '120000.00',
  difference: '300000.00',
  collectedOrders: 4,
  groupBy: 'month',
  series: [
    { period: '2026-10', collected: '200000.00', collectedOrders: 2, purchaseCost: '50000.00', paidToSuppliers: '0.00', difference: '150000.00' },
    { period: '2026-11', collected: '300000.00', collectedOrders: 2, purchaseCost: '150000.00', paidToSuppliers: '120000.00', difference: '150000.00' },
  ],
  products: [
    { productId: 1, name: 'Áo sơ mi', quantity: 2, grossItemValue: '300000.00', share: '60.00' },
    { productId: 2, name: 'Quần jean', quantity: 2, grossItemValue: '200000.00', share: '40.00' },
  ],
  updatedAt: '2026-11-17T03:00:00Z',
};

beforeEach(() => {
  adminApi.mockReset();
  adminApi.mockResolvedValue(report);
});
afterEach(cleanup);

it('renders the admin statistics page with both chart types and the product table', async () => {
  const { container } = render(<AdminStatisticsPage />);
  expect(await screen.findByText('Thống kê doanh thu')).toBeTruthy();
  expect(container.querySelectorAll('.stats-chart__group')).toHaveLength(2);
  expect(container.querySelector('[role="img"]')).toBeTruthy();
  expect(screen.getAllByText('Áo sơ mi').length).toBeGreaterThan(1);
  expect(screen.getByText('60.00%')).toBeTruthy();
  expect(screen.getAllByText(/500\.000/).length).toBeGreaterThan(0);
});

it('reloads the admin report with the chosen quarter preset', async () => {
  render(<AdminStatisticsPage />);
  await screen.findByText('Thống kê doanh thu');
  fireEvent.click(screen.getByRole('button', { name: 'Quý' }));
  await waitFor(() =>
    expect(adminApi.mock.calls.some(([url]) => String(url).includes('/api/admin/reports/statistics'))).toBe(true)
  );
  await waitFor(() =>
    expect(adminApi.mock.calls.some(([url]) => String(url).includes('groupBy=quarter'))).toBe(true)
  );
});

it('links from the dashboard widget to the full admin statistics page', async () => {
  const go = vi.fn();
  render(<AdminStatisticsWidget go={go} refreshKey={0} />);
  expect(await screen.findByText('Thống kê doanh thu')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Xem trang Thống kê' }));
  expect(go).toHaveBeenCalledWith('/admin/statistics');
});

it('does not crash when the shared dashboard mock returns an unrelated shape', async () => {
  adminApi.mockResolvedValue({ accounts: 5, products: 12, suppliers: 3 });
  render(<AdminStatisticsWidget go={vi.fn()} refreshKey={0} />);
  expect(await screen.findByText('Thống kê doanh thu')).toBeTruthy();
  expect(screen.getByText('Chưa có dữ liệu trong kỳ.')).toBeTruthy();
});
