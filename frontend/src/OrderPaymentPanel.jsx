import React,{useEffect,useRef,useState} from 'react';
import {adminApi} from './manager-api';
import {useManagerReadPause} from './useManagerResource';
import InvoiceDocument from './InvoiceDocument';
import './manager-refunds.css';

const dates=value=>value?new Date(value).toLocaleString('vi-VN'):'—';
const money=value=>new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND',maximumFractionDigits:0}).format(Number(value||0));
export const reviewLabels={MISSING_PAYMENT:'Thiếu bản ghi thanh toán',MISSING_LINK:'Thiếu link thanh toán',DUPLICATE_LINK:'Link giao dịch bị trùng',UNMAPPED_LEGACY_LINK:'Link cũ chưa đối chiếu được',PROVIDER_MISMATCH:'Thông tin PayOS không khớp',PARTIAL_PAYMENT:'Nhận tiền một phần',OVERPAYMENT:'Nhận tiền vượt giá trị đơn',UNKNOWN_PROVIDER_STATUS:'Trạng thái PayOS chưa nhận diện',CANCEL_NOT_CONFIRMED:'Chưa xác nhận hủy link',MULTIPLE_PAYMENTS:'Có nhiều giao dịch đã trả tiền',LOCAL_PROVIDER_CONFLICT:'Trạng thái web và PayOS chưa khớp',PROVIDER_UNAVAILABLE:'Chưa kết nối được PayOS'};

export default function OrderPaymentPanel({order,onUpdated,onEditingChange}){
  const [mode,setMode]=useState(''),[note,setNote]=useState(''),[busy,setBusy]=useState(false),[failure,setFailure]=useState('');
  const [invoice,setInvoice]=useState(null),[invoiceLoading,setInvoiceLoading]=useState(true),[showInvoice,setShowInvoice]=useState(false),[issueNote,setIssueNote]=useState('');
  const sending=useRef(false);
  useManagerReadPause(!!mode||showInvoice);
  useEffect(()=>{onEditingChange?.(!!mode||showInvoice||busy);return()=>onEditingChange?.(false);},[mode,showInvoice,busy,onEditingChange]);
  useEffect(()=>{let alive=true;setInvoiceLoading(true);adminApi(`/api/manager/orders/${order.id}/invoice`).then(data=>{if(alive)setInvoice(data);}).catch(error=>{if(alive&&error.status!==404)setFailure(error.message);}).finally(()=>{if(alive)setInvoiceLoading(false);});return()=>{alive=false;};},[order.id]);
  const refresh=async()=>{try{onUpdated(await adminApi(`/api/manager/orders/${order.id}`));}catch{setFailure('Đã lưu, nhưng chưa tải lại được chi tiết. Vui lòng tải lại; không gửi lại thao tác.');}};
  const submitCod=async event=>{event.preventDefault();if(sending.current)return;sending.current=true;setBusy(true);setFailure('');try{await adminApi(`/api/manager/orders/${order.id}/cod-collection`,{method:'PATCH',body:{note:note.trim()}});setMode('');setNote('');await refresh();}catch(error){setFailure(error.message);}finally{sending.current=false;setBusy(false);}};
  const reconcile=async()=>{if(sending.current)return;sending.current=true;setBusy(true);setFailure('');try{onUpdated(await adminApi(`/api/manager/orders/${order.id}/payment/reconcile`,{method:'POST'}));}catch(error){setFailure(error.message);}finally{sending.current=false;setBusy(false);}};
  const issue=async()=>{if(sending.current)return;sending.current=true;setBusy(true);setFailure('');try{const created=await adminApi(`/api/manager/orders/${order.id}/invoice`,{method:'POST',body:{note:issueNote.trim()}});setInvoice(created);setShowInvoice(true);setMode('');setIssueNote('');}catch(error){setFailure(error.message);}finally{sending.current=false;setBusy(false);}};
  const finance=order.finance;
  return <section className="refund-card" aria-label="Thanh toán và hóa đơn"><h3>Thanh toán và hóa đơn</h3>
    {finance?.reviewRequired&&<div role="status" className="refund-warning"><strong>Thanh toán cần kiểm tra</strong><p>{reviewLabels[finance.reviewReason]||'Cần đối chiếu với PayOS'}</p><small>{dates(finance.reviewAt)}</small></div>}
    {order.paymentMethod==='PAYOS'&&<button type="button" className="admin-action" disabled={busy||!!mode} onClick={reconcile}>Đối chiếu lại PayOS</button>}
    {finance?.codCollectedAt&&<p>Đã xác nhận thu toàn bộ COD: {dates(finance.codCollectedAt)} · {money(finance.codCollectedAmount)}</p>}
    {order.paymentMethod==='COD'&&['DELIVERED','COMPLETED'].includes(order.status)&&!finance?.codCollectedAt&&<button type="button" className="admin-action" disabled={busy||!!mode} onClick={()=>{setFailure('');setMode('COD');}}>Xác nhận đã thu COD</button>}
    {mode==='COD'&&<form className="refund-form" aria-label="Xác nhận thu COD" onSubmit={submitCod}><h4>Xác nhận đã thu toàn bộ {money(order.totalAmount)}</h4><p>Chỉ xác nhận khi đã kiểm tra thực tế tiền thu. Không tự chuyển tiền.</p><label>Ghi chú<textarea autoFocus required maxLength={1000} value={note} onChange={e=>setNote(e.target.value)} disabled={busy}/></label><div className="refund-actions"><button type="button" disabled={busy} onClick={()=>{setMode('');setFailure('');}}>Bỏ qua</button><button disabled={busy}>{busy?'Đang lưu…':'Xác nhận'}</button></div></form>}
    <div className="refund-actions">
      {invoiceLoading?<span role="status">Đang tải hóa đơn…</span>:invoice?<><button type="button" className="admin-action" onClick={()=>setShowInvoice(value=>!value)}>{showInvoice?'Ẩn hóa đơn':'Xem / in hóa đơn'}</button><small>Số {invoice.invoiceNumber}</small></>:order.status!=='CANCELLED'?<button type="button" className="admin-action" disabled={busy} onClick={()=>{setFailure('');setMode('INVOICE');}}>Lập hóa đơn bán hàng</button>:null}
    </div>
    {mode==='INVOICE'&&<form className="refund-form" aria-label="Lập hóa đơn" onSubmit={e=>{e.preventDefault();void issue();}}><h4>Lập hóa đơn cho đơn #{order.orderCode}</h4><p>Hóa đơn chụp lại thông tin khách hàng, sản phẩm và thanh toán tại thời điểm phát hành.</p><label>Ghi chú trên hóa đơn (không bắt buộc)<textarea maxLength={1000} value={issueNote} onChange={e=>setIssueNote(e.target.value)} disabled={busy}/></label><div className="refund-actions"><button type="button" disabled={busy} onClick={()=>{setMode('');setFailure('');}}>Bỏ qua</button><button className="admin-primary" disabled={busy}>{busy?'Đang lập…':'Phát hành hóa đơn'}</button></div></form>}
    {showInvoice&&invoice&&<InvoiceDocument invoice={invoice}/>}
    {failure&&<p role="alert">{failure}</p>}
  </section>;
}
