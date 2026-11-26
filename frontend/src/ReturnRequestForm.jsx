import React, { useMemo, useState } from 'react';
import AttachmentPicker from './AttachmentPicker';
import { money } from './ManagerOrderDetail';

const RESOLUTIONS = [['REFUND', 'Trả hàng hoàn tiền'], ['EXCHANGE', 'Đổi hàng'], ['OTHER', 'Khác']];
const lineKey = (item, index) => item.id ?? `legacy-${index}`;

export default function ReturnRequestForm({ items = [], busy = false, error = '', onSubmit, onCancel }) {
  const [attachments, setAttachments] = useState([]), [uploading, setUploading] = useState(false);
  const [reason, setReason] = useState('');
  const [selected, setSelected] = useState({});
  const [localError, setLocalError] = useState('');
  const legacy = !items.length || items.some(item => item.id == null);
  const chosen = useMemo(() => Object.entries(selected).filter(([, row]) => row.checked)
    .map(([id, row]) => ({ orderItemId: Number(id), quantity: Number(row.quantity), resolution: row.resolution })), [selected]);
  const toggle = (item, key) => setSelected(prev => ({
    ...prev,
    [key]: { checked: !prev[key]?.checked, quantity: prev[key]?.quantity ?? 1, resolution: prev[key]?.resolution ?? 'REFUND' },
  }));
  const update = (key, patch) => setSelected(prev => ({ ...prev, [key]: { ...prev[key], ...patch } }));
  const submit = event => {
    event.preventDefault();
    if (busy || uploading) return;
    if (!reason.trim() || (!legacy && !chosen.length)) { setLocalError('Chọn ít nhất một sản phẩm và nhập lý do.'); return; }
    setLocalError('');
    const payload = legacy ? { reason: reason.trim() } : { reason: reason.trim(), items: chosen };
    if (attachments.length) payload.attachmentIds = attachments.map(item => item.id);
    onSubmit(payload);
  };
  return <form className="shop-reason-form return-request-form" noValidate onSubmit={submit}>
    <h3>Yêu cầu đổi/trả</h3>
    {!legacy && <ul className="return-pick-list">{items.map((item, index) => {
      const key = lineKey(item, index);
      const row = selected[key];
      return <li key={key}>
        <label><input type="checkbox" aria-label={`Chọn ${item.name}`} checked={!!row?.checked} onChange={() => toggle(item, key)} /> {item.name} (đã mua {item.quantity})</label>
        {row?.checked && <>
          <label htmlFor={`return-qty-${key}`}>Số lượng {item.name}</label>
          <input id={`return-qty-${key}`} aria-label={`Số lượng ${item.name}`} type="number" min="1" max={item.quantity}
            value={row.quantity} onChange={event => update(key, { quantity: Math.min(item.quantity, Math.max(1, Number(event.target.value) || 1)) })} />
          <label htmlFor={`return-res-${key}`}>Hình thức xử lý {item.name}</label>
          <select id={`return-res-${key}`} aria-label={`Hình thức xử lý ${item.name}`} value={row.resolution} onChange={event => update(key, { resolution: event.target.value })}>
            {RESOLUTIONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
          <small>{money(Number(item.unitPrice || 0) * row.quantity)}</small>
        </>}
      </li>;
    })}</ul>}
    <label>Lý do đổi/trả<textarea aria-label="Lý do đổi/trả" required maxLength={1000} value={reason} onChange={event => setReason(event.target.value)} /></label>
    <AttachmentPicker value={attachments} onChange={setAttachments} onBusyChange={setUploading} disabled={busy}/>
    {(localError || error) && <p role="alert" className="shop-inline-error">{localError || error}</p>}
    <div className="order-actions">
      <button type="button" className="button button-light" disabled={busy || uploading} onClick={onCancel}>Quay lại</button>
      <button className="button button-dark" disabled={busy || uploading}>{busy ? 'Đang gửi...' : 'Gửi yêu cầu'}</button>
    </div>
  </form>;
}
