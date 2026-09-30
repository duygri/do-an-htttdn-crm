// @vitest-environment jsdom
import React from 'react';
import { render, fireEvent, screen, cleanup } from '@testing-library/react';
import { afterEach, it, expect, vi } from 'vitest';
import StorefrontHome from './StorefrontHome';
afterEach(cleanup);
it('uses permanent local Figma collection artwork rather than a random product', () => {
  render(<StorefrontHome onCatalog={vi.fn()}/>);
  expect(screen.getByRole('img').getAttribute('src')).toBe('/images/figma/daily-uniform.png');
});
it('keeps collection actions and catalog navigation functional', () => {
  const onCatalog=vi.fn();
  render(<StorefrontHome onCatalog={onCatalog}/>);
  fireEvent.click(screen.getByRole('button',{name:/Khám phá hàng mới/}));
  expect(onCatalog).toHaveBeenLastCalledWith('');
  fireEvent.click(screen.getByRole('button',{name:'Xem Daily Uniform'}));
  expect(onCatalog).toHaveBeenLastCalledWith('Áo polo');
  fireEvent.click(screen.getByRole('button',{name:'Khám phá bộ sưu tập Daily Uniform'}));
  expect(onCatalog).toHaveBeenLastCalledWith('Áo polo');
});
