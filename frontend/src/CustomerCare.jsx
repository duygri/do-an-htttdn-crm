import React,{useCallback,useEffect,useRef,useState} from 'react';
import {adminApi} from './manager-api';
import {useManagerResource,useManagerReadPause} from './useManagerResource';
import {managerQuery} from './useManagerFilters';
import './customer-care.css';

export const careLabels={TODO:'Chưa thực hiện',IN_PROGRESS:'Đang xử lý',COMPLETED:'Hoàn thành'};
const date=value=>new Date(value).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh',dateStyle:'short',timeStyle:'short'});
export const careDeadline=value=>value?new Date(`${value}:00+07:00`).toISOString():null;
const localDeadline=value=>{if(!value)return '';const d=new Date(new Date(value).getTime()+7*3600000);return d.toISOString().slice(0,16);};
function useCare(url,enabled=true){const fetcher=useCallback(signal=>adminApi(url,{signal}),[url]);return useManagerResource(fetcher,{enabled,keepPreviousData:true});}
function ErrorBox({message,retry}){return <div className="admin-list-error" role="alert"><p>{message}</p><button type="button" className="admin-action" onClick={retry}>Thử lại</button></div>;}
function Pager({data,page,onPage,loading}){return <div className="admin-pagination"><span>Trang {page+1}/{Math.max(1,data?.totalPages||0)} · {data?.totalElements||0} bản ghi</span><div><button type="button" className="admin-action" disabled={loading||page===0} onClick={()=>onPage(page-1)}>Trước</button><button type="button" className="admin-action" disabled={loading||page+1>=(data?.totalPages||0)} onClick={()=>onPage(page+1)}>Sau</button></div></div>;}

