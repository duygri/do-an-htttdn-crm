import React,{useState} from 'react';
import {api} from './api';
import InvoiceDocument from './InvoiceDocument';

export default function CustomerInvoice({orderId,onNotice}){
  const [invoice,setInvoice]=useState(null),[open,setOpen]=useState(false),[busy,setBusy]=useState(false);
  const view=async()=>{if(busy)return;setBusy(true);try{setInvoice(await api(`/api/orders/${orderId}/invoice`));setOpen(true);}catch(error){onNotice?.(error.status===404?'Hóa đơn chưa được cửa hàng phát hành.':error.message);}finally{setBusy(false);}};
  return <>
    <button type="button" className="button button-light" disabled={busy} onClick={view}>{busy?'ĐANG TẢI...':'XEM HÓA ĐƠN'}</button>
    {open&&invoice&&<div className="modal-backdrop" onMouseDown={event=>{if(event.target===event.currentTarget)setOpen(false);}}><div className="wide-modal" role="dialog" aria-modal="true" aria-label="Hóa đơn bán hàng"><div className="admin-actions" style={{textAlign:'right'}}><button type="button" className="button button-light" onClick={()=>setOpen(false)}>ĐÓNG</button></div><InvoiceDocument invoice={invoice}/></div></div>}
  </>;
}
