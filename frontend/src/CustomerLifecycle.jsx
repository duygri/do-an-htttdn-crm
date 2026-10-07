import React, {useState} from 'react';
import './customer-lifecycle.css';
export const lifecycleLabels={NO_PURCHASE:'Chưa mua hàng',FIRST_PURCHASE:'Mua lần đầu',RETURNING:'Khách quay lại',LOYAL:'Khách thân thiết'};
export const lifecycleExplanation='Chỉ tính đơn hoàn thành: thân thiết từ 5 đơn hoặc 5.000.000 đ; lâu chưa mua lại từ 30 ngày lịch Việt Nam kể từ ngày đặt gần nhất trong các đơn hoàn thành. Giá trị sau giảm giá, chưa trừ hoàn tiền; đơn hoàn thành có đổi trả vẫn tính.';
export default function CustomerLifecycle({segment}) {
  if(!segment)return null;
  return <div className="customer-lifecycle"><span className={`admin-badge lifecycle-${segment.stage}`}>{lifecycleLabels[segment.stage]||segment.stage}</span>{segment.dormant&&<span className="admin-badge warning">Lâu chưa mua lại</span>}{segment.daysSinceLastPurchase!=null&&<small>{segment.daysSinceLastPurchase} ngày từ lần mua gần nhất</small>}</div>;
}
export function CustomerLifecycleFilters({params,onChange}) {
  const value=k=>params.get(k)||'';
  const keys=['stage','dormant','minCompletedOrders','maxCompletedOrders','minCompletedValue','maxCompletedValue','lastPurchaseFrom','lastPurchaseTo','sort','direction'];
  const [expanded,setExpanded]=useState(()=>keys.some(k=>params.has(k)));
  const field=(key,label,type='number')=><label key={key}>{label}<input aria-label={label} type={type} min={type==='number'?0:undefined} value={value(key)} onChange={e=>onChange({[key]:e.target.value})}/></label>;
  return <details className="customer-lifecycle-filters" open={expanded} onToggle={e=>setExpanded(e.currentTarget.open)}><summary>Phân nhóm và bộ lọc nâng cao</summary><div className="customer-filter-grid">
    <label>Nhóm khách hàng<select aria-label="Nhóm khách hàng" value={value('stage')} onChange={e=>onChange({stage:e.target.value})}><option value="">Tất cả nhóm</option>{Object.entries(lifecycleLabels).map(([k,label])=><option key={k} value={k}>{label}</option>)}</select></label>
    <label>Mua lại<select aria-label="Mua lại" value={value('dormant')} onChange={e=>onChange({dormant:e.target.value})}><option value="">Tất cả</option><option value="true">Lâu chưa mua lại (≥30 ngày)</option><option value="false">Không có nhãn lâu chưa mua lại</option></select></label>
    {field('minCompletedOrders','Số đơn tối thiểu')}{field('maxCompletedOrders','Số đơn tối đa')}{field('minCompletedValue','Giá trị tối thiểu (đ)')}{field('maxCompletedValue','Giá trị tối đa (đ)')}{field('lastPurchaseFrom','Ngày mua từ','date')}{field('lastPurchaseTo','Ngày mua đến','date')}
    <label>Sắp xếp theo<select aria-label="Sắp xếp khách hàng" value={value('sort')||'createdAt'} onChange={e=>onChange({sort:e.target.value})}><option value="createdAt">Ngày đăng ký</option><option value="completedOrders">Số đơn hoàn thành</option><option value="completedValue">Giá trị đơn hoàn thành</option><option value="lastPurchaseAt">Ngày mua gần nhất</option></select></label>
    <label>Thứ tự<select aria-label="Thứ tự khách hàng" value={value('direction')||'desc'} onChange={e=>onChange({direction:e.target.value})}><option value="desc">Giảm dần</option><option value="asc">Tăng dần</option></select></label>
  </div><p>{lifecycleExplanation}</p><button className="admin-action" onClick={()=>onChange(Object.fromEntries(keys.map(k=>[k,''])))}>Xóa bộ lọc phân nhóm</button></details>;
}
