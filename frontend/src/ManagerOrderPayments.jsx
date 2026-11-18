import React,{useEffect,useRef,useState} from 'react';
import {adminApi} from './manager-api';
import {useManagerReadPause} from './useManagerResource';
import './manager-payments.css';

const reviewLabels={MISSING_PAYMENT:'Thiếu bản ghi thanh toán',MISSING_LINK:'Thiếu link thanh toán',DUPLICATE_LINK:'Link giao dịch bị trùng',UNMAPPED_LEGACY_LINK:'Link cũ chưa đối chiếu được',PROVIDER_MISMATCH:'Thông tin PayOS không khớp',PARTIAL_PAYMENT:'Nhận tiền một phần',OVERPAYMENT:'Nhận tiền vượt giá trị đơn',UNKNOWN_PROVIDER_STATUS:'Trạng thái PayOS chưa nhận diện',CANCEL_NOT_CONFIRMED:'Chưa xác nhận hủy link',MULTIPLE_PAYMENTS:'Có nhiều giao dịch đã trả tiền',LOCAL_PROVIDER_CONFLICT:'Trạng thái web và PayOS chưa khớp',PROVIDER_UNAVAILABLE:'Chưa kết nối được PayOS'};
const date=value=>value?new Date(value).toLocaleString('vi-VN'):'—';
const money=value=>new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND',maximumFractionDigits:0}).format(Number(value||0));

export default function ManagerOrderPayments({order,onUpdated,onEditingChange}){
  const [mode,setMode]=useState(false),[note,setNote]=useState(''),[busy,setBusy]=useState(false),[failure,setFailure]=useState('');
  const sending=useRef(false);
  useManagerReadPause(mode||busy);
  useEffect(()=>{onEditingChange?.(mode||busy);return()=>onEditingChange?.(false);},[mode,busy,onEditingChange]);
  const reconcile=async()=>{
    if(sending.current)return;
    sending.current=true;setBusy(true);setFailure('');
    try{onUpdated?.(await adminApi(`/api/manager/orders/${order.id}/payment/reconcile`,{method:'POST'}));}
    catch(error){setFailure(error.message);}
    finally{sending.current=false;setBusy(false);}
  };
  const submit=async event=>{
    event.preventDefault();if(sending.current)return;
    sending.current=true;setBusy(true);setFailure('');
    try{const updated=await adminApi(`/api/manager/orders/${order.id}/cod-collection`,{method:'PATCH',body:{note:note.trim()}});setMode(false);setNote('');onUpdated?.(updated);}
    catch(error){setFailure(error.message);}
    finally{sending.current=false;setBusy(false);}
  };
  const finance=order.finance||{};
  return <section className="manager-payments" aria-label="Thanh toán và đối soát"><h3>Thanh toán</h3>
    {finance.reviewRequired&&<div role="status" className="manager-payment-warning"><strong>Thanh toán cần kiểm tra</strong><p>{reviewLabels[finance.reviewReason]||'Cần đối chiếu với PayOS'}</p><small>{date(finance.reviewAt)}</small></div>}
    {order.paymentMethod==='PAYOS'&&<button type="button" className="admin-action" disabled={busy||mode} onClick={reconcile}>Đối chiếu lại PayOS</button>}
    {finance.codCollectedAt&&<p>Đã xác nhận thu toàn bộ COD: {date(finance.codCollectedAt)}</p>}
    {order.paymentMethod==='COD'&&['DELIVERED','COMPLETED'].includes(order.status)&&!finance.codCollectedAt&&!mode&&<button type="button" className="admin-action" disabled={busy} onClick={()=>{setFailure('');setMode(true);}}>Xác nhận đã thu COD</button>}
    {mode&&<form className="manager-payment-form" aria-label="Xác nhận thu COD" onSubmit={submit}><h4>Xác nhận đã thu toàn bộ {money(order.totalAmount)}</h4><p>Chỉ xác nhận sau khi đã kiểm tra thực tế khoản thu.</p><label>Ghi chú<textarea autoFocus required maxLength={1000} value={note} onChange={event=>setNote(event.target.value)} disabled={busy}/></label><div className="manager-payment-actions"><button type="button" disabled={busy} onClick={()=>{setMode(false);setFailure('');}}>Bỏ qua</button><button disabled={busy}>{busy?'Đang lưu…':'Xác nhận'}</button></div></form>}
    {failure&&<p role="alert">{failure}</p>}
  </section>;
}
