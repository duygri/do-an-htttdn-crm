import React,{useCallback,useState} from 'react';
import {adminApi} from './manager-api';
import {useManagerList,useManagerReadPause} from './useManagerResource';
import {useManagerFilters,managerQuery} from './useManagerFilters';
import InvoiceDocument from './InvoiceDocument';
import './manager-refunds.css';

const money=value=>new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND',maximumFractionDigits:0}).format(Number(value||0));
const date=value=>value?new Date(value).toLocaleString('vi-VN'):'—';

export default function ManagerInvoices(){
  const [filters,change]=useManagerFilters({q:'',page:'0'});
  const page=Number(filters.page)||0;
  const endpoint=useCallback(()=>`/api/manager/invoices?${managerQuery({q:filters.q,page,size:20})}`,[filters.q,page]);
  const {data,loading,error,load}=useManagerList(endpoint);
  const [selected,setSelected]=useState(null),[detail,setDetail]=useState(null),[busy,setBusy]=useState(false),[failure,setFailure]=useState('');
  useManagerReadPause(!!selected);
  const open=async invoice=>{setSelected(invoice);setDetail(null);setFailure('');setBusy(true);try{setDetail(await adminApi(`/api/manager/invoices/${invoice.id}`));}catch(error){setFailure(error.message);}finally{setBusy(false);}};
  return <section className="refund-card" aria-label="Hóa đơn bán hàng"><h2>Hóa đơn bán hàng</h2><p>Mỗi đơn hàng có tối đa một hóa đơn, được lập từ chi tiết đơn hàng và in trực tiếp.</p>
    <div className="refund-filters"><label>Tìm số hóa đơn hoặc khách hàng<input value={filters.q} onChange={e=>change({q:e.target.value})} placeholder="HD-... hoặc tên khách"/></label></div>
    {loading&&<p role="status">Đang tải hóa đơn…</p>}{error&&<p role="alert">{error} <button onClick={()=>load()}>Thử lại</button></p>}
    {!loading&&data?.content?.length===0&&<p>Chưa có hóa đơn nào. Vào chi tiết đơn hàng để lập hóa đơn.</p>}
    <div className="refund-ledger">{(data?.content||[]).map(invoice=><article key={invoice.id}><h3>{invoice.invoiceNumber} · Đơn #{invoice.orderCode}</h3><p>{invoice.buyerName} · {invoice.buyerEmail||'—'}</p><p>{date(invoice.issuedAt)} · {money(invoice.totalAmount)}</p><button className="admin-action" onClick={()=>open(invoice)}>Xem hóa đơn</button></article>)}</div>
    <nav className="refund-actions" aria-label="Phân trang hóa đơn"><button disabled={loading||page<=0} onClick={()=>change({page:String(page-1)})}>Trang trước</button><span>Trang {page+1} / {Math.max(1,data?.totalPages||0)}</span><button disabled={loading||page+1>=(data?.totalPages||0)} onClick={()=>change({page:String(page+1)})}>Trang sau</button></nav>
    {selected&&<section className="refund-card" aria-label="Chi tiết hóa đơn"><div className="refund-actions"><h3>{selected.invoiceNumber}</h3><button className="admin-action" onClick={()=>{setSelected(null);setDetail(null);}}>Đóng</button></div>{busy&&<p role="status">Đang tải chi tiết…</p>}{failure&&<p role="alert">{failure}</p>}{detail&&<InvoiceDocument invoice={detail}/>}</section>}
  </section>;
}
