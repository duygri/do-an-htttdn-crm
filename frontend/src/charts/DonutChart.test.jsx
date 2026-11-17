// @vitest-environment jsdom
import React from 'react';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import DonutChart from './DonutChart';

afterEach(cleanup);

const products = [
  { productId: 1, name: 'Áo sơ mi', grossItemValue: '250000.00', share: '50.00' },
  { productId: 2, name: 'Quần jean', grossItemValue: '150000.00', share: '30.00' },
  { productId: 3, name: 'Áo khoác', grossItemValue: '100000.00', share: '20.00' },
];

it('renders product slices, legend and a share-aware accessible label', () => {
  render(<DonutChart items={products} />);
  expect(screen.getByRole('img', { name: /Áo sơ mi 50%/ })).toBeTruthy();
  expect(screen.getByText('Quần jean')).toBeTruthy();
  expect(screen.getAllByText('50%').length).toBeGreaterThan(0);
  expect(screen.getByText(/Tổng doanh thu gộp/)).toBeTruthy();
});

it('folds the tail into an "other products" slice', () => {
  const many = Array.from({ length: 8 }, (_, index) => ({
    productId: index + 1,
    name: `Sản phẩm ${index + 1}`,
    grossItemValue: String(100 - index * 10),
    share: String(100 - index * 10),
  }));
  render(<DonutChart items={many} />);
  expect(screen.getByText('Sản phẩm khác')).toBeTruthy();
  expect(screen.getByRole('img').getAttribute('aria-label')).toContain('Sản phẩm khác');
});

it('shows an empty state when no product has revenue', () => {
  render(<DonutChart items={[]} />);
  expect(screen.getByText('Chưa có sản phẩm phát sinh trong kỳ.')).toBeTruthy();
});
