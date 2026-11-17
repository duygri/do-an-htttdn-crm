import React, { useMemo } from 'react';
import { compactVnd, GROUP_LABELS, moneyText, niceScale, shortPeriod } from './chart-math';
import './charts.css';

/** Grouped column chart: collected revenue (green) versus purchase cost (orange). */
export default function ColumnChart({ series = [], groupBy = 'month', ariaLabel, compact = false }) {
  const rows = Array.isArray(series) ? series : [];
  const { max, ticks } = useMemo(
    () => niceScale(Math.max(0, ...rows.flatMap((row) => [Number(row.collected) || 0, Number(row.purchaseCost) || 0]))),
    [rows]
  );
  if (!rows.length) return <p className="stats-chart__empty">Chưa có dữ liệu trong kỳ.</p>;
  const label = ariaLabel || `Biểu đồ doanh thu và chi phí theo ${GROUP_LABELS[groupBy] || 'kỳ'}`;
  const minWidth = Math.max(120, rows.length * (groupBy === 'day' ? 34 : 46));
  return (
    <div className={`stats-chart${compact ? ' is-compact' : ''}`}>
      <div className="stats-chart__axis" aria-hidden="true">
        {ticks.map((tick) => (
          <span key={tick} style={{ bottom: `${max ? (tick / max) * 100 : 0}%` }}>
            {compactVnd(tick)}
          </span>
        ))}
      </div>
      <div className="stats-chart__scroll">
        <div className="stats-chart__canvas" style={{ minWidth: `${minWidth}px` }}>
          <div className="stats-chart__grid" aria-hidden="true">
            {ticks.map((tick) => (
              <span key={tick} style={{ bottom: `${max ? (tick / max) * 100 : 0}%` }} />
            ))}
          </div>
          <ol className="stats-chart__groups" aria-label={label}>
            {rows.map((row) => {
              const collected = Math.max(0, Number(row.collected) || 0);
              const cost = Math.max(0, Number(row.purchaseCost) || 0);
              const tip = `${row.period}: Thu: ${moneyText(row.collected)}, Chi: ${moneyText(row.purchaseCost)}, Chênh lệch: ${moneyText(row.difference)}`;
              return (
                <li key={row.period} className="stats-chart__group" tabIndex={0} aria-label={tip} title={tip}>
                  <div className="stats-chart__plot" aria-hidden="true">
                    <span
                      className="stats-chart__bar is-collected"
                      style={{ height: `${max ? (collected / max) * 100 : 0}%` }}
                    />
                    <span
                      className="stats-chart__bar is-cost"
                      style={{ height: `${max ? (cost / max) * 100 : 0}%` }}
                    />
                  </div>
                  <span className="stats-chart__label" aria-hidden="true">
                    {shortPeriod(row.period, groupBy)}
                  </span>
                  <span className="stats-chart__tooltip" role="presentation">
                    <b>{row.period}</b>
                    <span>
                      <i className="is-collected" />
                      Thu: {moneyText(row.collected)}
                    </span>
                    <span>
                      <i className="is-cost" />
                      Chi: {moneyText(row.purchaseCost)}
                    </span>
                    <span>Chênh lệch: {moneyText(row.difference)}</span>
                  </span>
                </li>
              );
            })}
          </ol>
        </div>
      </div>
    </div>
  );
}
