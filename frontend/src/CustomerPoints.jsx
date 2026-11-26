import React, { useCallback, useEffect, useState, useRef } from 'react';
import { api } from './api';
import './customer-points.css';

const typeLabels = { EARN: 'Tích điểm', REDEEM: 'Đổi điểm', ADJUST: 'Điều chỉnh' };
const money = value => new Intl.NumberFormat('vi-VN').format(Number(value || 0)) + ' đ';

export default function CustomerPoints() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const requestKey = useRef(null);
  const sending = useRef(false);

  const load = useCallback(() => api('/api/account/points').then(setData).catch(current => setError(current.message)), []);

  useEffect(() => { load(); }, [load]);

  const redeem = async () => {
    if (sending.current) return;
    sending.current = true;
    const storageKey = data?.customerId ? `crm-point-redemption:${data.customerId}` : null;
    try { if(storageKey) requestKey.current ||= sessionStorage.getItem(storageKey); } catch { /* In-memory retry remains available. */ }
    requestKey.current ||= crypto.randomUUID();
    try { if(storageKey) sessionStorage.setItem(storageKey, requestKey.current); } catch { /* Storage may be unavailable. */ }
    setBusy(true);
    setError('');
    setResult(null);
    try {
      const redeemed = await api('/api/account/points/redeem', { method: 'POST', headers: {'Idempotency-Key': requestKey.current} });
      setResult(redeemed);
      setData(current => current ? {...current, balance: redeemed.balance} : current);
      try { if(storageKey && sessionStorage.getItem(storageKey) === requestKey.current) sessionStorage.removeItem(storageKey); } catch { /* The successful response is authoritative. */ }
      requestKey.current = null;
      await load();
    } catch (current) {
      setError(current.message);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  };

  if (error && !data) return <section className="profile-points" aria-label="Điểm thưởng"><h3>Điểm thưởng</h3><p role="alert">{error}</p><button type="button" onClick={()=>{setError('');load();}}>Thử lại</button></section>;
  if (!data) return <section className="profile-points" aria-label="Điểm thưởng"><p role="status">Đang tải điểm thưởng…</p></section>;

  return <section className="profile-points" aria-label="Điểm thưởng">
    <div className="profile-points-head"><h3>Điểm thưởng</h3><strong>{data.balance} điểm</strong></div>
    <p>Mỗi đơn hoàn thành được cộng điểm. Đổi điểm để nhận voucher mua sắm.</p>
    <button type="button" className="button button-dark" disabled={busy} onClick={redeem}>{busy ? 'ĐANG ĐỔI...' : 'ĐỔI ĐIỂM LẤY VOUCHER'}</button>
    {result && <p role="status" className="profile-points-result">Đã đổi {result.spentPoints} điểm — mã {result.code}, trị giá {money(result.discountValue)}.</p>}
    {error && <p role="alert">{error}</p>}
    <ul className="profile-points-history">
      {(data.history || []).map(entry => <li key={entry.id}><span>{typeLabels[entry.type] || entry.type}{entry.note ? ` · ${entry.note}` : ''}</span><b className={entry.points > 0 ? 'positive' : 'negative'}>{entry.points > 0 ? `+${entry.points}` : entry.points}</b></li>)}
      {!(data.history || []).length && <li><span>Chưa có giao dịch điểm.</span></li>}
    </ul>
  </section>;
}
