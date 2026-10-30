// @vitest-environment jsdom
import React, { StrictMode } from 'react';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
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

afterEach(cleanup);

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
