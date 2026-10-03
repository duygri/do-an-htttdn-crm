import AdminVouchers from "./AdminVouchers";
import { useManagerResource, useManagerList, ManagerSyncStatus } from "./useManagerResource";
import {CustomerCreate,SurveyAudience} from './ManagerTools';
import './internal-portal.css';
import { TicketPercent } from "lucide-react";
import { canAdvanceOrder, isFinalOrder } from './order-transitions';
import SurveyRewardEditor, { RewardFields, emptyReward } from './SurveyRewardEditor';
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
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
  Sun,
  Moon,
  ExternalLink,
  DollarSign,
  ArrowRight,
  RefreshCw,
  CircleAlert,
  Inbox,
  Search,
  Plus,
  Filter,
  CheckCircle2,
  TrendingUp,
  AlertTriangle,
  Eye,
  SlidersHorizontal,
  ChevronRight,
  Sparkles
} from "lucide-react";
import {
  adminApi,
  adminEndpoints,
  adminRefreshSession,
  adminSignIn,
  adminSignOut,
} from "./manager-api";

const money = (value) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(Number(value || 0));
const pageData = (value) => value?.content || [];
const navItems = [
  ["dashboard", "Tổng quan"],
  ["users", "Khách hàng"],
  ["products", "Sản phẩm"],
  ["orders", "Đơn hàng"],
  ["feedback", "Phản hồi"],
  ["surveys", "Khảo sát"],
  ["reports", "Báo cáo"],
  ["vouchers", "Mã giảm giá"],
];
const validSections = new Set(navItems.map(([key]) => key));
const navIcons = {
  dashboard: LayoutDashboard,
  users: UsersIcon,
  products: Package,
  orders: ShoppingCart,
  feedback: MessageSquare,
  surveys: ClipboardList,
  reports: Activity,
  vouchers: TicketPercent,
};
const statusLabels = {
  PENDING_PAYMENT: "Chờ thanh toán",
  PENDING: "Chờ xác nhận",
  CONFIRMED: "Đã xác nhận",
  SHIPPED: "Đang giao",
  DELIVERED: "Đã giao — chờ khách xác nhận",
  COMPLETED: "Khách đã nhận hàng",
  CANCELLED: "Đã hủy",
};
const statusColors = {
  PENDING_PAYMENT: "#f59e0b",
  PENDING: "#d97706",
  CONFIRMED: "#8b5cf6",
  SHIPPED: "#3b82f6",
  DELIVERED: "#10b981",
  COMPLETED: "#14b8a6",
  CANCELLED: "#ef4444",
};
const orderStatuses = Object.entries(statusLabels);
const orderStatusOptions = (current) => [
  [
    current,
    statusLabels[current] ||
      ({
        PENDING: "Chờ xác nhận",
        PREPARING: "Đang chuẩn bị",
        DELIVERING: "Đang giao",
      })[current] ||
      current,
  ],
  ...orderStatuses.filter(
    ([value]) => value !== current && canAdvanceOrder(current, value)
  ),
];
const paymentMethodLabels = {
  COD: "Thanh toán khi nhận hàng",
  PAYOS: "Thanh toán trực tuyến",
};
const paymentStatusLabels = {
  COD: "Thanh toán khi nhận hàng",
  PENDING: "Chờ thanh toán",
  PAID: "Đã thanh toán",
  CANCELLED: "Đã hủy",
  EXPIRED: "Hết hạn",
};
const formatDate = (value) =>
  value
    ? new Date(value).toLocaleString("vi-VN", {
        dateStyle: "medium",
        timeStyle: "short",
      })
    : "—";
const userOrigin = import.meta.env.VITE_USER_ORIGIN || "http://localhost:5173";
const normalizeAdminPath = (value) => {
  if (value === "/manager/login") return value;
  const key = value.replace(/^\/manager\/?/, "").split("/")[0] || "dashboard";
  return validSections.has(key)
    ? key === "dashboard"
      ? "/manager"
      : `/manager/${key}`
    : "/manager";
};

