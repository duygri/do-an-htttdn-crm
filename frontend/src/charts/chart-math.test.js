import { describe, expect, it } from 'vitest';
import { compactVnd, niceScale, periodRange, reportQuery, shortPeriod } from './chart-math';

describe('chart math', () => {
  it('keeps the existing compact currency and axis scale helpers', () => {
    expect(compactVnd(2500000)).toBe('2,5 tr');
    expect(compactVnd(1500000000)).toBe('1,5 tỷ');
    expect(niceScale(255000)).toEqual({ max: 300000, ticks: [0, 100000, 200000, 300000] });
    expect(niceScale(0)).toEqual({ max: 0, ticks: [0] });
    expect(shortPeriod('2026-Q4', 'quarter')).toBe('Q4/26');
  });

  it('builds a year-to-date range for month and quarter presets', () => {
    const now = new Date('2026-11-17T05:00:00Z');
    expect(periodRange('month', now)).toEqual({
      from: '2025-12-31T17:00:00.000Z',
      to: '2026-11-17T17:00:00.000Z',
      groupBy: 'month',
    });
    expect(periodRange('quarter', now).groupBy).toBe('quarter');
    expect(periodRange('quarter', now).from).toBe('2025-12-31T17:00:00.000Z');
  });

  it('builds five calendar years for the year preset', () => {
    const now = new Date('2026-11-17T05:00:00Z');
    expect(periodRange('year', now)).toEqual({
      from: '2021-12-31T17:00:00.000Z',
      to: '2026-11-17T17:00:00.000Z',
      groupBy: 'year',
    });
  });

  it('serialises a report range without empty values', () => {
    expect(reportQuery({ from: 'a', to: 'b', groupBy: 'month' })).toBe('from=a&to=b&groupBy=month');
  });
});
