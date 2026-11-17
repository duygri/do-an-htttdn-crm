import React from 'react';
import ColumnChart from './ColumnChart';
import DonutChart from './DonutChart';
import { moneyText } from './chart-math';
import './charts.css';

const PERIODS = [
  ['month', 'Tháng'],
  ['quarter', 'Quý'],
  ['year', 'Năm'],
];

const safeNumber = (value) => Number(value || 0);
const safePct = (value) => Math.min(100, Math.max(0, safeNumber(value)));

export default function StatsPanel({
  title = 'Thống kê',
  eyebrow = 'THỐNG KÊ',
  period = 'month',
  onPeriodChange,
  data,
  loading = false,
  error = '',
  backgroundError = '',
  onRetry,
  onOpenFull,
  variant = 'page',
  showProducts = false,
  className = '',
}) {
  const products = Array.isArray(data?.products) ? data.products : [];
  const series = Array.isArray(data?.series) ? data.series : [];
  const compact = variant === 'dashboard';
  const retryButton = onRetry ? (
    <button type="button" className="admin-action" onClick={() => onRetry()}>
      Thử lại
    </button>
  ) : null;

  return (
    <section className={`stats-panel${compact ? ' is-compact' : ''}${className ? ` ${className}` : ''}`} aria-label={title}>
      <div className="stats-panel__head">
        <div>
          <span className="stats-panel__eyebrow">{eyebrow}</span>
          <h2>{title}</h2>
          <p>Doanh thu đã thu, chi phí nhập hàng và đóng góp của từng sản phẩm.</p>
        </div>
        <div className="stats-period" role="group" aria-label="Kỳ thống kê">
          {PERIODS.map(([value, label]) => (
            <button
              key={value}
              type="button"
              className={period === value ? 'is-active' : ''}
              aria-pressed={period === value}
              onClick={() => onPeriodChange?.(value)}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {loading && !data ? <p role="status">Đang tải số liệu thống kê…</p> : null}
      {!loading && !data && error ? (
        <div className="stats-panel__error" role="alert">
          <span>{error}</span>
          {retryButton}
        </div>
      ) : null}
      {backgroundError ? (
        <p className="stats-panel__stale" role="status">
          Chưa cập nhật được số liệu mới. {backgroundError}
        </p>
      ) : null}

      {data ? (
        <>
          <div className="stats-kpis">
            <article>
              <span>Doanh thu đã thu</span>
              <strong>{moneyText(data.collected)}</strong>
              <small>{safeNumber(data.collectedOrders)} đơn có thu</small>
            </article>
            <article>
              <span>Chi phí nhập hàng</span>
              <strong>{moneyText(data.purchaseCost)}</strong>
              <small>Đã trả NCC: {moneyText(data.paidToSuppliers)}</small>
            </article>
            <article>
              <span>Chênh lệch</span>
              <strong className={safeNumber(data.difference) < 0 ? 'is-negative' : ''}>{moneyText(data.difference)}</strong>
              <small>Thu trừ chi phí nhập</small>
            </article>
          </div>

          <div className="stats-chart-grid">
            <div className="stats-card">
              <div className="stats-card__head">
                <h3>Diễn biến thu và chi phí</h3>
                <span className="stats-legend" aria-hidden="true">
                  <i className="is-collected" />Thu
                  <i className="is-cost" />Chi
                </span>
              </div>
              <ColumnChart series={series} groupBy={data.groupBy || period} compact={compact} />
            </div>
            <div className="stats-card">
              <div className="stats-card__head">
                <h3>Đóng góp theo sản phẩm</h3>
              </div>
              <DonutChart items={products} title="Tỷ trọng doanh thu sản phẩm" />
            </div>
          </div>

          {showProducts ? (
            <div className="stats-products">
              <h3>Toàn bộ sản phẩm trong kỳ</h3>
              {!products.length ? (
                <p>Chưa có sản phẩm phù hợp trong kỳ.</p>
              ) : (
                <div className="stats-products__scroll">
                  <table>
                    <caption>Doanh thu gộp theo sản phẩm</caption>
                    <thead>
                      <tr>
                        <th>Sản phẩm</th>
                        <th>Số lượng</th>
                        <th>Doanh thu gộp</th>
                        <th>% đóng góp</th>
                      </tr>
                    </thead>
                    <tbody>
                      {products.map((product) => (
                        <tr key={product.productId ?? product.id ?? product.name}>
                          <td>{product.name}</td>
                          <td>{product.quantity ?? '—'}</td>
                          <td>{moneyText(product.grossItemValue)}</td>
                          <td className="stats-share">
                            <span>{product.share == null ? '—' : `${Number(product.share).toFixed(2)}%`}</span>
                            <span className="stats-share__track" aria-hidden="true">
                              <span className="stats-share__fill" style={{ width: `${safePct(product.share)}%` }} />
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          ) : null}

          <div className="stats-panel__foot">
            <small>
              Cập nhật{' '}
              {data.updatedAt
                ? new Date(data.updatedAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })
                : '—'}
            </small>
            {onOpenFull ? (
              <button type="button" className="admin-action" onClick={onOpenFull}>
                Xem trang Thống kê
              </button>
            ) : null}
          </div>
        </>
      ) : null}
    </section>
  );
}
