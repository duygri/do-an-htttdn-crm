import ManagerVariants from './ManagerVariants';
import React, { useCallback, useState } from 'react';
import { adminApi, adminDownload } from './manager-api';
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

const stocktakeLabels = { DRAFT: 'Đang mở', COMPLETED: 'Hoàn tất', CANCELLED: 'Đã hủy' };
const tabs = [
  ['variants', 'Biến thể'],
  ['low', 'Sắp hết hàng'],
  ['ledger', 'Thẻ kho'],
  ['stocktake', 'Kiểm kê'],
  ['value', 'Giá trị tồn'],
];
const dateTime = (value) => (value ? new Date(value).toLocaleString('vi-VN') : '—');
const number = (value) => new Intl.NumberFormat('vi-VN').format(Number(value || 0));
const money = (value) => `${number(value)} đ`;

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
  const stocktakeEndpoint = useCallback(() => '/api/manager/stocktakes?page=0&size=20', []);
  const valueEndpoint = useCallback(() => '/api/manager/reports/inventory', []);
  const stocktakeList = useManagerList(stocktakeEndpoint, { enabled: tab === 'stocktake' });
  const report = useManagerList(valueEndpoint, { enabled: tab === 'value' });
  const [editing, setEditing] = useState(null);
  const [threshold, setThreshold] = useState('');
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState('');
  const [open, setOpen] = useState(null);
  const [counts, setCounts] = useState({});
  const [stocktakeBusy, setStocktakeBusy] = useState(false);
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

  const download = (path, filename) => adminDownload(path, filename).catch(error => setFailure(error.message));

  const showStocktake = (detail) => {
    setOpen(detail);
    setCounts(Object.fromEntries(detail.items.map(item =>
      [item.id, item.countedQuantity == null ? '' : String(item.countedQuantity)])));
  };

  const openStocktake = async (id) => {
    setFailure('');
    try {
      showStocktake(await adminApi(`/api/manager/stocktakes/${id}`));
    } catch (error) {
      setFailure(error.message);
    }
  };

  const createStocktake = async () => {
    if (stocktakeBusy) return;
    setStocktakeBusy(true);
    setFailure('');
    try {
      const detail = await adminApi('/api/manager/stocktakes', {
        method: 'POST', body: { note: '', productIds: [] },
      });
      stocktakeList.load();
      showStocktake(detail);
    } catch (error) {
      setFailure(error.message);
    } finally {
      setStocktakeBusy(false);
    }
  };

  const saveCounts = async () => {
    if (!open || stocktakeBusy) return;
    setStocktakeBusy(true);
    setFailure('');
    try {
      const detail = await adminApi(`/api/manager/stocktakes/${open.id}/items`, {
        method: 'PATCH',
        body: open.items.map(item => ({
          itemId: item.id,
          countedQuantity: counts[item.id] === '' || counts[item.id] == null ? null : Number(counts[item.id]),
          note: item.note || null,
        })),
      });
      showStocktake(detail);
      stocktakeList.load();
    } catch (error) {
      setFailure(error.message);
    } finally {
      setStocktakeBusy(false);
    }
  };

  const finishStocktake = async (action) => {
    if (!open || stocktakeBusy) return;
    setStocktakeBusy(true);
    setFailure('');
    try {
      showStocktake(await adminApi(`/api/manager/stocktakes/${open.id}/${action}`, { method: 'POST' }));
      stocktakeList.load();
    } catch (error) {
      setFailure(error.message);
    } finally {
      setStocktakeBusy(false);
    }
  };

  const lowRows = low.data?.content || [];
  const ledgerRows = ledger.data?.content || [];
  const stocktakes = stocktakeList.data?.content || [];
  const values = report.data;

  return (
    <section className="refund-card manager-inventory" aria-label="Tồn kho">
      <div className="refund-actions">
        <h2>Tồn kho</h2>
      </div>
      <p>Theo dõi cảnh báo sắp hết hàng, biến động tồn kho, kiểm kê và giá trị hàng hóa.</p>
      <div className="refund-actions" role="tablist" aria-label="Chế độ xem tồn kho">
        {tabs.map(([key, label]) => (
          <button
            key={key}
            type="button"
            role="tab"
            aria-selected={tab === key}
            className={tab === key ? 'admin-primary' : ''}
            onClick={() => setTab(key)}
          >
            {label}
          </button>
        ))}
      </div>

      {failure && <p role="alert">{failure}</p>}

      {tab === 'variants' && <ManagerVariants/>}
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
                  <tr key={`${row.productId}:${row.variantId||0}`}>
                    <td>{row.name}{row.variantId&&<small> · {row.color} / {row.size}</small>}</td>
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
            <button
              type="button"
              className="admin-action"
              onClick={() => download(`/api/manager/inventory/movements.csv?${managerQuery({ type: filters.type, from: filters.from, to: filters.to })}`, 'the-kho.csv')}
            >
              Xuất CSV
            </button>
          </div>
          {ledger.loading && <p role="status">Đang tải thẻ kho…</p>}
          {ledger.error && <p role="alert">{ledger.error} <button type="button" onClick={() => ledger.load()}>Thử lại</button></p>}
          {!ledger.loading && ledgerRows.length === 0 && <p>Chưa có biến động tồn kho phù hợp.</p>}
          <div className="inventory-table">
            <table>
              <thead>
                <tr><th>Thời gian</th><th>Sản phẩm</th><th>Loại</th><th>Thay đổi</th><th>Tổng tồn sản phẩm trước → sau</th><th>Người thao tác</th><th>Ghi chú</th></tr>
              </thead>
              <tbody>
                {ledgerRows.map((row) => (
                  <tr key={row.id}>
                    <td>{dateTime(row.createdAt)}</td>
                    <td>{row.productName}{row.variantId&&<small> · {row.color} / {row.size}</small>}</td>
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
      {tab === 'stocktake' && (
        <div>
          <div className="refund-actions">
            <button type="button" className="admin-primary" disabled={stocktakeBusy} onClick={createStocktake}>
              {stocktakeBusy ? 'Đang xử lý…' : 'Tạo phiếu kiểm kê'}
            </button>
          </div>
          {stocktakeList.loading && <p role="status">Đang tải phiếu kiểm kê…</p>}
          {stocktakeList.error && <p role="alert">{stocktakeList.error} <button type="button" onClick={() => stocktakeList.load()}>Thử lại</button></p>}
          {!stocktakeList.loading && stocktakes.length === 0 && <p>Chưa có phiếu kiểm kê nào.</p>}
          <div className="inventory-table">
            <table>
              <thead>
                <tr><th>Mã phiếu</th><th>Trạng thái</th><th>Tiến độ</th><th>Ngày tạo</th><th /></tr>
              </thead>
              <tbody>
                {stocktakes.map((row) => (
                  <tr key={row.id}>
                    <td>{row.code || `#${row.id}`}</td>
                    <td>{stocktakeLabels[row.status] || row.status}</td>
                    <td>{row.countedCount}/{row.itemCount}</td>
                    <td>{dateTime(row.createdAt)}</td>
                    <td><button type="button" className="admin-action" onClick={() => openStocktake(row.id)}>Mở</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {open && (
            <div className="inventory-stocktake-detail" role="region" aria-label="Chi tiết phiếu kiểm kê">
              <h3>Phiếu {open.code} — {stocktakeLabels[open.status] || open.status}</h3>
              <div className="inventory-table">
                <table>
                  <thead>
                    <tr><th>Sản phẩm</th><th>Tồn hệ thống</th><th>Số đếm</th><th>Lệch</th></tr>
                  </thead>
                  <tbody>
                    {open.items.map((item) => (
                      <tr key={item.id}>
                        <td>{item.productName}</td>
                        <td>{number(item.systemQuantity)}</td>
                        <td>
                          {open.status === 'DRAFT' ? (
                            <input
                              type="number"
                              min="0"
                              aria-label={`Số đếm ${item.productName}`}
                              value={counts[item.id] ?? ''}
                              onChange={(event) => setCounts(current => ({ ...current, [item.id]: event.target.value }))}
                            />
                          ) : (item.countedQuantity == null ? '—' : number(item.countedQuantity))}
                        </td>
                        <td className={item.difference < 0 ? 'inventory-down' : item.difference > 0 ? 'inventory-up' : ''}>
                          {item.difference == null ? '—' : (item.difference > 0 ? `+${number(item.difference)}` : number(item.difference))}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="refund-actions">
                {open.status === 'DRAFT' && (
                  <>
                    <button type="button" className="admin-primary" disabled={stocktakeBusy} onClick={saveCounts}>Lưu số đếm</button>
                    <button type="button" disabled={stocktakeBusy} onClick={() => finishStocktake('complete')}>Hoàn tất kiểm kê</button>
                    <button type="button" disabled={stocktakeBusy} onClick={() => finishStocktake('cancel')}>Hủy phiếu</button>
                  </>
                )}
                <button type="button" onClick={() => setOpen(null)}>Đóng</button>
              </div>
            </div>
          )}
        </div>
      )}

      {tab === 'value' && (
        <div>
          {report.loading && <p role="status">Đang tính giá trị tồn kho…</p>}
          {report.error && <p role="alert">{report.error} <button type="button" onClick={() => report.load()}>Thử lại</button></p>}
          {values && (
            <>
              <p className="inventory-summary">
                Tổng giá trị tồn kho: <strong>{money(values.totalValue)}</strong> · Tổng số lượng: {number(values.totalUnits)} · Thiếu giá vốn: {number(values.missingCostProducts)} · Dưới ngưỡng: {number(values.lowStockCount)}
              </p>
              <div className="refund-actions">
                <button type="button" className="admin-action" onClick={() => download('/api/manager/reports/inventory.csv', 'ton-kho.csv')}>Xuất CSV</button>
              </div>
              <div className="inventory-table">
                <table>
                  <thead>
                    <tr><th>Sản phẩm</th><th>Danh mục</th><th>Tồn</th><th>Ngưỡng</th><th>Đơn giá gần nhất</th><th>Giá trị</th><th /></tr>
                  </thead>
                  <tbody>
                    {values.products?.map((row) => (
                      <tr key={`${row.productId}:${row.variantId||0}`}>
                        <td>{row.name}{row.variantId&&<small> · {row.color} / {row.size}</small>}</td>
                        <td>{row.category || '—'}</td>
                        <td>{number(row.stock)}</td>
                        <td>{number(row.threshold)}</td>
                        <td>{money(row.unitCost)}</td>
                        <td>{money(row.value)}</td>
                        <td>{row.lowStock && <span className="inventory-down">Sắp hết</span>}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <h3>Tổng theo danh mục</h3>
              <div className="inventory-table">
                <table>
                  <thead>
                    <tr><th>Danh mục</th><th>Số lượng</th><th>Giá trị</th></tr>
                  </thead>
                  <tbody>
                    {values.categories?.map((row) => (
                      <tr key={row.category}>
                        <td>{row.category}</td>
                        <td>{number(row.units)}</td>
                        <td>{money(row.value)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </div>
      )}
    </section>
  );
}
