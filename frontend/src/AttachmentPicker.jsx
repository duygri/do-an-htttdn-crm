import React, { useEffect, useRef, useState } from 'react';
import { api } from './api';
import { adminApi } from './manager-api';
import './AttachmentPicker.css';

const MAX_FILES = 5, MAX_BYTES = 5 * 1024 * 1024;
export default function AttachmentPicker({ value = [], onChange, onBusyChange, disabled = false, manager = false, label = 'Ảnh đính kèm' }) {
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const [enabled,setEnabled]=useState(null),[configError,setConfigError]=useState(''),[retry,setRetry]=useState(0);
  useEffect(()=>{
    let active=true;setEnabled(null);setConfigError('');
    (manager?adminApi:api)(manager?'/api/manager/attachments/config':'/api/attachments/config')
      .then(data=>{if(active)setEnabled(data?.enabled===true);})
      .catch(cause=>{if(active){setEnabled(false);setConfigError(cause.message||'Không kiểm tra được dịch vụ ảnh.');}});
    return ()=>{active=false;};
  },[manager,retry]);
  const current = useRef(value), alive = useRef(true), inFlight = useRef(false), onBusy = useRef(onBusyChange);
  current.current = value; onBusy.current = onBusyChange;
  useEffect(() => { alive.current = true; return () => { alive.current = false; onBusy.current?.(false); }; }, []);
  async function select(event) {
    const files = Array.from(event.target.files || []); event.target.value = '';
    if (!files.length || inFlight.current || disabled || enabled!==true) return;
    setError('');
    if (files.length + current.current.length > MAX_FILES) { setError('Mỗi lần gửi tối đa 5 ảnh.'); return; }
    if (files.some(file => !['image/jpeg','image/png','image/webp'].includes(file.type) || !file.size || file.size > MAX_BYTES)) { setError('Chỉ nhận JPEG, PNG, WebP; mỗi ảnh tối đa 5 MB.'); return; }
    inFlight.current = true; setBusy(true); onBusy.current?.(true);
    try {
      for (const file of files) {
        const body = new FormData(); body.append('file', file);
        const uploaded = await (manager ? adminApi : api)(manager ? '/api/manager/attachments' : '/api/attachments', { method: 'POST', body });
        if (!alive.current) break;
        const next = [...current.current, uploaded]; current.current = next; onChange(next);
      }
    } catch (cause) { if (alive.current) setError(cause.message || 'Không tải được ảnh. Hãy thử lại.'); }
    finally { inFlight.current = false; if (alive.current) { setBusy(false); onBusy.current?.(false); } }
  }
  return <fieldset className="attachment-picker" disabled={disabled || busy}>
    <legend>{label}</legend>
    <p>JPEG, PNG hoặc WebP · tối đa 5 ảnh · 5 MB mỗi ảnh</p>
    <input aria-label={label} type="file" accept="image/jpeg,image/png,image/webp" multiple onChange={select} disabled={enabled!==true || value.length >= MAX_FILES || disabled || busy} />
    {enabled===null&&<p>Đang kiểm tra dịch vụ ảnh…</p>}
    {enabled===false&&<p>{configError||'Tải ảnh chưa được cấu hình. Anh/chị vẫn có thể gửi nội dung bằng văn bản.'}{configError&&<button type="button" onClick={()=>setRetry(v=>v+1)}>Kiểm tra lại dịch vụ ảnh</button>}</p>}
    {busy && <p role="status">Đang tải ảnh…</p>}
    {error && <p role="alert">{error}</p>}
    <ul>{value.map(item => <li key={item.id}><span>{item.name}</span><button type="button" aria-label={`Bỏ ảnh ${item.name}`} onClick={() => onChange(value.filter(v => v.id !== item.id))}>Bỏ ảnh</button></li>)}</ul>
  </fieldset>;
}

export function AttachmentLinks({ attachments = [], manager = false }) {
  const [error, setError] = useState(''), [busy, setBusy] = useState(null);
  const urls = useRef(new Set());
  useEffect(() => () => { urls.current.forEach(url => URL.revokeObjectURL(url)); urls.current.clear(); }, []);
  async function download(item) {
    setBusy(item.id); setError('');
    try {
      // Fetch a blob with the active bearer token; never put bearer tokens or provider URLs in the DOM.
      const blob = await (manager ? adminApi : api)(`${manager ? '/api/manager' : '/api'}/attachments/${encodeURIComponent(item.id)}/content`, { responseType: 'blob' });
      const url = URL.createObjectURL(blob); urls.current.add(url);
      const a = document.createElement('a'); a.href = url; a.download = item.name || 'attachment'; a.click();
      setTimeout(() => { URL.revokeObjectURL(url); urls.current.delete(url); }, 1000);
    } catch (cause) { setError(cause.message || 'Không thể tải ảnh.'); }
    finally { setBusy(null); }
  }
  if (!attachments.length) return null;
  return <div className="attachment-links"><ul>{attachments.map(item => <li key={item.id}><button type="button" disabled={busy !== null} onClick={() => download(item)}>{busy === item.id ? 'Đang tải…' : item.name}</button></li>)}</ul>{error && <p role="alert">{error}</p>}</div>;
}
