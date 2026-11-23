import React, { useEffect, useRef, useState } from 'react';
import { adminApi } from './api';
import './admin-statistics.css';

const STATUS_LABELS = { PENDING: 'Chờ hoàn', COMPLETED: 'Đã hoàn', REJECTED: 'Từ chối' };
const REASON_LABELS = { CANCEL: 'Hủy đơn', RETURN: 'Đổi trả' };
const METHOD_LABELS = { BANK_TRANSFER: 'Chuyển khoản', CASH: 'Tiền mặt', OTHER: 'Khác' };
const PAGE_SIZE = 20;
const EXPIRED_STATUSES = [401, 403, 423];

const money = (value) => `${Number(value || 0).toLocaleString('vi-VN')} ₫`;
const dateTime = (value) => (value ? new Date(value).toLocaleString('vi-VN') : '—');
/** Date inputs are calendar days in Vietnam (UTC+7), converted to the ISO instant the API expects. */
const toInstant = (value) => (value ? new Date(`${value}T00:00:00+07:00`).toISOString() : '');
/** The API treats `to` as exclusive, so the selected end day is sent as the start of the following Vietnam day. */
const toExclusiveInstant = (value) => {
  if (!value) return '';
  const next = new Date(`${value}T00:00:00+07:00`);
  next.setUTCDate(next.getUTCDate() + 1);
  return next.toISOString();
};

export default function AdminRefunds() {
  const [filters, setFilters] = useState({ status: '', from: '', to: '', q: '' });
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  const alive = useRef(true);

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    const params = new URLSearchParams();
    if (filters.status) params.set('status', filters.status);
    if (filters.from) params.set('from', toInstant(filters.from));
    if (filters.to) params.set('to', toExclusiveInstant(filters.to));
    if (filters.q.trim()) params.set('q', filters.q.trim());
    params.set('page', String(page));
    params.set('size', String(PAGE_SIZE));
    Promise.all([
      adminApi(`/api/admin/refunds?${params}`, { signal: controller.signal }),
      adminApi('/api/admin/refunds/summary', { signal: controller.signal }),
    ])
      .then(([list, totals]) => {
        if (!alive.current) return;
        setData(list);
        setSummary(totals);
      })
      .catch((failure) => {
        if (!alive.current || controller.signal.aborted) return;
        if (EXPIRED_STATUSES.includes(failure.status)) {
          window.dispatchEvent(new Event('admin-session-expired'));
          return;
        }
        setError(failure.message || 'Không tải được danh sách hoàn tiền.');
      })
      .finally(() => {
        if (alive.current) setLoading(false);
      });
    return () => controller.abort();
  }, [filters, page, retry]);

  const change = (key) => (event) => {
    const value = event.target.value;
    setPage(0);
    setFilters((current) => ({ ...current, [key]: value }));
  };

  const rows = data?.content || [];
  const totalPages = data?.totalPages || 0;

  return (
    <section className="admin-refunds">
      <header className="admin-refunds-head">
        <h2>Hoàn tiền</h2>
        <p>Theo dõi toàn bộ phiếu hoàn tiền của cửa hàng. Manager lập và xử lý; Admin chỉ giám sát.</p>
      </header>

      {summary && (
        <div className="admin-refund-summary">
          <p className="admin-refund-scope">Tổng hợp toàn hệ thống, không phụ thuộc bộ lọc bên dưới.</p>
          <span>Đang chờ {summary.pendingCount} · {money(summary.pendingAmount)}</span>
          <span>Đã hoàn {summary.completedCount} · {money(summary.completedAmount)}</span>
          <span>Từ chối {summary.rejectedCount}</span>
        </div>
      )}

      <div className="admin-refund-filters">
        <label>
          Trạng thái
          <select value={filters.status} onChange={change('status')}>
            <option value="">Tất cả</option>
            <option value="PENDING">Chờ hoàn</option>
            <option value="COMPLETED">Đã hoàn</option>
            <option value="REJECTED">Từ chối</option>
          </select>
        </label>
        <label>Từ ngày<input type="date" value={filters.from} onChange={change('from')} /></label>
        <label>Đến ngày<input type="date" value={filters.to} onChange={change('to')} /></label>
        <label>
          Tìm kiếm
          <input value={filters.q} onChange={change('q')} placeholder="Mã đơn hoặc tên khách" maxLength={120} />
        </label>
      </div>

      {error ? (
        <div className="admin-refund-error" role="alert">
          <p>{error}</p>
          <button type="button" onClick={() => setRetry((value) => value + 1)}>Thử lại</button>
        </div>
      ) : loading ? (
        <p className="admin-refund-loading" role="status">Đang tải…</p>
      ) : (
        <div className="admin-refund-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Mã đơn</th>
                <th>Khách</th>
                <th>Số tiền</th>
                <th>Lý do</th>
                <th>Phương thức</th>
                <th>Trạng thái</th>
                <th>Yêu cầu lúc</th>
                <th>Xử lý lúc</th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={8}>Không có phiếu hoàn tiền phù hợp.</td>
                </tr>
              ) : (
                rows.map((row) => (
                  <tr key={row.id}>
                    <td>#{row.orderCode}</td>
                    <td>{row.customerName}</td>
                    <td>{money(row.amount)}</td>
                    <td>{REASON_LABELS[row.reasonType] || row.reasonType}</td>
                    <td>{METHOD_LABELS[row.method] || row.method}</td>
                    <td>{STATUS_LABELS[row.status] || row.status}</td>
                    <td>{dateTime(row.requestedAt)}</td>
                    <td>{dateTime(row.processedAt)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {data && totalPages > 1 && (
        <div className="admin-refund-pagination">
          <button type="button" disabled={page <= 0} onClick={() => setPage((value) => value - 1)}>Trước</button>
          <span>Trang {page + 1}/{totalPages}</span>
          <button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)}>Sau</button>
        </div>
      )}
    </section>
  );
}
