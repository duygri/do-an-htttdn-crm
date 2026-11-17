import React,{useCallback,useMemo,useState} from 'react';
import {adminApi,adminDownload} from './manager-api';
import {useManagerResource} from './useManagerResource';
import {useManagerFilters,managerQuery} from './useManagerFilters';
import './cashflow-report.css';
import './finance-report-extra.css';
import './revenue-chart.css';

const money=value=>new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND',maximumFractionDigits:0}).format(Number(value||0));
const today=()=>new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const defaults=()=>{const now=today();return{fromDate:`${now.slice(0,4)}-01-01`,toDate:now,groupBy:'month'};};
const startOf=value=>new Date(`${value}T00:00:00+07:00`);
const endExclusive=value=>{const date=startOf(value);date.setUTCDate(date.getUTCDate()+1);return date;};
const GROUP_LABELS={day:'ngày',month:'tháng',quarter:'quý',year:'năm'};

export function compactVnd(value){
  const n=Number(value||0),abs=Math.abs(n),sign=n<0?'-':'';
  const fmt=(x,unit)=>`${new Intl.NumberFormat('vi-VN',{maximumFractionDigits:1}).format(x)}${unit}`;
  if(abs>=1e9)return sign+fmt(abs/1e9,' tỷ');
  if(abs>=1e6)return sign+fmt(abs/1e6,' tr');
  if(abs>=1e3)return sign+fmt(abs/1e3,'k');
  return sign+fmt(abs,'');
}

export function niceScale(rawMax){
  if(!(rawMax>0))return{max:0,ticks:[0]};
  const rough=rawMax/4,power=10**Math.floor(Math.log10(rough)),norm=rough/power;
  const step=(norm<=1?1:norm<=2?2:norm<=2.5?2.5:norm<=5?5:10)*power;
  const max=Math.ceil(rawMax/step)*step;
  const ticks=[];
  for(let v=0;v<=max+step*1e-6;v+=step)ticks.push(Math.round(v*100)/100);
  return{max,ticks};
}

const shortPeriod=(period,groupBy)=>{
  if(groupBy==='day')return `${period.slice(8,10)}/${period.slice(5,7)}`;
  if(groupBy==='month')return `${period.slice(5,7)}/${period.slice(2,4)}`;
  if(groupBy==='quarter'){const [year,quarter]=period.split('-Q');return `Q${quarter}/${year.slice(2)}`;}
  return period;
};

