// @vitest-environment jsdom
import React from 'react';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { beforeEach, afterEach, expect, it } from 'vitest';
import { cartLineKey, reconcileSelection, useCartSelection, subtractPurchased, summarizeCart, transferGuestSelection, saveSelection, readSelection } from './cart-selection';
import { CartDrawer } from './App';
const items = [
  { productId: 1, name: 'Áo', color: 'Đen', size: 'M', stock: 8, quantity: 2, unitPrice: 100, lineTotal: 200 },
  { productId: 1, name: 'Áo', color: 'Đen', size: 'L', stock: 8, quantity: 1, unitPrice: 100, lineTotal: 100 },
  { productId: 2, name: 'Quần', stock: 0, quantity: 1, unitPrice: 300, lineTotal: 300 },
];
beforeEach(() => sessionStorage.clear()); afterEach(cleanup);
function Fixture({ owner = 'guest', initialize = true }) {
  const cart = summarizeCart(items), selection = useCartSelection({ owner, cart, ready: true, initialize });
  return <CartDrawer page cart={cart} selection={selection}/>;
}
it('selects eligible variants, supports partial and no selection, and persists across remounts', () => {
  const view = render(<Fixture/>);
  const all = screen.getByLabelText('Chọn tất cả (2)');
  expect(all.checked).toBe(true);
  fireEvent.click(screen.getByLabelText('Chọn Áo Đen M'));
  expect(all.indeterminate).toBe(true);
  expect(screen.getByText('Đã chọn 1 sản phẩm (1 phân loại).')).toBeTruthy();
  view.unmount(); render(<Fixture/>);
  expect(screen.getByLabelText('Chọn Áo Đen M').checked).toBe(false);
  fireEvent.click(screen.getByLabelText('Chọn tất cả (2)'));
  fireEvent.click(screen.getByLabelText('Chọn tất cả (2)'));
  expect(screen.getByRole('button', { name: /TIẾN HÀNH ĐẶT HÀNG/ }).disabled).toBe(true);
});
it('does not initialize a selection when opening checkout directly', () => {
  render(<Fixture initialize={false}/>);
  expect(screen.getByRole('button', { name: /TIẾN HÀNH ĐẶT HÀNG/ }).disabled).toBe(true);
});
it('keeps variant choices separate and only removes purchased quantities', () => {
  const remaining = subtractPurchased(summarizeCart(items), [{ ...items[0], quantity: 1 }]);
  expect(remaining.items.map(item => item.quantity)).toEqual([1, 1, 1]);
  expect(remaining.subtotal).toBe(500);
  const value = { known: items.map(cartLineKey), selected: [cartLineKey(items[0])] };
  expect(reconcileSelection(value, [{ ...items[0], stock: 1 }, items[1]]).selected).toEqual([]);
});
it('transfers guest choices without mixing another customer account', () => {
  saveSelection('guest', { known: items.map(cartLineKey), selected: [cartLineKey(items[1])] });
  transferGuestSelection(2);
  expect(readSelection('customer-2').selected).toEqual([cartLineKey(items[1])]);
  expect(readSelection('customer-3')).toBeNull();
});