// Editors deliberately use one-off, abortable lookups rather than paused list polling.
function Picker({kind,value,onChange,disabled}){
 const [q,setQ]=useState(''),[page,setPage]=useState(0),[data,setData]=useState(null),[error,setError]=useState(''),[retry,setRetry]=useState(0);
 useEffect(()=>{const controller=new AbortController();setData(null);setError('');const base=kind==='staff'?'/api/manager/care/staff':'/api/manager/users';adminApi(`${base}?${managerQuery({q,page,size:10})}`,{signal:controller.signal}).then(d=>{if(!controller.signal.aborted)setData(d);}).catch(e=>{if(!controller.signal.aborted)setError(e.message);});return()=>controller.abort();},[kind,q,page,retry]);
 const label=kind==='staff'?'Người phụ trách':'Khách hàng';
 return <fieldset className="care-picker" disabled={disabled}><legend>{label}</legend><label>Tìm {label.toLowerCase()}<input aria-label={`Tìm ${label.toLowerCase()}`} type="search" maxLength={255} value={q} onKeyDown={e=>{if(e.key==='Enter')e.preventDefault();}} onChange={e=>{setQ(e.target.value);setPage(0);}}/></label>
 {error?<ErrorBox message={error} retry={()=>setRetry(n=>n+1)}/>:!data?<p role="status">Đang tải {label.toLowerCase()}...</p>:<><select aria-label={label} required value={value?.id||''} onChange={e=>onChange(data.content.find(row=>String(row.id)===e.target.value)||null)}><option value="">Chọn {label.toLowerCase()}</option>{value&&!data.content.some(row=>row.id===value.id)&&<option value={value.id}>{value.fullName} (đang chọn)</option>}{data.content.map(row=><option key={row.id} value={row.id}>{row.fullName} · {row.email}</option>)}</select>{!data.content.length&&<p>Không có kết quả phù hợp.</p>}<Pager data={data} page={page} onPage={setPage}/></>}
 </fieldset>;
}
function TaskForm({task,customer,onSaved,onClose}){
 const [selected,setSelected]=useState(customer||null),[staff,setStaff]=useState(task?{id:task.assigneeId,fullName:task.assigneeName}:null);
 const [draft,setDraft]=useState({title:task?.title||'',description:task?.description||'',due:localDeadline(task?.dueAt),status:task?.status||'TODO'});
 const [busy,setBusy]=useState(false),[error,setError]=useState('');const lock=useRef(false);
 const submit=async e=>{e.preventDefault();if(lock.current)return;if(!selected||!staff||!draft.due){setError('Chọn khách hàng, người phụ trách và hạn xử lý.');return;}lock.current=true;setBusy(true);setError('');
  try{const body={assigneeId:staff.id,title:draft.title.trim(),description:draft.description.trim(),dueAt:careDeadline(draft.due)};await adminApi(task?`/api/manager/care/tasks/${task.id}`:'/api/manager/care/tasks',{method:task?'PATCH':'POST',body:task?{...body,status:draft.status,version:task.version}:{...body,customerId:selected.id}});onSaved();}
  catch(e){setError(e.message);}finally{lock.current=false;setBusy(false);}
 };
 return <form className="care-form" aria-label={task?'Sửa công việc chăm sóc':'Tạo công việc chăm sóc'} onSubmit={submit}><h3>{task?'Cập nhật công việc':'Tạo công việc chăm sóc'}</h3><fieldset disabled={busy}>
 {customer?<p>Khách hàng: <strong>{customer.fullName}</strong></p>:<Picker kind="customer" value={selected} onChange={setSelected}/>}
 <label>Tiêu đề<input aria-label="Tiêu đề công việc" required maxLength={255} value={draft.title} onChange={e=>setDraft({...draft,title:e.target.value})}/></label>
 <label>Nội dung<textarea aria-label="Nội dung công việc" maxLength={4000} value={draft.description} onChange={e=>setDraft({...draft,description:e.target.value})}/></label>
 <Picker kind="staff" value={staff} onChange={setStaff}/>
 {task&&!task.assigneeActive&&<p className="care-warning">Người phụ trách cũ không còn hoạt động. Anh/chị có thể chọn quản lý khác; lịch sử công việc được giữ nguyên.</p>}
 <label>Hạn xử lý (giờ Việt Nam)<input aria-label="Hạn xử lý" type="datetime-local" required value={draft.due} onChange={e=>setDraft({...draft,due:e.target.value})}/></label>
 <p>Hạn đã qua sẽ được đánh dấu quá hạn. Hoàn thành công việc không gửi thông báo cho khách.</p>
 {task&&<label>Trạng thái<select aria-label="Trạng thái công việc" value={draft.status} onChange={e=>setDraft({...draft,status:e.target.value})}>{Object.entries(careLabels).filter(([key])=>task.status==='TODO'||key!=='TODO').map(([key,label])=><option key={key} value={key}>{label}</option>)}</select></label>}
 </fieldset>{error&&<p role="alert">{error} {error.includes('đã thay đổi')&&'Hãy đóng form và tải lại danh sách; nội dung đang nhập vẫn giữ đến khi đóng.'}</p>}<div className="care-actions"><button className="admin-primary" disabled={busy}>{busy?'Đang lưu...':'Lưu công việc'}</button><button type="button" className="admin-action" disabled={busy} onClick={onClose}>Hủy</button></div></form>;
}

