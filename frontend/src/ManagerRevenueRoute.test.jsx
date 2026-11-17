// @vitest-environment jsdom
import React from 'react';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { legacyStatisticsTarget, normalizeAdminPath, Reports, StatisticsPage } from './ManagerApp';
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

it('moves legacy report and revenue links to the statistics page and keeps the filters', () => {
  expect(legacyStatisticsTarget('/manager/reports?tab=revenue&fromDate=2026-03-01&groupBy=quarter&page=2'))
    .toBe('/manager/statistics?fromDate=2026-03-01&groupBy=quarter');
  expect(legacyStatisticsTarget('/manager/revenue?groupBy=year')).toBe('/manager/statistics?groupBy=year');
  expect(legacyStatisticsTarget('/manager/reports?tab=retention')).toBeNull();
  expect(legacyStatisticsTarget('/manager/statistics?groupBy=month')).toBeNull();
});

it('normalizes the legacy revenue route to the statistics section', () => {
  expect(normalizeAdminPath('/manager/revenue')).toBe('/manager/statistics');
  expect(normalizeAdminPath('/manager/statistics')).toBe('/manager/statistics');
});

it('no longer offers the revenue tab inside Reports', () => {
  render(<Reports go={vi.fn()} />);
  expect(screen.queryByRole('button', { name: 'Doanh thu', exact: true })).toBeNull();
  expect(screen.getByRole('button', { name: 'Giữ chân khách hàng', exact: true })).toBeTruthy();
});

it('renders the statistics page with a single page-level heading', async () => {
  adminApi.mockResolvedValue(report);
  window.history.replaceState({}, '', '/manager/statistics');
  render(<StatisticsPage />);
  expect(screen.getAllByRole('heading', { name: 'Thống kê' })).toHaveLength(1);
  expect(await screen.findByRole('region', { name: 'Báo cáo doanh thu' })).toBeTruthy();
});