export default function ManagerApp() {
  const [path, setPath] = useState(window.location.pathname);
  const [admin, setAdmin] = useState(null);
  const [checking, setChecking] = useState(true);
  const [sessionError,setSessionError]=useState('');
  const [notice, setNotice] = useState(null);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [theme, setTheme] = useState(
    () => window.localStorage.getItem("manager-theme") || "dark"
  );
  const section =
    path === "/manager/login"
      ? "login"
      : path.replace(/^\/manager\/?/, "").split("/")[0] || "dashboard";
  const go = (next) => {
    const target = next === "dashboard" ? "/manager" : `/manager/${next}`;
    window.history.pushState({}, "", target);
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
    const onPop = () => {
      const current = window.location.pathname;
      const normalized = normalizeAdminPath(current);
      if (current !== normalized)
        window.history.replaceState({}, "", normalized);
      setPath(normalized);
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
    <div className="admin-shell" data-theme={theme}>
      <button
        className="admin-mobile-toggle"
        aria-label="Mở menu quản trị"
        aria-expanded={mobileMenuOpen}
        onClick={() => setMobileMenuOpen((value) => !value)}
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
      <aside className={`admin-sidebar ${mobileMenuOpen ? "is-open" : ""}`}>
        <div className="admin-brand">
          <span className="admin-brand-mark">
            <Activity size={18} />
          </span>
          <div>
            ManagerPanel<small>ANH LỚN SHOP</small>
          </div>
        </div>
        <div className="admin-profile">
          <span>{admin.fullName?.slice(0, 1)?.toUpperCase() || "A"}</span>
          <div>
            <b>{admin.fullName}</b>
            <small>{admin.email || "Nhân viên quản lý"}</small>
          </div>
        </div>
        <nav>
          {navItems.map(([key, label]) => {
            const Icon = navIcons[key];
            return (
              <button
                key={key}
                className={section === key ? "active" : ""}
                aria-current={section === key ? "page" : undefined}
                onClick={() => go(key)}
              >
                <Icon size={17} aria-hidden="true" />
                {label}
              </button>
            );
          })}
        </nav>
        <button
          className="admin-logout"
          onClick={async () => {
            setAdmin(null);
            go("login");
            await adminSignOut();
          }}
        >
          <LogOut size={16} aria-hidden="true" />
          Đăng xuất
        </button>
      </aside>
      <main className="admin-main">
        <header className="admin-topbar">
          <div className="admin-topbar-left">
            <span className="admin-breadcrumb">
              Admin <span>/</span>{" "}
              <b>
                {navItems.find((item) => item[0] === section)?.[1] ||
                  "Tổng quan"}
              </b>
            </span>
          </div>
          <div className="admin-topbar-actions">
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
            {navItems.find((item) => item[0] === section)?.[1] || "Tổng quan"}
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
        {section === "dashboard" && <Dashboard go={go} />}
        {section === "users" && <Users notify={notify} />}{" "}
        {section === "products" && <Products notify={notify} />}{" "}
        {section === "orders" && <Orders notify={notify} />}{" "}
        {section === "feedback" && <Feedback notify={notify} />}{" "}
        {section === "surveys" && <Surveys notify={notify} />}
        {section === "reports" && <Reports />}
        {section === "vouchers" && <AdminVouchers />}
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
    <div className="admin-login" data-theme={theme}>
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

const recentOrdersEndpoint = () => adminEndpoints.orders({ size: 100 });
function Dashboard({ go }) {
  const fetchDashboard = useCallback(async signal => {
    const [revenue, users, surveys] = await Promise.all([
      adminApi(adminEndpoints.revenue, { signal }),
      adminApi(adminEndpoints.userReport, { signal }),
      adminApi(adminEndpoints.surveyStats, { signal }),
    ]);
    return { revenue, users, surveys };
  }, []);
  const { data, error, refreshing, load } = useManagerResource(fetchDashboard);
  if (error)
    return (
      <div className="admin-panel admin-error-state">
        <span className="admin-state-icon">
          <CircleAlert size={20} />
        </span>
        <h2>Không tải được dashboard</h2>
        <p>{error}</p>
        <button className="admin-primary" onClick={() => load()}>
          THỬ LẠI
        </button>
      </div>
    );
  if (!data) return <PanelLoading />;
  return (
    <div className="admin-content">
      {/* Dashboard Toolbar with Refresh & Status */}
      <div className="admin-dashboard-toolbar">
        <div className="admin-dashboard-toolbar-left">
          <span className="admin-kpi-headline">CHỈ SỐ HOẠT ĐỘNG TOÀN HỆ THỐNG</span>
        </div>
        <div className="admin-dashboard-toolbar-right">
          <button
            type="button"
            className="admin-btn-refresh"
            disabled={refreshing}
            onClick={() => load(true)}
            title="Đồng bộ dữ liệu mới nhất"
          >
            <RefreshCw size={13} className={refreshing ? "admin-spinner" : ""} />
            <span>{refreshing ? "Đang đồng bộ..." : "Cập nhật dữ liệu"}</span>
          </button>
        </div>
      </div>

      {/* KPI Stat Cards */}
      <div className="admin-stat-grid">
        <Stat
          label="Tổng khách hàng"
          value={data.users.customers ?? "—"}
          icon={UsersIcon}
          color="blue"
          detail="Tài khoản customer trong hệ thống"
          onClick={() => go("users")}
        />
        <Stat
          label="Tổng doanh thu"
          value={money(data.revenue.totalRevenue)}
          icon={DollarSign}
          color="green"
          detail="Doanh thu ghi nhận từ hệ thống"
          onClick={() => go("reports")}
        />
        <Stat
          label="Tổng đơn hàng"
          value={data.revenue.orders ?? "—"}
          icon={ShoppingCart}
          color="violet"
          detail="Tất cả trạng thái đơn hàng"
          onClick={() => go("orders")}
        />
        <Stat
          label="Khảo sát đã phát hành"
          value={data.surveys.published ?? "—"}
          icon={ClipboardList}
          color="amber"
          detail={`${data.surveys.total ?? 0} khảo sát trong hệ thống`}
          onClick={() => go("surveys")}
        />
      </div>

      {/* Operational Alert if Locked Customers Exist */}
      {Number(data.users.lockedCustomers) > 0 && (
        <div className="admin-operational-alert">
          <div className="admin-operational-alert-icon">
            <AlertTriangle size={18} />
          </div>
          <div className="admin-operational-alert-content">
            <strong>Thông báo an toàn & tài khoản</strong>
            <p>Hệ thống ghi nhận {data.users.lockedCustomers} tài khoản khách hàng đang bị khóa do vi phạm hoặc yêu cầu quản trị.</p>
          </div>
          <button type="button" className="admin-operational-alert-btn" onClick={() => go("users")}>
            Kiểm tra tài khoản <ArrowRight size={13} />
          </button>
        </div>
      )}

      {/* Quick Action Navigation Grid */}
      <section className="admin-panel admin-quick-actions-panel">
        <div className="admin-section-heading">
          <div>
            <h2>Lối tắt quản lý</h2>
            <p>Truy cập nhanh các phân hệ nghiệp vụ chính của cửa hàng</p>
          </div>
        </div>
        <div className="admin-quick-grid">
          <div className="admin-quick-card" role="button" tabIndex={0} onClick={() => go("orders")} title="Đến trang xử lý đơn hàng">
            <div className="admin-quick-icon blue">
              <ShoppingCart size={18} />
            </div>
            <div className="admin-quick-text">
              <b>Xử lý đơn hàng</b>
              <small>Theo dõi giao hàng & trạng thái</small>
            </div>
            <ChevronRight size={16} className="admin-quick-arrow" />
          </div>
          <div className="admin-quick-card" role="button" tabIndex={0} onClick={() => go("products")} title="Đến trang quản lý sản phẩm">
            <div className="admin-quick-icon green">
              <Package size={18} />
            </div>
            <div className="admin-quick-text">
              <b>Kho sản phẩm</b>
              <small>Quản lý giá bán & tồn kho</small>
            </div>
            <ChevronRight size={16} className="admin-quick-arrow" />
          </div>
          <div className="admin-quick-card" role="button" tabIndex={0} onClick={() => go("vouchers")} title="Đến trang mã giảm giá">
            <div className="admin-quick-icon violet">
              <TicketPercent size={18} />
            </div>
            <div className="admin-quick-text">
              <b>Mã giảm giá shop</b>
              <small>Khuyến mãi & voucher ưu đãi</small>
            </div>
            <ChevronRight size={16} className="admin-quick-arrow" />
          </div>
          <div className="admin-quick-card" role="button" tabIndex={0} onClick={() => go("users")} title="Đến danh sách khách hàng">
            <div className="admin-quick-icon amber">
              <UsersIcon size={18} />
            </div>
            <div className="admin-quick-text">
              <b>Hồ sơ khách hàng</b>
              <small>Tra cứu danh sách & phân quyền</small>
            </div>
            <ChevronRight size={16} className="admin-quick-arrow" />
          </div>
          <div className="admin-quick-card" role="button" tabIndex={0} onClick={() => go("surveys")} title="Đến trang khảo sát ý kiến">
            <div className="admin-quick-icon cyan">
              <ClipboardList size={18} />
            </div>
            <div className="admin-quick-text">
              <b>Khảo sát ý kiến</b>
              <small>Tạo câu hỏi & xem phản hồi</small>
            </div>
            <ChevronRight size={16} className="admin-quick-arrow" />
          </div>
          <div className="admin-quick-card" role="button" tabIndex={0} onClick={() => go("feedback")} title="Đến trang phản hồi đánh giá">
            <div className="admin-quick-icon rose">
              <MessageSquare size={18} />
            </div>
            <div className="admin-quick-text">
              <b>Phản hồi đánh giá</b>
              <small>Duyệt & trả lời khách hàng</small>
            </div>
            <ChevronRight size={16} className="admin-quick-arrow" />
          </div>
          <div className="admin-quick-card" role="button" tabIndex={0} onClick={() => go("reports")} title="Đến trang báo cáo doanh thu">
            <div className="admin-quick-icon indigo">
              <Activity size={18} />
            </div>
            <div className="admin-quick-text">
              <b>Báo cáo doanh thu</b>
              <small>Biểu đồ & phân tích chỉ số</small>
            </div>
            <ChevronRight size={16} className="admin-quick-arrow" />
          </div>
        </div>
      </section>

      {/* Overview Grid: Surveys & Accounts */}
      <div className="admin-overview-grid">
        <section className="admin-panel admin-survey-overview">
          <div className="admin-section-heading">
            <div>
              <h2>Tổng quan khảo sát</h2>
              <p>Theo trạng thái phát hành</p>
            </div>
            <button className="admin-action" onClick={() => go("surveys")}>
              Quản lý khảo sát <ArrowRight size={14} />
            </button>
          </div>
          {[
            ["Đã phát hành", data.surveys.published, "#3b82f6"],
            ["Bản nháp", data.surveys.draft, "#8b5cf6"],
          ].map(([label, count, color]) => {
            const percent = data.surveys.total
              ? Math.round((Number(count || 0) / data.surveys.total) * 100)
              : 0;
            return (
              <div className="admin-metric-row" key={label}>
                <div>
                  <span className="admin-metric-label">
                    <i style={{ background: color }} />
                    {label}
                  </span>
                  <b>
                    {count ?? 0}{" "}
                    <small className="admin-metric-pct">({percent}%)</small>
                  </b>
                </div>
                <div className="admin-progress">
                  <span
                    style={{
                      width: `${percent}%`,
                      background: color,
                    }}
                  />
                </div>
              </div>
            );
          })}
          <small className="admin-data-note">
            API hiện chưa cung cấp doanh thu theo tháng; không hiển thị biểu đồ
            từ dữ liệu giả.
          </small>
        </section>
        <section className="admin-panel admin-account-overview">
          <div className="admin-section-heading">
            <div>
              <h2>Tài khoản hệ thống</h2>
              <p>Dữ liệu báo cáo hiện tại</p>
            </div>
            <button className="admin-action" onClick={() => go("users")}>
              Quản lý tài khoản <ArrowRight size={14} />
            </button>
          </div>
          <div className="admin-account-total">
            <UsersIcon size={28} />
            <strong>{data.users.customers ?? "—"}</strong>
            <span>khách hàng</span>
          </div>
          <div className="admin-summary-line">
            <span>Nhân viên quản lý</span>
            <b>{data.users.admins ?? "—"}</b>
          </div>
          <div className="admin-summary-line">
            <span>Tài khoản bị khóa</span>
            <b style={{ color: Number(data.users.lockedCustomers) > 0 ? "#ef4444" : "inherit" }}>
              {data.users.lockedCustomers ?? "—"}
            </b>
          </div>
        </section>
      </div>

      {/* Recent Orders Component */}
      <RecentOrders go={go} />
    </div>
  );
}
function RecentOrders({ go }) {
  const { data, loading, error, load } = useAdminList(recentOrdersEndpoint);
  if (loading) return <PanelLoading />;
  if (error)
    return (
      <div className="admin-panel">
        <ListError message={error} onRetry={load} />
      </div>
    );
  const orders = pageData(data);
  const counts = Object.entries(statusLabels).map(([status, label]) => ({
    status,
    label,
    count: orders.filter((order) => order.status === status).length,
  }));
  return (
    <section className="admin-panel admin-recent-orders">
      <div className="admin-section-heading">
        <div>
          <h2>Đơn hàng gần đây</h2>
          <p>Phân bố trạng thái trong {orders.length} đơn mới nhất</p>
        </div>
        <button className="admin-action" onClick={() => go("orders")}>
          Xem tất cả <ArrowRight size={14} />
        </button>
      </div>
      <div className="admin-status-strip">
        {counts.map(({ status, label, count }) => (
          <span
            key={status}
            className="admin-status-chip"
            onClick={() => go("orders")}
            role="button"
            tabIndex={0}
            title={`Lọc đơn hàng: ${label}`}
          >
            <i style={{ background: statusColors[status] }} />
            {label}
            <b>{count}</b>
          </span>
        ))}
      </div>
      <Table headers={["Mã đơn", "Khách hàng", "Tổng tiền", "Trạng thái"]}>
        {orders.slice(0, 5).map((order) => {
          const initials = (order.customer?.fullName || "C").trim().slice(0, 1).toUpperCase();
          return (
            <tr key={order.id} className="admin-table-row-clickable" onClick={() => go("orders")} title="Nhấn để xem chi tiết đơn hàng">
              <td className="admin-code">
                <span className="admin-order-code-badge">#{order.orderCode}</span>
              </td>
              <td>
                <div className="admin-table-customer">
                  <span className="admin-customer-avatar">{initials}</span>
                  <span>{order.customer?.fullName || "Customer"}</span>
                </div>
              </td>
              <td className="admin-order-total-cell">
                <strong>{money(order.totalAmount)}</strong>
              </td>
              <td>
                <OrderBadge status={order.status} />
              </td>
            </tr>
          );
        })}
      </Table>
    </section>
  );
}
function OrderBadge({ status }) {
  return (
    <span className="admin-badge" style={{ color: statusColors[status] }}>
      <span className="admin-badge-dot" />
      {statusLabels[status] || status}
    </span>
  );
}
function Stat({ label, value, icon: Icon, color, detail, onClick }) {
  return (
    <div
      className={`admin-stat ${onClick ? "is-clickable" : ""}`}
      onClick={onClick}
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
      title={onClick ? `Xem chi tiết: ${label}` : undefined}
    >
      <div className={`admin-kpi-icon ${color}`}>
        <Icon size={20} aria-hidden="true" />
      </div>
      <div className="admin-stat-body">
        <span>{label}</span>
        <strong>{value}</strong>
        <small>{detail}</small>
      </div>
      {onClick && <ArrowRight size={14} className="admin-stat-arrow" aria-hidden="true" />}
    </div>
  );
}
function PanelLoading() {
  return (
    <div className="admin-panel admin-loading-panel" role="status">
      <RefreshCw size={20} className="admin-spinner" aria-hidden="true" />
      <p>Đang tải dữ liệu...</p>
    </div>
  );
}
export const useAdminList = useManagerList;

function Table({ headers, children }) {
  const hasRows = React.Children.count(children) > 0;
  return (
    <div className="admin-table-wrap">
      <table>
        <thead>
          <tr>
            {headers.map((header) => (
              <th key={header}>{header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {hasRows ? (
            children
          ) : (
            <tr>
              <td className="admin-table-empty" colSpan={headers.length}>
                <span className="admin-state-icon">
                  <Inbox size={20} />
                </span>
                <b>Chưa có dữ liệu</b>
                <small>Dữ liệu sẽ hiển thị tại đây khi có bản ghi.</small>
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
function ListError({ message, onRetry }) {
  return (
    <div className="admin-list-error">
      <span className="admin-state-icon">
        <CircleAlert size={20} />
      </span>
      <div>
        <b>Không tải được dữ liệu</b>
        <p>{message}</p>
      </div>
      <button className="admin-action" onClick={onRetry}>
        Thử lại
      </button>
    </div>
  );
}

function Users({ notify }) {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [dialog, setDialog] = useState(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const endpoint = useCallback(() => adminEndpoints.users({ page }), [page]);
  const { data, load, loading, error } = useAdminList(endpoint);
  const closeDialog = () => {
    if (!busy) {
      setDialog(null);
      setReason("");
    }
  };
  const update = async (user, locked, lockReason = "") => {
    setBusy(true);
    try {
      await adminApi(adminEndpoints.lockUser(user.id), {
        method: "PATCH",
        body: { locked, reason: locked ? lockReason.trim() : null },
      });
      notify("Đã cập nhật trạng thái tài khoản.");
      setDialog(null);
      setReason("");
      await load();
    } catch (error) {
      notify(error.message, "error");
    } finally {
      setBusy(false);
    }
  };
  const remove = async (user) => {
    setBusy(true);
    try {
      await adminApi(adminEndpoints.deleteUser(user.id), { method: "DELETE" });
      notify("Đã vô hiệu hóa tài khoản. Lịch sử đơn hàng được giữ lại.");
      setDialog(null);
      if (page > 0 && pageData(data).length === 1) setPage(page - 1);
      else await load();
    } catch (error) {
      notify(error.message, "error");
    } finally {
      setBusy(false);
    }
  };
  const users = pageData(data);
  const filteredUsers = useMemo(() => {
    if (!search.trim()) return users;
    const q = search.toLowerCase();
    return users.filter(
      (user) =>
        user.fullName?.toLowerCase().includes(q) ||
        user.email?.toLowerCase().includes(q) ||
        user.phone?.includes(q)
    );
  }, [users, search]);

  if (loading) return <PanelLoading />;
  if (error)
    return (
      <div className="admin-panel">
        <ListError message={error} onRetry={load} />
      </div>
    );
  return (
    <div className="admin-panel">
      <PanelTitle
        title="Khách hàng"
        subtitle="Theo dõi và kiểm soát tài khoản customer."
      />
      <CustomerCreate onSaved={load}/>
      <div className="admin-table-toolbar">
        <div className="admin-search-wrap">
          <Search size={15} className="admin-search-icon" aria-hidden="true" />
          <input
            type="search"
            className="admin-search-input"
            placeholder="Tìm theo tên, email, SĐT..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          {search && (
            <button
              type="button"
              className="admin-search-clear"
              onClick={() => setSearch("")}
            >
              <X size={13} />
            </button>
          )}
        </div>
        <span className="admin-toolbar-count">
          {filteredUsers.length} khách hàng
        </span>
      </div>
      <Table
        headers={["Họ tên", "Email", "Điện thoại", "Trạng thái", "Thao tác"]}
      >
        {filteredUsers.map((user) => (
          <tr key={user.id}>
            <td>
              <b>{user.fullName}</b>
            </td>
            <td>{user.email}</td>
            <td>{user.phone || "—"}</td>
            <td>
              <span
                className={`admin-badge ${user.locked ? "danger" : "success"}`}
              >
                {user.locked ? "Đã khóa" : "Hoạt động"}
              </span>
            </td>
            <td>
              <button
                className="admin-action"
                onClick={() =>
                  user.locked
                    ? update(user, false)
                    : setDialog({ type: "lock", user })
                }
              >
                {user.locked ? "Mở khóa" : "Khóa"}
              </button>
              <button
                className="admin-action danger-text"
                onClick={() => setDialog({ type: "delete", user })}
              >
                Xóa
              </button>
              <CustomerCreate user={user} onSaved={load}/>
            </td>
          </tr>
        ))}
      </Table>
      <PageControls
        page={page}
        totalPages={data?.totalPages || 0}
        totalElements={data?.totalElements}
        onPage={setPage}
      />
      {dialog && (
        <div
          className="admin-dialog-backdrop"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) closeDialog();
          }}
        >
          <div
            className="admin-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="admin-user-dialog-title"
          >
            <h2 id="admin-user-dialog-title">
              {dialog.type === "lock"
                ? "Khóa tài khoản"
                : "Vô hiệu hóa tài khoản"}
            </h2>
            <p>
              <strong>{dialog.user.fullName}</strong> · {dialog.user.email}
            </p>
            {dialog.type === "lock" ? (
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  if (reason.trim()) update(dialog.user, true, reason);
                }}
              >
                <label htmlFor="admin-lock-reason">
                  Lý do khóa gửi đến người dùng
                </label>
                <textarea
                  id="admin-lock-reason"
                  required
                  maxLength={2000}
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  placeholder="Nhập lý do cụ thể để người dùng thấy khi đăng nhập lại"
                />
                <small>{reason.length}/2000 ký tự</small>
                <div className="admin-dialog-actions">
                  <button type="button" onClick={closeDialog} disabled={busy}>
                    Hủy
                  </button>
                  <button
                    className="admin-primary"
                    disabled={busy || !reason.trim()}
                  >
                    {busy ? "Đang lưu..." : "Xác nhận khóa"}
                  </button>
                </div>
              </form>
            ) : (
              <>
                <p>
                  Tài khoản sẽ không thể đăng nhập và biến mất khỏi danh sách.
                  Đơn hàng cũ vẫn được giữ lại.
                </p>
                <div className="admin-dialog-actions">
                  <button onClick={closeDialog} disabled={busy}>
                    Hủy
                  </button>
                  <button
                    className="admin-primary admin-danger-button"
                    disabled={busy}
                    onClick={() => remove(dialog.user)}
                  >
                    {busy ? "Đang xóa..." : "Xóa tài khoản"}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function Products({ notify }) {
  const { data, load, loading, error } = useAdminList(adminEndpoints.products);
  const [search, setSearch] = useState("");
  const [dialogOpen, setDialogOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState(null);
  const [confirmArchive, setConfirmArchive] = useState(null);
  const [form, setForm] = useState({
    name: "",
    category: "",
    price: "",
    stock: 0,
    description: "",
    imageUrl: "",
  });

  const openAdd = () => {
    setEditing(null);
    setForm({
      name: "",
      category: "",
      price: "",
      stock: 0,
      description: "",
      imageUrl: "",
    });
    setDialogOpen(true);
  };

  const openEdit = (product) => {
    setEditing(product.id);
    setForm({
      name: product.name,
      category: product.category || "",
      price: product.price,
      stock: product.stock,
      description: product.description || "",
      imageUrl: product.imageUrl || "",
    });
    setDialogOpen(true);
  };

  const closeDialog = () => {
    if (saving) return;
    setDialogOpen(false);
    setEditing(null);
  };

  const save = async (event) => {
    event.preventDefault();
    setSaving(true);
    try {
      await adminApi(
        editing ? adminEndpoints.product(editing) : "/api/manager/products",
        {
          method: editing ? "PUT" : "POST",
          body: {
            ...form,
            price: Number(form.price),
            stock: Number(form.stock),
          },
        }
      );
      setForm({
        name: "",
        category: "",
        price: "",
        stock: 0,
        description: "",
        imageUrl: "",
      });
      setEditing(null);
      setDialogOpen(false);
      notify(editing ? "Đã cập nhật sản phẩm." : "Đã thêm sản phẩm mới.");
      load();
    } catch (error) {
      notify(error.message, "error");
    } finally {
      setSaving(false);
    }
  };

  const archive = async (id) => {
    try {
      await adminApi(adminEndpoints.product(id), { method: "DELETE" });
      notify("Đã ẩn sản phẩm.");
      setConfirmArchive(null);
      load();
    } catch (error) {
      notify(error.message, "error");
    }
  };

  const products = pageData(data);
  const filteredProducts = useMemo(() => {
    if (!search.trim()) return products;
    const q = search.toLowerCase();
    return products.filter(
      (product) =>
        product.name?.toLowerCase().includes(q) ||
        product.category?.toLowerCase().includes(q)
    );
  }, [products, search]);

  if (loading) return <PanelLoading />;
  if (error)
    return (
      <div className="admin-panel">
        <ListError message={error} onRetry={load} />
      </div>
    );
  return (
    <div className="admin-panel">
      <PanelTitle title="Sản phẩm" subtitle="Quản lý catalog và tồn kho." />

      <div className="admin-table-toolbar">
        <div className="admin-search-wrap">
          <Search size={15} className="admin-search-icon" aria-hidden="true" />
          <input
            type="search"
            className="admin-search-input"
            placeholder="Tìm theo tên hoặc danh mục..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          {search && (
            <button
              type="button"
              className="admin-search-clear"
              onClick={() => setSearch("")}
            >
              <X size={13} />
            </button>
          )}
        </div>
        <div className="admin-toolbar-actions">
          <span className="admin-toolbar-count">
            {filteredProducts.length} sản phẩm
          </span>
          <button
            type="button"
            className="admin-primary admin-btn-with-icon"
            onClick={openAdd}
          >
            <Plus size={16} /> Thêm sản phẩm
          </button>
        </div>
      </div>

      <Table headers={["Sản phẩm", "Danh mục", "Giá", "Tồn kho", ""]}>
        {filteredProducts.map((product) => (
          <tr key={product.id}>
            <td>
              <div className="admin-product-cell">
                {product.imageUrl ? (
                  <img
                    className="admin-product-thumb"
                    src={product.imageUrl}
                    alt={product.name}
                    onError={(e) => { e.currentTarget.style.display = "none"; }}
                  />
                ) : (
                  <div className="admin-product-thumb-placeholder">
                    <Package size={16} />
                  </div>
                )}
                <div>
                  <b>{product.name}</b>
                  {product.description && (
                    <small className="admin-product-desc-snippet">
                      {product.description.slice(0, 45)}
                      {product.description.length > 45 ? "..." : ""}
                    </small>
                  )}
                </div>
              </div>
            </td>
            <td>
              <span className="admin-category-tag">{product.category || "—"}</span>
            </td>
            <td>
              <b>{money(product.salePrice || product.price)}</b>
            </td>
            <td>
              <span
                className={`admin-stock-badge ${
                  product.stock <= 0
                    ? "out-of-stock"
                    : product.stock < 5
                    ? "low-stock"
                    : "in-stock"
                }`}
              >
                <i className="admin-stock-dot" />
                {product.stock <= 0 ? "Hết hàng" : product.stock < 5 ? `Sắp hết (${product.stock})` : `Còn (${product.stock})`}
              </span>
            </td>
            <td>
              <div className="admin-table-row-actions">
                <button
                  type="button"
                  className="admin-action"
                  onClick={() => openEdit(product)}
                >
                  Sửa
                </button>
                <button
                  type="button"
                  className="admin-action danger-text"
                  onClick={() => setConfirmArchive(product)}
                >
                  Ẩn
                </button>
              </div>
            </td>
          </tr>
        ))}
      </Table>

      {/* Dialogue Thêm / Chỉnh sửa sản phẩm */}
      {dialogOpen && (
        <div
          className="admin-dialog-backdrop"
          onClick={closeDialog}
        >
          <div
            className="admin-dialog admin-product-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="product-dialog-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="admin-dialog-header">
              <div className="admin-dialog-header-title">
                <Package size={20} className="admin-dialog-icon" />
                <h2 id="product-dialog-title">
                  {editing ? "Chỉnh sửa sản phẩm" : "Thêm sản phẩm mới"}
                </h2>
              </div>
              <button
                type="button"
                className="admin-dialog-close"
                aria-label="Đóng"
                disabled={saving}
                onClick={closeDialog}
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={save} className="admin-dialog-form">
              <div className="admin-form-grid">
                <div className="admin-form-group span-2">
                  <label htmlFor="prod-name">TÊN SẢN PHẨM *</label>
                  <input
                    id="prod-name"
                    required
                    placeholder="Ví dụ: Áo polo dệt kim Merino"
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                  />
                </div>

                <div className="admin-form-group">
                  <label htmlFor="prod-category">DANH MỤC *</label>
                  <input
                    id="prod-category"
                    required
                    list="category-suggestions"
                    placeholder="Ví dụ: Áo polo, Áo thun..."
                    value={form.category}
                    onChange={(e) => setForm({ ...form, category: e.target.value })}
                  />
                  <datalist id="category-suggestions">
                    <option value="Áo thun" />
                    <option value="Áo khoác" />
                    <option value="Áo polo" />
                    <option value="Quần" />
                    <option value="Phụ kiện" />
                  </datalist>
                </div>

                <div className="admin-form-group">
                  <label htmlFor="prod-price">GIÁ BÁN (VNĐ) *</label>
                  <input
                    id="prod-price"
                    required
                    type="number"
                    min="0"
                    step="1000"
                    placeholder="Ví dụ: 350000"
                    value={form.price}
                    onChange={(e) => setForm({ ...form, price: e.target.value })}
                  />
                </div>

                <div className="admin-form-group">
                  <label htmlFor="prod-stock">TỒN KHO *</label>
                  <input
                    id="prod-stock"
                    required
                    type="number"
                    min="0"
                    placeholder="Ví dụ: 50"
                    value={form.stock}
                    onChange={(e) => setForm({ ...form, stock: e.target.value })}
                  />
                </div>

                <div className="admin-form-group">
                  <label htmlFor="prod-image">URL HÌNH ẢNH</label>
                  <input
                    id="prod-image"
                    type="url"
                    placeholder="https://images.unsplash.com/..."
                    value={form.imageUrl}
                    onChange={(e) => setForm({ ...form, imageUrl: e.target.value })}
                  />
                </div>

                {form.imageUrl && (
                  <div className="admin-form-group span-2 admin-image-preview-group">
                    <span className="admin-preview-label">Xem trước ảnh:</span>
                    <div className="admin-image-preview">
                      <img
                        src={form.imageUrl}
                        alt="Preview"
                        onError={(e) => { e.currentTarget.style.display = "none"; }}
                        onLoad={(e) => { e.currentTarget.style.display = "block"; }}
                      />
                    </div>
                  </div>
                )}

                <div className="admin-form-group span-2">
                  <label htmlFor="prod-desc">MÔ TẢ CHI TIẾT</label>
                  <textarea
                    id="prod-desc"
                    rows={3}
                    placeholder="Chất liệu, kiểu dáng, hướng dẫn bảo quản..."
                    value={form.description}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                  />
                </div>
              </div>

              <div className="admin-dialog-actions">
                <button
                  type="button"
                  className="admin-dialog-cancel"
                  disabled={saving}
                  onClick={closeDialog}
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  className="admin-primary admin-btn-with-icon"
                  disabled={saving}
                >
                  {saving ? (
                    <>
                      <RefreshCw size={15} className="admin-spin" /> Đang lưu...
                    </>
                  ) : editing ? (
                    "Cập nhật sản phẩm"
                  ) : (
                    "Tạo sản phẩm mới"
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Confirmation dialog when archiving product */}
      {confirmArchive && (
        <div
          className="admin-dialog-backdrop"
          onClick={() => setConfirmArchive(null)}
        >
          <div
            className="admin-dialog"
            role="dialog"
            aria-modal="true"
            onClick={(e) => e.stopPropagation()}
          >
            <h2>Xác nhận ẩn sản phẩm</h2>
            <p>
              Bạn có chắc chắn muốn ẩn sản phẩm <b>{confirmArchive.name}</b> khỏi storefront không?
            </p>
            <div className="admin-dialog-actions">
              <button
                type="button"
                className="admin-dialog-cancel"
                onClick={() => setConfirmArchive(null)}
              >
                Hủy
              </button>
              <button
                type="button"
                className="admin-danger-button"
                onClick={() => archive(confirmArchive.id)}
              >
                Xác nhận ẩn
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function Orders({ notify }) {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");
  const [search, setSearch] = useState("");
  const [detailId, setDetailId] = useState(null);
  const detailEndpoint = useCallback(() => adminEndpoints.order(detailId), [detailId]);
  const { data: detailData, loading: detailLoading, error: detailError, load: reloadDetail } =
    useManagerList(detailEndpoint, { enabled: detailId !== null });
  const detail = detailId === null ? null : (detailData?.id === detailId ? detailData : { id: detailId });
  const [updatingId, setUpdatingId] = useState(null);
  const [cancelOrder, setCancelOrder] = useState(null);
  const [cancelReason, setCancelReason] = useState("");
  const [cancelError, setCancelError] = useState("");
  const openCancel = (order) => {
    setCancelOrder(order);
    setCancelReason("");
    setCancelError("");
  };
  const closeCancel = () => {
    if (updatingId === null) setCancelOrder(null);
  };
  const changeStatus = (order, nextStatus) =>
    nextStatus === "CANCELLED"
      ? openCancel(order)
      : update(order.id, nextStatus);
  const endpoint = useCallback(
    () => adminEndpoints.orders({ status, page }),
    [status, page]
  );
  const { data, load, loading, error } = useAdminList(endpoint);
  const update = async (id, status, reason) => {
    if (updatingId !== null) return;
    setUpdatingId(id);
    setCancelError("");
    try {
      const updated = await adminApi(adminEndpoints.orderStatus(id), {
        method: "PATCH",
        body: {
          status,
          ...(reason !== undefined ? { reason: reason.trim() } : {}),
        },
      });
      notify("Đã cập nhật đơn hàng.");
      if (detailId === id) await reloadDetail(true);
      setCancelOrder(null);
      await load();
    } catch (error) {
      if (status === "CANCELLED") setCancelError(error.message);
      notify(error.message, "error");
    } finally {
      setUpdatingId(null);
    }
  };
  const showDetail = (id) => {
    if (detailId === id) void reloadDetail();
    else setDetailId(id);
  };
  const orders = pageData(data);
  const filteredOrders = useMemo(() => {
    if (!search.trim()) return orders;
    const q = search.toLowerCase();
    return orders.filter(
      (order) =>
        String(order.orderCode || "").toLowerCase().includes(q) ||
        order.customer?.fullName?.toLowerCase().includes(q) ||
        order.customer?.email?.toLowerCase().includes(q)
    );
  }, [orders, search]);

  if (loading) return <PanelLoading />;
  if (error)
    return (
      <div className="admin-panel">
        <ListError message={error} onRetry={load} />
      </div>
    );
  return (
    <div className="admin-panel">
      <PanelTitle
        title="Đơn hàng"
        subtitle="Theo dõi và cập nhật tiến trình giao hàng."
      />
        <div className="admin-order-toolbar">
        <div className="admin-search-wrap">
          <Search size={15} className="admin-search-icon" aria-hidden="true" />
          <input
            type="search"
            className="admin-search-input"
            placeholder="Tìm theo mã đơn, khách hàng..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          {search && (
            <button
              type="button"
              className="admin-search-clear"
              onClick={() => setSearch("")}
            >
              <X size={13} />
            </button>
          )}
        </div>
        <div className="admin-order-filter-select">
          <label htmlFor="admin-order-status">Lọc trạng thái</label>
          <select
            id="admin-order-status"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            <option value="">Tất cả đơn hàng</option>
            {orderStatuses.map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
        <span>{data?.totalElements ?? 0} đơn hàng</span>
        <button type="button" className="admin-action" onClick={load} aria-label="Tải lại trạng thái thanh toán"><RefreshCw size={16} /> Tải lại</button>
      </div>
      <div className="admin-order-table">
        <Table
          headers={[
            "Mã đơn / Ngày",
            "Khách hàng",
            "Thanh toán",
            "Tổng tiền",
            "Trạng thái",
            "Thao tác",
          ]}
        >
          {filteredOrders.map((order) => (
            <tr key={order.id}>
              <td>
                <b className="admin-code">#{order.orderCode}</b>
                <small className="admin-table-subline">
                  {formatDate(order.createdAt)}
                </small>
              </td>
              <td>
                <b>{order.customer?.fullName || "Customer"}</b>
                <small className="admin-table-subline">
                  {order.customer?.email || ""}
                </small>
              </td>
              <td>
                {paymentMethodLabels[order.paymentMethod] ||
                  order.paymentMethod ||
                  "—"}
                {order.paymentMethod === "PAYOS" && <small className="admin-table-subline" aria-label="Trạng thái thanh toán">{paymentStatusLabels[order.paymentStatus] || order.paymentStatus || "—"}</small>}
              </td>
              <td>
                <strong>{money(order.totalAmount)}</strong>
              </td>
              <td>
                <OrderBadge status={order.status} />
              </td>
              <td>
                <div className="admin-order-actions">
                  <select
                    aria-label={`Cập nhật trạng thái đơn ${order.orderCode}`}
                    disabled={
                      updatingId !== null || isFinalOrder(order.status)
                    }
                    value={order.status}
                    onChange={(event) =>
                      changeStatus(order, event.target.value)
                    }
                  >
                    {orderStatusOptions(order.status).map(([value, label]) => (
                      <option key={value} value={value}>
                        {label}
                      </option>
                    ))}
                  </select>
                  <button
                    className="admin-action"
                    onClick={() => showDetail(order.id)}
                  >
                    Chi tiết
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </Table>
      </div>
      <div className="admin-order-cards">
        {filteredOrders.length ? (
          filteredOrders.map((order) => (
            <article className="admin-order-card" key={order.id}>
              <div>
                <b>#{order.orderCode}</b>
                <OrderBadge status={order.status} />
              </div>
              <small>{formatDate(order.createdAt)}</small>
              <p>{order.customer?.fullName || "Customer"}</p>
              <p>
                {paymentMethodLabels[order.paymentMethod] ||
                  order.paymentMethod ||
                  "—"}
              </p>
              {order.paymentMethod === "PAYOS" && <p aria-label="Trạng thái thanh toán">{paymentStatusLabels[order.paymentStatus] || order.paymentStatus || "—"}</p>}
              <strong>{money(order.totalAmount)}</strong>
              <div className="admin-order-actions">
                <select
                  aria-label={`Cập nhật trạng thái đơn ${order.orderCode}`}
                  disabled={
                    updatingId !== null || isFinalOrder(order.status)
                  }
                  value={order.status}
                  onChange={(event) => changeStatus(order, event.target.value)}
                >
                  {orderStatusOptions(order.status).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
                <button
                  className="admin-action"
                  onClick={() => showDetail(order.id)}
                >
                  Xem chi tiết
                </button>
              </div>
            </article>
          ))
        ) : (
          <div className="admin-order-empty">
            Không có đơn hàng trong trạng thái này.
          </div>
        )}
      </div>
      <PageControls
        page={page}
        totalPages={data?.totalPages || 0}
        totalElements={data?.totalElements}
        onPage={setPage}
      />
      {cancelOrder && (
        <div
          className="admin-dialog-backdrop"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) closeCancel();
          }}
        >
          <form
            className="admin-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="admin-cancel-title"
            onSubmit={(event) => {
              event.preventDefault();
              if (
                cancelReason.trim() &&
                cancelReason.trim().length <= 1000
              )
                update(cancelOrder.id, "CANCELLED", cancelReason);
            }}
          >
            <h2 id="admin-cancel-title">
              {cancelOrder.status === "CANCELLED"
                ? "Bổ sung lý do hủy đơn"
                : "Hủy đơn hàng"}{" "}
              #{cancelOrder.orderCode}
            </h2>
            <p>Lý do này sẽ được gửi đến người mua trong mục Thông báo.</p>
            <label htmlFor="admin-cancel-reason">Lý do hủy đơn</label>
            <textarea
              id="admin-cancel-reason"
              required
              maxLength={1000}
              value={cancelReason}
              disabled={updatingId !== null}
              onChange={(event) => setCancelReason(event.target.value)}
            />
            <small>{cancelReason.length}/1000 ký tự</small>
            {cancelError && (
              <p role="alert" className="danger-text">
                {cancelError}
              </p>
            )}
            <div className="admin-dialog-actions">
              <button
                type="button"
                onClick={closeCancel}
                disabled={updatingId !== null}
              >
                Quay lại
              </button>
              <button
                className="admin-primary admin-danger-button"
                disabled={updatingId !== null || !cancelReason.trim()}
              >
                {updatingId !== null
                  ? "Đang lưu..."
                  : cancelOrder.status === "CANCELLED"
                  ? "Lưu lý do"
                  : "Xác nhận hủy"}
              </button>
            </div>
          </form>
        </div>
      )}
      {detail && !cancelOrder && (
        <div
          className="admin-dialog-backdrop"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) setDetailId(null);
          }}
        >
          <div
            className="admin-dialog admin-order-detail"
            role="dialog"
            aria-modal="true"
            aria-labelledby="admin-order-detail-title"
          >
            <div className="admin-section-heading">
              <h2 id="admin-order-detail-title">
                Đơn hàng #{detail.orderCode || detail.id}
              </h2>
              <button
                aria-label="Đóng chi tiết"
                className="admin-action"
                onClick={() => setDetailId(null)}
              >
                <X size={18} />
              </button>
            </div>
            {detailLoading ? (
              <p role="status">Đang tải chi tiết...</p>
            ) : detailError ? (
              <ListError
                message={detailError}
                onRetry={() => showDetail(detail.id)}
              />
            ) : (
              <>
                <OrderBadge status={detail.status} />
                <div className="admin-detail-grid">
                  <div>
                    <span>Khách hàng</span>
                    <b>{detail.customer?.fullName || "—"}</b>
                    <small>{detail.customer?.email || ""}</small>
                  </div>
                  <div>
                    <span>Ngày đặt</span>
                    <b>{formatDate(detail.createdAt)}</b>
                  </div>
                  <div>
                    <span>Phương thức thanh toán</span>
                    <b>
                      {paymentMethodLabels[detail.paymentMethod] ||
                        detail.paymentMethod ||
                        "—"}
                    </b>
                  </div>
                  <div>
                    <span>Trạng thái thanh toán</span>
                    <b>
                      {paymentStatusLabels[detail.paymentStatus] ||
                        detail.paymentStatus ||
                        "—"}
                    </b>
                  </div>
                  <div className="admin-detail-wide">
                    <span>Địa chỉ giao hàng</span>
                    <b>{detail.deliveryAddress || "—"}</b>
                  </div>
                  {detail.status === "CANCELLED" && (
                    <div className="admin-detail-wide">
                      <span>Lý do hủy đơn</span>
                      <b className="admin-cancel-reason">
                        {detail.cancelReason?.trim() || "Chưa có lý do hủy"}
                      </b>
                      {!detail.cancelReason?.trim() && (
                        <button
                          className="admin-action"
                          onClick={() => openCancel(detail)}
                        >
                          Bổ sung lý do
                        </button>
                      )}
                    </div>
                  )}
                </div>
                <h3>Sản phẩm</h3>
                <div className="admin-detail-items">
                  {(detail.items || []).map((item) => (
                    <div key={item.id}>
                      <span>
                        {item.product?.name || "Sản phẩm"} × {item.quantity}
                        <small>
                          {[item.size, item.color].filter(Boolean).join(" / ")}
                        </small>
                      </span>
                      <b>
                        {money(
                          Number(item.unitPrice || 0) * item.quantity
                        )}
                      </b>
                    </div>
                  ))}
                </div>
                <div className="admin-detail-total">
                  <span>Tổng tiền</span>
                  <strong>{money(detail.totalAmount)}</strong>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function PageControls({
  page,
  totalPages,
  totalElements,
  onPage,
  disabled = false,
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="admin-pagination">
      <span>
        Tổng {totalElements ?? 0} bản ghi · Trang {page + 1}/{totalPages}
      </span>
      <div>
        <button
          disabled={disabled || page <= 0}
          onClick={() => onPage(page - 1)}
        >
          Trước
        </button>
        <button
          disabled={disabled || page + 1 >= totalPages}
          onClick={() => onPage(page + 1)}
        >
          Sau
        </button>
      </div>
    </div>
  );
}

function Feedback({ notify }) {
  const [page, setPage] = useState(0);
  const [busy, setBusy] = useState(false);
  const [deleting, setDeleting] = useState(null);
  const [replying, setReplying] = useState(null);
  const [reply, setReply] = useState("");
  const [actionError, setActionError] = useState("");
  const endpoint = useCallback(() => adminEndpoints.feedback({ page }), [page]);
  const { data, load, loading, error } = useAdminList(endpoint);
  const update = async (item, changes) => {
    if (busy) return;
    setBusy(true);
    setActionError("");
    try {
      await adminApi(adminEndpoints.feedbackItem(item.id), {
        method: "PATCH",
        body: changes,
      });
      notify("Đã cập nhật phản hồi.");
      setReplying(null);
      await load();
    } catch (error) {
      setActionError(error.message);
    } finally {
      setBusy(false);
    }
  };
  const remove = async () => {
    if (busy || !deleting) return;
    setBusy(true);
    setActionError("");
    try {
      await adminApi(adminEndpoints.feedbackItem(deleting.id), {
        method: "DELETE",
      });
      setDeleting(null);
      notify("Đã xóa phản hồi khỏi trang sản phẩm và danh sách quản lý.");
      if (page > 0 && pageData(data).length === 1) setPage(page - 1);
      else await load();
    } catch (error) {
      setActionError(error.message);
    } finally {
      setBusy(false);
    }
  };
  if (loading) return <PanelLoading />;
  if (error)
    return (
      <div className="admin-panel">
        <ListError message={error} onRetry={load} />
      </div>
    );
  return (
    <div className="admin-panel admin-feedback-panel">
      <PanelTitle
        title="Phản hồi"
        subtitle="Theo dõi đánh giá và quản lý nội dung hiển thị."
      />
      {actionError && !deleting && !replying && (
        <p role="alert" className="danger-text">
          {actionError}
        </p>
      )}
      <Table
        headers={[
          "Khách hàng",
          "Sản phẩm",
          "Đánh giá",
          "Nội dung",
          "Trạng thái",
          "Hiển thị",
          "Thao tác",
        ]}
      >
        {pageData(data).map((item) => (
          <tr key={item.id}>
            <td>{item.customer?.fullName || "Customer"}</td>
            <td>{item.product?.name || "—"}</td>
            <td>
              <span
                className="admin-star-rating"
                aria-label={`${item.rating} trên 5 sao`}
              >
                {"★".repeat(item.rating)}
              </span>
            </td>
            <td className="admin-feedback-content">
              {item.comment || "—"}
              {item.adminResponse && (
                <div className="admin-feedback-reply">
                  <b>Shop trả lời</b>
                  <p>{item.adminResponse}</p>
                </div>
              )}
            </td>
            <td>
              <select
                aria-label={`Trạng thái phản hồi ${item.id}`}
                value={item.status || "NEW"}
                disabled={busy}
                onChange={(event) =>
                  update(item, { status: event.target.value })
                }
              >
                <option value="NEW">Mới</option>
                <option value="IN_PROGRESS">Đang xử lý</option>
                <option value="RESOLVED">Đã xử lý</option>
              </select>
            </td>
            <td>
              <span
                className={`admin-badge ${item.hidden ? "danger" : "success"}`}
              >
                {item.hidden ? "Đã ẩn" : "Công khai"}
              </span>
            </td>
            <td>
              <div className="admin-feedback-actions">
                <button
                  className="admin-action"
                  disabled={busy}
                  onClick={() => {
                    setActionError("");
                    setReplying(item);
                    setReply(item.adminResponse || "");
                  }}
                >
                  {item.adminResponse ? "Sửa trả lời" : "Trả lời"}
                </button>
                <button
                  className="admin-action"
                  disabled={busy}
                  onClick={() => update(item, { hidden: !item.hidden })}
                >
                  {item.hidden ? "Hiện lại" : "Ẩn"}
                </button>
                <button
                  className="admin-action danger-text"
                  disabled={busy}
                  onClick={() => {
                    setActionError("");
                    setDeleting(item);
                  }}
                >
                  Xóa
                </button>
              </div>
            </td>
          </tr>
        ))}
      </Table>
      <PageControls
        page={page}
        totalPages={data?.totalPages || 0}
        totalElements={data?.totalElements}
        onPage={setPage}
        disabled={busy}
      />
      {replying && (
        <div
          className="admin-dialog-backdrop"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget && !busy)
              setReplying(null);
          }}
        >
          <form
            className="admin-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="admin-reply-title"
            onSubmit={(event) => {
              event.preventDefault();
              if (reply.trim())
                update(replying, { adminResponse: reply.trim() });
            }}
          >
            <h2 id="admin-reply-title">
              {replying.adminResponse
                ? "Sửa câu trả lời"
                : "Trả lời khách hàng"}
            </h2>
            <p className="admin-feedback-quote">
              <b>{replying.customer?.fullName || "Khách hàng"}</b>:{" "}
              {replying.comment}
            </p>
            <label htmlFor="admin-feedback-reply">Nội dung trả lời</label>
            <textarea
              id="admin-feedback-reply"
              required
              maxLength={4000}
              disabled={busy}
              value={reply}
              onChange={(event) => setReply(event.target.value)}
            />
            <small>
              {reply.length}/4000 ký tự · Câu trả lời hiển thị cùng đánh giá
              công khai.
            </small>
            {actionError && (
              <p role="alert" className="danger-text">
                {actionError}
              </p>
            )}
            <div className="admin-dialog-actions">
              <button
                type="button"
                disabled={busy}
                onClick={() => setReplying(null)}
              >
                Quay lại
              </button>
              <button
                className="admin-primary"
                disabled={busy || !reply.trim()}
              >
                {busy ? "Đang lưu..." : "Lưu trả lời"}
              </button>
            </div>
          </form>
        </div>
      )}
      {deleting && (
        <div
          className="admin-dialog-backdrop"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget && !busy)
              setDeleting(null);
          }}
        >
          <div
            className="admin-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="admin-delete-feedback-title"
          >
            <h2 id="admin-delete-feedback-title">Xóa phản hồi?</h2>
            <p>
              Phản hồi của {deleting.customer?.fullName || "khách hàng"} sẽ bị gỡ
              khỏi trang sản phẩm và danh sách quản lý. Dữ liệu lịch sử vẫn được
              giữ lại.
            </p>
            {actionError && (
              <p role="alert" className="danger-text">
                {actionError}
              </p>
            )}
            <div className="admin-dialog-actions">
              <button disabled={busy} onClick={() => setDeleting(null)}>
                Quay lại
              </button>
              <button
                className="admin-primary admin-danger-button"
                disabled={busy}
                onClick={remove}
              >
                {busy ? "Đang xóa..." : "Xác nhận xóa"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function Surveys({ notify }) {
  const [reward,setReward]=useState(emptyReward);
  const [page, setPage] = useState(0);
  const endpoint = useCallback(() => adminEndpoints.surveys({ page }), [page]);
  const { data, load, loading, error } = useAdminList(endpoint);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [audience,setAudience]=useState({audience:'ALL',customerIds:[]});
  const newQuestion = () => ({
    key: crypto.randomUUID(),
    text: "",
    type: "SINGLE_CHOICE",
    required: true,
    options: ["", ""],
  });
  const [questions, setQuestions] = useState(() => [newQuestion()]);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState("");
  const [deleting, setDeleting] = useState(null);

  const openCreate = () => {
    setAudience({audience:'ALL',customerIds:[]});
    setReward(emptyReward());
    setFormError("");
    setTitle("");
    setDescription("");
    setQuestions([newQuestion()]);
    setDialogOpen(true);
  };

  const closeDialog = () => {
    if (busy) return;
    setDialogOpen(false);
    setFormError("");
  };

  useEffect(() => {
    if (!dialogOpen) return;
    const onKeyDown = (e) => {
      if (e.key === "Escape") closeDialog();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [dialogOpen, busy]);

  const change = (key, patch) =>
    setQuestions((current) =>
      current.map((q) => (q.key === key ? { ...q, ...patch } : q))
    );

  const create = async (event) => {
    event.preventDefault();
    if (busy) return;
    setFormError("");
    const prepared = questions.map((q) => ({
      text: q.text.trim(),
      type: q.type,
      required: q.required,
      optionsJson: JSON.stringify(q.options.map((o) => o.trim())),
    }));
    if (
      !title.trim() ||
      !prepared.length ||
      prepared.some(
        (q) =>
          !q.text ||
          JSON.parse(q.optionsJson).length < 2 ||
          JSON.parse(q.optionsJson).some((o) => !o) ||
          new Set(JSON.parse(q.optionsJson)).size !==
            JSON.parse(q.optionsJson).length ||
          q.optionsJson.length > 4000
      )
    ) {
      setFormError(
        "Nhập tiêu đề, ít nhất một câu hỏi và ít nhất hai phương án khác nhau, không trống cho mỗi câu. Danh sách phương án tối đa 4.000 ký tự."
      );
      return;
    }
    setBusy(true);
    try {
      await adminApi("/api/manager/surveys", {
        method: "POST",
        body: {
          title: title.trim(),
          description: description.trim(),
          questions: prepared,
          ...audience,
          reward,
        },
      });
      setTitle("");
      setDescription("");
      setQuestions([newQuestion()]);
      setDialogOpen(false);
      notify("Đã lưu khảo sát nháp.");
      if (page) setPage(0);
      else await load();
    } catch (e) {
      setFormError(e.message);
    } finally {
      setBusy(false);
    }
  };

  const publish = async (survey) => {
    if (busy) return;
    setBusy(true);
    setFormError("");
    try {
      await adminApi(
        adminEndpoints.surveyPublish(survey.id) +
          "?value=" +
          (survey.status !== "PUBLISHED"),
        { method: "PATCH" }
      );
      notify("Đã cập nhật phát hành khảo sát.");
      await load();
    } catch (e) {
      setFormError(e.message);
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (busy || !deleting) return;
    setBusy(true);
    setFormError("");
    try {
      await adminApi(adminEndpoints.survey(deleting.id), { method: "DELETE" });
      setDeleting(null);
      notify("Đã xóa khảo sát. Kết quả cũ được giữ lại.");
      if (page > 0 && pageData(data).length === 1) setPage(page - 1);
      else await load();
    } catch (e) {
      setFormError(e.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="admin-panel">
      <PanelTitle
        title="Khảo sát"
        subtitle="Tạo câu hỏi trắc nghiệm và phát hành cho khách hàng."
      />

      <div className="admin-table-toolbar">
        <span className="admin-toolbar-count">
          {data?.totalElements ?? pageData(data).length} khảo sát
        </span>
        <button
          type="button"
          className="admin-primary admin-btn-with-icon"
          onClick={openCreate}
        >
          <Plus size={16} /> Tạo khảo sát
        </button>
      </div>

      {loading ? (
        <PanelLoading />
      ) : error ? (
        <ListError message={error} onRetry={load} />
      ) : (
        <>
          <Table headers={["Tiêu đề", "Trạng thái", "Thao tác"]}>
            {pageData(data).map((survey) => (
              <tr key={survey.id}>
                <td>
                  <b>{survey.title}</b>
                  <SurveyRewardEditor survey={survey} onSaved={load}/>
                  <small className="admin-table-subline">
                    {survey.questions?.length || 0} câu hỏi
                    {survey.description ? ` • ${survey.description}` : ""}
                  </small>
                </td>
                <td>
                  <span
                    className={`admin-badge ${
                      survey.status === "PUBLISHED" ? "is-active" : ""
                    }`}
                  >
                    {survey.status === "PUBLISHED" ? "Đã phát hành" : "Bản nháp"}
                  </span>
                </td>
                <td>
                  <div className="admin-table-row-actions">
                    <button
                      type="button"
                      className="admin-action"
                      disabled={busy}
                      onClick={() => publish(survey)}
                    >
                      {survey.status === "PUBLISHED"
                        ? "Gỡ phát hành"
                        : "Phát hành"}
                    </button>
                    <button
                      type="button"
                      className="admin-action danger-text"
                      disabled={busy}
                      onClick={() => {
                        setFormError("");
                        setDeleting(survey);
                      }}
                    >
                      Xóa
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </Table>
          <PageControls
            page={page}
            totalPages={data?.totalPages || 0}
            totalElements={data?.totalElements}
            onPage={setPage}
            disabled={busy}
          />
        </>
      )}

      {/* Modal Dialog Tạo Khảo Sát */}
      {dialogOpen && (
        <div className="admin-dialog-backdrop" onClick={closeDialog}>
          <div
            className="admin-dialog admin-survey-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="survey-dialog-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="admin-dialog-header">
              <div className="admin-dialog-header-title">
                <ClipboardList size={20} className="admin-dialog-icon" />
                <h2 id="survey-dialog-title">Tạo khảo sát mới</h2>
              </div>
              <button
                type="button"
                className="admin-dialog-close"
                aria-label="Đóng"
                disabled={busy}
                onClick={closeDialog}
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={create} className="admin-dialog-form">
              <div className="admin-survey-dialog-body">
                <RewardFields value={reward} onChange={setReward} disabled={busy}/>
                <SurveyAudience value={audience} onChange={setAudience} disabled={busy}/>
                {formError && (
                  <p role="alert" className="danger-text" style={{ margin: 0 }}>
                    {formError}
                  </p>
                )}

                <fieldset disabled={busy} className="admin-survey-fieldset">
                  <div className="admin-form-group">
                    <label htmlFor="survey-title">Tên khảo sát</label>
                    <input
                      id="survey-title"
                      required
                      maxLength={255}
                      autoFocus
                      placeholder="Ví dụ: Khảo sát mức độ hài lòng về chất lượng dịch vụ"
                      value={title}
                      onChange={(e) => setTitle(e.target.value)}
                    />
                  </div>

                  <div className="admin-form-group">
                    <label htmlFor="survey-desc">Mô tả</label>
                    <textarea
                      id="survey-desc"
                      maxLength={4000}
                      rows={2}
                      placeholder="Mô tả mục đích khảo sát hoặc thông điệp gửi tới khách hàng..."
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                    />
                  </div>

                  <div
                    className="admin-survey-options-header"
                    style={{ marginTop: 8 }}
                  >
                    DANH SÁCH CÂU HỎI ({questions.length})
                  </div>

                  {questions.map((q, index) => (
                    <div className="admin-survey-card" key={q.key}>
                      <div className="admin-survey-card-header">
                        <div className="admin-survey-card-title">
                          <span className="admin-survey-card-badge">
                            #{index + 1}
                          </span>
                          <h3>Câu hỏi {index + 1}</h3>
                        </div>
                        {questions.length > 1 && (
                          <button
                            type="button"
                            className="admin-action danger-text"
                            onClick={() =>
                              setQuestions((current) =>
                                current.filter((item) => item.key !== q.key)
                              )
                            }
                          >
                            Xóa câu hỏi
                          </button>
                        )}
                      </div>

                      <div className="admin-form-group">
                        <label htmlFor={`survey-q-${index}`}>
                          Nội dung câu hỏi {index + 1}
                        </label>
                        <textarea
                          id={`survey-q-${index}`}
                          required
                          maxLength={1000}
                          rows={2}
                          placeholder="Nhập nội dung câu hỏi..."
                          value={q.text}
                          onChange={(e) =>
                            change(q.key, { text: e.target.value })
                          }
                        />
                      </div>

                      <div className="admin-survey-controls-row">
                        <div className="admin-survey-type-select">
                          <label
                            htmlFor={`survey-q-type-${index}`}
                            style={{
                              marginBottom: 4,
                              display: "block",
                              fontSize: "12px",
                              fontWeight: 600,
                            }}
                          >
                            Kiểu câu hỏi {index + 1}
                          </label>
                          <select
                            id={`survey-q-type-${index}`}
                            value={q.type}
                            onChange={(e) =>
                              change(q.key, { type: e.target.value })
                            }
                          >
                            <option value="SINGLE_CHOICE">
                              Chọn một đáp án (Radio)
                            </option>
                            <option value="MULTIPLE_CHOICE">
                              Chọn nhiều đáp án (Checkbox)
                            </option>
                          </select>
                        </div>

                        <label className="admin-survey-required-label">
                          <input
                            type="checkbox"
                            checked={q.required}
                            onChange={(e) =>
                              change(q.key, { required: e.target.checked })
                            }
                          />
                          <span>Bắt buộc trả lời</span>
                        </label>
                      </div>

                      <div className="admin-survey-options-group">
                        <span className="admin-survey-options-header">
                          Các phương án trả lời:
                        </span>
                        {q.options.map((option, oi) => (
                          <div className="admin-survey-option-row" key={oi}>
                            <div
                              className={`admin-survey-option-bullet ${
                                q.type === "MULTIPLE_CHOICE" ? "square" : ""
                              }`}
                            />
                            <input
                              aria-label={`Câu ${index + 1}, phương án ${oi + 1}`}
                              required
                              maxLength={4000}
                              placeholder={`Phương án ${oi + 1}`}
                              value={option}
                              onChange={(e) =>
                                change(q.key, {
                                  options: q.options.map((v, i) =>
                                    i === oi ? e.target.value : v
                                  ),
                                })
                              }
                            />
                            <button
                              type="button"
                              className="admin-survey-option-delete"
                              disabled={q.options.length <= 2}
                              title={
                                q.options.length <= 2
                                  ? "Cần tối thiểu 2 phương án"
                                  : "Xóa phương án này"
                              }
                              onClick={() =>
                                change(q.key, {
                                  options: q.options.filter((_, i) => i !== oi),
                                })
                              }
                            >
                              Xóa
                            </button>
                          </div>
                        ))}

                        <button
                          type="button"
                          className="admin-survey-add-option"
                          onClick={() =>
                            change(q.key, { options: [...q.options, ""] })
                          }
                        >
                          <Plus size={14} /> Thêm phương án
                        </button>
                      </div>
                    </div>
                  ))}

                  <button
                    type="button"
                    className="admin-survey-add-question-btn"
                    onClick={() =>
                      setQuestions((current) => [...current, newQuestion()])
                    }
                  >
                    <Plus size={16} /> Thêm câu hỏi mới
                  </button>
                </fieldset>
              </div>

              <div className="admin-dialog-actions" style={{ marginTop: 16 }}>
                <button
                  type="button"
                  className="admin-dialog-cancel"
                  disabled={busy}
                  onClick={closeDialog}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  className="admin-primary"
                  disabled={busy || !questions.length}
                >
                  {busy ? "Đang lưu..." : "Lưu bản nháp"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal xác nhận xóa khảo sát */}
      {deleting && (
        <div className="admin-dialog-backdrop" onClick={() => !busy && setDeleting(null)}>
          <div
            className="admin-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="survey-delete-title"
            onClick={(e) => e.stopPropagation()}
          >
            <h2 id="survey-delete-title">Xóa khảo sát?</h2>
            <p>
              Khảo sát “{deleting.title}” sẽ bị gỡ khỏi danh sách. Câu hỏi và
              kết quả đã thu thập được giữ lại.
            </p>
            {formError && (
              <p role="alert" className="danger-text">
                {formError}
              </p>
            )}
            <div className="admin-dialog-actions">
              <button
                type="button"
                className="admin-dialog-cancel"
                disabled={busy}
                onClick={() => setDeleting(null)}
              >
                Quay lại
              </button>
              <button
                type="button"
                className="admin-primary admin-danger-button"
                disabled={busy}
                onClick={remove}
              >
                {busy ? "Đang xóa..." : "Xác nhận xóa"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function ReportBars({ items = [] }) {
  return (
    <div className="admin-report-bars">
      {items.map((item, index) => (
        <div key={index}>
          <div className="admin-report-bar-label">
            <span>{item.label}</span>
            <b>
              {item.count} · {item.percent}%
            </b>
          </div>
          <div className="admin-report-track">
            <div style={{ width: Math.min(100, item.percent) + "%" }} />
          </div>
        </div>
      ))}
    </div>
  );
}

function CustomerReport() {
  const endpoint = useCallback(() => "/api/manager/reports/customers", []);
  const { data, loading, error, load } = useAdminList(endpoint);
  if (loading) return <PanelLoading />;
  if (error) return <ListError message={error} onRetry={load} />;
  if (!data) return null;
  return (
    <>
      <div className="admin-report-summary">
        {[
          ["Khách hàng", data.total],
          ["Hoạt động", data.active],
          ["Bị khóa", data.locked],
          ["Đã khai báo tuổi", data.knownAge],
          ["Đã khai báo sở thích", data.knownPreferences],
        ].map(([label, value]) => (
          <div key={label}>
            <span>{label}</span>
            <strong>{value}</strong>
          </div>
        ))}
      </div>
      {!data.total && <p>Chưa có dữ liệu khách hàng.</p>}
      <div className="admin-report-columns">
        <section>
          <h3>Phân bố độ tuổi</h3>
          <p>Tỷ lệ trên tổng khách hàng chưa xóa.</p>
          <ReportBars items={data.ages} />
        </section>
        <section>
          <h3>Sở thích đã khai báo</h3>
          <p>
            10 nội dung phổ biến nhất; gom nội dung giống nhau, không tự phân
            loại.
          </p>
          <ReportBars items={data.preferences} />
        </section>
      </div>
    </>
  );
}

function TextAnswerReport({ surveyId, questionId }) {
  const [page, setPage] = useState(0);
  const endpoint = useCallback(
    () =>
      `/api/manager/reports/surveys/${surveyId}/questions/${questionId}/answers?page=${page}`,
    [surveyId, questionId, page]
  );
  const { data, loading, error, load } = useAdminList(endpoint);
  if (loading) return <PanelLoading />;
  if (error) return <ListError message={error} onRetry={load} />;
  return (
    <>
      <ul className="admin-text-answers">
        {(data?.content || []).map((answer, i) => (
          <li key={i}>{answer}</li>
        ))}
      </ul>
      {!data?.content?.length && <p>Chưa có câu trả lời.</p>}
      <PageControls
        page={page}
        totalPages={data?.totalPages || 0}
        totalElements={data?.totalElements}
        onPage={setPage}
      />
    </>
  );
}

function SurveyResult({ id }) {
  const endpoint = useCallback(
    () => `/api/manager/reports/surveys/${id}/results`,
    [id]
  );
  const { data, loading, error, load } = useAdminList(endpoint);
  if (loading) return <PanelLoading />;
  if (error) return <ListError message={error} onRetry={load} />;
  if (!data) return null;
  return (
    <div>
      <h3>{data.title}</h3>
      <p>
        {data.responses} lượt trả lời ·{" "}
        {data.status === "PUBLISHED" ? "Đã phát hành" : "Bản nháp"}
      </p>
      {!data.responses && <p>Khảo sát chưa có người trả lời.</p>}
      {(data.questions || []).map((q, i) => (
        <section className="admin-report-question" key={q.id}>
          <h4>
            {i + 1}. {q.text}
          </h4>
          <p>
            {q.type === "MULTIPLE_CHOICE"
              ? "Chọn nhiều đáp án"
              : ["SINGLE", "SINGLE_CHOICE"].includes(q.type)
              ? "Chọn một đáp án"
              : "Văn bản"}{" "}
            · Đã trả lời: {q.answered} · Bỏ qua: {q.skipped}
          </p>
          {(q.invalid > 0 || q.invalidOptions) && (
            <p className="danger-text">
              Có {q.invalid} bài có đáp án không hợp lệ.
              {q.invalidOptions && " Cấu hình phương án cũ không hợp lệ."}
            </p>
          )}
          {["SINGLE", "SINGLE_CHOICE", "MULTIPLE_CHOICE"].includes(q.type) ? (
            <>
              <ReportBars items={q.options} />
              <p>
                Tỷ lệ trên {q.answered} người trả lời câu này.
                {q.type === "MULTIPLE_CHOICE" &&
                  " Tổng tỷ lệ có thể vượt 100%."}
              </p>
            </>
          ) : (
            <TextAnswerReport surveyId={id} questionId={q.id} />
          )}
        </section>
      ))}
    </div>
  );
}

function SurveyReports() {
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null);
  const endpoint = useCallback(
    () => `/api/manager/reports/surveys?page=${page}`,
    [page]
  );
  const { data, loading, error, load } = useAdminList(endpoint);
  return (
    <>
      {loading ? (
        <PanelLoading />
      ) : error ? (
        <ListError message={error} onRetry={load} />
      ) : (
        <>
          <label className="admin-report-select">
            Chọn khảo sát
            <select
              value={selected || ""}
              onChange={(e) => setSelected(e.target.value || null)}
            >
              <option value="">Chọn khảo sát để xem kết quả</option>
              {(data?.content || []).map((s) => (
                <option key={s.id} value={s.id}>
                  {s.title} ·{" "}
                  {s.status === "PUBLISHED" ? "Đã phát hành" : "Bản nháp"} ·{" "}
                  {s.responses} lượt
                </option>
              ))}
            </select>
          </label>
          {!data?.content?.length && <p>Chưa có khảo sát.</p>}
          <PageControls
            page={page}
            totalPages={data?.totalPages || 0}
            totalElements={data?.totalElements}
            onPage={(p) => {
              setSelected(null);
              setPage(p);
            }}
          />
        </>
      )}
      {selected && <SurveyResult key={selected} id={selected} />}
    </>
  );
}

function Reports() {
  const [tab, setTab] = useState("customers");
  return (
    <div className="admin-panel">
      <PanelTitle
        title="Báo cáo"
        subtitle="Dữ liệu tổng hợp toàn bộ thời gian."
      />
      <div className="admin-report-tabs">
        <button
          className={tab === "customers" ? "admin-primary" : "admin-action"}
          aria-pressed={tab === "customers"}
          onClick={() => setTab("customers")}
        >
          <UsersIcon size={15} aria-hidden="true" />
          Khách hàng
        </button>
        <button
          className={tab === "surveys" ? "admin-primary" : "admin-action"}
          aria-pressed={tab === "surveys"}
          onClick={() => setTab("surveys")}
        >
          <ClipboardList size={15} aria-hidden="true" />
          Kết quả khảo sát
        </button>
      </div>
      {tab === "customers" ? <CustomerReport /> : <SurveyReports />}
    </div>
  );
}

function PanelTitle({ title, subtitle }) {
  return (
    <div className="admin-panel-title">
      <div>
        <span className="admin-eyebrow">ADMIN MANAGEMENT</span>
        <h2>{title}</h2>
        <p>{subtitle}</p>
      </div>
    </div>
  );
}
