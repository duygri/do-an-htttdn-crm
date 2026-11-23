import React, { useEffect, useRef, useState } from 'react';
import { adminApi } from './manager-api';
import { SHIPMENT_LABELS, nextEventOptions, shipmentActions, shipmentDate, shipmentTone } from './shipment-display';

const EMPTY_EVENT = { status: '', note: '' };

export default function ShipmentPanel({ detail, onUpdated }) {
  const [carriers, setCarriers] = useState([]);
  const [form, setForm] = useState({ carrierCode: '', trackingCode: '', note: '' });
  const [eventForm, setEventForm] = useState(EMPTY_EVENT);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const lock = useRef(false);
  const { canCreate, canRecordEvent } = shipmentActions(detail);
  const status = detail?.shipment?.status;
  const options = canRecordEvent ? nextEventOptions(status) : [];
  const eventStatus = options.includes(eventForm.status) ? eventForm.status : (options[0] || '');
  useEffect(() => {
    if (!canCreate) return undefined;
    let alive = true;
    adminApi('/api/manager/shipping/carriers').then(list => alive && setCarriers(list)).catch(e => alive && setError(e.message));
    return () => { alive = false; };
  }, [canCreate]);
  const submit = async (event) => {
    event.preventDefault();
    if (lock.current || !form.carrierCode || !form.trackingCode.trim()) return;
    lock.current = true; setBusy(true); setError('');
    try { const updated = await adminApi(`/api/manager/orders/${detail.id}/shipments`, { method: 'POST', body: form }); onUpdated(updated); }
    catch (e) { setError(e.message); }
    finally { lock.current = false; setBusy(false); }
  };
  const addEvent = async (event) => {
    event.preventDefault();
    if (lock.current || !canRecordEvent || !eventStatus) return;
    if (['FAILED', 'RETURNED'].includes(eventStatus) && !eventForm.note.trim()) { setError('Ghi chú là bắt buộc khi giao thất bại hoặc hoàn hàng.'); return; }
    lock.current = true; setBusy(true); setError('');
    try {
      const updated = await adminApi(`/api/manager/orders/${detail.id}/shipments/${detail.shipment.id}/events`, { method: 'POST', body: { status: eventStatus, note: eventForm.note } });
      onUpdated(updated);
      setEventForm(EMPTY_EVENT);
    }
    catch (e) { setError(e.message); }
    finally { lock.current = false; setBusy(false); }
  };
  if (!canCreate && !detail?.shipment) return null;
  return (
    <section className="admin-return-management" aria-label="Vận chuyển">
      <h3>Vận chuyển</h3>
      {error && <p role="alert" className="danger-text">{error}</p>}
      {detail.shipment ? (
        <>
          <p>{detail.shipment.carrierName} · <span className="shipment-status" data-tone={shipmentTone(status)}>{SHIPMENT_LABELS[status] || status}</span></p>
          <p>Mã vận đơn: <b>{detail.shipment.trackingCode}</b></p>
          <ol className="return-history">{(detail.shipment.events || []).map((e, i) => (
            <li key={i} data-tone={shipmentTone(e.status)}><b>{SHIPMENT_LABELS[e.status] || e.status}</b><small>{shipmentDate(e.occurredAt)}</small>{e.note && <p>{e.note}</p>}</li>
          ))}</ol>
          {canRecordEvent && (
            <form onSubmit={addEvent} className="admin-return-form">
              <label htmlFor="shipment-event-status">Cập nhật hành trình</label>
              <select id="shipment-event-status" value={eventStatus} onChange={e => setEventForm({ ...eventForm, status: e.target.value })}>
                {options.map(value => <option key={value} value={value}>{SHIPMENT_LABELS[value]}</option>)}
              </select>
              <label htmlFor="shipment-event-note">Ghi chú</label>
              <textarea id="shipment-event-note" maxLength={1000} value={eventForm.note} onChange={e => setEventForm({ ...eventForm, note: e.target.value })} />
              <div className="admin-dialog-actions"><button className="admin-primary" disabled={busy}>{busy ? 'Đang lưu...' : 'Thêm cập nhật'}</button></div>
            </form>
          )}
        </>
      ) : (
        <form onSubmit={submit} className="admin-return-form">
          <label htmlFor="shipment-carrier">Hãng vận chuyển</label>
          <select id="shipment-carrier" required value={form.carrierCode} onChange={e => setForm({ ...form, carrierCode: e.target.value })}>
            <option value="">Chọn hãng</option>
            {carriers.map(c => <option key={c.code} value={c.code}>{c.name}</option>)}
          </select>
          <label htmlFor="shipment-code">Mã vận đơn</label>
          <input id="shipment-code" required maxLength={120} value={form.trackingCode} onChange={e => setForm({ ...form, trackingCode: e.target.value })} />
          <label htmlFor="shipment-note">Ghi chú (không bắt buộc)</label>
          <textarea id="shipment-note" maxLength={1000} value={form.note} onChange={e => setForm({ ...form, note: e.target.value })} />
          <div className="admin-dialog-actions"><button className="admin-primary" disabled={busy || !form.carrierCode || !form.trackingCode.trim()}>{busy ? 'Đang tạo...' : 'Tạo vận đơn'}</button></div>
        </form>
      )}
    </section>
  );
}
