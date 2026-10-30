import { beforeEach, afterEach, it, expect, vi } from 'vitest';

beforeEach(() => { vi.resetModules(); vi.stubGlobal('fetch', vi.fn()); });
afterEach(() => vi.unstubAllGlobals());

it.each([200, 204])('accepts an empty successful archive response (%s) without retrying', async status => {
  const { adminApi } = await import('./manager-api');
  fetch.mockResolvedValue(new Response(null, { status }));
  await expect(adminApi('/api/manager/products/7', { method: 'DELETE' })).resolves.toBeNull();
  expect(fetch).toHaveBeenCalledTimes(1);
  const { isManagerMutating } = await import('./manager-sync');
  expect(isManagerMutating()).toBe(false);
});

it('preserves JSON responses and rejects malformed nonempty success bodies clearly', async () => {
  const { adminApi } = await import('./manager-api');
  fetch.mockResolvedValueOnce(new Response('{"content":[{"id":7}]}', { status: 200 }));
  await expect(adminApi('/api/manager/products')).resolves.toEqual({ content: [{ id: 7 }] });
  fetch.mockResolvedValueOnce(new Response('{invalid', { status: 200 }));
  await expect(adminApi('/api/manager/products')).rejects.toMatchObject({
    status: 200, code: 'INVALID_RESPONSE', message: expect.stringContaining('Máy chủ trả về dữ liệu không hợp lệ'),
  });
});

it('retains structured business errors and falls back for empty or non-JSON errors', async () => {
  const { adminApi } = await import('./manager-api');
  fetch.mockResolvedValueOnce(new Response(JSON.stringify({ message: 'Không tìm thấy sản phẩm.', code: 'NOT_FOUND', fields: { id: 'Sai ID' } }), { status: 404 }));
  await expect(adminApi('/api/manager/products/7', { method: 'DELETE' })).rejects.toMatchObject({ status: 404, code: 'NOT_FOUND', fields: { id: 'Sai ID' }, message: 'Không tìm thấy sản phẩm.' });
  for (const body of [null, '<html>upstream error</html>', 'null']) {
    fetch.mockResolvedValueOnce(new Response(body, { status: 503 }));
    await expect(adminApi('/api/manager/products')).rejects.toMatchObject({ status: 503, message: 'Yêu cầu thất bại (503)' });
  }
});
