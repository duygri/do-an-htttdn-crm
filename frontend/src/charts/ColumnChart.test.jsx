// @vitest-environment jsdom
import React from 'react';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import ColumnChart from './ColumnChart';

afterEach(cleanup);

const series = [
  { period: '2026-10', collected: '100000.00', purchaseCost: '0.00', difference: '100000.00' },
  { period: '2026-11', collected: '255000.00', purchaseCost: '40000.00', difference: '215000.00' },
];

it('renders one grouped pair of bars per period with accessible tooltips', () => {
  const { container } = render(<ColumnChart series={series} groupBy="month" />);
  const groups = container.querySelectorAll('.stats-chart__group');
  expect(groups).toHaveLength(2);
  groups.forEach((group) => {
    expect(group.querySelectorAll('.stats-chart__bar')).toHaveLength(2);
    expect(group.querySelector('.stats-chart__bar.is-collected')).toBeTruthy();
    expect(group.querySelector('.stats-chart__bar.is-cost')).toBeTruthy();
  });
  expect(groups[1].getAttribute('aria-label')).toContain('Thu:');
  expect(groups[1].getAttribute('aria-label')).toContain('Chi:');
  expect(groups[1].getAttribute('tabindex')).toBe('0');
  expect(container.textContent).not.toMatch(/NaN|Infinity/);
});

it('shows an explicit empty state instead of an empty plot', () => {
  render(<ColumnChart series={[]} groupBy="month" />);
  expect(screen.getByText('Chưa có dữ liệu trong kỳ.')).toBeTruthy();
});

it('clamps negative and missing values to zero', () => {
  const { container } = render(
    <ColumnChart
      series={[{ period: '2026-11', collected: '-100', purchaseCost: null }]}
      groupBy="month"
    />
  );
  const bars = container.querySelectorAll('.stats-chart__bar');
  expect(bars[0].style.height).toBe('0%');
  expect(bars[1].style.height).toBe('0%');
});
