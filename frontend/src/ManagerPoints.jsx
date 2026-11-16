import React, { useCallback, useState } from 'react';
import { adminApi } from './manager-api';
import { useManagerResource } from './useManagerResource';

export default function ManagerPoints({ customerId }) {
  const fetcher = useCallback(signal => adminApi(`/api/manager/customers/${customerId}/points`, { signal }), [customerId]);
  const { data, loading, error, load } = useManagerResource(fetcher);
  const [form, setForm] = useState({ points: '', note: '' });
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState('');
  const submit = async event => {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setFailure('');
    try {
      await adminApi(`/api/manager/customers/${customerId}/points`, { method: 'POST', body: { points: Number(form.points), note: form.note } });
      setForm({ points: '', note: '' });
      load();
    } catch (current) {
      setFailure(current.message);
    } finally {
      setBusy(false);
    }
  };
  return <div className="admin-panel" aria-label="Điểm thưởng khách hàng">
    <h2>Điểm thưởng</h2>
    {loading && !data ? <p role="status">Đang tải điểm...</p> : error && !data ? <p role="alert">{error} <button className="admin-action" onClick={() => load()}>Thử lại</button></p> : data && <>
      <p><strong>{data.balance}</strong> điểm hiện có</p>
      <form className="profile-points-form" onSubmit={submit}>
        <label>Điểm điều chỉnh *<input type="number" required value={form.points} onChange={event => setForm({ ...form, points: event.target.value })} placeholder="VD: 50 hoặc -20"/></label>
        <label>Ghi chú *<input required maxLength={500} value={form.note} onChange={event => setForm({ ...form, note: event.target.value })}/></label>
        {failure && <p role="alert">{failure}</p>}
        <button className="admin-primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu điều chỉnh'}</button>
      </form>
      {!!(data.history || []).length && <ul className="profile-points-history">{(data.history || []).slice(0, 10).map(entry => <li key={entry.id}><span>{entry.type}{entry.note ? ` · ${entry.note}` : ''}</span><b>{entry.points > 0 ? `+${entry.points}` : entry.points}</b></li>)}</ul>}
    </>}
  </div>;
}
