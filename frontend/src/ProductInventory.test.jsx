// @vitest-environment jsdom
import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import ManagerApp from './ManagerApp';
import { adminApi, adminRefreshSession } from './manager-api';

vi.mock('./manager-api', async importOriginal => ({
  ...(await importOriginal()), adminApi: vi.fn(), adminRefreshSession: vi.fn(),
}));

let product;
beforeEach(() => {
  vi.resetAllMocks();
  window.localStorage.clear();
  window.history.replaceState({}, '', '/manager/products');
  adminRefreshSession.mockResolvedValue({ manager: { fullName: 'Inventory manager' } });
  product = { id: 7, name: 'Inventory product', category: 'Clothes', price: 100000, stock: 10, active: true };
  adminApi.mockImplementation(async (url, options = {}) => {
    if (options.method === 'PUT') { product = { ...product, ...options.body }; return product; }
    if (options.method === 'PATCH') { product = { ...product, stock: options.body.quantity }; return product; }
    if (options.method === 'POST') { product = { ...product, ...options.body }; return product; }
    if (url.endsWith('/categories')) return ['Clothes'];
    return { content: [product], totalPages: 1, totalElements: 1 };
  });
});
afterEach(cleanup);

async function openEdit() {
  render(<ManagerApp />);
  await screen.findByText('Inventory product');
  fireEvent.click(screen.getByRole('button', { name: 'Sửa', exact: true }));
}

it('does not resubmit loaded stock when saving a catalog edit after checkout', async () => {
  await openEdit();
  product = { ...product, stock: 7 };
  fireEvent.change(screen.getByLabelText('TÊN SẢN PHẨM *'), { target: { value: 'Renamed product' } });
  fireEvent.click(screen.getByRole('button', { name: 'Cập nhật sản phẩm' }));
  await screen.findByText('Đã cập nhật sản phẩm.');
  expect(product.name).toBe('Renamed product');
  expect(product.stock).toBe(7);
  const writes = adminApi.mock.calls.filter(([, options]) => options?.method);
  expect(writes).toHaveLength(1);
  expect(writes[0][1].body).not.toHaveProperty('stock');
  await screen.findByText('Còn (7)');
});

it('uses the explicit stock API only for a deliberately changed quantity', async () => {
  await openEdit();
  fireEvent.change(screen.getByLabelText('TỒN KHO *'), { target: { value: '12' } });
  fireEvent.click(screen.getByRole('button', { name: 'Cập nhật sản phẩm' }));
  await screen.findByText('Đã cập nhật sản phẩm.');
  expect(adminApi).toHaveBeenCalledWith('/api/manager/products/7/stock', { method: 'PATCH', body: { quantity: 12 } });
  expect(product.stock).toBe(12);
  expect(adminApi.mock.calls.find(([, options]) => options?.method === 'PUT')[1].body).not.toHaveProperty('stock');
});

it('retains initial stock when creating a product', async () => {
  render(<ManagerApp />);
  await screen.findByText('Inventory product');
  fireEvent.click(screen.getByRole('button', { name: /Thêm sản phẩm/ }));
  fireEvent.change(screen.getByLabelText('TÊN SẢN PHẨM *'), { target: { value: 'New product' } });
  fireEvent.change(screen.getByLabelText('DANH MỤC *'), { target: { value: 'Clothes' } });
  fireEvent.change(screen.getByLabelText('GIÁ BÁN (VNĐ) *'), { target: { value: '100000' } });
  fireEvent.change(screen.getByLabelText('TỒN KHO *'), { target: { value: '5' } });
  fireEvent.click(screen.getByRole('button', { name: 'Tạo sản phẩm mới' }));
  await waitFor(() => expect(product.name).toBe('New product'));
  expect(product.stock).toBe(5);
  expect(adminApi.mock.calls.filter(([, options]) => options?.method).map(([, options]) => options.method)).toEqual(['POST']);
});

it('explains a stock failure after catalog save and retains the form for retry', async () => {
  const normalRequest = adminApi.getMockImplementation();
  let failStock = true;
  adminApi.mockImplementation((url, options = {}) => {
    if (options.method === 'PATCH' && failStock) return Promise.reject(new Error('Stock unavailable'));
    return normalRequest(url, options);
  });
  await openEdit();
  fireEvent.change(screen.getByLabelText('TÊN SẢN PHẨM *'), { target: { value: 'Renamed product' } });
  fireEvent.change(screen.getByLabelText('TỒN KHO *'), { target: { value: '12' } });
  fireEvent.click(screen.getByRole('button', { name: 'Cập nhật sản phẩm' }));
  await screen.findByText(/Đã lưu thông tin sản phẩm nhưng chưa cập nhật được tồn kho/);
  expect(product.name).toBe('Renamed product');
  expect(product.stock).toBe(10);
  expect(screen.getByLabelText('TÊN SẢN PHẨM *').value).toBe('Renamed product');
  expect(screen.getByLabelText('TỒN KHO *').value).toBe('12');
  failStock = false;
  fireEvent.click(screen.getByRole('button', { name: 'Cập nhật sản phẩm' }));
  await screen.findByText('Đã cập nhật sản phẩm.');
  expect(product.stock).toBe(12);
});
