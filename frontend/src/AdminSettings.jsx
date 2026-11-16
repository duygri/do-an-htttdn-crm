import React, { useEffect, useState } from 'react';
import { adminApi } from './api';
import './admin-settings.css';

export default function AdminSettings() {
  const [values, setValues] = useState(null);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let alive = true;
    adminApi('/api/admin/settings')
      .then((data) => {
        if (alive) setValues(data);
      })
      .catch((e) => {
        if (alive) setError(e.message);
      });
    return () => {
      alive = false;
    };
  }, []);

  const change = (key, value) => setValues((current) => ({ ...current, [key]: value }));

  const save = async (event) => {
    event.preventDefault();
    setError('');
    setSaved('');
    setBusy(true);
    try {
      setValues(await adminApi('/api/admin/settings', { method: 'PUT', body: values }));
      setSaved('Đã lưu cấu hình.');
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  if (error && !values) return <p role="alert">{error}</p>;
  if (!values) return <p role="status">Đang tải cấu hình…</p>;

  return (
    <section className="admin-settings" aria-label="Cấu hình hệ thống">
      <form onSubmit={save}>
        <fieldset>
          <legend>Hóa đơn</legend>
          <p className="admin-settings-hint">Thông tin người bán được chụp lại khi phát hành hóa đơn.</p>
          <label>Tên người bán
            <input maxLength={150} value={values['invoice.seller.name'] || ''} onChange={(e) => change('invoice.seller.name', e.target.value)} />
          </label>
          <label>Địa chỉ
            <input maxLength={1000} value={values['invoice.seller.address'] || ''} onChange={(e) => change('invoice.seller.address', e.target.value)} />
          </label>
          <label>Điện thoại
            <input maxLength={40} value={values['invoice.seller.phone'] || ''} onChange={(e) => change('invoice.seller.phone', e.target.value)} />
          </label>
          <label>Mã số thuế
            <input maxLength={40} value={values['invoice.seller.taxCode'] || ''} onChange={(e) => change('invoice.seller.taxCode', e.target.value)} />
          </label>
        </fieldset>

        <fieldset>
          <legend>Tồn kho</legend>
          <label>Ngưỡng tồn thấp mặc định
            <input type="number" min="0" max="100000" value={values['inventory.lowStockDefault'] || ''} onChange={(e) => change('inventory.lowStockDefault', e.target.value)} />
          </label>
        </fieldset>

        <fieldset>
          <legend>Điểm thưởng</legend>
          <label>Điểm mỗi 10.000đ
            <input type="number" min="0" max="1000" value={values['loyalty.pointsPer10000'] || ''} onChange={(e) => change('loyalty.pointsPer10000', e.target.value)} />
          </label>
          <label>Điểm cần để đổi
            <input type="number" min="1" max="1000000" value={values['loyalty.redeemPoints'] || ''} onChange={(e) => change('loyalty.redeemPoints', e.target.value)} />
          </label>
          <label>Giá trị voucher đổi (đ)
            <input type="number" min="0" max="100000000" value={values['loyalty.redeemValue'] || ''} onChange={(e) => change('loyalty.redeemValue', e.target.value)} />
          </label>
        </fieldset>

        {error && <p role="alert">{error}</p>}
        {saved && <p role="status">{saved}</p>}
        <button type="submit" className="admin-primary" disabled={busy}>
          {busy ? 'Đang lưu…' : 'Lưu cấu hình'}
        </button>
      </form>
    </section>
  );
}