export default function RevenueReport(){
  const [filters,change]=useManagerFilters(useMemo(defaults,[]));
  const from=startOf(filters.fromDate),to=endExclusive(filters.toDate);
  const valid=!!filters.fromDate&&!!filters.toDate&&from<to&&(to-from)<=366*5*86400000&&['day','month','quarter','year'].includes(filters.groupBy);
  const fetcher=useCallback(signal=>adminApi('/api/manager/reports/revenue?'+managerQuery({from:from.toISOString(),to:to.toISOString(),groupBy:filters.groupBy}),{signal}),[filters.fromDate,filters.toDate,filters.groupBy]);
  const {data,loading,error,backgroundError,load}=useManagerResource(fetcher,{enabled:valid});
  const [downloadError,setDownloadError]=useState('');
  const query=()=>managerQuery({from:from.toISOString(),to:to.toISOString(),groupBy:filters.groupBy});
  const exportCsv=async()=>{setDownloadError('');try{await adminDownload('/api/manager/reports/revenue.csv?'+query(),'doanh-thu.csv');}catch(e){setDownloadError(e.message);}};
  const exportProducts=async()=>{setDownloadError('');try{await adminDownload('/api/manager/reports/product-revenue.csv?'+query(),'doanh-thu-san-pham.csv');}catch(e){setDownloadError(e.message);}};
  const series=useMemo(()=>data?.series||[],[data]);
  const products=data?.products||[];
  const {max,ticks}=useMemo(()=>niceScale(Math.max(0,...series.flatMap(p=>[Number(p.collected)||0,Number(p.purchaseCost)||0]))),[series]);
  const density=series.length>180?'is-dense':series.length>60?'is-medium':'';
  const minWidth=Math.max(100,series.length*(filters.groupBy==='day'?30:filters.groupBy==='quarter'?64:44));
  const pct=value=>Math.min(100,Math.max(0,Number(value)||0));
  return <section className="cashflow-report" aria-label="Báo cáo doanh thu">
    <p className="cashflow-intro">Doanh thu tính theo tiền đã thu thực tế: PayOS xác nhận PAID hoặc COD đã được Manager xác nhận thu. Chi phí nhập hàng lấy từ phiếu nhập theo ngày hóa đơn; “đã trả nhà cung cấp” là tiền thực chi theo ngày thanh toán.</p>
    <div className="cashflow-filters"><label>Từ ngày<input type="date" value={filters.fromDate} onChange={e=>change({fromDate:e.target.value})}/></label><label>Đến hết ngày<input type="date" value={filters.toDate} onChange={e=>change({toDate:e.target.value})}/></label><label>Nhóm theo<select value={filters.groupBy} onChange={e=>change({groupBy:e.target.value})}><option value="day">Ngày</option><option value="month">Tháng</option><option value="quarter">Quý</option><option value="year">Năm</option></select></label><button type="button" className="admin-action" disabled={!valid} onClick={exportCsv}>Xuất CSV</button></div>
    {downloadError&&<p role="alert">{downloadError}</p>}
    {!valid&&<p role="alert">Chọn khoảng ngày hợp lệ, tối đa 5 năm.</p>}
    {valid&&(error||backgroundError)&&<div role="alert">{data&&'Đang giữ số liệu gần nhất. '}{error||backgroundError} <button className="admin-action" onClick={()=>load(true)}>Thử lại</button></div>}
    {valid&&loading&&!data&&<p role="status">Đang tải báo cáo doanh thu…</p>}
    {valid&&data&&<><div className="cashflow-metrics">{[['Doanh thu đã thu',money(data.collected)],['Chi phí nhập hàng',money(data.purchaseCost)],['Đã trả nhà cung cấp',data.paidToSuppliers!=null?money(data.paidToSuppliers):'—'],['Chênh lệch',money(data.difference)],['Đơn có thu tiền',data.collectedOrders]].map(([label,value])=><article key={label}><h4>{label}</h4><strong>{value}</strong></article>)}</div>
    {Number(data.collectedOrders)===0&&Number(data.purchaseCost)===0&&<p>Không phát sinh thu hoặc chi phí trong kỳ.</p>}
    <p>Đơn PayOS 0 đồng không tính lượt thu. Doanh thu chưa trừ giá vốn của hàng đổi trả; chi phí nhập chỉ ghi nhận theo phiếu nhập đã lập, còn “đã trả nhà cung cấp” là tiền thực chi, không cộng dồn vào chênh lệch để tránh trùng.</p>
    <div className="cashflow-warning"><b>Dữ liệu cũ chưa xác định mốc (toàn thời gian)</b><p>{data.unknownMilestone?.orders||0} đơn · {money(data.unknownMilestone?.amount)}</p><p>Không đưa vào kỳ báo cáo và không đoán ngày thu từ ngày cập nhật đơn.</p></div>
    <h4>Diễn biến doanh thu và chi phí</h4><p>Cột xanh: doanh thu đã thu; cột cam: chi phí nhập hàng. Dùng phím Tab hoặc rê chuột để xem số tiền chính xác từng kỳ; bảng bên dưới hiển thị toàn bộ số liệu.</p>
    <div className="cashflow-legend" aria-hidden="true"><span className="is-collected"><i/>Doanh thu đã thu</span><span className="is-cost"><i/>Chi phí nhập hàng</span></div>
    {!series.length?<p>Chưa có kỳ dữ liệu để vẽ biểu đồ.</p>:<div className={`cashflow-chart ${density} ${filters.groupBy==='day'?'is-day':''}`} style={density==='is-dense'?{'--chart-label':'8px'}:undefined}>
      <div className="cashflow-chart__axis" aria-hidden="true">{ticks.map(t=><span key={t} style={{bottom:`${max?t/max*100:0}%`}}>{compactVnd(t)}</span>)}</div>
      <div className="cashflow-chart__scroll"><div className="cashflow-chart__canvas" style={{minWidth:`${minWidth}px`}}>
        <div className="cashflow-chart__grid" aria-hidden="true">{ticks.map(t=><span key={t} style={{bottom:`${max?t/max*100:0}%`}}/>)}</div>
        <ol className="cashflow-chart__groups" aria-label={`Biểu đồ doanh thu và chi phí theo ${GROUP_LABELS[filters.groupBy]}`}>{series.map(p=>{
          const collected=Math.max(0,Number(p.collected)||0),cost=Math.max(0,Number(p.purchaseCost)||0);
          const tip=`${p.period}: doanh thu ${money(p.collected)}, chi phí ${money(p.purchaseCost)}, chênh lệch ${money(p.difference)}`;
          return <li key={p.period} className="cashflow-chart__group" tabIndex={0} aria-label={tip} title={tip}>
            <div className="cashflow-chart__plot" aria-hidden="true"><span className="cashflow-chart__bar is-collected" style={{height:`${max?collected/max*100:0}%`}}/><span className="cashflow-chart__bar is-cost" style={{height:`${max?cost/max*100:0}%`}}/></div>
            <span className="cashflow-chart__label" aria-hidden="true">{shortPeriod(p.period,filters.groupBy)}</span>
            <span className="cashflow-chart__tooltip" role="presentation"><b>{p.period}</b><span><i className="is-collected"/>Thu: {money(p.collected)}</span><span><i className="is-cost"/>Chi: {money(p.purchaseCost)}</span><span>Chênh lệch: {money(p.difference)}</span></span>
          </li>;
        })}</ol>
      </div></div>
    </div>}
    <div className="cashflow-table"><table><caption>Số liệu theo {GROUP_LABELS[filters.groupBy]} Việt Nam</caption><thead><tr><th>Kỳ</th><th>Đơn có thu</th><th>Doanh thu</th><th>Chi phí nhập</th><th>Đã trả NCC</th><th>Chênh lệch</th></tr></thead><tbody>{(data.series||[]).map(p=><tr key={p.period}><td>{p.period}</td><td>{p.collectedOrders}</td><td>{money(p.collected)}</td><td>{money(p.purchaseCost)}</td><td>{p.paidToSuppliers!=null?money(p.paidToSuppliers):'—'}</td><td>{money(p.difference)}</td></tr>)}</tbody></table></div>
    <h4>Doanh thu theo từng sản phẩm</h4><p>Toàn bộ sản phẩm phát sinh trong kỳ (đơn đã thu, chưa hủy); doanh thu gộp trước giảm giá cấp đơn và chưa trừ hàng đổi trả. “% đóng góp” tính trên tổng doanh thu gộp của danh sách.</p>
    <div className="cashflow-products-actions"><button type="button" className="admin-action" disabled={!valid} onClick={exportProducts}>Xuất CSV sản phẩm</button></div>
    {!products.length?<p>Chưa có sản phẩm phù hợp trong kỳ.</p>:<div className="cashflow-table cashflow-products-table"><table><caption>Toàn bộ sản phẩm trong kỳ</caption><thead><tr><th>Sản phẩm</th><th>Số lượng</th><th>Doanh thu gộp</th><th>% đóng góp</th></tr></thead><tbody>{products.map(p=><tr key={p.productId}><td>{p.name}</td><td>{p.quantity}</td><td>{money(p.grossItemValue)}</td><td className="cashflow-share">{p.share==null?'—':<><span className="cashflow-share__value">{`${Number(p.share).toFixed(2)}%`}</span><span className="cashflow-share__track" aria-hidden="true"><span className="cashflow-share__fill" style={{width:`${pct(p.share)}%`}}/></span></>}</td></tr>)}</tbody></table></div>}
    <small>Cập nhật {data.updatedAt?new Date(data.updatedAt).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'}):'—'}. Tự cập nhật mỗi 5 giây khi tab đang mở.</small></>}
  </section>;
}
