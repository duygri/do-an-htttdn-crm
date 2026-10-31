// @vitest-environment jsdom
import { beforeEach, afterEach, expect, it, vi } from 'vitest';

beforeEach(() => {
  vi.resetModules();
  vi.stubEnv('VITE_API_BASE_URL', '');
  vi.stubGlobal('fetch', vi.fn());
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

const response = (status, body) => ({
  ok: status < 400,
  status,
  json: async () => body,
  text: async () => (body === undefined ? '' : JSON.stringify(body)),
});

it('notifies the system admin portal when the refresh cookie is rejected', async () => {
  const { adminSignIn, adminRefreshSession } = await import('./api');
  fetch.mockResolvedValueOnce(response(200, { accessToken: 'admin-token' }));
  await adminSignIn({});
  const listener = vi.fn();
  window.addEventListener('admin-session-expired', listener);
  fetch.mockResolvedValueOnce(response(401, {}));
  expect(await adminRefreshSession()).toBe(null);
  expect(listener).toHaveBeenCalledTimes(1);
  window.removeEventListener('admin-session-expired', listener);
});
