import React, { useEffect, useMemo, useState } from 'react';
import { api, endpoints } from './api';

const nav = [
  ['dashboard', '⌂', 'Tổng quan'], ['users', '♙', 'Khách hàng'], ['products', '▦', 'Sản phẩm'],
  ['orders', '▤', 'Đơn hàng'], ['feedback', '♡', 'Phản hồi'], ['surveys', '☑', 'Khảo sát'], ['reports', '◒', 'Báo cáo'],
];
const money = (n) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(Number(n || 0));
const date = (value) => value ? new Date(value).toLocaleDateString('vi-VN') : '—';

function App() {
  const [active, setActive] = useState('dashboard');
  const [query, setQuery] = useState('');
  const [data, setData] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = async (section = active) => {
    setLoading(true); setError('');
    try {
      const calls = section === 'dashboard' || section === 'reports'
        ? [api(endpoints.revenue), api(endpoints.userReport), api(endpoints.surveyStats), api(endpoints.orders), api(endpoints.products())]
        : [api(['users', 'products'].includes(section) ? endpoints[section](query) : endpoints[section])];
      const result = await Promise.all(calls);
      if (section === 'dashboard') setData({ revenue: result[0], userReport: result[1], surveyStats: result[2], orders: result[3], products: result[4] });
      else if (section === 'reports') setData({ reports: { ...result[0], ...result[1], ...result[2] } });
      else setData({ [section]: result[0] });
    } catch (e) { setError(e.message); } finally { setLoading(false); }
  };
  useEffect(() => { load(active); }, [active]);
  const title = nav.find(([id]) => id === active)?.[2] || 'Tổng quan';
  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><span className="brand-mark">✦</span><span>NovaCRM</span></div>
      <div className="workspace-label">WORKSPACE</div>
      <nav>{nav.map(([id, icon, label]) => <button key={id} className={active === id ? 'nav-item active' : 'nav-item'} onClick={() => setActive(id)}><span className="nav-icon">{icon}</span>{label}{id === 'feedback' && <span className="nav-badge">4</span>}</button>)}</nav>
      <div className="sidebar-bottom"><button className="nav-item"><span className="nav-icon">⚙</span>Cài đặt</button><div className="profile"><div className="avatar">AD</div><div><strong>Admin User</strong><small>Quản trị viên</small></div><span className="dots">•••</span></div></div>
    </aside>
    <main className="main-content">
      <header className="topbar"><div className="crumb">Workspace <span>/</span> <b>{title}</b></div><div className="top-actions"><button className="icon-button">⌕</button><button className="icon-button notification">♧<i></i></button><div className="avatar small">AD</div></div></header>
      <section className="page-content"><div className="page-heading"><div><p className="eyebrow">THỨ NĂM, 17 THÁNG 9, 2026</p><h1>{active === 'dashboard' ? 'Chào buổi sáng, Admin 👋' : title}</h1><p className="subheading">{active === 'dashboard' ? 'Đây là những gì đang diễn ra với hệ thống của bạn hôm nay.' : `Quản lý và theo dõi ${title.toLowerCase()} trong hệ thống.`}</p></div><button className="primary-button" onClick={() => load(active)}>↻ <span>Làm mới dữ liệu</span></button></div>
        {error && <div className="error-banner">Không thể tải dữ liệu: {error}. Kiểm tra backend tại {import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}.</div>}
        {loading ? <Loading /> : active === 'dashboard' ? <Dashboard data={data} onNavigate={setActive} /> : <Section id={active} data={data[active]} query={query} setQuery={setQuery} onSearch={() => load(active)} />}
      </section>
    </main>
  </div>;
}

