// @vitest-environment jsdom
import React from 'react';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { afterEach, it, expect, vi } from 'vitest';
import ReturnRequestForm from './ReturnRequestForm';
afterEach(cleanup);
const items = [{ id: 11, name: 'Áo', quantity: 2, unitPrice: 100000 }, { id: 12, name: 'Quần', quantity: 1, unitPrice: 200000 }];
it('submits only the chosen item with capped quantity and resolution', () => {
  const onSubmit = vi.fn();
  render(<ReturnRequestForm items={items} onSubmit={onSubmit} onCancel={() => {}} />);
  fireEvent.click(screen.getByLabelText(/Áo/));
  fireEvent.change(screen.getByLabelText('Số lượng Áo'), { target: { value: '1' } });
  fireEvent.change(screen.getByLabelText('Hình thức xử lý Áo'), { target: { value: 'EXCHANGE' } });
  fireEvent.change(screen.getByLabelText('Lý do đổi/trả'), { target: { value: 'Áo chật' } });
  fireEvent.click(screen.getByRole('button', { name: 'Gửi yêu cầu' }));
  expect(onSubmit).toHaveBeenCalledWith({ reason: 'Áo chật', items: [{ orderItemId: 11, quantity: 1, resolution: 'EXCHANGE' }] });
});
it('requires a reason and blocks an empty selection', () => {
  const onSubmit = vi.fn();
  render(<ReturnRequestForm items={items} onSubmit={onSubmit} onCancel={() => {}} />);
  fireEvent.click(screen.getByRole('button', { name: 'Gửi yêu cầu' }));
  expect(onSubmit).not.toHaveBeenCalled();
  expect(screen.getByText('Chọn ít nhất một sản phẩm và nhập lý do.')).toBeTruthy();
});
it('omits items when legacy orders have no line ids', () => {
  const onSubmit = vi.fn();
  render(<ReturnRequestForm items={[{ name: 'Áo', quantity: 2 }]} onSubmit={onSubmit} onCancel={() => {}} />);
  fireEvent.change(screen.getByLabelText('Lý do đổi/trả'), { target: { value: 'Đổi cả đơn' } });
  fireEvent.click(screen.getByRole('button', { name: 'Gửi yêu cầu' }));
  expect(onSubmit).toHaveBeenCalledWith({ reason: 'Đổi cả đơn' });
});
