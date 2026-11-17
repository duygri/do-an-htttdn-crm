// @vitest-environment jsdom
import React, { StrictMode } from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
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

beforeEach(() => {
  vi.resetAllMocks();
  window.history.replaceState({}, '', '/admin');
  window.localStorage.clear();
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

it('does not prefill the admin email', async () => {
  window.history.replaceState({}, '', '/admin/login');
  adminRefreshSession.mockResolvedValue({ admin: null });
  render(<SystemAdminApp />, { wrapper });
  await waitFor(() => expect(screen.getByLabelText('Email')).toBeTruthy());
  expect(screen.getByLabelText('Email').value).toBe('');
});

it('shows real supplier counts instead of hardcoded infrastructure metrics', async () => {
  adminRefreshSession.mockResolvedValue({ admin: { fullName: 'Quản trị', email: 'admin@example.com' } });
  adminApi.mockResolvedValue({ accounts: 5, products: 12, suppliers: 3 });
  render(<SystemAdminApp />, { wrapper });
  await waitFor(() => expect(screen.getByText('Đối tác cung ứng')).toBeTruthy());
  await waitFor(() => {
    const card = screen.getByText('Đối tác cung ứng').closest('.internal-kpi-card');
    expect(card.textContent).toContain('3');
  });
  expect(screen.queryByText('100%')).toBeNull();
  expect(screen.getByText('PostgreSQL')).toBeTruthy();
  expect(screen.getByText('ADMIN / MANAGER')).toBeTruthy();
});

it('keeps the portal usable when browser storage is blocked', async () => {
  const blocked = () => {
    throw new Error('storage blocked');
  };
  vi.spyOn(Storage.prototype, 'getItem').mockImplementation(blocked);
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(blocked);
  adminRefreshSession.mockResolvedValue({ admin: { fullName: 'Quản trị', email: 'admin@example.com' } });
  adminApi.mockResolvedValue({ accounts: 5, products: 12, suppliers: 3 });
  render(<SystemAdminApp />, { wrapper });
  await waitFor(() => expect(screen.getByText('Đối tác cung ứng')).toBeTruthy());
  fireEvent.click(screen.getByRole('button', { name: 'Chế độ sáng' }));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Chế độ tối' })).toBeTruthy());
});

it('shows the yearly finance totals on the dashboard', async () => {
  adminRefreshSession.mockResolvedValue({ admin: { fullName: 'Quản trị', email: 'admin@example.com' } });
  adminApi.mockImplementation(async (url) => url.includes('finance-summary')
    ? { collected: 500000, purchaseCost: 200000, paidToSuppliers: 120000, difference: 300000, collectedOrders: 3 }
    : { accounts: 5, products: 12, suppliers: 3 });
  render(<SystemAdminApp />, { wrapper });
  const group = await screen.findByRole('group', { name: 'Thống kê tài chính' });
  await waitFor(() => expect(group.textContent).toContain('500.000'));
  expect(group.textContent).toContain('Đã trả:');
  expect(group.textContent).toContain('120.000');
  expect(group.textContent).toContain('300.000');
  expect(screen.queryByText(/nhà cung cấp/i)).toBeNull();
});

const statisticsPayload = (groupBy = 'month') => ({
  groupBy,
  collected: 600000,
  purchaseCost: 200000,
  paidToSuppliers: 100000,
  difference: 400000,
  collectedOrders: 4,
  series: [
    { period: '2026-10', collected: 400000, purchaseCost: 100000, paidToSuppliers: 50000, difference: 300000 },
    { period: '2026-11', collected: 200000, purchaseCost: 100000, paidToSuppliers: 50000, difference: 100000 },
  ],
  products: [{ productId: 1, name: 'Áo sơ mi', quantity: 2, grossItemValue: 360000, share: 60 }],
});

const mockAdmin = () => adminApi.mockImplementation(async (url) => {
  if (url.includes('/reports/statistics')) return statisticsPayload();
  if (url.includes('finance-summary')) return { collected: 500000, purchaseCost: 200000, paidToSuppliers: 120000, difference: 300000, collectedOrders: 3 };
  return { accounts: 5, products: 12, suppliers: 3 };
});

it('registers the statistics page in the admin menu and route', async () => {
  adminRefreshSession.mockResolvedValue({ admin: { fullName: 'Quản trị', email: 'admin@example.com' } });
  mockAdmin();
  window.history.replaceState({}, '', '/admin/statistics');
  render(<SystemAdminApp />, { wrapper });
  await waitFor(() => expect(screen.getByRole('link', { name: 'Thống kê' })).toBeTruthy());
  expect(screen.getByRole('link', { name: 'Thống kê' }).getAttribute('href')).toBe('/admin/statistics');
  await waitFor(() => expect(screen.getByRole('img', { name: /Tỷ trọng doanh thu sản phẩm/ })).toBeTruthy());
  expect(screen.getByRole('list', { name: /Biểu đồ doanh thu và chi phí/ })).toBeTruthy();
  await waitFor(() => expect(screen.getByText('60.00%')).toBeTruthy());
  expect(window.location.pathname).toBe('/admin/statistics');
});

it('shows both dashboard charts and links to the statistics page', async () => {
  adminRefreshSession.mockResolvedValue({ admin: { fullName: 'Quản trị', email: 'admin@example.com' } });
  mockAdmin();
  render(<SystemAdminApp />, { wrapper });
  const widget = await waitFor(() => {
    const donut = screen.getByRole('img', { name: /Tỷ trọng doanh thu sản phẩm/ });
    return donut.closest('.admin-statistics-widget');
  });
  expect(widget.textContent).toContain('Thống kê doanh thu');
  fireEvent.click(screen.getByRole('button', { name: 'Xem trang Thống kê' }));
  expect(window.location.pathname).toBe('/admin/statistics');
});

it('keeps the dashboard statistics widget graceful when the payload is unrelated', async () => {
  adminRefreshSession.mockResolvedValue({ admin: { fullName: 'Quản trị', email: 'admin@example.com' } });
  adminApi.mockResolvedValue({ accounts: 5, products: 12, suppliers: 3 });
  render(<SystemAdminApp />, { wrapper });
  await waitFor(() => expect(screen.getByText('Đối tác cung ứng')).toBeTruthy());
  await waitFor(() => expect(screen.getByText('Chưa có dữ liệu trong kỳ.')).toBeTruthy());
  expect(screen.getByText('Chưa có sản phẩm phát sinh trong kỳ.')).toBeTruthy();
});

it('returns to the login screen when the admin session expires', async () => {  adminRefreshSession.mockResolvedValue({ admin: { fullName: 'Quản trị', email: 'admin@example.com' } });
  adminApi.mockResolvedValue({ accounts: 5, products: 12, suppliers: 3 });
  render(<SystemAdminApp />, { wrapper });
  await waitFor(() => expect(screen.getByText('Đối tác cung ứng')).toBeTruthy());
  act(() => {
    window.dispatchEvent(new Event('admin-session-expired'));
  });
  await waitFor(() => expect(window.location.pathname).toBe('/admin/login'));
  expect(screen.getByLabelText('Email')).toBeTruthy();
  expect(screen.queryByRole('alert')).toBeNull();
});