function Loading() { return <div className="loading"><div className="spinner"></div><p>Đang tải dữ liệu từ server...</p></div>; }
function Stat({ label, value, detail, tone }) { return <div className="stat-card"><div className={`stat-icon ${tone}`}>{tone === 'purple' ? '◒' : tone === 'green' ? '↗' : tone === 'orange' ? '▤' : '♙'}</div><div className="stat-label">{label}</div><strong>{value}</strong><span className={detail?.startsWith('-') ? 'trend down' : 'trend'}>{detail}</span></div>; }
function Dashboard({ data, onNavigate }) {
  const orders = data.orders?.content || [], products = data.products?.content || [];
  return <>
    <div className="stats-grid"><Stat label="TỔNG DOANH THU" value={money(data.revenue?.totalRevenue)} detail="+12.5% so với tháng trước" tone="purple"/><Stat label="KHÁCH HÀNG" value={(data.userReport?.customers || 0).toLocaleString('vi-VN')} detail="+8.2% so với tháng trước" tone="green"/><Stat label="ĐƠN HÀNG" value={(data.revenue?.orders || 0).toLocaleString('vi-VN')} detail="+5.7% so với tháng trước" tone="orange"/><Stat label="KHẢO SÁT ĐANG CHẠY" value={data.surveyStats?.published || 0} detail="Cần chú ý trong tuần này" tone="blue"/></div>
    <div className="content-grid"><div className="panel chart-panel"><div className="panel-heading"><div><h2>Doanh thu</h2><p>Tổng quan doanh thu theo thời gian</p></div><select><option>6 tháng qua</option><option>Năm nay</option></select></div><div className="chart"><div className="y-labels"><span>120M</span><span>80M</span><span>40M</span><span>0</span></div><div className="chart-area"><div className="grid-lines"><i></i><i></i><i></i><i></i></div><div className="bars">{['T1','T2','T3','T4','T5','T6'].map((m,i)=><div className="bar-col" key={m}><div className="bar" style={{height:`${[42,58,48,76,65,88][i]}%`}}></div><small>{m}</small></div>)}</div></div></div></div><div className="panel survey-panel"><div className="panel-heading"><div><h2>Khảo sát</h2><p>Trạng thái khảo sát</p></div><button className="text-button" onClick={() => onNavigate('surveys')}>Xem tất cả →</button></div><div className="donut-wrap"><div className="donut"><strong>{data.surveyStats?.published || 0}</strong><small>Đang chạy</small></div><div className="legend"><span><i className="dot purple"></i>Đã xuất bản <b>{data.surveyStats?.published || 0}</b></span><span><i className="dot gray"></i>Bản nháp <b>{data.surveyStats?.draft || 0}</b></span></div></div></div></div>
    <div className="content-grid lower"><div className="panel"><div className="panel-heading"><div><h2>Đơn hàng gần đây</h2><p>Các giao dịch mới nhất</p></div><button className="text-button" onClick={() => onNavigate('orders')}>Xem tất cả →</button></div><OrderTable orders={orders.slice(0,5)} /></div><div className="panel"><div className="panel-heading"><div><h2>Sản phẩm bán chạy</h2><p>Theo doanh thu</p></div><button className="text-button" onClick={() => onNavigate('products')}>Quản lý →</button></div><ProductList products={products.slice(0,5)} /></div></div>
  </>;
}
function Section({ id, data, query, setQuery, onSearch }) { const rows = data?.content || []; const search = ['users','products'].includes(id); return <div className="panel full-panel"><div className="toolbar"><div><h2>{labelFor(id)}</h2><p>{data?.totalElements ?? rows.length} bản ghi từ API</p></div>{search && <div className="search"><span>⌕</span><input value={query} onChange={e => setQuery(e.target.value)} onKeyDown={e => e.key === 'Enter' && onSearch()} placeholder={`Tìm ${id === 'users' ? 'khách hàng' : 'sản phẩm'}...`}/><button onClick={onSearch}>Tìm</button></div>}</div>{id === 'orders' ? <OrderTable orders={rows} detailed /> : id === 'products' ? <ProductList products={rows} table /> : id === 'users' ? <UserTable users={rows} /> : id === 'feedback' ? <FeedbackTable rows={rows} /> : id === 'surveys' ? <SurveyTable rows={rows} /> : <ReportCards data={data} />}</div>; }
const labelFor = (id) => ({users:'Khách hàng',products:'Sản phẩm & tồn kho',orders:'Đơn hàng',feedback:'Phản hồi khách hàng',surveys:'Quản lý khảo sát',reports:'Báo cáo & phân tích'})[id];
function Status({ children }) { return <span className={`status ${String(children).toLowerCase().replaceAll('_','-')}`}>{String(children || '—').replaceAll('_',' ')}</span>; }
function OrderTable({ orders, detailed }) { return <div className="table-wrap"><table><thead><tr><th>MÃ ĐƠN</th><th>KHÁCH HÀNG</th><th>TỔNG TIỀN</th><th>TRẠNG THÁI</th><th>NGÀY TẠO</th></tr></thead><tbody>{orders.map(o => <tr key={o.id}><td><b>#{o.orderCode || o.id}</b></td><td>{o.customer?.fullName || o.customer?.email || 'Khách hàng'}</td><td className="amount">{money(o.totalAmount)}</td><td><Status>{o.status}</Status></td><td>{date(o.createdAt)}</td></tr>)}{!orders.length && <Empty />}</tbody></table></div>; }
function ProductList({ products, table }) { if (table) return <div className="table-wrap"><table><thead><tr><th>SẢN PHẨM</th><th>DANH MỤC</th><th>GIÁ</th><th>TỒN KHO</th><th>TRẠNG THÁI</th></tr></thead><tbody>{products.map(p=><tr key={p.id}><td><div className="product-name"><span className="product-thumb">{p.name?.[0] || 'P'}</span><b>{p.name}</b></div></td><td>{p.category || 'Chưa phân loại'}</td><td className="amount">{money(p.price)}</td><td><b className={p.stock < 10 ? 'low-stock' : ''}>{p.stock}</b></td><td><Status>{p.active ? 'ACTIVE' : 'INACTIVE'}</Status></td></tr>)}{!products.length && <Empty />}</tbody></table></div>; return <div className="mini-list">{products.map(p=><div className="mini-row" key={p.id}><span className="product-thumb">{p.name?.[0] || 'P'}</span><div><b>{p.name || 'Sản phẩm'}</b><small>{p.category || 'Chưa phân loại'}</small></div><strong>{money(p.price)}</strong></div>)}{!products.length && <p className="empty">Chưa có sản phẩm.</p>}</div>; }
function UserTable({ users }) { return <div className="table-wrap"><table><thead><tr><th>KHÁCH HÀNG</th><th>EMAIL</th><th>SỐ ĐIỆN THOẠI</th><th>TRẠNG THÁI</th><th>NGÀY THAM GIA</th></tr></thead><tbody>{users.map(u=><tr key={u.id}><td><div className="product-name"><span className="avatar table-avatar">{(u.fullName || 'KH').slice(0,2).toUpperCase()}</span><b>{u.fullName}</b></div></td><td>{u.email}</td><td>{u.phone || '—'}</td><td><Status>{u.locked ? 'LOCKED' : 'ACTIVE'}</Status></td><td>{date(u.createdAt)}</td></tr>)}{!users.length && <Empty />}</tbody></table></div>; }
function FeedbackTable({ rows }) { return <div className="table-wrap"><table><thead><tr><th>KHÁCH HÀNG</th><th>SẢN PHẨM</th><th>ĐÁNH GIÁ</th><th>NỘI DUNG</th><th>TRẠNG THÁI</th></tr></thead><tbody>{rows.map(f=><tr key={f.id}><td>{f.customer?.fullName || f.customer?.email || '—'}</td><td>{f.product?.name || '—'}</td><td className="rating">{'★'.repeat(f.rating)}<span>{'★'.repeat(5-f.rating)}</span></td><td className="truncate">{f.comment || '—'}</td><td><Status>{f.status}</Status></td></tr>)}{!rows.length && <Empty />}</tbody></table></div>; }
function SurveyTable({ rows }) { return <div className="table-wrap"><table><thead><tr><th>KHẢO SÁT</th><th>MÔ TẢ</th><th>SỐ CÂU HỎI</th><th>TRẠNG THÁI</th><th>NGÀY TẠO</th></tr></thead><tbody>{rows.map(s=><tr key={s.id}><td><b>{s.title}</b></td><td className="truncate">{s.description || '—'}</td><td>{s.questions?.length || 0}</td><td><Status>{s.status}</Status></td><td>{date(s.createdAt)}</td></tr>)}{!rows.length && <Empty />}</tbody></table></div>; }
function ReportCards({ data }) { return <div className="report-grid"><div className="report-card"><span>Doanh thu tổng</span><strong>{money(data?.totalRevenue)}</strong><small>Từ các đơn hàng không bị hủy</small></div><div className="report-card"><span>Khách hàng</span><strong>{data?.customers || 0}</strong><small>{data?.lockedCustomers || 0} tài khoản đang khóa</small></div><div className="report-card"><span>Tỷ lệ khảo sát</span><strong>{data?.published || 0} đang chạy</strong><small>{data?.draft || 0} bản nháp cần hoàn thiện</small></div></div>; }
function Empty() { return <tr><td colSpan="5" className="empty">Chưa có dữ liệu.</td></tr>; }

export default App;
