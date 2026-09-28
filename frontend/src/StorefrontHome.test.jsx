// @vitest-environment jsdom
import React from 'react';
import { render, fireEvent, screen, cleanup } from '@testing-library/react';
import { afterEach, it, expect, vi } from 'vitest';
import StorefrontHome from './StorefrontHome';
afterEach(cleanup);
it('uses a real product for the hero and opens its detail', () => {
  const product={id:12,name:'Áo thun thật',imageUrl:'/product.png'};
  const onProduct=vi.fn(); const onCatalog=vi.fn();
  render(<StorefrontHome products={[product]} onProduct={onProduct} onCatalog={onCatalog}/>);
  expect(screen.getByAltText(product.name).getAttribute('src')).toBe(product.imageUrl);
  fireEvent.click(screen.getByRole('button',{name:/KHÁM PHÁ THIẾT KẾ/}));
  expect(onProduct).toHaveBeenCalledWith(product);
  fireEvent.click(screen.getByRole('button',{name:'Bắt đầu với áo thun'}));
  expect(onCatalog).toHaveBeenCalledWith('Áo thun');
});
it('keeps category navigation usable while products are unavailable', () => {
  const onCatalog=vi.fn();
  render(<StorefrontHome products={[]} onCatalog={onCatalog} onProduct={vi.fn()}/>);
  expect(screen.queryByRole('img')).toBeNull();
  fireEvent.click(screen.getByRole('button',{name:/02 Áo khoác/}));
  expect(onCatalog).toHaveBeenCalledWith('Áo khoác');
});
