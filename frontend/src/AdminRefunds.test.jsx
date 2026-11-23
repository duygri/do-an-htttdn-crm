// @vitest-environment jsdom
import React from 'react';
import { render, screen, cleanup, waitFor, fireEvent } from '@testing-library/react';
import { afterEach, describe, it, expect, vi } from 'vitest';
import AdminRefunds from './AdminRefunds';
import { adminApi } from './api';

vi.mock('./api', async (original) => ({ ...(await original()), adminApi: vi.fn() }));

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

const summaryPayload = { pendingCount: 1, pendingAmount: 50000, completedCount: 1, completedAmount: 100000, rejectedCount: 0 };

const listPayload = (overrides = {}) => ({
  content: [{
    id: 1, orderId: 9, orderCode: 9009, customerName: 'Khách A', amount: 100000, status: 'COMPLETED',
    reasonType: 'RETURN', method: 'BANK_TRANSFER', requestedAt: '2026-11-20T00:00:00Z', processedAt: '2026-11-21T00:00:00Z',
  }],
  totalElements: 1, totalPages: 1, number: 0, size: 20,
  ...overrides,
});

const isSummary = (url) => String(url).endsWith('/summary');

const mockRefunds = (list, summary = summaryPayload) => adminApi.mockImplementation(async (url) => (isSummary(url) ? summary : list));

it('lists refunds with totals', async () => {
  mockRefunds(listPayload());
  render(<AdminRefunds />);
  expect(await screen.findByText('Khách A')).toBeTruthy();
  expect(screen.getAllByText(/100\.000/).length).toBeGreaterThan(0);
  expect(screen.getByText(/Đang chờ/)).toBeTruthy();
});

describe('Admin refunds page', () => {
  it('renders the summary, the refund row and no bank reference or edit control', async () => {
    mockRefunds(listPayload());
    render(<AdminRefunds />);
    const table = await screen.findByRole('table');
    expect(table.className).toBe('admin-table');
    const summary = document.querySelector('.admin-refund-summary');
    expect(summary.textContent).toContain('Đang chờ 1');
    expect(summary.textContent).toContain('50.000 ₫');
    expect(summary.textContent).toContain('Đã hoàn 1');
    expect(summary.textContent).toContain('Từ chối 0');
    expect(screen.getByText('#9009')).toBeTruthy();
    expect(screen.getByText('Đổi trả')).toBeTruthy();
    expect(screen.getByText('Chuyển khoản')).toBeTruthy();
    const statusCell = Array.from(table.querySelectorAll('td')).find((cell) => cell.textContent === 'Đã hoàn');
    expect(statusCell).toBeTruthy();
    expect(document.body.textContent).not.toMatch(/bankReference|FT-/);
    expect(screen.queryByRole('button', { name: /sửa|xử lý|hoàn tiền thành công|từ chối/i })).toBeNull();
  });

  it('sends the filters as query parameters and resets to the first page', async () => {
    mockRefunds(listPayload());
    render(<AdminRefunds />);
    await screen.findByText('Khách A');
    fireEvent.change(screen.getByLabelText('Trạng thái'), { target: { value: 'PENDING' } });
    await waitFor(() => expect(adminApi.mock.calls.some(([url]) => String(url).includes('status=PENDING'))).toBe(true));
    fireEvent.change(screen.getByLabelText('Từ ngày'), { target: { value: '2026-11-01' } });
    await waitFor(() => expect(adminApi.mock.calls.some(([url]) => String(url).includes('from=2026-10-31T17%3A00%3A00.000Z'))).toBe(true));
    fireEvent.change(screen.getByLabelText('Tìm kiếm'), { target: { value: '9009' } });
    await waitFor(() => expect(adminApi.mock.calls.some(([url]) => String(url).includes('q=9009'))).toBe(true));
    expect(adminApi.mock.calls.some(([url]) => String(url).includes('size=20'))).toBe(true);
  });

  it('treats the end date as inclusive by sending the start of the next Vietnam day', async () => {
    mockRefunds(listPayload({ content: [], totalElements: 0, totalPages: 0 }));
    render(<AdminRefunds />);
    await screen.findByText('Không có phiếu hoàn tiền phù hợp.');
    fireEvent.change(screen.getByLabelText('Đến ngày'), { target: { value: '2026-11-02' } });
    await waitFor(() => expect(adminApi.mock.calls.some(([url]) => String(url).includes('to=2026-11-02T17%3A00%3A00.000Z'))).toBe(true));
  });

  it('paginates twenty refunds per page', async () => {
    mockRefunds(listPayload({ totalElements: 25, totalPages: 2 }));
    render(<AdminRefunds />);
    await screen.findByText('Khách A');
    expect(screen.getByText('Trang 1/2')).toBeTruthy();
    const next = screen.getByRole('button', { name: 'Sau' });
    fireEvent.click(next);
    await waitFor(() => expect(adminApi.mock.calls.some(([url]) => String(url).includes('page=1'))).toBe(true));
    await waitFor(() => expect(screen.getByText('Trang 2/2')).toBeTruthy());
    expect(screen.getByRole('button', { name: 'Sau' }).disabled).toBe(true);
    expect(screen.getByRole('button', { name: 'Trước' }).disabled).toBe(false);
  });

  it('shows an empty state when nothing matches', async () => {
    mockRefunds(listPayload({ content: [], totalElements: 0, totalPages: 0 }));
    render(<AdminRefunds />);
    expect(await screen.findByText('Không có phiếu hoàn tiền phù hợp.')).toBeTruthy();
  });

  it('shows an error with retry and reloads on click', async () => {
    adminApi.mockRejectedValue(Object.assign(new Error('Máy chủ gặp sự cố.'), { status: 500 }));
    render(<AdminRefunds />);
    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toContain('Máy chủ gặp sự cố.');
    adminApi.mockReset();
    mockRefunds(listPayload());
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('Khách A')).toBeTruthy();
  });

  it('signals an expired admin session instead of rendering the error box', async () => {
    const expired = vi.fn();
    window.addEventListener('admin-session-expired', expired);
    adminApi.mockRejectedValue(Object.assign(new Error('Không có quyền truy cập portal này.'), { status: 403 }));
    render(<AdminRefunds />);
    await waitFor(() => expect(expired).toHaveBeenCalled());
    expect(screen.queryByRole('alert')).toBeNull();
    window.removeEventListener('admin-session-expired', expired);
  });
});
