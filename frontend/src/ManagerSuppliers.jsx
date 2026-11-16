import React,{useCallback,useState} from 'react';
import {adminApi,adminDownload} from './manager-api';
import {useManagerList,useManagerReadPause} from './useManagerResource';
import {useManagerFilters,managerQuery} from './useManagerFilters';
import './manager-refunds.css';

const empty={code:'',name:'',email:'',phone:'',address:'',notes:'',active:true};
const date=value=>value?new Date(value).toLocaleDateString('vi-VN'):'—';
const today=()=>new Date().toISOString().slice(0,10);
const money=value=>`${new Intl.NumberFormat('vi-VN').format(Number(value||0))} đ`;

export default function ManagerSuppliers(){
  const [filters,change]=useManagerFilters({q:'',active:'',page:'0'});
  const page=Number(filters.page)||0;
  const endpoint=useCallback(()=>`/api/manager/suppliers?${managerQuery({q:filters.q,active:filters.active,page,size:20,sort:'name',desc:'false'})}`,[filters.q,filters.active,page]);
  const {data,loading,error,load}=useManagerList(endpoint);
  const [form,setForm]=useState(null),[editing,setEditing]=useState(null),[busy,setBusy]=useState(false),[failure,setFailure]=useState('');
  const [detail,setDetail]=useState(null),[drafts,setDrafts]=useState({}),[detailError,setDetailError]=useState(''),[detailBusy,setDetailBusy]=useState(false);
  useManagerReadPause(!!form||!!detail);
  const openAdd=()=>{setEditing(null);setForm({...empty});setFailure('');};
  const openEdit=supplier=>{setEditing(supplier.id);setForm({code:supplier.code,name:supplier.name,email:supplier.email||'',phone:supplier.phone||'',address:supplier.address||'',notes:supplier.notes||'',active:supplier.active});setFailure('');};
  const save=async event=>{event.preventDefault();if(busy)return;setBusy(true);setFailure('');try{await adminApi(editing?`/api/manager/suppliers/${editing}`:'/api/manager/suppliers',{method:editing?'PUT':'POST',body:{...form,active:!!form.active}});setForm(null);setEditing(null);load();}catch(error){setFailure(error.message);}finally{setBusy(false);}};
  const seedDrafts=next=>setDrafts(Object.fromEntries((next?.receipts||[]).map(row=>[row.id,{amount:String(row.outstanding),paidAt:today(),method:'CASH',note:''}])));
  const openDetail=async id=>{setDetailBusy(true);setDetailError('');try{const next=await adminApi(`/api/manager/suppliers/${id}`);setDetail(next);seedDrafts(next);}catch(error){setDetailError(error.message);}finally{setDetailBusy(false);}};
  const reloadDetail=async id=>{const next=await adminApi(`/api/manager/suppliers/${id}`);setDetail(next);seedDrafts(next);load();};
  const toggleStatus=async()=>{if(detailBusy)return;setDetailBusy(true);setDetailError('');try{const updated=await adminApi(`/api/manager/suppliers/${detail.id}/status`,{method:'PATCH',body:{active:!detail.active}});setDetail({...detail,active:updated.active});load();}catch(error){setDetailError(error.message);}finally{setDetailBusy(false);}};
  const changeDraft=(receiptId,key,value)=>setDrafts(current=>({...current,[receiptId]:{...current[receiptId],[key]:value}}));
  const pay=async(event,receipt)=>{event.preventDefault();if(detailBusy)return;const draft=drafts[receipt.id]||{};setDetailBusy(true);setDetailError('');try{await adminApi(`/api/manager/suppliers/${detail.id}/payments`,{method:'POST',body:{receiptId:receipt.id,amount:Number(draft.amount),paidAt:draft.paidAt,method:draft.method,note:draft.note||null}});await reloadDetail(detail.id);}catch(error){setDetailError(error.message);}finally{setDetailBusy(false);}};
  const download=()=>adminDownload(`/api/manager/suppliers/${detail.id}/reports/summary.csv`,'cong-no-ncc.csv').catch(error=>setDetailError(error.message));
  return <section className="refund-card" aria-label="Nhà cung cấp"><div className="refund-actions"><h2>Nhà cung cấp</h2><button className="admin-primary" onClick={openAdd}>Thêm nhà cung cấp</button></div><p>Quản lý đối tác cung cấp hàng và gắn nhà cung cấp cho sản phẩm.</p>
    <div className="refund-filters"><label>Tìm theo mã, tên, email, điện thoại<input value={filters.q} onChange={e=>change({q:e.target.value})}/></label><label>Trạng thái<select value={filters.active} onChange={e=>change({active:e.target.value})}><option value="">Tất cả</option><option value="true">Đang hợp tác</option><option value="false">Ngừng hợp tác</option></select></label></div>
    {form&&<form className="refund-form" aria-label={editing?'Sửa nhà cung cấp':'Thêm nhà cung cấp'} onSubmit={save}><h3>{editing?'Sửa nhà cung cấp':'Nhà cung cấp mới'}</h3><label>Mã nhà cung cấp *<input required maxLength={40} value={form.code} onChange={e=>setForm({...form,code:e.target.value.toUpperCase()})}/></label><label>Tên *<input required maxLength={150} value={form.name} onChange={e=>setForm({...form,name:e.target.value})}/></label><label>Email<input type="email" maxLength={255} value={form.email} onChange={e=>setForm({...form,email:e.target.value})}/></label><label>Điện thoại<input maxLength={30} value={form.phone} onChange={e=>setForm({...form,phone:e.target.value})}/></label><label>Địa chỉ<input maxLength={1000} value={form.address} onChange={e=>setForm({...form,address:e.target.value})}/></label><label>Ghi chú<textarea maxLength={4000} value={form.notes} onChange={e=>setForm({...form,notes:e.target.value})}/></label><label><span><input type="checkbox" checked={!!form.active} onChange={e=>setForm({...form,active:e.target.checked})}/> Đang hợp tác</span></label>{failure&&<p role="alert">{failure}</p>}<div className="refund-actions"><button type="button" disabled={busy} onClick={()=>{setForm(null);setFailure('');}}>Bỏ qua</button><button className="admin-primary" disabled={busy}>{busy?'Đang lưu…':'Lưu nhà cung cấp'}</button></div></form>}
    {loading&&<p role="status">Đang tải nhà cung cấp…</p>}{error&&<p role="alert">{error} <button onClick={()=>load()}>Thử lại</button></p>}
    {!loading&&data?.content?.length===0&&<p>Chưa có nhà cung cấp phù hợp.</p>}
    <div className="refund-ledger">{(data?.content||[]).map(supplier=><article key={supplier.id}><h3>{supplier.code} · {supplier.name}</h3><p>{[supplier.email,supplier.phone].filter(Boolean).join(' · ')||'Chưa có liên hệ'}</p><p>{supplier.address||'Chưa có địa chỉ'} · {supplier.active?'Đang hợp tác':'Ngừng hợp tác'} · Tạo {date(supplier.createdAt)}</p><button className="admin-action" onClick={()=>openDetail(supplier.id)}>Chi tiết</button>{' '}<button className="admin-action" onClick={()=>openEdit(supplier)}>Sửa</button></article>)}</div>
    {detailBusy&&!detail&&<p role="status">Đang tải chi tiết nhà cung cấp…</p>}
    {detail&&<article className="refund-form" aria-label="Chi tiết nhà cung cấp">
      <div className="refund-actions"><h3>{detail.code} · {detail.name}</h3><button type="button" disabled={detailBusy} onClick={toggleStatus}>{detail.active?'Ngừng hợp tác':'Kích hoạt lại'}</button>{' '}<button type="button" onClick={download}>Xuất CSV công nợ</button>{' '}<button type="button" onClick={()=>setDetail(null)}>Đóng</button></div>
      <p>{[detail.email,detail.phone].filter(Boolean).join(' · ')||'Chưa có liên hệ'} · {detail.address||'Chưa có địa chỉ'} · {detail.active?'Đang hợp tác':'Ngừng hợp tác'}</p>
      <p>Sản phẩm: {detail.productCount} · Phiếu nhập: {detail.receiptCount} · Tổng mua: {money(detail.totalPurchase)} · Đã trả: {money(detail.totalPaid)} · Còn nợ: <strong>{money(detail.outstanding)}</strong> · Lần nhập gần nhất: {date(detail.lastReceiptDate)}</p>
      {detailError&&<p role="alert">{detailError}</p>}
      <h4>Sản phẩm của nhà cung cấp</h4>
      {(detail.products||[]).length===0?<p>Chưa gán sản phẩm nào.</p>:<div className="refund-ledger"><table><thead><tr><th>Sản phẩm</th><th>Tồn</th><th>Giá bán</th><th>Trạng thái</th></tr></thead><tbody>{detail.products.map(row=><tr key={row.id}><td>{row.name}</td><td>{row.stock}</td><td>{money(row.price)}</td><td>{row.active?'Đang bán':'Ngừng bán'}</td></tr>)}</tbody></table></div>}
      <h4>Phiếu nhập và công nợ</h4>
      {(detail.receipts||[]).length===0?<p>Chưa có phiếu nhập hàng.</p>:<div className="refund-ledger"><table><thead><tr><th>Hóa đơn</th><th>Ngày</th><th>Tổng tiền</th><th>Đã trả</th><th>Còn nợ</th></tr></thead><tbody>{detail.receipts.map(row=><tr key={row.id}><td>{row.invoiceNumber}</td><td>{date(row.invoiceDate)}</td><td>{money(row.totalAmount)}</td><td>{money(row.paidAmount)}</td><td>{money(row.outstanding)}</td></tr>)}</tbody></table></div>}
      {detail.receipts?.filter(row=>row.outstanding>0).map(row=><form key={row.id} className="refund-form" aria-label={`Thanh toán ${row.invoiceNumber}`} onSubmit={event=>pay(event,row)}>
        <h4>Thanh toán {row.invoiceNumber}</h4>
        <label>Số tiền {row.invoiceNumber}<input type="number" min="1" step="any" value={drafts[row.id]?.amount||''} onChange={e=>changeDraft(row.id,'amount',e.target.value)}/></label>
        <label>Ngày thanh toán {row.invoiceNumber}<input type="date" value={drafts[row.id]?.paidAt||''} onChange={e=>changeDraft(row.id,'paidAt',e.target.value)}/></label>
        <label>Phương thức {row.invoiceNumber}<select value={drafts[row.id]?.method||'CASH'} onChange={e=>changeDraft(row.id,'method',e.target.value)}><option value="CASH">Tiền mặt</option><option value="BANK_TRANSFER">Chuyển khoản</option></select></label>
        <label>Ghi chú {row.invoiceNumber}<input maxLength={500} value={drafts[row.id]?.note||''} onChange={e=>changeDraft(row.id,'note',e.target.value)}/></label>
        <button className="admin-primary" disabled={detailBusy}>Ghi nhận thanh toán {row.invoiceNumber}</button>
      </form>)}
    </article>}
    <nav className="refund-actions" aria-label="Phân trang nhà cung cấp"><button disabled={loading||page<=0} onClick={()=>change({page:String(page-1)})}>Trang trước</button><span>Trang {page+1} / {Math.max(1,data?.totalPages||0)}</span><button disabled={loading||page+1>=(data?.totalPages||0)} onClick={()=>change({page:String(page+1)})}>Trang sau</button></nav>
  </section>;
}
