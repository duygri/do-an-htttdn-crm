import React,{useCallback,useMemo,useState} from 'react';
import {adminApi} from './manager-api';
import {useManagerList,useManagerReadPause} from './useManagerResource';
import {useManagerFilters,managerQuery} from './useManagerFilters';
import './manager-refunds.css';

const money=value=>new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND',maximumFractionDigits:0}).format(Number(value||0));
const date=value=>value?new Date(value).toLocaleDateString('vi-VN'):'—';
const today=()=>new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const emptyLine=()=>({productId:'',quantity:'1',unitCost:''});

export default function ManagerSupplierReceipts(){
  const [filters,change]=useManagerFilters({supplierId:'',fromDate:'',toDate:'',page:'0'});
  const page=Number(filters.page)||0;
  const endpoint=useCallback(()=>`/api/manager/supplier-receipts?${managerQuery({supplierId:filters.supplierId,from:filters.fromDate||undefined,to:filters.toDate||undefined,page,size:20})}`,[filters.supplierId,filters.fromDate,filters.toDate,page]);
  const {data,loading,error,load}=useManagerList(endpoint);
  const {data:supplierPage}=useManagerList(useCallback(()=>'/api/manager/suppliers?size=100&active=true&sort=name&desc=false',[]));
  const {data:productPage}=useManagerList(useCallback(()=>'/api/manager/products?size=100&q=&active=true&sort=name&direction=ASC',[]));
  const suppliers=supplierPage?.content||[],products=productPage?.content||[];
  const [open,setOpen]=useState(false),[form,setForm]=useState(null),[lines,setLines]=useState([]),[busy,setBusy]=useState(false),[failure,setFailure]=useState('');
  useManagerReadPause(open);
  const start=()=>{setForm({supplierId:'',invoiceNumber:'',invoiceDate:today(),note:''});setLines([emptyLine()]);setFailure('');setOpen(true);};
  const setLine=(index,patch)=>setLines(current=>current.map((line,i)=>i===index?{...line,...patch}:line));
  const total=useMemo(()=>lines.reduce((sum,line)=>sum+Number(line.quantity||0)*Number(line.unitCost||0),0),[lines]);
  const submit=async event=>{event.preventDefault();if(busy)return;if(!lines.length||lines.some(line=>!line.productId||Number(line.quantity)<1||line.unitCost==='')){setFailure('Vui lòng chọn sản phẩm, số lượng và đơn giá cho mọi dòng.');return;}setBusy(true);setFailure('');try{await adminApi('/api/manager/supplier-receipts',{method:'POST',body:{supplierId:Number(form.supplierId),invoiceNumber:form.invoiceNumber.trim(),invoiceDate:form.invoiceDate,note:form.note.trim()||null,items:lines.map(line=>({productId:Number(line.productId),quantity:Number(line.quantity),unitCost:Number(line.unitCost)}))}});setOpen(false);setForm(null);setLines([]);load();}catch(error){setFailure(error.message);}finally{setBusy(false);}};
  return <section className="refund-card" aria-label="Phiếu nhập hàng"><div className="refund-actions"><h2>Phiếu nhập hàng</h2><button className="admin-primary" onClick={start}>Lập phiếu nhập</button></div><p>Ghi nhận hóa đơn mua hàng từ nhà cung cấp để tính chi phí và doanh thu chênh lệch. Phiếu nhập không tự cộng tồn kho.</p>
    <div className="refund-filters"><label>Nhà cung cấp<select value={filters.supplierId} onChange={e=>change({supplierId:e.target.value})}><option value="">Tất cả</option>{suppliers.map(supplier=><option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}</select></label><label>Từ ngày<input type="date" value={filters.fromDate} onChange={e=>change({fromDate:e.target.value})}/></label><label>Đến ngày<input type="date" value={filters.toDate} onChange={e=>change({toDate:e.target.value})}/></label></div>
    {open&&form&&<form className="refund-form" aria-label="Lập phiếu nhập hàng" onSubmit={submit}><h3>Phiếu nhập mới</h3><label>Nhà cung cấp *<select required value={form.supplierId} onChange={e=>setForm({...form,supplierId:e.target.value})}><option value="">Chọn nhà cung cấp</option>{suppliers.map(supplier=><option key={supplier.id} value={supplier.id}>{supplier.code} · {supplier.name}</option>)}</select></label><label>Số hóa đơn nhà cung cấp *<input required maxLength={80} value={form.invoiceNumber} onChange={e=>setForm({...form,invoiceNumber:e.target.value})}/></label><label>Ngày hóa đơn *<input required type="date" value={form.invoiceDate} onChange={e=>setForm({...form,invoiceDate:e.target.value})}/></label><label>Ghi chú<textarea maxLength={1000} value={form.note} onChange={e=>setForm({...form,note:e.target.value})}/></label>
      <fieldset><legend>Dòng hàng</legend>{lines.map((line,index)=><div key={index} className="refund-filters"><label>Sản phẩm<select required value={line.productId} onChange={e=>setLine(index,{productId:e.target.value})}><option value="">Chọn sản phẩm</option>{products.map(product=><option key={product.id} value={product.id}>{product.name}</option>)}</select></label><label>Số lượng<input required type="number" min="1" max="1000000" value={line.quantity} onChange={e=>setLine(index,{quantity:e.target.value})}/></label><label>Đơn giá nhập<input required type="number" min="0" step="1000" value={line.unitCost} onChange={e=>setLine(index,{unitCost:e.target.value})}/></label><button type="button" className="admin-action" disabled={busy||lines.length<=1} onClick={()=>setLines(current=>current.filter((_,i)=>i!==index))}>Xóa dòng</button></div>)}<button type="button" className="admin-action" disabled={busy} onClick={()=>setLines(current=>[...current,emptyLine()])}>Thêm dòng</button></fieldset>
      <p><b>Tổng tiền nhập: {money(total)}</b></p>{failure&&<p role="alert">{failure}</p>}<div className="refund-actions"><button type="button" disabled={busy} onClick={()=>{setOpen(false);setFailure('');}}>Bỏ qua</button><button className="admin-primary" disabled={busy}>{busy?'Đang lưu…':'Lưu phiếu nhập'}</button></div></form>}
    {loading&&<p role="status">Đang tải phiếu nhập…</p>}{error&&<p role="alert">{error} <button onClick={()=>load()}>Thử lại</button></p>}
    {!loading&&data?.content?.length===0&&<p>Chưa có phiếu nhập hàng phù hợp.</p>}
    <div className="refund-ledger">{(data?.content||[]).map(receipt=><article key={receipt.id}><h3>{receipt.invoiceNumber} · {receipt.supplier?.name}</h3><p>Ngày hóa đơn {date(receipt.invoiceDate)} · {money(receipt.totalAmount)}</p><p>{receipt.items?.length||0} dòng hàng{receipt.note?` · ${receipt.note}`:''}</p><details><summary>Chi tiết dòng hàng</summary><ul>{(receipt.items||[]).map(item=><li key={item.id}>{item.product?.name} × {item.quantity} · {money(item.unitCost)} = {money(item.lineTotal)}</li>)}</ul></details></article>)}</div>
    <nav className="refund-actions" aria-label="Phân trang phiếu nhập"><button disabled={loading||page<=0} onClick={()=>change({page:String(page-1)})}>Trang trước</button><span>Trang {page+1} / {Math.max(1,data?.totalPages||0)}</span><button disabled={loading||page+1>=(data?.totalPages||0)} onClick={()=>change({page:String(page+1)})}>Trang sau</button></nav>
  </section>;
}
