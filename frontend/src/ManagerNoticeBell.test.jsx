// @vitest-environment jsdom
import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import ManagerNoticeBell from './ManagerNoticeBell';
import { adminApi } from './manager-api';

vi.mock('./manager-api', async importOriginal => ({ ...(await importOriginal()), adminApi: vi.fn() }));

const notice = { id: 5, type: 'LOW_STOCK', title: 'Sản phẩm sắp hết', content: 'Áo sơ mi còn 3 (ngưỡng 5).', link: '/manager/inventory?tab=low', readAt: null, createdAt: '2026-11-16T03:00:00Z' };

beforeEach(() => {
  vi.resetAllMocks();
  adminApi.mockImplementation(async (url, options = {}) => {
    if (options.method === 'PATCH') return undefined;
    if (url.startsWith('/api/manager/notices/summary')) return { unreadCount: 2 };
    if (url.startsWith('/api/manager/notices?')) return { page: { content: [notice], totalPages: 1, totalElements: 1 }, unread: 2 };
    return undefined;
  });
});
afterEach(cleanup);

it('shows the unread badge and navigates when a notice is opened', async () => {
  const go = vi.fn();
  render(<ManagerNoticeBell go={go} location="/manager/dashboard" />);
  const trigger = await screen.findByRole('button', { name: 'Thông báo vận hành: 2 chưa đọc' });
  fireEvent.click(trigger);
  expect(await screen.findByText('Sản phẩm sắp hết')).toBeTruthy();
  fireEvent.click(screen.getByText('Sản phẩm sắp hết'));
  await waitFor(() => expect(adminApi).toHaveBeenCalledWith('/api/manager/notices/5/read', { method: 'PATCH' }));
  expect(go).toHaveBeenCalledWith('inventory?tab=low');
});

it('marks every notice as read without navigating away', async () => {
  const go = vi.fn();
  render(<ManagerNoticeBell go={go} location="/manager/dashboard" />);
  fireEvent.click(await screen.findByRole('button', { name: 'Thông báo vận hành: 2 chưa đọc' }));
  fireEvent.click(await screen.findByRole('button', { name: 'Đánh dấu tất cả đã đọc' }));
  await waitFor(() => expect(adminApi).toHaveBeenCalledWith('/api/manager/notices/read-all', { method: 'PATCH' }));
  expect(go).not.toHaveBeenCalled();
});
