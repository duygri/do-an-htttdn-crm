import React, {useEffect, useRef, useState} from 'react';
import {adminApi} from './api';
import './admin-emails.css';

const statuses = ['PENDING', 'PROCESSING', 'ACCEPTED', 'FAILED', 'UNKNOWN'];
export default function AdminEmails() {
  const [status,setStatus]=useState('');
  const [page,setPage]=useState(0);
  const [revision,setRevision]=useState(0);
  const [data,setData]=useState(null);
  const [error,setError]=useState('');
  const [loading,setLoading]=useState(true);
  const [busy,setBusy]=useState(false);
  const [confirm,setConfirm]=useState(null);
  const dialogRef=useRef(null);
  const returnFocusRef=useRef(null);
  useEffect(()=>{
    if(!confirm)return;
    const dialog=dialogRef.current;
    const previous=returnFocusRef.current;
    dialog.showModal();
    return ()=>{dialog.close();if(previous?.isConnected)previous.focus();};
  },[confirm]);
  useEffect(()=>{
    const controller=new AbortController();
    setLoading(true); setError(''); setData(null); setConfirm(null);
    adminApi(`/api/admin/emails?page=${page}${status?`&status=${status}`:''}`,{signal:controller.signal})
      .then(value=>{if(!controller.signal.aborted)setData(value);})
      .catch(e=>{if(!controller.signal.aborted)setError(e.message);})
      .finally(()=>{if(!controller.signal.aborted)setLoading(false);});
    return ()=>controller.abort();
  },[status,page,revision]);
  const retry=async row=>{
    setBusy(true);setError('');
    try {
      await adminApi(`/api/admin/emails/${row.id}/retry`,{method:'POST',body:{acknowledgeUnknown:row.status==='UNKNOWN'}});
      setConfirm(null);setRevision(v=>v+1);
    } catch(e){setError(e.message);} finally {setBusy(false);}
  };
  return <section className="admin-panel admin-emails" aria-label="Email giao dịch">
    <h2>Email giao dịch</h2>
    <p>ACCEPTED: Brevo đã chấp nhận yêu cầu, chưa xác nhận thư đã đến hộp thư.</p>
    <p>UNKNOWN: chưa rõ nhà cung cấp đã nhận hay chưa. Kiểm tra Brevo trước khi thử lại để tránh gửi trùng. Tối đa 5 lần gửi cho mỗi email.</p>
    <div className="email-controls">
      <label>Trạng thái <select value={status} disabled={busy} onChange={e=>{setStatus(e.target.value);setPage(0);}}><option value="">Tất cả</option>{statuses.map(s=><option key={s}>{s}</option>)}</select></label>
      <button disabled={busy||loading} onClick={()=>setRevision(v=>v+1)}>Tải lại</button>
    </div>
    {error&&<p role="alert">{error}<button disabled={busy} onClick={()=>setRevision(v=>v+1)}>Thử tải lại</button></p>}
    {loading&&<p role="status">Đang tải email…</p>}
    {data&&!loading&&<>
      {!data.content.length&&<p>Chưa có email phù hợp.</p>}
      <div className="email-table"><table><thead><tr><th>ID</th><th>Trạng thái</th><th>Lần gửi</th><th>Người nhận</th><th>Kết quả</th><th>Cập nhật</th><th>Thao tác</th></tr></thead>
        <tbody>{data.content.map(row=><tr key={row.id}><td>#{row.id}</td><td>{row.status}</td><td>{row.attempts}/5</td><td>{row.recipientHint}</td><td>{row.reason||'—'}</td><td>{new Date(row.updatedAt).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'})}</td><td>{['FAILED','UNKNOWN'].includes(row.status)&&row.attempts<5&&<button disabled={busy} onClick={event=>{returnFocusRef.current=event.currentTarget;setConfirm(row);}}>Thử lại #{row.id}</button>}</td></tr>)}</tbody>
      </table></div>
      <nav aria-label="Phân trang email"><button disabled={page===0||busy} onClick={()=>setPage(v=>v-1)}>Trang trước</button><span>Trang {page+1} / {Math.max(1,data.totalPages)}</span><button disabled={page+1>=data.totalPages||busy} onClick={()=>setPage(v=>v+1)}>Trang sau</button></nav>
    </>}
    {confirm&&<dialog ref={dialogRef} aria-label="Xác nhận thử lại email" className="email-confirm"
      onCancel={event=>{event.preventDefault();if(!busy)setConfirm(null);}}>
      <h3>Thử lại email #{confirm.id}</h3>
      <p>{confirm.status==='UNKNOWN'?'Email có thể đã được gửi. Tôi đã kiểm tra Brevo và chấp nhận nguy cơ gửi trùng.':'Hãy kiểm tra và khắc phục lỗi cấu hình hoặc quyền gửi trước khi thử lại.'}</p>
      <button disabled={busy} onClick={()=>retry(confirm)}>Xác nhận thử lại</button><button autoFocus disabled={busy} onClick={()=>setConfirm(null)}>Hủy</button>
    </dialog>}
  </section>;
}
