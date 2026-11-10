import React,{useCallback,useMemo} from 'react';
import {adminApi} from './manager-api';
import {useManagerResource} from './useManagerResource';
import {useManagerFilters,managerQuery} from './useManagerFilters';
import './cashflow-report.css';

const money=value=>new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND',maximumFractionDigits:0}).format(Number(value||0));
const today=()=>new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const defaults=()=>{const now=today();return{fromDate:`${now.slice(0,4)}-01-01`,toDate:now,groupBy:'month'};};
const startOf=value=>new Date(`${value}T00:00:00+07:00`);
const endExclusive=value=>{const date=startOf(value);date.setUTCDate(date.getUTCDate()+1);return date;};

export default function RevenueReport(){
  const [filters,change]=useManagerFilters(useMemo(defaults,[]));
  const from=startOf(filters.fromDate),to=endExclusive(filters.toDate);
  const valid=!!filters.fromDate&&!!filters.toDate&&from<to&&(to-from)<=366*5*86400000&&['day','month','quarter','year'].includes(filters.groupBy);
  const fetcher=useCallback(signal=>adminApi('/api/manager/reports/revenue?'+managerQuery({from:from.toISOString(),to:to.toISOString(),groupBy:filters.groupBy}),{signal}),[filters.fromDate,filters.toDate,filters.groupBy]);
  const {data,loading,error,backgroundError,load}=useManagerResource(fetcher,{enabled:valid});
  const max=Math.max(1,...(data?.series||[]).flatMap(p=>[Number(p.collected),Number(p.purchaseCost)]));
  return <section className="cashflow-report" aria-label="Báo cáo doanh thu"><h3>Doanh thu</h3><p>Doanh thu tính theo tiền đã thu thực tế: PayOS xác nhận PAID hoặc COD đã được Manager xác nhận thu. Chi phí lấy từ phiếu nhập hàng theo ngày hóa đơn nhà cung cấp.</p>
    <div className="cashflow-filters"><label>Từ ngày<input type="date" value={filters.fromDate} onChange={e=>change({fromDate:e.target.value})}/></label><label>Đến hết ngày<input type="date" value={filters.toDate} onChange={e=>change({toDate:e.target.value})}/></label><label>Nhóm theo<select value={filters.groupBy} onChange={e=>change({groupBy:e.target.value})}><option value="day">Ngày</option><option value="month">Tháng</option><option value="quarter">Quý</option><option value="year">Năm</option></select></label></div>
    {!valid&&<p role="alert">Chọn khoảng ngày hợp lệ, tối đa 5 năm.</p>}
    {valid&&(error||backgroundError)&&<div role="alert">{data&&'Đang giữ số liệu gần nhất. '}{error||backgroundError} <button className="admin-action" onClick={()=>load(true)}>Thử lại</button></div>}
    {valid&&loading&&!data&&<p role="status">Đang tải báo cáo doanh thu…</p>}
    {valid&&data&&<><div className="cashflow-metrics">{[['Doanh thu đã thu',money(data.collected)],['Chi phí nhập hàng',money(data.purchaseCost)],['Chênh lệch',money(data.difference)],['Đơn có thu tiền',data.collectedOrders]].map(([label,value])=><article key={label}><h4>{label}</h4><strong>{value}</strong></article>)}</div>
    {Number(data.collectedOrders)===0&&Number(data.purchaseCost)===0&&<p>Không phát sinh thu hoặc chi phí trong kỳ.</p>}
    <p>Đơn PayOS 0 đồng không tính lượt thu. Doanh thu chưa trừ giá vốn của hàng đổi trả; chi phí nhập chỉ ghi nhận theo phiếu nhập đã lập.</p>
    <div className="cashflow-warning"><b>Dữ liệu cũ chưa xác định mốc (toàn thời gian)</b><p>{data.unknownMilestone?.orders||0} đơn · {money(data.unknownMilestone?.amount)}</p><p>Không đưa vào kỳ báo cáo và không đoán ngày thu từ ngày cập nhật đơn.</p></div>
    <h4>Diễn biến doanh thu và chi phí</h4><p>Thanh xanh: doanh thu; thanh cam: chi phí nhập. Bảng hiển thị số tiền chính xác.</p><div className="cashflow-series">{(data.series||[]).map(p=><div key={p.period}><span>{p.period}</span><div aria-hidden="true"><i style={{width:`${Math.max(0,Number(p.collected))/max*100}%`}}/><i className="is-refund" style={{width:`${Math.max(0,Number(p.purchaseCost))/max*100}%`}}/></div><small>Thu {money(p.collected)} · Chi {money(p.purchaseCost)}</small></div>)}</div>
    <div className="cashflow-table"><table><caption>Số liệu theo {({day:'ngày',month:'tháng',quarter:'quý',year:'năm'})[filters.groupBy]} Việt Nam</caption><thead><tr><th>Kỳ</th><th>Đơn có thu</th><th>Doanh thu</th><th>Chi phí nhập</th><th>Chênh lệch</th></tr></thead><tbody>{(data.series||[]).map(p=><tr key={p.period}><td>{p.period}</td><td>{p.collectedOrders}</td><td>{money(p.collected)}</td><td>{money(p.purchaseCost)}</td><td>{money(p.difference)}</td></tr>)}</tbody></table></div>
    <h4>Top 10 sản phẩm theo số lượng</h4><p>Chỉ đơn đã thu, chưa hủy; giá trị sản phẩm trước giảm giá cấp đơn và chưa trừ hàng đổi trả.</p>
    {!data.topProducts?.length?<p>Chưa có sản phẩm phù hợp trong kỳ.</p>:<div className="cashflow-table"><table><thead><tr><th>Sản phẩm</th><th>Số lượng</th><th>Giá trị trước giảm</th></tr></thead><tbody>{data.topProducts.map(p=><tr key={p.productId}><td>{p.name}</td><td>{p.quantity}</td><td>{money(p.grossItemValue)}</td></tr>)}</tbody></table></div>}
    <small>Cập nhật {data.updatedAt?new Date(data.updatedAt).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'}):'—'}. Tự cập nhật mỗi 5 giây khi tab đang mở.</small></>}
  </section>;
}
