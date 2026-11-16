// @vitest-environment jsdom
import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import CustomerPoints from './CustomerPoints';
import CustomerProfile from './CustomerProfile';
import { api } from './api';
import { adminApi } from './manager-api';

vi.mock('./api', () => ({ api: vi.fn() }));
vi.mock('./manager-api', async importOriginal => ({ ...(await importOriginal()), adminApi: vi.fn() }));

const points = { balance: 120, history: [{ id: 1, type: 'EARN', points: 25, note: 'Đơn #9009', orderId: 9, createdAt: '2026-11-10T03:00:00Z' }] };
const profile = {
  customer: { id: 2, fullName: 'Nguyễn An', email: 'an@test.vn', phone: '0900000000', age: 22, preferences: '', createdAt: '2026-01-01T00:00:00Z', locked: false },
  summary: { totalOrders: 1, completedOrders: 1, cancelledOrders: 0, returnOrders: 0, completedValue: 255000, lastCompletedOrderAt: '2026-11-10T03:00:00Z' },
  segmentation: null,
};

beforeEach(() => {
  vi.resetAllMocks();
  api.mockResolvedValue(points);
});
afterEach(cleanup);

it('shows the customer balance and latest point entries', async () => {
  render(<CustomerPoints />);
  expect(await screen.findByText('120 điểm')).toBeTruthy();
  expect(screen.getByText(/Tích điểm/)).toBeTruthy();
  expect(screen.getByText('+25')).toBeTruthy();
});

it('redeems points and shows the voucher code', async () => {
  api.mockImplementation(async url => url === '/api/account/points/redeem'
    ? { code: 'DIEM-ABC12345', discountValue: 20000, spentPoints: 100, balance: 20, expiresAt: '2026-12-16T00:00:00Z' }
    : points);
  render(<CustomerPoints />);
  fireEvent.click(await screen.findByRole('button', { name: 'ĐỔI ĐIỂM LẤY VOUCHER' }));
  expect(await screen.findByText(/DIEM-ABC12345/)).toBeTruthy();
  expect(api).toHaveBeenCalledWith('/api/account/points/redeem', { method: 'POST' });
});

it('shows an error when points cannot be redeemed', async () => {
  api.mockImplementation(async url => {
    if (url === '/api/account/points/redeem') throw new Error('Bạn không đủ điểm.');
    return points;
  });
  render(<CustomerPoints />);
  fireEvent.click(await screen.findByRole('button', { name: 'ĐỔI ĐIỂM LẤY VOUCHER' }));
  expect((await screen.findByRole('alert')).textContent).toContain('Bạn không đủ điểm.');
});

it('shows and adjusts customer points inside the manager profile', async () => {
  adminApi.mockImplementation(async (url, options = {}) => {
    if (url === '/api/manager/users/2/profile') return profile;
    if (url.startsWith('/api/manager/users/2/profile/orders')) return { content: [], totalElements: 0, totalPages: 0 };
    if (url === '/api/manager/customers/2/points') return options.method === 'POST' ? { balance: 125, history: [] } : { balance: 75, history: [] };
    return { content: [], totalElements: 0, totalPages: 0 };
  });
  render(<CustomerProfile customerId="2" location="/manager/users/2" go={() => {}} />);
  expect(await screen.findByText('75')).toBeTruthy();
  fireEvent.change(screen.getByLabelText('Điểm điều chỉnh *'), { target: { value: '50' } });
  fireEvent.change(screen.getByLabelText('Ghi chú *'), { target: { value: 'Bù điểm khiếu nại' } });
  fireEvent.click(screen.getByRole('button', { name: 'Lưu điều chỉnh' }));
  await waitFor(() => expect(adminApi).toHaveBeenCalledWith('/api/manager/customers/2/points', { method: 'POST', body: { points: 50, note: 'Bù điểm khiếu nại' } }));
});
