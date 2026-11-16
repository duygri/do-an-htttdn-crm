// @vitest-environment jsdom
import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import ManagerApp from './ManagerApp';
import { adminApi, adminDownload, adminRefreshSession } from './manager-api';

vi.mock('./manager-api', async importOriginal => ({
  ...(await importOriginal()), adminApi: vi.fn(), adminDownload: vi.fn(), adminRefreshSession: vi.fn(),
}));

const page = content => ({ content, totalElements: content.length, totalPages: content.length ? 1 : 0 });

beforeEach(() => {
  vi.resetAllMocks();
  adminRefreshSession.mockResolvedValue({ manager: { fullName: 'Quản lý' } });
  adminDownload.mockResolvedValue(undefined);
  adminApi.mockImplementation(async () => page([]));
});
afterEach(cleanup);

it('exports the current order filters as CSV', async () => {
  window.history.replaceState({}, '', '/manager/orders');
  render(<ManagerApp />);
  fireEvent.click(await screen.findByRole('button', { name: 'Xuất CSV' }));
  await waitFor(() => expect(adminDownload).toHaveBeenCalledTimes(1));
  const [path, filename] = adminDownload.mock.calls[0];
  expect(path).toContain('/api/manager/orders.csv?');
  expect(path).not.toContain('paymentStatus');
  expect(filename).toBe('don-hang.csv');
});

it('exports the current customer search as CSV', async () => {
  window.history.replaceState({}, '', '/manager/users?q=an');
  render(<ManagerApp />);
  fireEvent.click(await screen.findByRole('button', { name: 'Xuất CSV' }));
  await waitFor(() => expect(adminDownload).toHaveBeenCalledTimes(1));
  expect(adminDownload).toHaveBeenCalledWith('/api/manager/customers.csv?q=an', 'khach-hang.csv');
});