export default function CustomerCare({customer,go,location}){
 const defaults={q:'',status:'',assigneeId:'',overdue:'',customerId:'',from:'',to:'',page:'0'};
 const params=new URLSearchParams((location||window.location.pathname+window.location.search).split('?')[1]);
 const prefix=customer?'care_':'';
 const filters=Object.fromEntries(Object.entries(defaults).map(([key,value])=>[key,params.get(prefix+key)||value]));
 const change=patch=>{const next=new URLSearchParams(params);const update={...patch,...(!Object.hasOwn(patch,'page')?{page:'0'}:{})};Object.entries(update).forEach(([key,value])=>value===''||String(value)===defaults[key]?next.delete(prefix+key):next.set(prefix+key,String(value)));go(`${customer?`users/${customer.id}`:'care'}${next.size?'?'+next:''}`);};
 const page=Math.max(0,parseInt(filters.page,10)||0),[search,setSearch]=useState(filters.q);
 useEffect(()=>setSearch(filters.q),[filters.q]);
 const selectedId=customer?.id||filters.customerId;
 const selectedProfile=useCare(selectedId&&!customer?`/api/manager/users/${selectedId}/profile`:'',!!selectedId&&!customer);
 const fixedCustomer=customer||(!selectedProfile.stale?selectedProfile.data?.customer:null);
 const url=`/api/manager/care/tasks?${managerQuery({...filters,customerId:selectedId,page,size:10})}`;
 const list=useCare(url);const data=list.stale?null:list.data;
 const [form,setForm]=useState(null),[notice,setNotice]=useState('');
 useManagerReadPause(!!form);
 const saved=()=>{setForm(null);setNotice('Đã lưu công việc chăm sóc.');};
 return <section className="customer-care"><div className="admin-panel"><h2>{customer?'Công việc chăm sóc':'Chăm sóc khách hàng'}</h2><p>Ghi nhận việc cần làm, người phụ trách và hạn xử lý. Không tự gửi tin hoặc cấp voucher.</p>
 {selectedId&&!customer&&(fixedCustomer?<p>Đang xem: <strong>{fixedCustomer.fullName}</strong> <button disabled={!!form} className="admin-action" onClick={()=>change({customerId:''})}>Xem tất cả khách hàng</button></p>:selectedProfile.error?<ErrorBox message={selectedProfile.error} retry={()=>selectedProfile.load()}/>:<p>Đang tải khách hàng...</p>)}
 <button className="admin-primary" disabled={!!form||!!selectedId&&!fixedCustomer} onClick={()=>{setNotice('');setForm({task:null});}}>Tạo công việc chăm sóc</button>
 {form&&<TaskForm key={form.task?.id||'new'} task={form.task} customer={form.task?{id:form.task.customerId,fullName:form.task.customerName}:fixedCustomer} onClose={()=>setForm(null)} onSaved={saved}/>}
 {notice&&<p role="status">{notice}</p>}
 <fieldset disabled={!!form} className="care-filters"><legend>Lọc công việc chăm sóc</legend><label>Tìm công việc<input type="search" aria-label="Tìm công việc" maxLength={255} value={search} onChange={e=>setSearch(e.target.value)} onKeyDown={e=>{if(e.key==='Enter')change({q:search.trim()});}}/></label><button className="admin-action" onClick={()=>change({q:search.trim()})}>Tìm</button>
 <label>Trạng thái<select aria-label="Lọc trạng thái chăm sóc" value={filters.status} onChange={e=>change({status:e.target.value})}><option value="">Tất cả</option>{Object.entries(careLabels).map(([k,v])=><option key={k} value={k}>{v}</option>)}</select></label>
 <label>Hạn công việc<select aria-label="Lọc quá hạn" value={filters.overdue} onChange={e=>change({overdue:e.target.value})}><option value="">Tất cả hạn</option><option value="true">Quá hạn</option><option value="false">Không quá hạn</option></select></label>
 <AssigneeFilter value={filters.assigneeId} onChange={id=>change({assigneeId:id})}/>
 <label>Hạn từ ngày<input type="date" value={filters.from} onChange={e=>change({from:e.target.value})}/></label><label>Hạn đến ngày<input type="date" value={filters.to} onChange={e=>change({to:e.target.value})}/></label>
 </fieldset>
 {list.loading&&(!data||list.stale)&&<p role="status">Đang tải công việc...</p>}{list.error&&!list.data&&<ErrorBox message={list.error} retry={()=>list.load()}/>}
 {data&&!data.content.length&&<p>Chưa có công việc phù hợp.</p>}
 <div className="care-task-list">{data?.content.map(task=><article className="care-task" key={task.id}><div className="care-task-heading"><h3>{task.title}</h3><span className="admin-badge">{careLabels[task.status]}</span>{task.overdue&&<span className="care-warning">Quá hạn</span>}</div><p>Khách hàng: <strong>{task.customerName}</strong> {go&&<button className="admin-action" onClick={()=>go(`users/${task.customerId}?tab=care`)}>Xem hồ sơ</button>}</p><p>Phụ trách: {task.assigneeName}{!task.assigneeActive&&' (không còn hoạt động)'}</p><p>Hạn xử lý: {date(task.dueAt)}</p><p className="care-content">{task.description||'Không có nội dung bổ sung.'}</p><small>Tạo: {date(task.createdAt)} · Cập nhật: {date(task.updatedAt)}{task.completedAt&&` · Hoàn thành: ${date(task.completedAt)}`}</small>{task.status!=='COMPLETED'&&<button className="admin-action" disabled={!!form} onClick={()=>{setNotice('');setForm({task});}}>Xử lý công việc</button>}</article>)}</div>
 <Pager data={data} page={page} onPage={next=>change({page:String(next)})} loading={list.loading||!!form}/>
 </div>{fixedCustomer&&<CareNotes key={fixedCustomer.id} customerId={fixedCustomer.id}/>}</section>;
}
export function AssigneeFilter({value,onChange}){
 const [open,setOpen]=useState(false),[selected,setSelected]=useState(null);
 return <div><button className="admin-action" onClick={()=>setOpen(v=>!v)}>{value?String(selected?.id)===value?selected.fullName:`Phụ trách #${value}`:'Lọc người phụ trách'}</button>{value&&<button className="admin-action" onClick={()=>{setSelected(null);onChange('');}}>Bỏ lọc người phụ trách</button>}{open&&<Picker kind="staff" value={selected} onChange={row=>{setSelected(row);onChange(row?String(row.id):'');setOpen(false);}}/>}</div>;
}
export function CareNotes({customerId}){
 const [page,setPage]=useState(0),[draft,setDraft]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');const lock=useRef(false);
 const resource=useCare(`/api/manager/users/${customerId}/care/notes?page=${page}&size=10`),data=resource.stale?null:resource.data;
 useManagerReadPause(!!draft||busy);
 const submit=async e=>{e.preventDefault();if(lock.current||!draft.trim())return;lock.current=true;setBusy(true);setError('');try{await adminApi(`/api/manager/users/${customerId}/care/notes`,{method:'POST',body:{content:draft.trim()}});setDraft('');setPage(0);setNotice('Đã lưu ghi chú chăm sóc.');}catch(e){setError(e.message);}finally{lock.current=false;setBusy(false);}};
 return <div className="admin-panel care-notes"><h2>Ghi chú chăm sóc</h2><form onSubmit={submit}><label>Nội dung ghi chú<textarea aria-label="Nội dung ghi chú" required maxLength={4000} disabled={busy} value={draft} onChange={e=>{setDraft(e.target.value);setNotice('');}}/></label><small>{draft.length}/4.000 ký tự · Ghi chú đã lưu không sửa hoặc xóa trong bản cơ bản.</small><button className="admin-primary" disabled={busy||!draft.trim()}>{busy?'Đang lưu...':'Lưu ghi chú'}</button>{draft&&<button type="button" className="admin-action" disabled={busy} onClick={()=>{setDraft('');setError('');}}>Bỏ bản nháp</button>}{error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}</form>
 {resource.loading&&!data&&<p>Đang tải ghi chú...</p>}{resource.error&&!resource.data&&<ErrorBox message={resource.error} retry={()=>resource.load()}/>} {data&&!data.content.length&&<p>Chưa có ghi chú chăm sóc.</p>}
 {data?.content.map(note=><article className="care-note" key={note.id}><p className="care-content">{note.content}</p><small>{note.authorName} · {date(note.createdAt)}</small></article>)}<Pager data={data} page={page} onPage={setPage} loading={resource.loading||!!draft||busy}/></div>;
}
