import ManagerMyTasks,{MyTaskSummary,CareTaskDetail} from './ManagerMyTasks';
import ManagerNotificationBell from './ManagerNotificationBell';
import { ManagerSyncStatus } from "./useManagerResource";
import './internal-portal.css';
import React, { useEffect, useState } from "react";
const AdminVouchers = React.lazy(() => import('./AdminVouchers'));
const CustomerProfile = React.lazy(() => import('./CustomerProfile'));
const CustomerCare = React.lazy(() => import('./CustomerCare'));
const CareDashboard = React.lazy(() => import('./CareDashboard'));
const SupportTickets = React.lazy(()=>import('./SupportTickets'));
const ManagerRefunds = React.lazy(()=>import('./ManagerRefunds'));
import {
  Activity,
  LayoutDashboard,
  Users as UsersIcon,
  Package,
  ShoppingCart,
  MessageSquare,
  ClipboardList,
  LogOut,
  Menu,
  X,
  PanelLeftClose,
  PanelLeftOpen,
  Sun,
  Moon,
  ExternalLink,
  DollarSign,
  TicketPercent
} from "lucide-react";
import { Dashboard } from "./manager/Dashboard";
import { Users } from "./manager/Customers";
import { Products } from "./manager/Products";
import { Orders } from "./manager/Orders";
import { Feedback } from "./manager/Feedback";
import { Surveys } from "./manager/Surveys";
import { Reports } from "./manager/Reports";
import { PanelLoading } from "./manager/shared";
export { Reports } from "./manager/Reports";
export { useAdminList } from "./manager/shared";
import {
  adminRefreshSession,
  adminSignIn,
  adminSignOut,
} from "./manager-api";

const navItems = [
  ["dashboard", "Tổng quan"],
  ["users", "Khách hàng"],
  ["care", "Chăm sóc khách hàng"],
  ["my-tasks", "Việc của tôi"],
  ["care-dashboard", "Hiệu quả chăm sóc"],
  ["products", "Sản phẩm"],
  ["orders", "Đơn hàng"],
  ["refunds", "Hoàn tiền"],
  ["feedback", "Phản hồi"],
  ["support-tickets", "Phiếu hỗ trợ"],
  ["surveys", "Khảo sát"],
  ["reports", "Báo cáo"],
  ["vouchers", "Mã giảm giá"],
];
const validSections = new Set(navItems.map(([key]) => key));
const navIcons = {
  dashboard: LayoutDashboard,
  users: UsersIcon,
  care: ClipboardList,
  'my-tasks': ClipboardList,
  'care-dashboard': Activity,
  products: Package,
  orders: ShoppingCart,
  refunds: DollarSign,
  feedback: MessageSquare,
  'support-tickets': MessageSquare,
  surveys: ClipboardList,
  reports: Activity,
  vouchers: TicketPercent,
};
const userOrigin = import.meta.env.VITE_USER_ORIGIN || "http://localhost:5173";
const normalizeAdminPath = (value) => {
  if (value === "/manager/login") return value;
  if (/^\/manager\/users\/[^/]+$/.test(value)) return value;
  const key = value.replace(/^\/manager\/?/, "").split("/")[0] || "dashboard";
  return validSections.has(key)
    ? key === "dashboard"
      ? "/manager"
      : `/manager/${key}`
    : "/manager";
};

