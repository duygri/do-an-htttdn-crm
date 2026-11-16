// @vitest-environment jsdom
import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import RevenueReport from './RevenueReport';
import { adminApi, adminDownload } from './manager-api';

vi.mock('./manager-api', async importOriginal => ({ ...(await importOriginal()), adminApi: vi.fn(), adminDownload: vi.fn() }));

const report = {
  collected: '255000.00', purchaseCost: '40000.00', difference: '215000.00', collectedOrders: 1,
  unknownMilestone: { orders: 0, amount: '0.00' },
  series: [{ period: '2026-11', collected: '255000.00', collectedOrders: 1, purchaseCost: '40000.00', difference: '215000.00' }],
  topProducts: [{ productId: 7, name: 'Áo sơ mi', quantity: 2, grossItemValue: '250000.00' }],
  updatedAt: '2026-11-16T03:00:00Z',
};

beforeEach(() => {
  vi.resetAllMocks();
  adminApi.mockResolvedValue(report);
  adminDownload.mockResolvedValue(undefined);
});
afterEach(cleanup);

it('downloads the current revenue report as CSV', async () => {
  render(<RevenueReport />);
  expect(await screen.findByText('Áo sơ mi')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Xuất CSV' }));
  await waitFor(() => expect(adminDownload).toHaveBeenCalledTimes(1));
  const [path, filename] = adminDownload.mock.calls[0];
  expect(path).toContain('/api/manager/reports/revenue.csv?');
  expect(path).toContain('groupBy=month');
  expect(path).toContain('from=');
  expect(path).toContain('to=');
  expect(filename).toBe('doanh-thu.csv');
});
