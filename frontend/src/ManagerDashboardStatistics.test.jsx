// @vitest-environment jsdom
import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import ManagerDashboardStatistics from './ManagerDashboardStatistics';
import { adminApi } from './manager-api';

vi.mock('./manager-api', async (importOriginal) => ({ ...(await importOriginal()), adminApi: vi.fn() }));

const report = {
  collected: '280000.00', purchaseCost: '60000.00', paidToSuppliers: '20000.00', difference: '220000.00', collectedOrders: 3,
  groupBy: 'month',
  unknownMilestone: { orders: 0, amount: '0.00' },
  series: [
    { period: '2026-10', collected: '100000.00', collectedOrders: 1, purchaseCost: '20000.00', paidToSuppliers: '0.00', difference: '80000.00' },
    { period: '2026-11', collected: '180000.00', collectedOrders: 2, purchaseCost: '40000.00', paidToSuppliers: '20000.00', difference: '140000.00' },
  ],
  products: [
    { productId: 1, name: 'Áo sơ mi', quantity: 2, grossItemValue: '200000.00', share: '71.43' },
    { productId: 2, name: 'Quần jean', quantity: 1, grossItemValue: '80000.00', share: '28.57' },
  ],
  updatedAt: '2026-11-17T03:00:00Z',
};

beforeEach(() => {
  adminApi.mockReset();
  adminApi.mockResolvedValue(report);
});
afterEach(cleanup);

it('shows column and donut charts and links to the full statistics page', async () => {
  const go = vi.fn();
  const { container } = render(<ManagerDashboardStatistics go={go} />);
  expect(await screen.findByText('Thống kê doanh thu')).toBeTruthy();
  expect(await screen.findByText('Áo sơ mi')).toBeTruthy();
  expect(container.querySelectorAll('.stats-chart__group')).toHaveLength(2);
  expect(container.querySelector('[role="img"]')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Xem trang Thống kê' }));
  expect(go).toHaveBeenCalledWith('statistics');
});

it('reloads with the selected quarter preset', async () => {
  render(<ManagerDashboardStatistics go={vi.fn()} />);
  await screen.findByText('Thống kê doanh thu');
  await waitFor(() => expect(adminApi.mock.calls.length).toBeGreaterThan(0));
  fireEvent.click(screen.getByRole('button', { name: 'Quý' }));
  await waitFor(() =>
    expect(adminApi.mock.calls.some(([url]) => String(url).includes('groupBy=quarter'))).toBe(true)
  );
  expect(screen.getByRole('button', { name: 'Quý' }).getAttribute('aria-pressed')).toBe('true');
});
