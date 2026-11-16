import React, { useCallback, useState } from 'react';
import { adminApi } from './manager-api';
import { useManagerList, useManagerReadPause } from './useManagerResource';
import { useManagerFilters, managerQuery } from './useManagerFilters';
import './manager-inventory.css';

export const movementLabels = {
  SALE: 'Bán hàng',
  CANCEL_RELEASE: 'Hoàn kho do hủy',
  ADJUST: 'Điều chỉnh',
  INITIAL: 'Khởi tạo',
  STOCKTAKE: 'Kiểm kê',
};

const dateTime = (value) => (value ? new Date(value).toLocaleString('vi-VN') : '—');
const number = (value) => new Intl.NumberFormat('vi-VN').format(Number(value || 0));

export default function ManagerInventory() {
  const [tab, setTab] = useState('low');
  const [filters, change] = useManagerFilters({ type: '', from: '', to: '', page: '0' });
  const page = Number(filters.page) || 0;
  const lowEndpoint = useCallback(
    () => `/api/manager/inventory/low-stock?${managerQuery({ page: 0, size: 100 })}`,
    [],
  );
  const ledgerEndpoint = useCallback(
    () => `/api/manager/inventory/movements?${managerQuery({ type: filters.type, from: filters.from, to: filters.to, page, size: 20 })}`,
    [filters.type, filters.from, filters.to, page],
  );
  const low = useManagerList(lowEndpoint, { enabled: tab === 'low' });
  const ledger = useManagerList(ledgerEndpoint, { enabled: tab === 'ledger' });
  const [editing, setEditing] = useState(null);
  const [threshold, setThreshold] = useState('');
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState('');
  useManagerReadPause(editing !== null);

  const startEdit = (row) => {
    setEditing(row.productId);
    setThreshold(String(row.threshold));
    setFailure('');
  };

  const saveThreshold = async (row) => {
    if (busy) return;
    setBusy(true);
    setFailure('');
    try {
      const value = threshold === '' ? null : Number(threshold);
      await adminApi(`/api/manager/products/${row.productId}/stock-threshold`, {
        method: 'PATCH',
        body: { threshold: value },
      });
      setEditing(null);
      low.load();
    } catch (error) {
      setFailure(error.message);
    } finally {
      setBusy(false);
    }
  };

  const lowRows = low.data?.content || [];
  const ledgerRows = ledger.data?.content || [];

  return (
    <section className="refund-card manager-inventory" aria-label="Tồn kho">
      <div className="refund-actions">
        <h2>Tồn kho</h2>
      </div>
      <p>Theo dõi cảnh báo sắp hết hàng và toàn bộ biến động tồn kho của sản phẩm.</p>
      <div className="refund-actions" role="tablist" aria-label="Chế độ xem tồn kho">
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'low'}
          className={tab === 'low' ? 'admin-primary' : ''}
          onClick={() => setTab('low')}
        >
          Sắp hết hàng
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'ledger'}
          className={tab === 'ledger' ? 'admin-primary' : ''}
          onClick={() => setTab('ledger')}
        >
          Thẻ kho
        </button>
      </div>

      {failure && <p role="alert">{failure}</p>}

      {tab === 'low' && (
        <div>
          {low.loading && <p role="status">Đang tải cảnh báo tồn kho…</p>}
          {low.error && <p role="alert">{low.error} <button type="button" onClick={() => low.load()}>Thử lại</button></p>}
          {!low.loading && lowRows.length === 0 && <p>Không có sản phẩm nào dưới ngưỡng cảnh báo.</p>}
          <div className="inventory-table">
            <table>
              <thead>
                <tr><th>Sản phẩm</th><th>Danh mục</th><th>Tồn</th><th>Ngưỡng</th><th /></tr>
              </thead>
              <tbody>
                {lowRows.map((row) => (
                  <tr key={row.productId}>
                    <td>{row.name}</td>
                    <td>{row.category || '—'}</td>
                    <td>{number(row.stock)}</td>
                    <td>
                      {editing === row.productId ? (
                        <input
                          type="number"
                          min="0"
                          max="100000"
                          aria-label={`Ngưỡng ${row.name}`}
                          value={threshold}
                          onChange={(event) => setThreshold(event.target.value)}
                        />
                      ) : number(row.threshold)}
                    </td>
                    <td>
                      {editing === row.productId ? (
                        <>
                          <button type="button" className="admin-primary" disabled={busy} onClick={() => saveThreshold(row)}>
                            {busy ? 'Đang lưu…' : 'Lưu'}
                          </button>{' '}
                          <button type="button" onClick={() => setEditing(null)}>Hủy</button>
                        </>
                      ) : (
                        <button type="button" className="admin-action" onClick={() => startEdit(row)}>Sửa ngưỡng</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {tab === 'ledger' && (
        <div>
          <div className="refund-filters">
            <label>Loại biến động
              <select value={filters.type} onChange={(event) => change({ type: event.target.value })}>
                <option value="">Tất cả</option>
                {Object.entries(movementLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </label>
            <label>Từ ngày<input type="date" value={filters.from} onChange={(event) => change({ from: event.target.value })} /></label>
            <label>Đến ngày<input type="date" value={filters.to} onChange={(event) => change({ to: event.target.value })} /></label>
          </div>
          {ledger.loading && <p role="status">Đang tải thẻ kho…</p>}
          {ledger.error && <p role="alert">{ledger.error} <button type="button" onClick={() => ledger.load()}>Thử lại</button></p>}
          {!ledger.loading && ledgerRows.length === 0 && <p>Chưa có biến động tồn kho phù hợp.</p>}
          <div className="inventory-table">
            <table>
              <thead>
                <tr><th>Thời gian</th><th>Sản phẩm</th><th>Loại</th><th>Thay đổi</th><th>Trước → sau</th><th>Người thao tác</th><th>Ghi chú</th></tr>
              </thead>
              <tbody>
                {ledgerRows.map((row) => (
                  <tr key={row.id}>
                    <td>{dateTime(row.createdAt)}</td>
                    <td>{row.productName}</td>
                    <td>{movementLabels[row.type] || row.type}</td>
                    <td className={row.quantity < 0 ? 'inventory-down' : 'inventory-up'}>
                      {row.quantity > 0 ? `+${number(row.quantity)}` : number(row.quantity)}
                    </td>
                    <td>{number(row.stockBefore)} → {number(row.stockAfter)}</td>
                    <td>{row.actorName || '—'}</td>
                    <td>{row.note || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <nav className="refund-actions" aria-label="Phân trang thẻ kho">
            <button type="button" disabled={ledger.loading || page <= 0} onClick={() => change({ page: String(page - 1) })}>Trang trước</button>
            <span>Trang {page + 1} / {Math.max(1, ledger.data?.totalPages || 0)}</span>
            <button type="button" disabled={ledger.loading || page + 1 >= (ledger.data?.totalPages || 0)} onClick={() => change({ page: String(page + 1) })}>Trang sau</button>
          </nav>
        </div>
      )}
    </section>
  );
}
