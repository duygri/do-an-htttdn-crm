// @vitest-environment jsdom
import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import RevenueReport, { compactVnd, niceScale } from './RevenueReport';
import { adminApi, adminDownload } from './manager-api';

vi.mock('./manager-api', async importOriginal => ({ ...(await importOriginal()), adminApi: vi.fn(), adminDownload: vi.fn() }));

const report = {
  collected: '255000.00', purchaseCost: '40000.00', paidToSuppliers: '15000.00', difference: '215000.00', collectedOrders: 1,
  unknownMilestone: { orders: 0, amount: '0.00' },
  series: [{ period: '2026-11', collected: '255000.00', collectedOrders: 1, purchaseCost: '40000.00', paidToSuppliers: '15000.00', difference: '215000.00' }],
  topProducts: [{ productId: 7, name: 'Áo sơ mi', quantity: 2, grossItemValue: '250000.00' }],
  products: [{ productId: 7, name: 'Áo sơ mi', quantity: 2, grossItemValue: '250000.00', share: '100.00' }],
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

it('shows the paid-to-supplier total and exports the full product list', async () => {
  render(<RevenueReport />);
  expect(await screen.findByText('100.00%')).toBeTruthy();
  expect(screen.getByText('Đã trả nhà cung cấp')).toBeTruthy();
  expect(screen.getByText('Đã trả nhà cung cấp').closest('article').textContent).toContain('15.000');
  fireEvent.click(screen.getByRole('button', { name: 'Xuất CSV sản phẩm' }));
  await waitFor(() => expect(adminDownload).toHaveBeenCalledTimes(1));
  const [path, filename] = adminDownload.mock.calls[0];
  expect(path).toContain('/api/manager/reports/product-revenue.csv?');
  expect(path).toContain('groupBy=month');
  expect(filename).toBe('doanh-thu-san-pham.csv');
});

const multiReport = {
  ...report,
  series: [
    { period: '2026-09', collected: '100000.00', collectedOrders: 1, purchaseCost: '0.00', paidToSuppliers: '0.00', difference: '100000.00' },
    { period: '2026-10', collected: '0.00', collectedOrders: 0, purchaseCost: '50000.00', paidToSuppliers: '0.00', difference: '-50000.00' },
    { period: '2026-11', collected: '255000.00', collectedOrders: 1, purchaseCost: '40000.00', paidToSuppliers: '15000.00', difference: '215000.00' },
  ],
};

it('renders a grouped column chart with two bars per period and exact tooltip values', async () => {
  adminApi.mockResolvedValue(multiReport);
  const { container } = render(<RevenueReport />);
  await screen.findByText('Áo sơ mi');
  const groups = container.querySelectorAll('.cashflow-chart__group');
  expect(groups).toHaveLength(3);
  groups.forEach((group) => {
    expect(group.querySelectorAll('.cashflow-chart__bar')).toHaveLength(2);
    expect(group.querySelector('.cashflow-chart__bar.is-collected')).toBeTruthy();
    expect(group.querySelector('.cashflow-chart__bar.is-cost')).toBeTruthy();
  });
  const bars = groups[2].querySelectorAll('.cashflow-chart__bar');
  expect(Number.parseFloat(bars[0].style.height)).toBeGreaterThan(80);
  expect(Number.parseFloat(bars[1].style.height)).toBeGreaterThan(0);
  expect(Number.parseFloat(groups[1].querySelector('.is-cost').style.height)).toBeGreaterThan(0);
  expect(groups[1].getAttribute('tabindex')).toBe('0');
  expect(groups[1].getAttribute('aria-label')).toContain('chi phí');
  expect(groups[1].textContent).toContain('Thu:');
  expect(groups[1].textContent).toContain('Chi:');
  expect(document.body.textContent).not.toMatch(/NaN|Infinity/);
});

it('renders a horizontal contribution bar in the product table', async () => {
  const { container } = render(<RevenueReport />);
  await screen.findByText('100.00%');
  const cell = container.querySelector('.cashflow-share');
  expect(cell.querySelector('.cashflow-share__track')).toBeTruthy();
  expect(cell.querySelector('.cashflow-share__fill').style.width).toBe('100%');
});

it('handles an empty series and builds readable axis ticks', async () => {
  adminApi.mockResolvedValue({ ...report, series: [] });
  render(<RevenueReport />);
  expect(await screen.findByText('Chưa có kỳ dữ liệu để vẽ biểu đồ.')).toBeTruthy();
  expect(niceScale(255000)).toEqual({ max: 300000, ticks: [0, 100000, 200000, 300000] });
  expect(niceScale(0)).toEqual({ max: 0, ticks: [0] });
  expect(compactVnd(2500000)).toBe('2,5 tr');
  expect(compactVnd(1500000000)).toBe('1,5 tỷ');
});
