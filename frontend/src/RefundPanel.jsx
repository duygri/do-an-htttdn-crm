import React, { useRef, useState } from 'react';
import { adminApi } from './manager-api';

const money = value => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(Number(value || 0));
const date = value => value ? new Date(value).toLocaleString('vi-VN') : '—';
export const REFUND_LABELS = { PENDING: 'Chờ hoàn', COMPLETED: 'Đã hoàn', REJECTED: 'Từ chối' };
const METHOD_LABELS = { CASH: 'Tiền mặt', BANK_TRANSFER: 'Chuyển khoản', OTHER: 'Khác' };

export default function RefundPanel({ detail, onUpdated }) {
  const finance = detail.finance || {};
  const eligible = detail.status === 'CANCELLED' || ['APPROVED', 'COMPLETED'].includes(detail.returnStatus);
  const [form, setForm] = useState({ amount: '', method: 'BANK_TRANSFER', bankReference: '', note: '', evidenceUrl: '', requestKey: '' });
  const [processing, setProcessing] = useState(null); // {refundId,status}
  const [note, setNote] = useState('');
  const [bank, setBank] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  if (!eligible) return null;

  const openProcessing = (refundId, status, prefillBank = '') => {
    if (lock.current) return;
    setProcessing({ refundId, status }); setNote(''); setBank(prefillBank); setError('');
  };
  const call = async fn => {
    if (lock.current) return;
    lock.current = true; setBusy(true); setError('');
    try {
      const updated = await fn();
      onUpdated(updated); setProcessing(null); setNote(''); setBank('');
    } catch (e) { setError(e.message); }
    finally { lock.current = false; setBusy(false); }
  };
  const create = event => {
    event.preventDefault();
    const amount = Number(form.amount);
    if (!amount || amount <= 0) { setError('Số tiền hoàn phải lớn hơn 0.'); return; }
    call(() => adminApi(`/api/manager/orders/${detail.id}/refunds`, {
      method: 'POST',
      body: { ...form, amount, bankReference: form.bankReference.trim(), note: form.note.trim() },
    }));
  };
  const process = event => {
    event.preventDefault();
    if (!note.trim()) { setError('Ghi chú xử lý là bắt buộc.'); return; }
    if (processing.status === 'COMPLETED' && !bank.trim()) { setError('Nhập mã giao dịch ngân hàng để hoàn tất.'); return; }
    call(() => adminApi(`/api/manager/orders/${detail.id}/refunds/${processing.refundId}`, {
      method: 'PATCH',
      body: { status: processing.status, processingNote: note.trim(), bankReference: bank.trim(), evidenceUrl: '' },
    }));
  };

  return (
    <section className="admin-return-management" aria-label="Hoàn tiền">
      <h3>Sổ hoàn tiền</h3>
      <p>Còn có thể hoàn: <b>{money(finance.refundableAmount)}</b> · Đã hoàn {money(finance.refundedAmount)} · Đang chờ {money(finance.refundPendingAmount)}</p>
      {error && <p role="alert" className="danger-text">{error}</p>}
      {(detail.refunds || []).length > 0 && <ul className="return-history">
        {detail.refunds.map(r => <li key={r.id}>
          <b>{money(r.amount)} · {REFUND_LABELS[r.status] || r.status}</b>
          <small>{METHOD_LABELS[r.method] || 'Khác'} · {date(r.requestedAt)}</small>
          {r.bankReference && <small>Mã giao dịch: {r.bankReference}</small>}
          {r.note && <p>{r.note}</p>}
          {r.processingNote && <p>Xử lý: {r.processingNote}</p>}
          {r.status === 'PENDING' && !processing && <div className="admin-dialog-actions">
            <button type="button" className="admin-primary" disabled={busy} onClick={() => openProcessing(r.id, 'COMPLETED', r.bankReference || '')}>Hoàn tất</button>
            <button type="button" className="admin-action danger-text" disabled={busy} onClick={() => openProcessing(r.id, 'REJECTED')}>Từ chối</button>
          </div>}
        </li>)}
      </ul>}
      {!detail.refunds?.length && !processing && <p>Chưa có phiếu hoàn tiền nào.</p>}
      {processing && <form onSubmit={process} className="admin-return-form" noValidate>
        <h4>{processing.status === 'COMPLETED' ? 'Xác nhận đã hoàn tiền' : 'Từ chối hoàn tiền'}</h4>
        <label htmlFor="refund-processing-note">Ghi chú xử lý</label>
        <textarea id="refund-processing-note" required maxLength={1000} disabled={busy} value={note} onChange={e => setNote(e.target.value)} />
        {processing.status === 'COMPLETED' && <>
          <label htmlFor="refund-bank">Mã giao dịch</label>
          <input id="refund-bank" required maxLength={120} disabled={busy} value={bank} onChange={e => setBank(e.target.value)} />
        </>}
        <div className="admin-dialog-actions">
          <button type="button" onClick={() => { setProcessing(null); setError(''); }} disabled={busy}>Quay lại</button>
          <button className="admin-primary" disabled={busy}>{busy ? 'Đang lưu...' : processing.status === 'COMPLETED' ? 'Xác nhận hoàn tất' : 'Xác nhận từ chối'}</button>
        </div>
      </form>}
      {!processing && <form onSubmit={create} className="admin-return-form">
        <h4>Tạo phiếu hoàn</h4>
        <label htmlFor="refund-amount">Số tiền hoàn</label>
        <input id="refund-amount" type="number" min="1" max={finance.refundableAmount || 0} required disabled={busy} value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })} />
        <label htmlFor="refund-method">Phương thức</label>
        <select id="refund-method" disabled={busy} value={form.method} onChange={e => setForm({ ...form, method: e.target.value })}>
          <option value="BANK_TRANSFER">Chuyển khoản</option><option value="CASH">Tiền mặt</option><option value="OTHER">Khác</option>
        </select>
        <label htmlFor="refund-bank-ref">Mã giao dịch</label>
        <input id="refund-bank-ref" maxLength={120} disabled={busy} value={form.bankReference} onChange={e => setForm({ ...form, bankReference: e.target.value })} />
        <label htmlFor="refund-note">Ghi chú hoàn tiền</label>
        <textarea id="refund-note" maxLength={1000} disabled={busy} value={form.note} onChange={e => setForm({ ...form, note: e.target.value })} />
        <label htmlFor="refund-evidence">Link chứng từ</label>
        <input id="refund-evidence" maxLength={500} disabled={busy} value={form.evidenceUrl} onChange={e => setForm({ ...form, evidenceUrl: e.target.value })} />
        <div className="admin-dialog-actions"><button className="admin-primary" disabled={busy || !finance.refundableAmount}>{busy ? 'Đang tạo...' : 'Tạo phiếu hoàn'}</button></div>
      </form>}
    </section>
  );
}