export default function ManagerApp() {
  const [path, setPath] = useState(window.location.pathname + window.location.search);
  const [admin, setAdmin] = useState(null);
  const [checking, setChecking] = useState(true);
  const [sessionError,setSessionError]=useState('');
  const [notice, setNotice] = useState(null);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(
    () => {
      try {
        return window.localStorage.getItem("manager-sidebar-collapsed") === "true";
      } catch {
        return false;
      }
    }
  );
  const [theme, setTheme] = useState(
    () => window.localStorage.getItem("manager-theme") || "light"
  );
  const section =
    path === "/manager/login"
      ? "login"
      : path.split('?')[0].replace(/^\/manager\/?/, "").split("/")[0] || "dashboard";
  const profileMatch = path.split('?')[0].match(/^\/manager\/users\/([^/]+)$/);
  const go = (next, state = {}) => {
    const target = next === "dashboard" ? "/manager" : `/manager/${next}`;
    window.history.pushState(state, "", target);
    window.dispatchEvent(new Event('manager:navigation'));
    setPath(target);
    setMobileMenuOpen(false);
  };
  const notify = (message, type = "success") => {
    setNotice({ message, type });
    window.setTimeout(() => setNotice(null), 3500);
  };

  useEffect(() => {
    window.localStorage.setItem("manager-theme", theme);
  }, [theme]);
  useEffect(() => {
    try {
      window.localStorage.setItem(
        "manager-sidebar-collapsed",
        String(sidebarCollapsed)
      );
    } catch {
      // Keep the sidebar usable in memory when persistence is unavailable.
    }
  }, [sidebarCollapsed]);
  useEffect(() => {
    const onPop = () => {
      const current = window.location.pathname;
      const normalized = normalizeAdminPath(current);
      if (current !== normalized)
        window.history.replaceState({}, "", normalized + window.location.search);
      setPath(normalized + window.location.search);
    };
    onPop();
    window.addEventListener("popstate", onPop);
    adminRefreshSession()
      .then((session) => {
        if (session?.manager) setAdmin(session.manager);
        else {window.history.replaceState({},'', '/manager/login');setPath('/manager/login');}
      })
      .catch(e=>setSessionError(e.message))
      .finally(() => setChecking(false));
    return () => window.removeEventListener("popstate", onPop);
  }, []);
  useEffect(() => {
    const expired = () => {
      setAdmin(null);
      window.history.replaceState({}, '', '/manager/login');
      setPath('/manager/login');
      setNotice({ message: 'Phiên quản lý đã hết hạn. Vui lòng đăng nhập lại.', type: 'error' });
    };
    window.addEventListener('manager:session-expired', expired);
    return () => window.removeEventListener('manager:session-expired', expired);
  }, []);
  if (checking)
    return <div className="admin-loading">Đang kiểm tra phiên quản trị...</div>;
  if(sessionError)return <div role="alert">{sessionError}<button onClick={()=>window.location.reload()}>Thử lại</button></div>;
  if (!admin || path === "/manager/login")
    return (
      <AdminLogin
        theme={theme}
        onAuthenticated={(session) => {
          setAdmin(session.manager);
          go("dashboard");
        }}
        onNotice={notify}
        error={notice?.message}
      />
    );
  return (
    <div className={`admin-shell manager-shell${profileMatch ? ' manager-profile-page' : ''}`} data-theme={theme} onKeyDown={event=>{if(['DIV','SPAN'].includes(event.target.tagName)&&event.target.getAttribute('role')==='button'&&['Enter',' '].includes(event.key)){event.preventDefault();event.target.click();}}}>
      <button
        className="admin-mobile-toggle"
        aria-label={mobileMenuOpen ? "Đóng menu quản lý" : "Mở menu quản lý"}
        aria-expanded={mobileMenuOpen}
        onClick={() => setMobileMenuOpen((value) => !value)}
        onKeyDown={event => { if (event.key === 'Escape') setMobileMenuOpen(false); }}
      >
        {mobileMenuOpen ? <X size={18} /> : <Menu size={18} />}
      </button>
      {mobileMenuOpen && (
        <button
          className="admin-sidebar-backdrop"
          aria-label="Đóng menu"
          onClick={() => setMobileMenuOpen(false)}
        />
      )}
      <aside id="manager-sidebar" className={`admin-sidebar${sidebarCollapsed ? " is-collapsed" : ""}${mobileMenuOpen ? " is-open" : ""}`} onKeyDown={event => { if (event.key === 'Escape') setMobileMenuOpen(false); }}>
        <div className="admin-brand">
          <span className="admin-brand-mark">
            <Activity size={18} />
          </span>
          <div className="admin-brand-copy">
            ANH LỚN<small>Quản lý cửa hàng · CRM</small>
          </div>
          <button
            type="button"
            className="admin-collapse-toggle"
            aria-label={sidebarCollapsed ? "Mở rộng thanh điều hướng" : "Thu gọn thanh điều hướng"}
            aria-expanded={!sidebarCollapsed}
            aria-controls="manager-sidebar"
            title={sidebarCollapsed ? "Mở rộng thanh điều hướng" : "Thu gọn thanh điều hướng"}
            onClick={() => setSidebarCollapsed((value) => !value)}
          >
            {sidebarCollapsed ? (
              <PanelLeftOpen size={16} aria-hidden="true" />
            ) : (
              <PanelLeftClose size={16} aria-hidden="true" />
            )}
          </button>
        </div>
        <nav aria-label="Chức năng quản lý">
          {navItems.map(([key, label]) => {
            const Icon = navIcons[key];
            return (
              <button
                key={key}
                className={section === key ? "active" : ""}
                aria-current={section === key ? "page" : undefined}
                title={sidebarCollapsed ? label : undefined}
                onClick={() => go(key)}
              >
                <Icon size={17} aria-hidden="true" />
                <span className="admin-nav-label">{label}</span>
              </button>
            );
          })}
        </nav>
        <div className="manager-sidebar-footer">
        <div className="admin-profile">
          <span>{admin.fullName?.slice(0, 1)?.toUpperCase() || "A"}</span>
          <div className="admin-profile-copy">
            <b>{admin.fullName}</b>
            <small>{admin.email || "Nhân viên quản lý"}</small>
          </div>
        </div>
        <button
          className="admin-logout"
          title={sidebarCollapsed ? "Đăng xuất" : undefined}
          onClick={async () => {
            setAdmin(null);
            go("login");
            await adminSignOut();
          }}
        >
          <LogOut size={16} aria-hidden="true" />
          <span className="admin-logout-label">Đăng xuất</span>
        </button>
        </div>
      </aside>
      <main className="admin-main">
        <header className="admin-topbar">
          <div className="admin-topbar-left">
            <span className="admin-breadcrumb">
              Quản lý <span>/</span>{" "}
              <b>
                {navItems.find((item) => item[0] === section)?.[1] ||
                  "Tổng quan"}
              </b>
            </span>
          </div>
          <div className="admin-topbar-actions">
            <ManagerNotificationBell go={go} location={path}/>
            <span className="admin-live-dot">DỮ LIỆU TRỰC TIẾP</span>
            <button
              className="manager-theme-toggle"
              aria-label={`Chuyển sang giao diện ${
                theme === "dark" ? "sáng" : "tối"
              }`}
              onClick={() =>
                setTheme((value) => (value === "dark" ? "light" : "dark"))
              }
            >
              {theme === "dark" ? <Sun size={16} /> : <Moon size={16} />}
              <span>{theme === "dark" ? "Sáng" : "Tối"}</span>
            </button>
            <a
              href={userOrigin}
              onClick={(event) => {
                event.preventDefault();
                window.location.href = userOrigin;
              }}
            >
              Mở storefront <ExternalLink size={14} aria-hidden="true" />
            </a>
          </div>
        </header>
        <div className="admin-page-heading">
          <h1>
            {profileMatch ? 'Hồ sơ khách hàng 360°' : navItems.find((item) => item[0] === section)?.[1] || "Tổng quan"}
          </h1>
          <p>
            {section === "dashboard"
              ? `${new Date().toLocaleDateString("vi-VN", {
                  weekday: "long",
                  day: "numeric",
                  month: "long",
                  year: "numeric",
                })} — Xin chào, ${admin.fullName || "Admin"}`
              : "Quản lý và cập nhật dữ liệu cửa hàng."}
          </p>
        </div>
        <ManagerSyncStatus />
        {(section==='care'||section==='my-tasks')&&new URLSearchParams(path.split('?')[1]).get('taskId')&&<CareTaskDetail key={new URLSearchParams(path.split('?')[1]).get('taskId')} taskId={new URLSearchParams(path.split('?')[1]).get('taskId')} onOpenTask={id=>{const p=new URLSearchParams(path.split('?')[1]);p.set('taskId',id);go(`${section}?${p}`);}} onClose={()=>{const p=new URLSearchParams(path.split('?')[1]);p.delete('taskId');go(`${section}${p.size?'?'+p:''}`);}}/>}
        <React.Suspense fallback={<PanelLoading/>}>
        {section === 'dashboard' && <MyTaskSummary go={go}/>}
        {section === 'my-tasks' && <ManagerMyTasks go={go} location={path}/>}
        {section === "dashboard" && <Dashboard go={go} />}
        {section === "users" && (profileMatch ? <CustomerProfile key={profileMatch[1]} customerId={profileMatch[1]} location={path} go={go} /> : <Users notify={notify} location={path} go={go} />)}{" "}
        {section === "products" && <Products notify={notify} />}{" "}
        {section === "orders" && <Orders notify={notify} />}{" "}
        {section === "refunds" && <ManagerRefunds/>}
        {section === "feedback" && <Feedback notify={notify} />}{" "}
        {section==='support-tickets'&&<SupportTickets manager location={path} navigate={go}/>}
        {section === "surveys" && <Surveys notify={notify} location={path} go={go} />}
        {section === "reports" && <Reports go={go} />}
        {section === "vouchers" && <AdminVouchers />}
        {section === "care" && <CustomerCare go={go} location={path}/>}
        {section === "care-dashboard" && <CareDashboard go={go} location={path}/>}
        </React.Suspense>
      </main>
      {notice && (
        <div className={`admin-toast ${notice.type}`}>{notice.message}</div>
      )}
    </div>
  );
}
function AdminLogin({ theme, onAuthenticated, onNotice, error }) {
  const [form, setForm] = useState({ email: "", password: "" });
  const [busy, setBusy] = useState(false);
  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    try {
      const session = await adminSignIn(form);
      onAuthenticated(session);
    } catch (error) {
      onNotice(error.message, "error");
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="admin-login manager-login" data-theme={theme}>
      <div className="admin-login-card">
        <div className="admin-mark">
          AL<span>MANAGER</span>
        </div>
        <span className="admin-eyebrow">ANH LỚN SHOP / PRIVATE AREA</span>
        <h1>
          Không gian
          <br />
          <i>quản lý vận hành.</i>
        </h1>
        <p>Đăng nhập để quản lý cửa hàng và dữ liệu khách hàng.</p>
        {error&&<p role="alert">{error}</p>}
        <form onSubmit={submit}>
          <label>
            EMAIL
            <input
              type="email"
              required
              value={form.email}
              onChange={(event) =>
                setForm({ ...form, email: event.target.value })
              }
            />
          </label>
          <label>
            MẬT KHẨU
            <input
              type="password"
              required
              value={form.password}
              onChange={(event) =>
                setForm({ ...form, password: event.target.value })
              }
            />
          </label>
          <button className="admin-primary" disabled={busy}>
            {busy ? "ĐANG ĐĂNG NHẬP..." : "ĐĂNG NHẬP QUẢN LÝ →"}
          </button>
        </form>
        <small className="admin-dev-note">
          Tài khoản quản lý do Admin hệ thống cấp.
        </small>
        <a href={userOrigin}>← Về storefront</a>
      </div>
    </div>
  );
}
