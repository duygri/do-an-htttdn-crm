import React,{useCallback,useState} from 'react';
import {adminApi} from './manager-api';
import {useManagerList,useManagerReadPause} from './useManagerResource';
import {useManagerFilters,managerQuery} from './useManagerFilters';
import './manager-refunds.css';

const empty={code:'',name:'',email:'',phone:'',address:'',notes:'',active:true};
const date=value=>value?new Date(value).toLocaleDateString('vi-VN'):'—';

export default function ManagerSuppliers(){
  const [filters,change]=useManagerFilters({q:'',active:'',page:'0'});
  const page=Number(filters.page)||0;
  const endpoint=useCallback(()=>`/api/manager/suppliers?${managerQuery({q:filters.q,active:filters.active,page,size:20,sort:'name',desc:'false'})}`,[filters.q,filters.active,page]);
  const {data,loading,error,load}=useManagerList(endpoint);
  const [form,setForm]=useState(null),[editing,setEditing]=useState(null),[busy,setBusy]=useState(false),[failure,setFailure]=useState('');
  useManagerReadPause(!!form);
  const openAdd=()=>{setEditing(null);setForm({...empty});setFailure('');};
  const openEdit=supplier=>{setEditing(supplier.id);setForm({code:supplier.code,name:supplier.name,email:supplier.email||'',phone:supplier.phone||'',address:supplier.address||'',notes:supplier.notes||'',active:supplier.active});setFailure('');};
  const save=async event=>{event.preventDefault();if(busy)return;setBusy(true);setFailure('');try{await adminApi(editing?`/api/manager/suppliers/${editing}`:'/api/manager/suppliers',{method:editing?'PUT':'POST',body:{...form,active:!!form.active}});setForm(null);setEditing(null);load();}catch(error){setFailure(error.message);}finally{setBusy(false);}};
  return <section className="refund-card" aria-label="Nhà cung cấp"><div className="refund-actions"><h2>Nhà cung cấp</h2><button className="admin-primary" onClick={openAdd}>Thêm nhà cung cấp</button></div><p>Quản lý đối tác cung cấp hàng và gắn nhà cung cấp cho sản phẩm.</p>
    <div className="refund-filters"><label>Tìm theo mã, tên, email, điện thoại<input value={filters.q} onChange={e=>change({q:e.target.value})}/></label><label>Trạng thái<select value={filters.active} onChange={e=>change({active:e.target.value})}><option value="">Tất cả</option><option value="true">Đang hợp tác</option><option value="false">Ngừng hợp tác</option></select></label></div>
    {form&&<form className="refund-form" aria-label={editing?'Sửa nhà cung cấp':'Thêm nhà cung cấp'} onSubmit={save}><h3>{editing?'Sửa nhà cung cấp':'Nhà cung cấp mới'}</h3><label>Mã nhà cung cấp *<input required maxLength={40} value={form.code} onChange={e=>setForm({...form,code:e.target.value.toUpperCase()})}/></label><label>Tên *<input required maxLength={150} value={form.name} onChange={e=>setForm({...form,name:e.target.value})}/></label><label>Email<input type="email" maxLength={255} value={form.email} onChange={e=>setForm({...form,email:e.target.value})}/></label><label>Điện thoại<input maxLength={30} value={form.phone} onChange={e=>setForm({...form,phone:e.target.value})}/></label><label>Địa chỉ<input maxLength={1000} value={form.address} onChange={e=>setForm({...form,address:e.target.value})}/></label><label>Ghi chú<textarea maxLength={4000} value={form.notes} onChange={e=>setForm({...form,notes:e.target.value})}/></label><label><span><input type="checkbox" checked={!!form.active} onChange={e=>setForm({...form,active:e.target.checked})}/> Đang hợp tác</span></label>{failure&&<p role="alert">{failure}</p>}<div className="refund-actions"><button type="button" disabled={busy} onClick={()=>{setForm(null);setFailure('');}}>Bỏ qua</button><button className="admin-primary" disabled={busy}>{busy?'Đang lưu…':'Lưu nhà cung cấp'}</button></div></form>}
    {loading&&<p role="status">Đang tải nhà cung cấp…</p>}{error&&<p role="alert">{error} <button onClick={()=>load()}>Thử lại</button></p>}
    {!loading&&data?.content?.length===0&&<p>Chưa có nhà cung cấp phù hợp.</p>}
    <div className="refund-ledger">{(data?.content||[]).map(supplier=><article key={supplier.id}><h3>{supplier.code} · {supplier.name}</h3><p>{[supplier.email,supplier.phone].filter(Boolean).join(' · ')||'Chưa có liên hệ'}</p><p>{supplier.address||'Chưa có địa chỉ'} · {supplier.active?'Đang hợp tác':'Ngừng hợp tác'} · Tạo {date(supplier.createdAt)}</p><button className="admin-action" onClick={()=>openEdit(supplier)}>Sửa</button></article>)}</div>
    <nav className="refund-actions" aria-label="Phân trang nhà cung cấp"><button disabled={loading||page<=0} onClick={()=>change({page:String(page-1)})}>Trang trước</button><span>Trang {page+1} / {Math.max(1,data?.totalPages||0)}</span><button disabled={loading||page+1>=(data?.totalPages||0)} onClick={()=>change({page:String(page+1)})}>Trang sau</button></nav>
  </section>;
}
