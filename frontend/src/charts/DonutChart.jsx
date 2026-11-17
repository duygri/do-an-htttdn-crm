import React, { useMemo } from 'react';
import { CHART_PALETTE, moneyText } from './chart-math';
import './charts.css';

const percent = (value) => `${Number(value || 0).toFixed(1).replace('.0', '')}%`;

/** CSS conic-gradient donut. Keeps the six largest products and folds the rest into "Sản phẩm khác". */
export default function DonutChart({ items = [], formatValue = moneyText, title = 'Tỷ trọng doanh thu sản phẩm' }) {
  const { slices, total } = useMemo(() => {
    const valid = (Array.isArray(items) ? items : [])
      .map((item, index) => ({
        id: item.productId ?? item.id ?? index,
        label: item.name || item.label || 'Sản phẩm',
        value: Math.max(0, Number(item.grossItemValue ?? item.value) || 0),
      }))
      .filter((item) => item.value > 0)
      .sort((a, b) => b.value - a.value);
    const top = valid.slice(0, 6);
    if (valid.length > 6) {
      top.push({
        id: 'other',
        label: 'Sản phẩm khác',
        value: valid.slice(6).reduce((sum, item) => sum + item.value, 0),
      });
    }
    const sum = valid.reduce((acc, item) => acc + item.value, 0);
    return {
      total: sum,
      slices: top.map((item, index) => ({
        ...item,
        color: CHART_PALETTE[index % CHART_PALETTE.length],
        share: sum ? (item.value / sum) * 100 : 0,
      })),
    };
  }, [items]);

  if (!total) return <p className="stats-donut__empty">Chưa có sản phẩm phát sinh trong kỳ.</p>;

  let angle = 0;
  const stops = slices.map((slice) => {
    const start = angle;
    angle += (slice.value / total) * 360;
    return `${slice.color} ${start}deg ${angle}deg`;
  });
  const description = slices.map((slice) => `${slice.label} ${percent(slice.share)}`).join(', ');

  return (
    <div className="stats-donut">
      <div
        className="stats-donut__ring"
        role="img"
        aria-label={`${title}: ${description}`}
        style={{ background: `conic-gradient(${stops.join(',')})` }}
      >
        <div className="stats-donut__hole">
          <small>Tổng doanh thu gộp</small>
          <strong>{formatValue(total)}</strong>
        </div>
      </div>
      <ul className="stats-donut__legend">
        {slices.map((slice) => (
          <li key={slice.id}>
            <i style={{ background: slice.color }} aria-hidden="true" />
            <span className="stats-donut__name" title={slice.label}>
              {slice.label}
            </span>
            <b>{percent(slice.share)}</b>
            <small>{formatValue(slice.value)}</small>
          </li>
        ))}
      </ul>
    </div>
  );
}
