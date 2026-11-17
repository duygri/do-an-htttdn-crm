// @vitest-environment jsdom
import React from 'react';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { legacyRevenueTarget, Reports, RevenuePage } from './ManagerApp';
import { adminApi } from './manager-api';

vi.mock('./manager-api', async original => ({ ...(await original()), adminApi: vi.fn() }));

const report = {
  collected: '0.00', purchaseCost: '0.00', paidToSuppliers: '0.00', difference: '0.00', collectedOrders: 0,
  unknownMilestone: { orders: 0, amount: '0.00' }, series: [], products: [], updatedAt: '2026-11-17T03:00:00Z',
};

beforeEach(() => {
  adminApi.mockReset();
  adminApi.mockResolvedValue({ total: 0, ages: [], preferences: [] });
  window.history.replaceState({}, '', '/manager/reports');
});
afterEach(cleanup);

it('moves legacy revenue links to the new page and keeps the report filters', () => {
  expect(legacyRevenueTarget('/manager/reports?tab=revenue&fromDate=2026-03-01&groupBy=quarter&page=2'))
    .toBe('/manager/revenue?fromDate=2026-03-01&groupBy=quarter');
  expect(legacyRevenueTarget('/manager/reports?tab=retention')).toBeNull();
  expect(legacyRevenueTarget('/manager/revenue')).toBeNull();
});

it('no longer offers the revenue tab inside Reports', () => {
  render(<Reports go={vi.fn()} />);
  expect(screen.queryByRole('button', { name: 'Doanh thu', exact: true })).toBeNull();
  expect(screen.getByRole('button', { name: 'Giữ chân khách hàng', exact: true })).toBeTruthy();
});

it('renders the revenue page with a single page-level heading', async () => {
  adminApi.mockResolvedValue(report);
  window.history.replaceState({}, '', '/manager/revenue');
  render(<RevenuePage />);
  expect(screen.getAllByRole('heading', { name: 'Doanh thu' })).toHaveLength(1);
  expect(await screen.findByRole('region', { name: 'Báo cáo doanh thu' })).toBeTruthy();
});
