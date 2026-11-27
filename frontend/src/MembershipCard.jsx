import React,{useEffect,useState} from 'react';
import {api} from './api';
import './membership.css';
export const tierLabels={BRONZE:'Đồng',SILVER:'Bạc',GOLD:'Vàng',DIAMOND:'Kim cương'};
export const money=value=>Number(value||0).toLocaleString('vi-VN')+' đ';
export function MembershipBadge({membership}){return membership?<span className={'membership-badge tier-'+membership.tier?.toLowerCase()}>Hạng {membership.label||tierLabels[membership.tier]} · {membership.discountPercent}%</span>:null;}
export default function MembershipCard({membership}){
 const [data,setData]=useState(membership),[error,setError]=useState(''),[retry,setRetry]=useState(0);
 useEffect(()=>{if(membership){setData(membership);return;}let live=true;setError('');api('/api/account/membership').then(value=>{if(live)setData(value);}).catch(reason=>{if(live)setError(reason.message);});return()=>{live=false;};},[membership,retry]);
 return <section className="membership-card" aria-label="Hạng thành viên"><h3>Hạng thành viên</h3>{error&&<p role="alert">{error} <button onClick={()=>setRetry(v=>v+1)}>Thử lại</button></p>}{!data&&!error&&<p role="status">Đang tải hạng thành viên…</p>}{data&&<><MembershipBadge membership={data}/><p>Tự động giảm {data.discountPercent}% mỗi đơn trước khi áp dụng voucher.</p><p>Giá trị đơn hoàn thành: <strong>{money(data.completedValue)}</strong></p>{data.nextThreshold?<p>Còn {money(data.remainingValue)} để đạt hạng tiếp theo.</p>:<p>Bạn đã đạt hạng cao nhất.</p>}<small>Đồng: dưới 2 triệu · Bạc: từ 2 triệu · Vàng: từ 5 triệu · Kim cương: từ 10 triệu. Tính toàn thời gian trên đơn hoàn thành, chưa trừ hoàn tiền.</small></>}</section>;
}
