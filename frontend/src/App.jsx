import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  api,
  currentApiBase,
  endpoints,
  refreshSession,
  signIn,
  signOut,
  signUp,
} from "./api";
import {
  ArrowLeft,
  ArrowUpRight,
  Bell,
  Check,
  ChevronRight,
  CircleAlert,
  Heart,
  Headphones,
  Menu,
  Minus,
  PackageOpen,
  MapPin,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  ShoppingBag,
  SlidersHorizontal,
  Star,
  Trash2,
  Truck,
  KeyRound,
  UserRound,
  X,
} from "lucide-react";

const money = (value) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(Number(value || 0));
const date = (value) =>
  value
    ? new Date(value).toLocaleDateString("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
      })
    : "—";
const csv = (value) =>
  String(value || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
const surveyOptions = (value) => {
  try {
    const parsed = JSON.parse(value || "[]");
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};
const discount = (product) =>
  product?.salePrice && product.salePrice < product.price
    ? Math.round((1 - product.salePrice / product.price) * 100)
    : 0;
const FALLBACK_IMAGE =
  "https://images.unsplash.com/photo-1529139574466-a303027c1d8b?auto=format&fit=crop&w=900&q=85";
const imageSrc = (value) =>
  typeof value === "string" && value.trim() ? value : FALLBACK_IMAGE;
const protectImage = (event) => {
  event.currentTarget.onerror = null;
  event.currentTarget.src = FALLBACK_IMAGE;
};
const GUEST_CART_KEY = "anh-lon-shop-guest-cart";
const emptyCart = () => ({ items: [], subtotal: 0, itemCount: 0 });
const readGuestCart = () => {
  if (typeof window === "undefined") return emptyCart();
  try {
    const value = JSON.parse(window.localStorage.getItem(GUEST_CART_KEY) || "null");
    return value?.items ? value : emptyCart();
  } catch {
    return emptyCart();
  }
};
const saveGuestCart = (value) => {
  if (typeof window !== "undefined") window.localStorage.setItem(GUEST_CART_KEY, JSON.stringify(value));
};
const clearGuestCart = () => {
  if (typeof window !== "undefined") window.localStorage.removeItem(GUEST_CART_KEY);
};

function App() {
  const [products, setProducts] = useState({
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
  });
  const [categories, setCategories] = useState([]);
  const [catalogPage, setCatalogPage] = useState(
    () =>
      typeof window !== "undefined" && window.location.pathname === "/san-pham",
  );
  const [filters, setFilters] = useState(() => {
    const isCatalog =
      typeof window !== "undefined" && window.location.pathname === "/san-pham";
    const params = new URLSearchParams(
      typeof window !== "undefined" ? window.location.search : "",
    );
    return {
      keyword: "",
      category: isCatalog ? params.get("category") || "" : "",
      minPrice: isCatalog ? params.get("minPrice") || "" : "",
      maxPrice: isCatalog ? params.get("maxPrice") || "" : "",
      gender: "NAM",
      sort: "newest",
      page: 0,
    };
  });
  const [searchInput, setSearchInput] = useState("");
  const [user, setUser] = useState(null);
  const [cart, setCart] = useState(() => readGuestCart());
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [cartOpen, setCartOpen] = useState(false);
  const [authOpen, setAuthOpen] = useState(false);
  const [authMode, setAuthMode] = useState("login");
  const [authAfterLogin, setAuthAfterLogin] = useState(null);
  const [ordersOpen, setOrdersOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  const [surveysOpen, setSurveysOpen] = useState(false);
  const [wishlistOpen, setWishlistOpen] = useState(false);
  const [wishlist, setWishlist] = useState([]);
  const [addressesOpen, setAddressesOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [notificationCount, setNotificationCount] = useState(0);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [notice, setNotice] = useState(null);
  const [catalogError, setCatalogError] = useState("");
  const [loading, setLoading] = useState(true);
  const cartUpdateLocks = useRef(new Set());
  const [updatingCartKeys, setUpdatingCartKeys] = useState([]);

  const openAuth = (afterLogin = null) => {
    setAuthMode("login");
    setAuthAfterLogin(afterLogin);
    setAuthOpen(true);
  };

  const loadProducts = async () => {
    setLoading(true);
    try {
      setCatalogError("");
      setProducts(await api(endpoints.catalog(filters)));
    } catch (error) {
      const message =
        error.message === "Failed to fetch"
          ? "Không kết nối được với máy chủ. Hãy khởi động backend rồi bấm thử lại."
          : error.message;
      setCatalogError(message);
      showNotice(message, "error");
    } finally {
      setLoading(false);
    }
  };
  const loadCart = async () => {
    if (!user) return;
    try {
      const serverCart = await api(endpoints.cart);
      const guest = readGuestCart();
      if (guest.items.length) {
        const merged = [...serverCart.items];
        guest.items.forEach((guestItem) => {
          const found = merged.find((item) => item.productId === guestItem.productId && item.size === guestItem.size && item.color === guestItem.color);
          if (found) found.quantity += guestItem.quantity;
          else merged.push({ productId: guestItem.productId, quantity: guestItem.quantity, size: guestItem.size, color: guestItem.color });
        });
        try {
          setCart(await api(endpoints.cart, { method: "PUT", body: { items: merged.map(({ productId, quantity, size, color }) => ({ productId, quantity, size, color })) } }));
          clearGuestCart();
        } catch {
          setCart(serverCart);
          showNotice("Một số sản phẩm trong giỏ tạm không còn đủ hàng.", "error");
        }
      } else setCart(serverCart);
    } catch (error) {
      if (error.status !== 401) showNotice(error.message, "error");
    }
  };
  const showNotice = (message, type = "success") => {
    setNotice({ message, type });
    window.setTimeout(() => setNotice(null), 4200);
  };

  useEffect(() => {
    api(endpoints.categories)
      .then(setCategories)
      .catch(() => null);
    refreshSession().then((session) => {
      if (session?.customer) setUser(session.customer);
    });
  }, []);
  useEffect(() => {
    if (catalogPage) loadProducts();
  }, [
    catalogPage,
    filters.keyword,
    filters.category,
    filters.minPrice,
    filters.maxPrice,
    filters.sort,
    filters.page,
  ]);
  useEffect(() => {
    if (user) loadCart();
    else {
      setCart(readGuestCart());
      setWishlist([]);
    }
    if (user) {
      api(endpoints.wishlist).then(setWishlist).catch(() => setWishlist([]));
      api(endpoints.notifications).then((data) => setNotificationCount(data.unreadCount || 0)).catch(() => null);
    }
  }, [user]);

  const chooseCategory = (category) =>
    setFilters((current) => ({ ...current, category, page: 0 }));
  const search = (event) => {
    event?.preventDefault();
    setFilters((current) => ({ ...current, keyword: searchInput, page: 0 }));
  };
  const openCatalog = (category = "", options = {}) => {
    const nextFilters = {
      ...filters,
      keyword: "",
      minPrice: "",
      maxPrice: "",
      ...options,
      category,
      page: 0,
    };
    const params = new URLSearchParams();
    if (category) params.set("category", category);
    if (nextFilters.minPrice) params.set("minPrice", nextFilters.minPrice);
    if (nextFilters.maxPrice) params.set("maxPrice", nextFilters.maxPrice);
    setMobileMenuOpen(false);
    setCatalogPage(true);
    setFilters(nextFilters);
    window.history.pushState(
      {},
      "",
      `/san-pham${params.toString() ? `?${params.toString()}` : ""}`,
    );
    window.scrollTo({ top: 0, behavior: "smooth" });
  };
  const goHome = (event) => {
    event?.preventDefault();
    setMobileMenuOpen(false);
    setCatalogPage(false);
    setSearchInput("");
    setFilters((current) => ({
      ...current,
      keyword: "",
      category: "",
      minPrice: "",
      maxPrice: "",
      page: 0,
    }));
    window.history.pushState({}, "", "/");
    window.scrollTo({ top: 0, behavior: "smooth" });
  };
  useEffect(() => {
    const syncRoute = () => {
      const isCatalog = window.location.pathname === "/san-pham";
      const params = new URLSearchParams(window.location.search);
      setCatalogPage(isCatalog);
      setFilters((current) => ({
        ...current,
        category: isCatalog ? params.get("category") || "" : "",
        minPrice: isCatalog ? params.get("minPrice") || "" : "",
        maxPrice: isCatalog ? params.get("maxPrice") || "" : "",
        page: 0,
      }));
    };
    window.addEventListener("popstate", syncRoute);
    return () => window.removeEventListener("popstate", syncRoute);
  }, []);
  const openProduct = async (product) => {
    try {
      setSelectedProduct({
        product: await api(endpoints.product(product.id)),
        feedback: [],
      });
      const feedback = await api(endpoints.feedback(product.id));
      setSelectedProduct((current) =>
        current ? { ...current, feedback } : current,
      );
    } catch (error) {
      showNotice(error.message, "error");
    }
  };
  const localCart = (product, quantity = 1, variant = {}) => {
    const existing = cart.items.find(
      (item) =>
        item.productId === product.id &&
        item.size === variant.size &&
        item.color === variant.color,
    );
    const items = existing
      ? cart.items.map((item) =>
          item.productId === product.id &&
          item.size === variant.size &&
          item.color === variant.color
            ? {
                ...item,
                quantity: item.quantity + quantity,
                lineTotal: Number(item.unitPrice) * (item.quantity + quantity),
              }
            : item,
        )
      : [
          ...cart.items,
          {
            productId: product.id,
            name: product.name,
            imageUrl: product.imageUrl,
            unitPrice: product.salePrice || product.price,
            quantity,
            lineTotal: Number(product.salePrice || product.price) * quantity,
            stock: product.stock,
            size: variant.size,
            color: variant.color,
          },
        ];
    const subtotal = items.reduce(
      (sum, item) => sum + Number(item.lineTotal),
      0,
    );
    const nextCart = {
      items,
      subtotal,
      itemCount: items.reduce((sum, item) => sum + item.quantity, 0),
    };
    setCart(nextCart);
    saveGuestCart(nextCart);
  };
  const addToCart = async (product, variant = {}, quantity = 1) => {
    const requestedQuantity = Math.max(1, Number(quantity) || 1);
    const existing = cart.items.find(
      (item) =>
        item.productId === product.id &&
        item.size === variant.size &&
        item.color === variant.color,
    );
    if (existing && existing.quantity + requestedQuantity > product.stock) {
      showNotice(`Sản phẩm chỉ còn ${product.stock} sản phẩm trong kho.`, "error");
      return;
    }
    if (!product.stock || requestedQuantity > product.stock) {
      showNotice("Sản phẩm đã hết hàng hoặc không đủ số lượng.", "error");
      return;
    }
    if (!user) {
      localCart(product, requestedQuantity, variant);
      setCartOpen(true);
      showNotice("Đã thêm sản phẩm vào giỏ tạm. Đăng nhập khi thanh toán nhé.");
      return;
    }
    try {
      setCart(
        await api(endpoints.addCart, {
          method: "POST",
          body: { productId: product.id, quantity: requestedQuantity, ...variant },
        }),
      );
      setCartOpen(true);
      showNotice("Đã thêm vào giỏ hàng.");
    } catch (error) {
      showNotice(error.message, "error");
    }
  };
  const updateCart = async (line, quantity) => {
    const nextQuantity = Math.max(0, Number(quantity) || 0);
    const sameLine = (item) =>
      item.productId === line.productId &&
      item.size === line.size &&
      item.color === line.color;
    if (nextQuantity > line.stock) {
      showNotice(`Sản phẩm chỉ còn ${line.stock} sản phẩm trong kho.`, "error");
      return;
    }
    if (!user) {
      const items = cart.items.filter((item) => !sameLine(item));
      const nextItems = nextQuantity < 1 ? items : cart.items.map((item) => sameLine(item) ? { ...item, quantity: nextQuantity, lineTotal: Number(item.unitPrice) * nextQuantity } : item);
      const nextCart = {
        items: nextItems,
        subtotal: nextItems.reduce((sum, item) => sum + Number(item.unitPrice) * item.quantity, 0),
        itemCount: nextItems.reduce((sum, item) => sum + item.quantity, 0),
      };
      setCart(nextCart);
      saveGuestCart(nextCart);
    } else {
      const updateKey = `${line.productId}|${line.size || ""}|${line.color || ""}`;
      if (cartUpdateLocks.current.has(updateKey)) return;
      cartUpdateLocks.current.add(updateKey);
      setUpdatingCartKeys((current) => [...current, updateKey]);
      try {
        setCart(
          await api(endpoints.cart, {
            method: "PUT",
            body: {
              items: cart.items
                .map((item) => ({
                  productId: item.productId,
                  quantity: sameLine(item) ? nextQuantity : item.quantity,
                  size: item.size,
                  color: item.color,
                }))
                .filter((item) => item.quantity > 0),
            },
          }),
        );
      } catch (error) {
        showNotice(error.message, "error");
      } finally {
        cartUpdateLocks.current.delete(updateKey);
        setUpdatingCartKeys((current) => current.filter((key) => key !== updateKey));
      }
    }
  };
  const toggleWishlist = async (product) => {
    if (!user) {
      openAuth("wishlist");
      return;
    }
    const exists = wishlist.some((item) => item.productId === product.id);
    try {
      if (exists) {
        await api(endpoints.wishlistItem(product.id), { method: "DELETE" });
        setWishlist((current) => current.filter((item) => item.productId !== product.id));
        showNotice("Đã bỏ khỏi danh sách yêu thích.");
      } else {
        const saved = await api(endpoints.wishlistItem(product.id), { method: "POST" });
        setWishlist((current) => [saved, ...current]);
        showNotice("Đã thêm vào danh sách yêu thích.");
      }
    } catch (error) {
      showNotice(error.message, "error");
    }
  };
  const reloadNotifications = async () => {
    if (!user) return;
    try {
      const data = await api(endpoints.notifications);
      setNotificationCount(data.unreadCount || 0);
    } catch { /* thông báo không làm gián đoạn việc mua hàng */ }
  };
  const startCheckout = () => {
    if (!cart.items.length) return showNotice("Giỏ hàng đang trống.", "error");
    if (!user) {
      setCartOpen(false);
      openAuth("checkout");
      return;
    }
    setCartOpen(false);
    setCheckoutOpen(true);
  };
  const afterAuth = (customer) => {
    const nextAction = authAfterLogin;
    setUser(customer);
    setAuthOpen(false);
    setAuthAfterLogin(null);
    if (nextAction === "orders") setOrdersOpen(true);
    if (nextAction === "profile") setProfileOpen(true);
    if (nextAction === "wishlist") setWishlistOpen(true);
    if (nextAction === "notifications") setNotificationsOpen(true);
    if (nextAction === "checkout") setCheckoutOpen(true);
    showNotice(`Chào mừng ${customer.fullName}!`);
  };
  const logout = async () => {
    await signOut();
    setUser(null);
    setProfileOpen(false);
      setCart(readGuestCart());
    showNotice("Bạn đã đăng xuất.");
  };

  return (
    <div className={catalogPage ? "site-shell catalog-page" : "site-shell"}>
      <div className="announcement">
        MIỄN PHÍ VẬN CHUYỂN ĐƠN TỪ 699.000₫ <span>•</span> ĐỔI SIZE TRONG 30
        NGÀY
      </div>
      <header className="site-header">
        <button
          className="mobile-menu icon-button"
          aria-label={mobileMenuOpen ? "Đóng menu" : "Mở menu"}
          aria-expanded={mobileMenuOpen}
          onClick={() => setMobileMenuOpen((value) => !value)}
        >
          {mobileMenuOpen ? (
            <X size={22} strokeWidth={1.8} />
          ) : (
            <Menu size={22} strokeWidth={1.8} />
          )}
        </button>
        <a className="wordmark" href="/" onClick={goHome}>
          ANH LỚN <em>SHOP</em>
        </a>
        <nav
          className={`main-nav ${mobileMenuOpen ? "is-open" : ""}`}
          aria-label="Điều hướng chính"
        >
          <button onClick={() => openCatalog("")}>HÀNG MỚI</button>
          <button onClick={() => openCatalog("Áo khoác")}>ÁO KHOÁC</button>
          <button onClick={() => openCatalog("Áo thun")}>ÁO THUN</button>
          <button onClick={() => openCatalog("Áo polo")}>ÁO POLO</button>
          <button onClick={() => openCatalog("Quần")}>QUẦN</button>
          <button
            className="sale-link"
            onClick={() =>
              openCatalog("", { minPrice: "", maxPrice: "500000" })
            }
          >
            ƯU ĐÃI
          </button>
        </nav>
        <div className="header-actions">
          <button
            className="icon-button"
            aria-label="Tìm kiếm"
            onClick={() => openCatalog("")}
          >
            <Search size={20} strokeWidth={1.8} />
          </button>
          <button
            className="icon-button notification-button"
            aria-label="Thông báo"
            title="Thông báo"
            onClick={() => user ? setNotificationsOpen(true) : openAuth("notifications")}
          >
            <Bell size={22} strokeWidth={1.8} />
            {user && notificationCount > 0 && <span>{notificationCount > 9 ? "9+" : notificationCount}</span>}
          </button>
          <button
            className="icon-button"
            aria-label="Sản phẩm yêu thích"
            title="Sản phẩm yêu thích"
            onClick={() => user ? setWishlistOpen(true) : openAuth("wishlist")}
          >
            <Heart size={22} strokeWidth={1.8} />
          </button>
          <button
            className={`icon-button account-button${user ? " signed-in" : ""}`}
            aria-label="Tài khoản"
            title={user ? `Tài khoản của ${user.fullName}` : "Đăng nhập tài khoản"}
            onClick={() => (user ? setProfileOpen(true) : openAuth("profile"))}
          >
            <UserRound size={28} strokeWidth={1.8} />
            {user && <span className="account-status-dot" aria-hidden="true" />}
          </button>
          <button
            className="cart-button icon-button"
            aria-label="Giỏ hàng"
            onClick={() => setCartOpen(true)}
          >
            <ShoppingBag size={20} strokeWidth={1.8} />
            <span>{cart.itemCount || 0}</span>
          </button>
        </div>
      </header>

      <main id="top">
        <section className="hero">
          <div className="hero-copy">
            <p className="kicker">BỘ SƯU TẬP THU ĐÔNG 2026</p>
            <h1>
              ĐI CÙNG
              <br />
              <i>CHẤT RIÊNG.</i>
            </h1>
            <p className="hero-description">
              Những thiết kế nam hiện đại, thoải mái và đủ linh hoạt cho mọi
              nhịp sống thành thị.
            </p>
            <button
              className="button button-light"
              onClick={() => openCatalog("")}
            >
              KHÁM PHÁ BỘ SƯU TẬP <ArrowUpRight size={16} />
            </button>
          </div>
          <div className="hero-note">
            <span>01</span>
            <div>
              <b>DÁNG / CÔNG NĂNG</b>
              <small>Thiết kế có chủ đích</small>
            </div>
          </div>
        </section>
        <section className="category-strip">
          <div className="section-intro">
            <p className="kicker">ĐƯỢC CHỌN CHO BẠN</p>
            <h2>
              Mặc đẹp, sống
              <br />
              <i>đúng nhịp.</i>
            </h2>
          </div>
          <div
            className="category-card jacket"
            onClick={() => openCatalog("Áo khoác")}
          >
            <div>
              <span>01</span>
              <h3>ÁO KHOÁC</h3>
              <small>Che chắn có phong cách →</small>
            </div>
          </div>
          <div
            className="category-card everyday"
            onClick={() => openCatalog("Áo thun")}
          >
            <div>
              <span>02</span>
              <h3>HẰNG NGÀY</h3>
              <small>Nền tảng cho tủ đồ →</small>
            </div>
          </div>
          <div
            className="category-card essential"
            onClick={() => openCatalog("Áo polo")}
          >
            <div>
              <span>03</span>
              <h3>THIẾT YẾU</h3>
              <small>Tối giản & tinh tế →</small>
            </div>
          </div>
        </section>

        <section className="home-catalog-callout">
          <p className="kicker">TỦ ĐỒ NAM / BỘ SƯU TẬP MỚI</p>
          <h2>
            Những món đồ <i>đáng có.</i>
          </h2>
          <p>
            Khám phá toàn bộ thiết kế nam của Anh Lớn Shop, được tuyển chọn để
            phối đồ dễ dàng mỗi ngày.
          </p>
          <button
            className="button button-dark"
            onClick={() => openCatalog("")}
          >
            XEM TẤT CẢ SẢN PHẨM <ArrowUpRight size={16} />
          </button>
        </section>

        <section className="catalog-section" id="catalog">
          <div className="catalog-head">
            <div>
              <p className="kicker">TỦ ĐỒ NAM</p>
              <h2>
                Những món đồ <i>đáng có.</i>
              </h2>
              <p className="catalog-count">
                {products.totalElements || 0} sản phẩm được tuyển chọn
              </p>
            </div>
            <form className="catalog-search-wrap" onSubmit={search}>
              <Search size={19} aria-hidden="true" />
              <input
                className="catalog-search"
                value={searchInput}
                onChange={(event) => setSearchInput(event.target.value)}
                placeholder="Tìm kiếm sản phẩm..."
              />
              <button>TÌM</button>
            </form>
          </div>
          <div className="filter-bar">
            <div className="filter-pills">
              <button
                className={!filters.category ? "active" : ""}
                onClick={() => chooseCategory("")}
              >
                TẤT CẢ
              </button>
              {categories.slice(0, 5).map((category) => (
                <button
                  className={filters.category === category ? "active" : ""}
                  key={category}
                  onClick={() => chooseCategory(category)}
                >
                  {category.toUpperCase()}
                </button>
              ))}
            </div>
            <div className="filter-tools">
              <select
                aria-label="Sắp xếp sản phẩm"
                value={filters.sort}
                onChange={(event) =>
                  setFilters((current) => ({
                    ...current,
                    sort: event.target.value,
                    page: 0,
                  }))
                }
              >
                <option value="newest">MỚI NHẤT</option>
                <option value="price_asc">GIÁ TĂNG DẦN</option>
                <option value="price_desc">GIÁ GIẢM DẦN</option>
              </select>
              <button
                className="filter-toggle"
                onClick={() => setFiltersOpen((value) => !value)}
              >
                <SlidersHorizontal size={16} /> BỘ LỌC{" "}
                {filtersOpen ? <X size={15} /> : null}
              </button>
            </div>
          </div>
          {filtersOpen && (
            <div className="filter-panel">
              <label>
                GIÁ TỪ
                <input
                  type="number"
                  min="0"
                  value={filters.minPrice}
                  placeholder="0"
                  onChange={(event) =>
                    setFilters((current) => ({
                      ...current,
                      minPrice: event.target.value,
                      page: 0,
                    }))
                  }
                />
              </label>
              <span>—</span>
              <label>
                ĐẾN
                <input
                  type="number"
                  min="0"
                  value={filters.maxPrice}
                  placeholder="Không giới hạn"
                  onChange={(event) =>
                    setFilters((current) => ({
                      ...current,
                      maxPrice: event.target.value,
                      page: 0,
                    }))
                  }
                />
              </label>
              <button
                type="button"
                onClick={() =>
                  setFilters((current) => ({
                    ...current,
                    minPrice: "",
                    maxPrice: "",
                    page: 0,
                  }))
                }
              >
                XOÁ GIÁ
              </button>
            </div>
          )}
          {loading ? (
            <div className="loading-grid">
              {[1, 2, 3, 4].map((item) => (
                <div className="skeleton" key={item} />
              ))}
            </div>
          ) : (
            <div className="product-grid">
              {products.content?.map((product) => (
                <ProductCard
                  key={product.id}
                  product={product}
                  onOpen={() => openProduct(product)}
                  onAdd={() => addToCart(product, { size: csv(product.sizes)[0], color: csv(product.colors)[0] })}
                  isWishlisted={wishlist.some((item) => item.productId === product.id)}
                  onWishlist={() => toggleWishlist(product)}
                />
              ))}
            </div>
          )}
          {!loading && catalogError ? (
            <div className="empty-state error-state">
              <CircleAlert size={34} />
              <h3>Chưa kết nối được cửa hàng</h3>
              <p>{catalogError}</p>
              <button className="button button-dark" onClick={loadProducts}>
                THỬ KẾT NỐI LẠI
              </button>
            </div>
          ) : (
            !loading &&
            !products.content?.length && (
              <div className="empty-state">
                <Search size={34} />
                <h3>Chưa tìm thấy sản phẩm</h3>
                <p>Thử xoá bớt bộ lọc hoặc tìm kiếm bằng từ khoá khác.</p>
                <button
                  className="button button-dark"
                  onClick={() => {
                    setSearchInput("");
                    setFilters({
                      keyword: "",
                      category: "",
                      minPrice: "",
                      maxPrice: "",
                      gender: "NAM",
                      sort: "newest",
                      page: 0,
                    });
                  }}
                >
                  XOÁ BỘ LỌC
                </button>
              </div>
            )
          )}
          {products.totalPages > 1 && (
            <div className="pagination">
              <button
                disabled={filters.page === 0}
                onClick={() =>
                  setFilters((current) => ({
                    ...current,
                    page: current.page - 1,
                  }))
                }
              >
                <ChevronRight size={17} className="rotate-180" aria-label="Trang trước" />
              </button>
              <span>
                TRANG {filters.page + 1} / {products.totalPages}
              </span>
              <button
                disabled={filters.page + 1 >= products.totalPages}
                onClick={() =>
                  setFilters((current) => ({
                    ...current,
                    page: current.page + 1,
                  }))
                }
              >
                <ChevronRight size={17} aria-label="Trang sau" />
              </button>
            </div>
          )}
        </section>
        <section className="promise">
          <div>
            <ShieldCheck size={24} />
            <b>THIẾT KẾ CÓ CHỦ ĐÍCH</b>
            <small>Từng đường cắt đều có lý do</small>
          </div>
          <div>
            <RefreshCw size={24} />
            <b>ĐỔI SIZE DỄ DÀNG</b>
            <small>Trong vòng 30 ngày</small>
          </div>
          <div>
            <Truck size={24} />
            <b>GIAO HÀNG TOÀN QUỐC</b>
            <small>Đóng gói chỉn chu</small>
          </div>
          <div>
            <Headphones size={24} />
            <b>HỖ TRỢ TẬN TÂM</b>
            <small>Luôn sẵn sàng lắng nghe</small>
          </div>
        </section>
      </main>
      <footer className="footer">
        <div className="footer-brand">
          <a className="wordmark" href="/" onClick={goHome}>
            ANH LỚN <em>SHOP</em>
          </a>
          <p>Quần áo nam hiện đại cho những người luôn chuyển động.</p>
          <small>© 2026 ANH LỚN SHOP. BẢO LƯU MỌI QUYỀN.</small>
        </div>
        <div>
          <b>KHÁM PHÁ</b>
          <button onClick={() => openCatalog("")}>Hàng mới</button>
          <button onClick={() => openCatalog("Áo khoác")}>Áo khoác</button>
          <button onClick={() => openCatalog("Áo thun")}>Áo thun</button>
        </div>
        <div>
          <b>HỖ TRỢ</b>
          <button
            onClick={() => (user ? setOrdersOpen(true) : openAuth("orders"))}
          >
            Đơn hàng của tôi
          </button>
          <button onClick={() => setSurveysOpen(true)}>
            Khảo sát phong cách
          </button>
          <button onClick={() => (user ? setProfileOpen(true) : openAuth("profile"))}>
            Tài khoản của tôi
          </button>
        </div>
        <div>
          <b>THEO DÕI</b>
          <p className="socials">IG &nbsp; FB &nbsp; TT</p>
          <small>Nhận tin mới và ưu đãi riêng.</small>
        </div>
      </footer>

      {notice && (
        <div className={`toast ${notice.type}`}>
          <span>
            {notice.type === "error" ? (
              <CircleAlert size={18} />
            ) : (
              <Check size={18} />
            )}
          </span>
          {notice.message}
        </div>
      )}
      {selectedProduct && (
          <ProductModal
            data={selectedProduct}
            user={user}
            onClose={() => setSelectedProduct(null)}
            onAdd={(variant, quantity) =>
              addToCart(selectedProduct.product, variant, quantity)
            }
            onLogin={() => {
              setSelectedProduct(null);
              setAuthMode("login");
              setAuthOpen(true);
            }}
            onNotice={showNotice}
          />
      )}
      {cartOpen && (
        <CartDrawer
          cart={cart}
          onClose={() => setCartOpen(false)}
          onUpdate={updateCart}
          updatingKeys={updatingCartKeys}
          onCheckout={startCheckout}
        />
      )}
      {authOpen && (
        <AuthModal
          mode={authMode}
          onModeChange={setAuthMode}
          onClose={() => setAuthOpen(false)}
          onAuthenticated={afterAuth}
          onNotice={showNotice}
        />
      )}
      {checkoutOpen && (
        <EnhancedCheckoutModal
          cart={cart}
          user={user}
          onClose={() => setCheckoutOpen(false)}
          onComplete={async () => {
            setCheckoutOpen(false);
            clearGuestCart();
            setCart(emptyCart());
            showNotice(
              "Đặt hàng thành công! Bạn có thể theo dõi trạng thái trong mục đơn hàng.",
            );
          }}
          onNotice={showNotice}
        />
      )}
      {ordersOpen && (
        <OrdersModal
          onClose={() => setOrdersOpen(false)}
          onNotice={showNotice}
          onChanged={reloadNotifications}
        />
      )}
      {profileOpen && (
        <ProfileModal
          user={user}
           onClose={() => setProfileOpen(false)}
           onUser={setUser}
           onLogout={logout}
           onOpenOrders={() => {
             setProfileOpen(false);
             setOrdersOpen(true);
           }}
           onOpenWishlist={() => {
             setProfileOpen(false);
             setWishlistOpen(true);
           }}
           onOpenNotifications={() => {
             setProfileOpen(false);
             setNotificationsOpen(true);
           }}
           onOpenSurveys={() => {
            setProfileOpen(false);
            setSurveysOpen(true);
          }}
          onOpenAddresses={() => {
            setProfileOpen(false);
            setAddressesOpen(true);
          }}
          onOpenPassword={() => {
            setProfileOpen(false);
            setPasswordOpen(true);
          }}
          onNotice={showNotice}
        />
      )}
      {surveysOpen && (
        <SurveyModal
          user={user}
          onClose={() => setSurveysOpen(false)}
          onLogin={() => {
            setSurveysOpen(false);
            setAuthOpen(true);
          }}
          onNotice={showNotice}
        />
      )}
      {wishlistOpen && <WishlistModal items={wishlist} onClose={() => setWishlistOpen(false)} onRemove={(item) => toggleWishlist(item)} onOpen={openProduct} onNotice={showNotice} />}
      {addressesOpen && <AddressModal onClose={() => setAddressesOpen(false)} onNotice={showNotice} />}
      {notificationsOpen && <NotificationsModal onClose={() => setNotificationsOpen(false)} onNotice={showNotice} onCount={setNotificationCount} />}
      {passwordOpen && <PasswordModal onClose={() => setPasswordOpen(false)} onNotice={showNotice} />}
    </div>
  );
}

function ProductCard({ product, onOpen, onAdd, isWishlisted, onWishlist }) {
  const sale = discount(product);
  return (
    <article className="product-card">
      <div className="product-image" onClick={onOpen}>
        <img
          src={imageSrc(product.imageUrl)}
          onError={protectImage}
          alt={product.name}
          loading="lazy"
        />
        <div className="product-labels">
          {product.badge && <span>{product.badge}</span>}
          {sale > 0 && <span className="sale-badge">-{sale}%</span>}
        </div>
        <button
          className="quick-add"
          aria-label={`Thêm ${product.name} vào giỏ`}
          onClick={(event) => {
            event.stopPropagation();
            onAdd();
          }}
        >
          THÊM VÀO GIỎ <Plus size={15} />
        </button>
        <button
          className={isWishlisted ? "heart active" : "heart"}
          aria-label={isWishlisted ? "Bỏ khỏi yêu thích" : "Thêm vào yêu thích"}
          onClick={(event) => {
            event.stopPropagation();
            onWishlist();
          }}
        >
          <Heart size={19} fill={isWishlisted ? "currentColor" : "none"} />
        </button>
      </div>
      <div className="product-info" onClick={onOpen}>
        <div className="product-meta">
          <span>{product.category || "HÀNG NAM"}</span>
          <span>{product.stock > 0 ? "CÒN HÀNG" : "HẾT HÀNG"}</span>
        </div>
        <h3>{product.name}</h3>
        <div className="price-row">
          <strong>{money(product.salePrice || product.price)}</strong>
          {sale > 0 && <del>{money(product.price)}</del>}
        </div>
      </div>
    </article>
  );
}

function ProductModal({ data, user, onClose, onAdd, onLogin, onNotice }) {
  const product = data.product;
  const [size, setSize] = useState(
    csv(product.sizes)[1] || csv(product.sizes)[0] || "M",
  );
  const [color, setColor] = useState(csv(product.colors)[0] || "Đen");
  const [quantity, setQuantity] = useState(1);
  const [feedback, setFeedback] = useState({ rating: 5, comment: "" });
  const [sending, setSending] = useState(false);
  const sale = discount(product);
  const submitFeedback = async (event) => {
    event.preventDefault();
    if (!user) return onLogin();
    setSending(true);
    try {
      await api(endpoints.feedback(product.id), {
        method: "POST",
        body: feedback,
      });
      onNotice("Đã gửi đánh giá, cảm ơn bạn!");
      const next = await api(endpoints.feedback(product.id));
      setFeedback({ rating: 5, comment: "" });
      data.feedback = next;
    } catch (error) {
      onNotice(error.message, "error");
    } finally {
      setSending(false);
    }
  };
  return (
    <div
      className="modal-backdrop"
      onMouseDown={(event) => event.target === event.currentTarget && onClose()}
    >
      <div className="product-modal">
        <button
          className="close-button"
          aria-label="Đóng chi tiết sản phẩm"
          onClick={onClose}
        >
          <X size={20} />
        </button>
        <div className="product-modal-image">
          <img
            src={imageSrc(product.imageUrl)}
            onError={protectImage}
            alt={product.name}
          />
          {product.badge && <span>{product.badge}</span>}
        </div>
        <div className="product-modal-content">
          <p className="kicker">
            {product.category || "HÀNG NAM"} / ANH LỚN SHOP
          </p>
          <h2>{product.name}</h2>
          <div className="modal-price">
            <strong>{money(product.salePrice || product.price)}</strong>
            {sale > 0 && (
              <>
                <del>{money(product.price)}</del>
                <em>Tiết kiệm {sale}%</em>
              </>
            )}
          </div>
          <p className="modal-description">
            {product.description ||
              "Thiết kế cân bằng giữa công năng và vẻ ngoài hiện đại, phù hợp cho tủ đồ hằng ngày."}
          </p>
          <div className="choice">
            <label>
              MÀU SẮC <b>{color}</b>
            </label>
            <div className="choice-row">
              {csv(product.colors).map((item) => (
                <button
                  className={
                    color === item ? "choice-chip active" : "choice-chip"
                  }
                  key={item}
                  onClick={() => setColor(item)}
                >
                  {item}
                </button>
              ))}
            </div>
          </div>
          <div className="choice">
            <label>
              KÍCH THƯỚC <b>{size}</b>
            </label>
            <div className="choice-row">
              {csv(product.sizes).map((item) => (
                <button
                  className={
                    size === item ? "choice-chip active" : "choice-chip"
                  }
                  key={item}
                  onClick={() => setSize(item)}
                >
                  {item}
                </button>
              ))}
            </div>
            <small className="size-help">
              Không chắc size? Xem bảng hướng dẫn kích thước{" "}
              <ChevronRight size={14} />
            </small>
          </div>
          <div className="product-quantity">
            <label>
              SỐ LƯỢNG <b>{quantity}</b>
            </label>
            <div className="quantity product-quantity-control">
              <button
                type="button"
                aria-label="Giảm số lượng sản phẩm"
                disabled={quantity <= 1}
                onClick={() =>
                  setQuantity((current) => Math.max(1, current - 1))
                }
              >
                <Minus size={17} />
              </button>
              <span>{quantity}</span>
              <button
                type="button"
                aria-label="Tăng số lượng sản phẩm"
                disabled={quantity >= product.stock}
                onClick={() =>
                  setQuantity((current) => Math.min(product.stock, current + 1))
                }
              >
                <Plus size={17} />
              </button>
            </div>
            <small>Còn {product.stock} sản phẩm trong kho</small>
          </div>
          <button
            className="button button-dark add-modal"
            disabled={!product.stock}
            onClick={() => {
              onAdd({ size, color }, quantity);
              onClose();
            }}
          >
            {product.stock
              ? `THÊM ${quantity} VÀO GIỎ — ${money((product.salePrice || product.price) * quantity)}`
              : "SẢN PHẨM TẠM HẾT HÀNG"}
          </button>
          <div className="product-details">
            <span>
              <ShieldCheck size={15} />{" "}
              {product.material || "Chất liệu chọn lọc"}
            </span>
            <span>
              <RefreshCw size={15} /> Đổi size trong 30 ngày
            </span>
            <span>
              <Truck size={15} /> Giao hàng toàn quốc
            </span>
          </div>
          <div className="feedback-section">
            <div className="feedback-heading">
              <h3>ĐÁNH GIÁ KHÁCH HÀNG</h3>
              <span>
                {data.feedback.length
                  ? `(${data.feedback.length})`
                  : "Chưa có đánh giá"}
              </span>
            </div>
            {data.feedback.slice(0, 3).map((item) => (
              <div className="feedback-item" key={item.id}>
                <div>
                  <b>{item.customerName}</b>
                  <span>
                    {Array.from({ length: item.rating }, (_, index) => (
                      <Star key={`on-${index}`} size={14} fill="currentColor" />
                    ))}
                    {Array.from({ length: 5 - item.rating }, (_, index) => (
                      <Star key={`off-${index}`} size={14} />
                    ))}
                  </span>
                </div>
                <p>{item.comment || "Sản phẩm rất ổn."}</p>
              </div>
            ))}
            <form className="feedback-form" onSubmit={submitFeedback}>
              <div className="rating-input">
                {[1, 2, 3, 4, 5].map((value) => (
                  <button
                    type="button"
                    aria-label={`${value} sao`}
                    key={value}
                    className={value <= feedback.rating ? "chosen" : ""}
                    onClick={() =>
                      setFeedback((current) => ({ ...current, rating: value }))
                    }
                  >
                    <Star
                      size={17}
                      fill={value <= feedback.rating ? "currentColor" : "none"}
                    />
                  </button>
                ))}
              </div>
              <textarea
                value={feedback.comment}
                onChange={(event) =>
                  setFeedback((current) => ({
                    ...current,
                    comment: event.target.value,
                  }))
                }
                placeholder={
                  user
                    ? "Chia sẻ trải nghiệm của bạn sau khi mua..."
                    : "Đăng nhập để viết đánh giá"
                }
                maxLength="4000"
                disabled={!user}
              />
              <small className="feedback-help">
                Chỉ khách hàng đã mua sản phẩm mới có thể gửi đánh giá.
              </small>
              <button disabled={sending}>
                {!user ? "ĐĂNG NHẬP ĐỂ ĐÁNH GIÁ" : sending ? "ĐANG GỬI..." : "GỬI ĐÁNH GIÁ"}
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}

function CartDrawer({ cart, onClose, onUpdate, updatingKeys = [], onCheckout }) {
  return (
    <div
      className="drawer-backdrop"
      onMouseDown={(event) => event.target === event.currentTarget && onClose()}
    >
      <aside className="cart-drawer">
        <div className="drawer-head">
          <div>
            <p className="kicker">GIỎ HÀNG CỦA BẠN</p>
            <h2>{cart.itemCount || 0} sản phẩm</h2>
          </div>
          <button type="button" className="close-button" aria-label="Đóng giỏ hàng" onClick={onClose}>
            <X size={20} />
          </button>
        </div>
        <div className="cart-items">
          {cart.items.length ? (
            cart.items.map((item) => (
              <div
                className="cart-line"
                key={`${item.productId}-${item.size || ""}-${item.color || ""}`}
              >
                <img
                  src={imageSrc(item.imageUrl)}
                  onError={protectImage}
                  alt={item.name}
                />
                <div className="cart-line-info">
                  <h3>{item.name}</h3>
                  <small>{money(item.unitPrice)}</small>
                  <div className="quantity">
                    <button
                      type="button"
                      aria-label={`Giảm số lượng ${item.name}`}
                      disabled={updatingKeys.includes(`${item.productId}|${item.size || ""}|${item.color || ""}`)}
                      onClick={() => onUpdate(item, item.quantity - 1)}
                    >
                      <Minus size={15} />
                    </button>
                    <span>{item.quantity}</span>
                    <button
                      type="button"
                      aria-label={`Tăng số lượng ${item.name}`}
                      disabled={
                        item.quantity >= item.stock ||
                        updatingKeys.includes(`${item.productId}|${item.size || ""}|${item.color || ""}`)
                      }
                      onClick={() => onUpdate(item, item.quantity + 1)}
                    >
                      <Plus size={15} />
                    </button>
                  </div>
                </div>
                <strong>{money(item.lineTotal)}</strong>
                <button
                  className="remove-line"
                  aria-label={`Xóa ${item.name} khỏi giỏ hàng`}
                  type="button"
                  disabled={updatingKeys.includes(`${item.productId}|${item.size || ""}|${item.color || ""}`)}
                  onClick={() => onUpdate(item, 0)}
                >
                  <Trash2 size={17} />
                </button>
              </div>
            ))
          ) : (
            <div className="drawer-empty">
              <PackageOpen size={38} />
              <h3>Giỏ hàng còn trống</h3>
              <p>Thêm một món đồ bạn thích để bắt đầu.</p>
            </div>
          )}
        </div>
        <div className="drawer-foot">
          <div className="subtotal">
            <span>TẠM TÍNH</span>
            <strong>{money(cart.subtotal)}</strong>
          </div>
          <p>Phí vận chuyển sẽ được tính ở bước thanh toán.</p>
          <button
            className="button button-dark"
            disabled={!cart.items.length}
            onClick={onCheckout}
          >
            TIẾN HÀNH ĐẶT HÀNG <ArrowUpRight size={16} />
          </button>
          <button type="button" className="continue-shopping" onClick={onClose}>
            Tiếp tục mua sắm
          </button>
        </div>
      </aside>
    </div>
  );
}

function AuthModal({ mode, onModeChange, onClose, onAuthenticated, onNotice }) {
  const [form, setForm] = useState({
    email: "",
    password: "",
    fullName: "",
    phone: "",
    age: "",
  });
  const [busy, setBusy] = useState(false);
  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    try {
      if (mode === "register") {
        await signUp({
          ...form,
          age: form.age === "" ? null : Number(form.age),
        });
        onNotice("Tạo tài khoản thành công.");
      }
      const data = await signIn({ email: form.email, password: form.password });
      onAuthenticated(data.customer);
    } catch (error) {
      onNotice(error.message, "error");
    } finally {
      setBusy(false);
    }
  };
  return (
    <div
      className="modal-backdrop"
      onMouseDown={(event) => event.target === event.currentTarget && onClose()}
    >
      <div className="auth-modal">
        <button className="close-button" aria-label="Đóng đăng nhập" onClick={onClose}>
          <X size={20} />
        </button>
        <div className="auth-mark">
          AL<span>SHOP</span>
        </div>
        <p className="kicker">ANH LỚN SHOP</p>
        <h2>
          {mode === "login" ? "Chào mừng trở lại." : "Tạo tài khoản mới."}
        </h2>
        <p className="auth-subtitle">
          {mode === "login"
            ? "Đăng nhập để quản lý đơn hàng và lưu những món đồ yêu thích."
            : "Tham gia cộng đồng những người mặc đẹp theo cách riêng."}
        </p>
        <form onSubmit={submit}>
          {mode === "register" && (
            <>
              <label>
                HỌ VÀ TÊN
                <input
                  required
                  value={form.fullName}
                  onChange={(event) =>
                    setForm({ ...form, fullName: event.target.value })
                  }
                  placeholder="Nguyễn Minh Khang"
                />
              </label>
              <label>
                SỐ ĐIỆN THOẠI
                <input
                  value={form.phone}
                  onChange={(event) =>
                    setForm({ ...form, phone: event.target.value })
                  }
                  placeholder="09xx xxx xxx"
                />
              </label>
              <label>
                TUỔI <span className="optional-label">(không bắt buộc)</span>
                <input
                  type="number"
                  min="13"
                  max="120"
                  value={form.age}
                  onChange={(event) =>
                    setForm({ ...form, age: event.target.value })
                  }
                  placeholder="Ví dụ: 22"
                />
              </label>
            </>
          )}
          <label>
            EMAIL
            <input
              type="email"
              required
              value={form.email}
              onChange={(event) =>
                setForm({ ...form, email: event.target.value })
              }
              placeholder="ban@email.com"
            />
          </label>
          <label>
            MẬT KHẨU
            <input
              type="password"
              required
              minLength="8"
              value={form.password}
              onChange={(event) =>
                setForm({ ...form, password: event.target.value })
              }
              placeholder="Ít nhất 8 ký tự, gồm chữ và số"
            />
          </label>
          <button className="button button-dark" disabled={busy}>
            {busy
              ? "ĐANG XỬ LÝ..."
              : mode === "login"
                ? "ĐĂNG NHẬP →"
                : "TẠO TÀI KHOẢN →"}
          </button>
        </form>
        <div className="auth-switch">
          {mode === "login" ? "Chưa có tài khoản?" : "Bạn đã có tài khoản?"}{" "}
          <button
            onClick={() =>
              onModeChange(mode === "login" ? "register" : "login")
            }
          >
            {mode === "login" ? "Đăng ký ngay" : "Đăng nhập"}
          </button>
        </div>
      </div>
    </div>
  );
}

function EnhancedCheckoutModal({ cart, onClose, onComplete, onNotice }) {
  const [addresses, setAddresses] = useState([]);
  const [form, setForm] = useState({ recipientName: "", phone: "", addressLine: "", ward: "", district: "", province: "", paymentMethod: "COD", voucherCode: "" });
  const [voucher, setVoucher] = useState(null);
  const [voucherBusy, setVoucherBusy] = useState(false);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  useEffect(() => { api(endpoints.addresses).then((items) => { setAddresses(items); const selected = items.find((item) => item.defaultAddress) || items[0]; if (selected) setForm((current) => ({ ...current, ...selected, addressLine: selected.addressLine || "" })); }).catch(() => null); }, []);
  const setField = (key, value) => setForm((current) => ({ ...current, [key]: value }));
  const selectAddress = (address) => setForm((current) => ({ ...current, ...address }));
  const deliveryAddress = [form.recipientName, form.phone, form.addressLine, form.ward, form.district, form.province].filter(Boolean).join(", ");
  const applyVoucher = async () => { if (!form.voucherCode.trim()) return onNotice("Vui lòng nhập mã giảm giá.", "error"); setVoucherBusy(true); try { setVoucher(await api(endpoints.validateVoucher(form.voucherCode.trim(), cart.subtotal))); onNotice("Đã áp dụng mã giảm giá."); } catch (error) { setVoucher(null); onNotice(error.message, "error"); } finally { setVoucherBusy(false); } };
  const submit = async (event) => { event.preventDefault(); setBusy(true); try { const data = await api("/api/orders", { method: "POST", body: { deliveryAddress, paymentMethod: form.paymentMethod, voucherCode: voucher?.code || undefined, items: cart.items.map((item) => ({ productId: item.productId, quantity: item.quantity, size: item.size, color: item.color })) } }); setResult(data); } catch (error) { onNotice(error.message, "error"); } finally { setBusy(false); } };
  if (result) return <div className="modal-backdrop"><div className="success-modal"><div className="success-icon"><Check size={28} aria-hidden="true" /></div><p className="kicker">ĐẶT HÀNG THÀNH CÔNG</p><h2>Đơn hàng #{result.order.orderCode}</h2><p>{form.paymentMethod === "PAYOS" ? "Đơn hàng đang chờ thanh toán. Bạn có thể mở liên kết bên dưới để hoàn tất." : "Đơn hàng COD đã được ghi nhận. Chúng tôi sẽ liên hệ bạn sớm."}</p>{result.paymentUrl && <a className="button button-dark" href={result.paymentUrl} target="_blank" rel="noreferrer">MỞ TRANG THANH TOÁN <ArrowUpRight size={16} /></a>}<button className="continue-shopping" onClick={onComplete}>XONG, TIẾP TỤC MUA SẮM</button></div></div>;
  const total = Math.max(0, cart.subtotal - Number(voucher?.discountAmount || 0));
  return <div className="modal-backdrop"><div className="checkout-modal"><button className="close-button" aria-label="Đóng thanh toán" onClick={onClose}><X size={20} /></button><div className="checkout-main"><p className="kicker">BƯỚC 01 / 02</p><h2>Thông tin giao hàng</h2>{addresses.length > 0 && <div className="saved-addresses"><div className="saved-addresses-head"><b>ĐỊA CHỈ ĐÃ LƯU</b><span>{addresses.length} địa chỉ</span></div><div className="saved-address-list">{addresses.map((address) => <button type="button" key={address.id} className={form.id === address.id ? "saved-address active" : "saved-address"} onClick={() => selectAddress(address)}><MapPin size={16} /><span><b>{address.label || "Địa chỉ"} {address.defaultAddress && "· Mặc định"}</b><small>{address.recipientName} · {address.phone}<br />{address.addressLine}, {address.district}, {address.province}</small></span></button>)}</div></div>}<form onSubmit={submit}><div className="form-two-columns"><label>NGƯỜI NHẬN<input required value={form.recipientName} onChange={(event) => setField("recipientName", event.target.value)} placeholder="Nguyễn Minh Khang" /></label><label>SỐ ĐIỆN THOẠI<input required value={form.phone} onChange={(event) => setField("phone", event.target.value)} placeholder="09xx xxx xxx" /></label></div><label>ĐỊA CHỈ NHẬN HÀNG<textarea required value={form.addressLine} onChange={(event) => setField("addressLine", event.target.value)} placeholder="Số nhà, tên đường" /></label><div className="form-three-columns"><input value={form.ward} onChange={(event) => setField("ward", event.target.value)} placeholder="Phường/Xã" /><input value={form.district} onChange={(event) => setField("district", event.target.value)} placeholder="Quận/Huyện" /><input required value={form.province} onChange={(event) => setField("province", event.target.value)} placeholder="Tỉnh/Thành phố" /></div><label>MÃ GIẢM GIÁ<div className="voucher-input"><input value={form.voucherCode} onChange={(event) => { setField("voucherCode", event.target.value.toUpperCase()); setVoucher(null); }} placeholder="Nhập mã voucher" /><button type="button" onClick={applyVoucher} disabled={voucherBusy}>{voucherBusy ? "ĐANG KIỂM TRA" : "ÁP DỤNG"}</button></div></label><label>PHƯƠNG THỨC THANH TOÁN<div className="payment-options"><button type="button" className={form.paymentMethod === "PAYOS" ? "payment-option active" : "payment-option"} onClick={() => setField("paymentMethod", "PAYOS")}><b>payOS / VietQR</b><small>Thanh toán nhanh qua ngân hàng</small></button><button type="button" className={form.paymentMethod === "COD" ? "payment-option active" : "payment-option"} onClick={() => setField("paymentMethod", "COD")}><b>Thanh toán khi nhận hàng</b><small>COD toàn quốc</small></button></div></label><button className="button button-dark" disabled={busy}>{busy ? "ĐANG TẠO ĐƠN..." : <>XÁC NHẬN ĐẶT HÀNG <ArrowUpRight size={16} /></>}</button></form></div><div className="checkout-summary"><p className="kicker">TÓM TẮT ĐƠN HÀNG</p>{cart.items.map((item) => <div className="summary-line" key={`${item.productId}-${item.size || ""}-${item.color || ""}`}><span>{item.name} <small>× {item.quantity}</small></span><b>{money(item.lineTotal)}</b></div>)}<div className="summary-total"><span>TẠM TÍNH</span><strong>{money(cart.subtotal)}</strong></div>{voucher && <div className="summary-line discount-line"><span>Giảm giá ({voucher.code})</span><b>-{money(voucher.discountAmount)}</b></div>}<div className="summary-total final"><span>TỔNG CỘNG</span><strong>{money(total)}</strong></div><p className="secure-note"><ShieldCheck size={15} /> Thông tin của bạn được bảo mật trong suốt quá trình thanh toán.</p></div></div></div>;
}

function CheckoutModal({ cart, onClose, onComplete, onNotice }) {
  const [form, setForm] = useState({
    deliveryAddress: "",
    paymentMethod: "PAYOS",
  });
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    try {
      const data = await api("/api/orders", {
        method: "POST",
        body: {
          ...form,
          items: cart.items.map((item) => ({
            productId: item.productId,
            quantity: item.quantity,
            size: item.size,
            color: item.color,
          })),
        },
      });
      setResult(data);
    } catch (error) {
      onNotice(error.message, "error");
    } finally {
      setBusy(false);
    }
  };
  if (result)
    return (
      <div className="modal-backdrop">
        <div className="success-modal">
          <div className="success-icon"><Check size={28} aria-hidden="true" /></div>
          <p className="kicker">ĐẶT HÀNG THÀNH CÔNG</p>
          <h2>Đơn hàng #{result.order.orderCode}</h2>
          <p>
            {form.paymentMethod === "PAYOS"
              ? "Đơn hàng đang chờ thanh toán. Bạn có thể mở liên kết bên dưới để hoàn tất."
              : "Đơn hàng COD đã được ghi nhận. Chúng tôi sẽ liên hệ bạn sớm."}
          </p>
          {result.paymentUrl && (
            <a
              className="button button-dark"
              href={result.paymentUrl}
              target="_blank"
              rel="noreferrer"
            >
              MỞ TRANG THANH TOÁN <ArrowUpRight size={16} />
            </a>
          )}
          <button className="continue-shopping" onClick={onComplete}>
            XONG, TIẾP TỤC MUA SẮM
          </button>
        </div>
      </div>
    );
  return (
    <div className="modal-backdrop">
      <div className="checkout-modal">
        <button className="close-button" aria-label="Đóng thanh toán" onClick={onClose}>
          <X size={20} />
        </button>
        <div className="checkout-main">
          <p className="kicker">BƯỚC 01 / 02</p>
          <h2>Thông tin giao hàng</h2>
          <form onSubmit={submit}>
            <label>
              ĐỊA CHỈ NHẬN HÀNG
              <textarea
                required
                value={form.deliveryAddress}
                onChange={(event) =>
                  setForm({ ...form, deliveryAddress: event.target.value })
                }
                placeholder="Số nhà, đường, phường/xã, quận/huyện, tỉnh/thành"
              />
            </label>
            <label>
              PHƯƠNG THỨC THANH TOÁN
              <div className="payment-options">
                <button
                  type="button"
                  className={
                    form.paymentMethod === "PAYOS"
                      ? "payment-option active"
                      : "payment-option"
                  }
                  onClick={() => setForm({ ...form, paymentMethod: "PAYOS" })}
                >
                  <b>payOS / VietQR</b>
                  <small>Thanh toán nhanh qua ngân hàng</small>
                </button>
                <button
                  type="button"
                  className={
                    form.paymentMethod === "COD"
                      ? "payment-option active"
                      : "payment-option"
                  }
                  onClick={() => setForm({ ...form, paymentMethod: "COD" })}
                >
                  <b>Thanh toán khi nhận hàng</b>
                  <small>COD toàn quốc</small>
                </button>
              </div>
            </label>
            <button className="button button-dark" disabled={busy}>
              {busy ? "ĐANG TẠO ĐƠN..." : <>XÁC NHẬN ĐẶT HÀNG <ArrowUpRight size={16} /></>}
            </button>
          </form>
        </div>
        <div className="checkout-summary">
          <p className="kicker">TÓM TẮT ĐƠN HÀNG</p>
          {cart.items.map((item) => (
            <div
              className="summary-line"
              key={`${item.productId}-${item.size || ""}-${item.color || ""}`}
            >
              <span>
                {item.name} <small>× {item.quantity}</small>
              </span>
              <b>{money(item.lineTotal)}</b>
            </div>
          ))}
          <div className="summary-total">
            <span>TỔNG CỘNG</span>
            <strong>{money(cart.subtotal)}</strong>
          </div>
          <p className="secure-note">
            <ShieldCheck size={15} /> Thông tin của bạn được bảo mật trong suốt quá trình thanh toán.
          </p>
        </div>
      </div>
    </div>
  );
}

function OrdersModal({ onClose, onNotice, onChanged }) {
  const [orders, setOrders] = useState(null);
  const [selected, setSelected] = useState(null);
  const load = () => api(endpoints.orders()).then((data) => setOrders(data.content || [])).catch((error) => onNotice(error.message, "error"));
  useEffect(() => {
    load();
  }, []);
  return (
    <div className="modal-backdrop">
      <div className="wide-modal">
        <button className="close-button" aria-label="Đóng lịch sử đơn hàng" onClick={onClose}>
          <X size={20} />
        </button>
        <p className="kicker">TÀI KHOẢN / LỊCH SỬ</p>
        <h2>Đơn hàng của tôi</h2>
        {selected ? (
          <div className="order-detail">
            <button className="back-link" onClick={() => setSelected(null)}>
              <ArrowLeft size={15} /> Quay lại danh sách
            </button>
            <OrderView order={selected} onNotice={onNotice} onChanged={async () => { setSelected(null); await load(); onChanged?.(); }} />
          </div>
        ) : orders === null ? (
          <div className="modal-loading">Đang tải đơn hàng...</div>
        ) : orders.length ? (
          <div className="order-list">
            {orders.map((order) => (
              <button
                className="order-row"
                key={order.id}
                onClick={async () => { try { setSelected(await api(endpoints.order(order.id))); } catch (error) { onNotice(error.message, "error"); } }}
              >
                <div>
                  <b>#{order.orderCode}</b>
                  <small>
                    {date(order.createdAt)} · {order.items?.length || 0} sản
                    phẩm
                  </small>
                </div>
                <div>
                  <Status value={order.status} />
                  <strong>{money(order.totalAmount)}</strong>
                </div>
            <ChevronRight size={18} aria-label="Xem chi tiết đơn hàng" />
              </button>
            ))}
          </div>
        ) : (
          <div className="empty-state compact">
            <PackageOpen size={34} />
            <h3>Bạn chưa có đơn hàng</h3>
            <p>Những món đồ bạn đặt sẽ xuất hiện ở đây.</p>
          </div>
        )}
      </div>
    </div>
  );
}
function OrderView({ order, onNotice, onChanged }) {
  const [busy, setBusy] = useState(false);
  const cancellableStatuses = ["PENDING_PAYMENT", "PENDING", "CONFIRMED", "PREPARING"];
  const cancel = async () => { const reason = window.prompt("Lý do hủy đơn:", "Tôi muốn thay đổi sản phẩm hoặc địa chỉ giao hàng."); if (reason === null) return; setBusy(true); try { await api(endpoints.cancelOrder(order.id), { method: "PATCH", body: { reason: reason.trim() || "Khách hàng yêu cầu hủy đơn." } }); onNotice("Đã hủy đơn hàng thành công."); await onChanged?.(); } catch (error) { onNotice(error.message, "error"); } finally { setBusy(false); } };
  const requestReturn = async () => { const reason = window.prompt("Lý do đổi/trả hàng:", "Sản phẩm không phù hợp với tôi."); if (!reason) return; setBusy(true); try { await api(endpoints.returnOrder(order.id), { method: "POST", body: { reason } }); onNotice("Đã gửi yêu cầu đổi/trả hàng."); await onChanged?.(); } catch (error) { onNotice(error.message, "error"); } finally { setBusy(false); } };
  return (
    <div className="order-view">
      <div className="order-view-head">
        <div>
          <p className="kicker">MÃ ĐƠN #{order.orderCode}</p>
          <h3>{date(order.createdAt)}</h3>
        </div>
        <Status value={order.status} />
      </div>
      <div className="order-progress">
        <span className="done">ĐẶT HÀNG</span>
        <i
          className={
            ["CONFIRMED", "SHIPPED", "DELIVERED", "COMPLETED"].includes(
              order.status,
            )
              ? "done"
              : ""
          }
        />
        <span
          className={
            ["SHIPPED", "DELIVERED", "COMPLETED"].includes(order.status)
              ? "done"
              : ""
          }
        >
          ĐANG GIAO
        </span>
        <i
          className={
            ["DELIVERED", "COMPLETED"].includes(order.status) ? "done" : ""
          }
        />
        <span
          className={
            ["DELIVERED", "COMPLETED"].includes(order.status) ? "done" : ""
          }
        >
          ĐÃ NHẬN
        </span>
      </div>
      {order.items?.map((item) => (
        <div className="order-item" key={`${item.productId}-${item.size || ""}-${item.color || ""}`}>
          <img
            src={imageSrc(item.imageUrl)}
            onError={protectImage}
            alt={item.name}
          />
          <div>
            <b>{item.name}</b>
            <small>Số lượng: {item.quantity} · {item.size || "Không chọn size"} · {item.color || "Không chọn màu"}</small>
          </div>
          <strong>{money(item.lineTotal)}</strong>
        </div>
      ))}
      {order.discountAmount > 0 && <div className="order-total"><span>GIẢM GIÁ {order.voucherCode ? `(${order.voucherCode})` : ""}</span><strong>-{money(order.discountAmount)}</strong></div>}
      <div className="order-total"><span>TỔNG ĐƠN HÀNG</span><strong>{money(order.totalAmount)}</strong></div>
      {order.trackingCode && <p className="order-address"><b>Mã theo dõi</b><br />{order.trackingCode}</p>}
      {(order.returnStatus || "NONE") !== "NONE" && <p className="order-address"><b>Đổi/trả hàng: {order.returnStatus}</b><br />{order.returnReason}</p>}
      <p className="order-address">
        <b>Địa chỉ giao hàng</b>
        <br />
        {order.deliveryAddress}
      </p>
      <div className="order-actions">{cancellableStatuses.includes(order.status) && <button type="button" className="button button-light" disabled={busy} onClick={cancel}>{busy ? "ĐANG HỦY..." : "HỦY ĐƠN"}</button>}{["DELIVERED", "COMPLETED"].includes(order.status) && (order.returnStatus || "NONE") === "NONE" && <button type="button" className="button button-light" disabled={busy} onClick={requestReturn}>YÊU CẦU ĐỔI/TRẢ</button>}</div>
    </div>
  );
}
function WishlistModal({ items, onClose, onRemove, onOpen, onNotice }) {
  return <div className="modal-backdrop"><div className="wide-modal wishlist-modal"><button className="close-button" aria-label="Đóng yêu thích" onClick={onClose}><X size={20} /></button><p className="kicker">TÀI KHOẢN / YÊU THÍCH</p><h2>Món đồ bạn thích.</h2>{items.length ? <div className="wishlist-grid">{items.map((item) => <article className="wishlist-card" key={item.productId}><button className="wishlist-image" onClick={() => onOpen({ id: item.productId })}><img src={imageSrc(item.imageUrl)} onError={protectImage} alt={item.name} /></button><div className="wishlist-card-content"><b>{item.name}</b><small>{item.category || "HÀNG NAM"}</small><strong>{money(item.salePrice || item.price)}</strong></div><button className="wishlist-remove" onClick={() => onRemove({ id: item.productId })}>Bỏ thích</button></article>)}</div> : <div className="empty-state compact"><Heart size={34} /><h3>Chưa có món đồ yêu thích</h3><p>Chạm vào biểu tượng trái tim để lưu sản phẩm.</p></div>}</div></div>;
}

function AddressModal({ onClose, onNotice }) {
  const blank = { label: "", recipientName: "", phone: "", addressLine: "", ward: "", district: "", province: "", defaultAddress: false };
  const [items, setItems] = useState(null); const [form, setForm] = useState(blank); const [editing, setEditing] = useState(null); const [busy, setBusy] = useState(false);
  const load = () => api(endpoints.addresses).then(setItems).catch((error) => onNotice(error.message, "error"));
  useEffect(() => {
    load();
  }, []);
  const save = async (event) => { event.preventDefault(); setBusy(true); try { const data = await api(editing ? endpoints.address(editing) : endpoints.addresses, { method: editing ? "PUT" : "POST", body: form }); setItems((current) => editing ? current.map((item) => item.id === editing ? data : item) : [data, ...(current || [])]); setForm(blank); setEditing(null); onNotice("Đã lưu địa chỉ giao hàng."); } catch (error) { onNotice(error.message, "error"); } finally { setBusy(false); } };
  const remove = async (id) => { if (!window.confirm("Xóa địa chỉ này?")) return; try { await api(endpoints.address(id), { method: "DELETE" }); load(); } catch (error) { onNotice(error.message, "error"); } };
  const makeDefault = async (id) => { try { await api(endpoints.defaultAddress(id), { method: "PATCH" }); load(); } catch (error) { onNotice(error.message, "error"); } };
  return <div className="modal-backdrop"><div className="wide-modal address-modal"><button className="close-button" aria-label="Đóng địa chỉ" onClick={onClose}><X size={20} /></button><p className="kicker">TÀI KHOẢN / ĐỊA CHỈ</p><h2>Địa chỉ giao hàng</h2><div className="address-layout"><div className="address-list">{items === null ? <div className="modal-loading">Đang tải địa chỉ...</div> : items.length ? items.map((item) => <article className={item.defaultAddress ? "address-card default" : "address-card"} key={item.id}><div><b>{item.label || "Địa chỉ"} {item.defaultAddress && <small>MẶC ĐỊNH</small>}</b><p>{item.recipientName} · {item.phone}<br />{item.addressLine}, {item.ward}, {item.district}, {item.province}</p></div><div className="address-actions"><button onClick={() => { setEditing(item.id); setForm({ ...item }); }}>Sửa</button>{!item.defaultAddress && <button onClick={() => makeDefault(item.id)}>Đặt mặc định</button>}<button onClick={() => remove(item.id)}>Xóa</button></div></article>) : <div className="empty-state compact"><MapPin size={28} /><p>Bạn chưa lưu địa chỉ nào.</p></div>}<button className="button button-light address-add" onClick={() => { setEditing(null); setForm(blank); }}>+ THÊM ĐỊA CHỈ</button></div><form className="address-form" onSubmit={save}><p className="kicker">{editing ? "CHỈNH SỬA" : "ĐỊA CHỈ MỚI"}</p><label>TÊN GỢI NHỚ<input value={form.label} onChange={(event) => setForm({ ...form, label: event.target.value })} placeholder="Nhà riêng" /></label><label>NGƯỜI NHẬN<input required value={form.recipientName} onChange={(event) => setForm({ ...form, recipientName: event.target.value })} /></label><label>SỐ ĐIỆN THOẠI<input required value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} /></label><label>ĐỊA CHỈ<input required value={form.addressLine} onChange={(event) => setForm({ ...form, addressLine: event.target.value })} /></label><div className="form-three-columns"><input value={form.ward || ""} onChange={(event) => setForm({ ...form, ward: event.target.value })} placeholder="Phường/Xã" /><input value={form.district || ""} onChange={(event) => setForm({ ...form, district: event.target.value })} placeholder="Quận/Huyện" /><input required value={form.province || ""} onChange={(event) => setForm({ ...form, province: event.target.value })} placeholder="Tỉnh/Thành phố" /></div><label className="checkbox-label"><input type="checkbox" checked={form.defaultAddress} onChange={(event) => setForm({ ...form, defaultAddress: event.target.checked })} /> Đặt làm địa chỉ mặc định</label><button className="button button-dark" disabled={busy}>{busy ? "ĐANG LƯU..." : "LƯU ĐỊA CHỈ"}</button></form></div></div></div>;
}

function NotificationsModal({ onClose, onNotice, onCount }) {
  const [data, setData] = useState(null);
  const load = () => api(endpoints.notifications).then((value) => { setData(value); onCount(value.unreadCount || 0); }).catch((error) => onNotice(error.message, "error"));
  useEffect(() => {
    load();
  }, []);
  const read = async (item) => { if (item.readAt) return; try { await api(endpoints.notificationRead(item.id), { method: "PATCH" }); load(); } catch (error) { onNotice(error.message, "error"); } };
  const readAll = async () => { try { await api(endpoints.notificationsReadAll, { method: "PATCH" }); load(); } catch (error) { onNotice(error.message, "error"); } };
  return <div className="modal-backdrop"><div className="wide-modal notification-modal"><button className="close-button" aria-label="Đóng thông báo" onClick={onClose}><X size={20} /></button><div className="notification-head"><div><p className="kicker">TÀI KHOẢN / CẬP NHẬT</p><h2>Thông báo</h2></div><button className="back-link" onClick={readAll}>ĐÁNH DẤU ĐÃ ĐỌC</button></div>{data === null ? <div className="modal-loading">Đang tải thông báo...</div> : data.items.length ? <div className="notification-list">{data.items.map((item) => <button className={item.readAt ? "notification-item read" : "notification-item"} key={item.id} onClick={() => read(item)}><Bell size={18} /><span><b>{item.title}</b><small>{item.content}</small><em>{date(item.createdAt)}</em></span></button>)}</div> : <div className="empty-state compact"><Bell size={34} /><h3>Chưa có thông báo</h3><p>Các cập nhật về đơn hàng sẽ xuất hiện ở đây.</p></div>}</div></div>;
}

function PasswordModal({ onClose, onNotice }) {
  const [mode, setMode] = useState("change"); const [busy, setBusy] = useState(false); const [form, setForm] = useState({ currentPassword: "", newPassword: "", email: "", code: "" });
  const submit = async (event) => { event.preventDefault(); setBusy(true); try { if (mode === "change") { await api(endpoints.changePassword, { method: "POST", body: { currentPassword: form.currentPassword, newPassword: form.newPassword } }); onNotice("Đã đổi mật khẩu. Vui lòng đăng nhập lại."); onClose(); } else if (mode === "forgot") { const result = await api(endpoints.forgotPassword, { method: "POST", body: { email: form.email } }); if (result.resetCode) { setForm({ ...form, code: result.resetCode }); setMode("reset"); onNotice(`Mã đặt lại mật khẩu: ${result.resetCode}`); } else { onNotice(result.message); } } else { await api(endpoints.resetPassword, { method: "POST", body: { email: form.email, code: form.code, newPassword: form.newPassword } }); onNotice("Đặt lại mật khẩu thành công."); setMode("change"); } } catch (error) { onNotice(error.message, "error"); } finally { setBusy(false); } };
  return <div className="modal-backdrop"><div className="auth-modal password-modal"><button className="close-button" aria-label="Đóng mật khẩu" onClick={onClose}><X size={20} /></button><div className="auth-mark"><KeyRound size={20} /></div><p className="kicker">BẢO MẬT TÀI KHOẢN</p><h2>{mode === "change" ? "Đổi mật khẩu." : mode === "forgot" ? "Lấy lại quyền truy cập." : "Đặt mật khẩu mới."}</h2><p className="auth-subtitle">Mật khẩu mới cần có ít nhất 8 ký tự, gồm chữ và số.</p><form onSubmit={submit}>{mode === "change" && <label>MẬT KHẨU HIỆN TẠI<input type="password" required value={form.currentPassword} onChange={(event) => setForm({ ...form, currentPassword: event.target.value })} /></label>}{mode !== "change" && <label>EMAIL<input type="email" required value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} /></label>}{mode === "reset" && <label>MÃ 6 CHỮ SỐ<input inputMode="numeric" pattern="[0-9]{6}" required value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} /></label>}{mode !== "forgot" && <label>MẬT KHẨU MỚI<input type="password" minLength="8" required value={form.newPassword} onChange={(event) => setForm({ ...form, newPassword: event.target.value })} /></label>}<button className="button button-dark" disabled={busy}>{busy ? "ĐANG XỬ LÝ..." : mode === "change" ? "ĐỔI MẬT KHẨU" : mode === "forgot" ? "NHẬN MÃ ĐẶT LẠI" : "ĐẶT MẬT KHẨU MỚI"}</button></form><div className="auth-switch">{mode === "change" ? <button onClick={() => setMode("forgot")}>Quên mật khẩu?</button> : <button onClick={() => setMode("change")}>Quay lại đổi mật khẩu</button>}</div></div></div>;
}

function Status({ value }) {
  const labels = {
    PENDING_PAYMENT: "Chờ thanh toán",
    PENDING: "Chờ xác nhận",
    CONFIRMED: "Đã xác nhận",
    PREPARING: "Đang chuẩn bị",
    SHIPPED: "Đang giao",
    DELIVERING: "Đang giao",
    DELIVERED: "Đã giao",
    COMPLETED: "Hoàn tất",
    CANCELLED: "Đã huỷ",
  };
  return (
    <span className={`status-pill ${String(value).toLowerCase()}`}>
      {labels[value] || value}
    </span>
  );
}

function ProfileModal({ user, onClose, onUser, onLogout, onOpenOrders, onOpenWishlist, onOpenNotifications, onOpenSurveys, onOpenAddresses, onOpenPassword, onNotice }) {
  const [form, setForm] = useState({
    fullName: user?.fullName || "",
    phone: user?.phone || "",
    age: user?.age ?? "",
    preferences: user?.preferences || "",
  });
  const [busy, setBusy] = useState(false);
  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    try {
      const updated = await api(endpoints.profile, {
        method: "PUT",
        body: { ...form, age: form.age === "" ? null : Number(form.age) },
      });
      onUser(updated);
      onNotice("Đã lưu thông tin cá nhân.");
      onClose();
    } catch (error) {
      onNotice(error.message, "error");
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="modal-backdrop">
      <div className="profile-modal">
        <button className="close-button" aria-label="Đóng hồ sơ" onClick={onClose}>
          <X size={20} />
        </button>
        <div className="profile-cover">
          <span>{user?.fullName?.slice(0, 1) || "H"}</span>
        </div>
        <p className="kicker">TÀI KHOẢN CỦA BẠN</p>
        <h2>{user?.fullName}</h2>
        <p className="profile-email">{user?.email}</p>
        <form onSubmit={save}>
          <label>
            HỌ VÀ TÊN
            <input
              required
              value={form.fullName}
              onChange={(event) =>
                setForm({ ...form, fullName: event.target.value })
              }
            />
          </label>
          <label>
            SỐ ĐIỆN THOẠI
            <input
              value={form.phone}
              onChange={(event) =>
                setForm({ ...form, phone: event.target.value })
              }
            />
          </label>
          <label>
            TUỔI <span className="optional-label">(không bắt buộc)</span>
            <input
              type="number"
              min="13"
              max="120"
              value={form.age}
              onChange={(event) =>
                setForm({ ...form, age: event.target.value })
              }
              placeholder="Ví dụ: 22"
            />
          </label>
          <label>
            PHONG CÁCH ƯA THÍCH
            <textarea
              value={form.preferences}
              onChange={(event) =>
                setForm({ ...form, preferences: event.target.value })
              }
              placeholder="Ví dụ: tối giản, màu trung tính, form relaxed..."
            />
          </label>
          <button className="button button-dark" disabled={busy}>
            {busy ? "ĐANG LƯU..." : "LƯU THAY ĐỔI"}
          </button>
        </form>
        <button className="profile-survey-link account-menu-primary" onClick={onOpenOrders}>
          <span>Đơn hàng của tôi</span>
          <ArrowUpRight size={16} aria-hidden="true" />
        </button>
        <button className="profile-survey-link" onClick={onOpenWishlist}>
          <span>Sản phẩm yêu thích</span>
          <Heart size={16} aria-hidden="true" />
        </button>
        <button className="profile-survey-link" onClick={onOpenNotifications}>
          <span>Thông báo</span>
          <Bell size={16} aria-hidden="true" />
        </button>
        <button className="profile-survey-link" onClick={onOpenSurveys}>
          <span>Khảo sát phong cách</span>
          <ArrowUpRight size={16} aria-hidden="true" />
        </button>
        <button className="profile-survey-link" onClick={onOpenAddresses}>
          <span>Quản lý địa chỉ giao hàng</span>
          <MapPin size={16} aria-hidden="true" />
        </button>
        <button className="profile-survey-link" onClick={onOpenPassword}>
          <span>Đổi hoặc quên mật khẩu</span>
          <KeyRound size={16} aria-hidden="true" />
        </button>
        <button className="logout-link" onClick={onLogout}>
          Đăng xuất khỏi tài khoản
        </button>
      </div>
    </div>
  );
}

function SurveyModal({ user, onClose, onLogin, onNotice }) {
  const [surveys, setSurveys] = useState(null);
  const [survey, setSurvey] = useState(null);
  const [answers, setAnswers] = useState({});
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    api(user ? endpoints.mySurveys : endpoints.surveys)
      .then(setSurveys)
      .catch((error) => onNotice(error.message, "error"));
  }, []);
  const submit = async (event) => {
    event.preventDefault();
    if (!user) return onLogin();
    setBusy(true);
    try {
      await api(endpoints.surveySubmit, {
        method: "POST",
        body: {
          surveyId: survey.id,
          answers: Object.entries(answers).map(([questionId, value]) => ({
            questionId: Number(questionId),
            value,
          })),
        },
      });
      onNotice("Cảm ơn bạn đã chia sẻ gu thời trang!");
      onClose();
    } catch (error) {
      onNotice(error.message, "error");
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="modal-backdrop">
      <div className="survey-modal">
        <button className="close-button" aria-label="Đóng khảo sát" onClick={onClose}>
          <X size={20} />
        </button>
        {!survey ? (
          <>
            <p className="kicker">ANH LỚN SHOP / KHẢO SÁT</p>
            <h2>
              Chọn đúng gu,
              <br />
              <i>mặc đúng mình.</i>
            </h2>
            <p>
              Chia sẻ một chút về phong cách của bạn để những gợi ý lần sau trở
              nên riêng tư hơn.
            </p>
            {surveys === null ? (
              <div className="modal-loading">Đang tải khảo sát...</div>
            ) : surveys.length ? (
              <div className="survey-list">
                {surveys.map((item) => (
                  <button
                    key={item.id}
                    disabled={item.completed}
                    className={item.completed ? "completed" : ""}
                    onClick={() => setSurvey(item)}
                  >
                    <span><ArrowUpRight size={18} aria-hidden="true" /></span>
                    <div>
                      <b>{item.title}</b>
                      <small>{item.description}</small>
                    </div>
                    <em>{item.completed ? "ĐÃ HOÀN THÀNH" : "BẮT ĐẦU"}</em>
                  </button>
                ))}
              </div>
            ) : (
              <div className="empty-state compact">
                <h3>Chưa có khảo sát mới</h3>
              </div>
            )}
          </>
        ) : (
          <>
            <button className="back-link" onClick={() => setSurvey(null)}>
              <ArrowLeft size={15} /> Các khảo sát
            </button>
            <p className="kicker">KHẢO SÁT / {survey.title}</p>
            <h2>{survey.title}</h2>
            <p>{survey.description}</p>
            <form className="survey-form" onSubmit={submit}>
              {survey.completed && (
                <div className="survey-completed-note">
                  Bạn đã hoàn thành khảo sát này. Cảm ơn bạn đã chia sẻ cùng Anh Lớn Shop.
                </div>
              )}
              {survey.questions?.map((question) => (
                <label key={question.id}>
                  {question.text} {question.required && <sup>*</sup>}
                  {question.type === "SINGLE" ? (
                    <select
                      required={question.required}
                      value={answers[question.id] || ""}
                      onChange={(event) =>
                        setAnswers({
                          ...answers,
                          [question.id]: event.target.value,
                        })
                      }
                    >
                      <option value="">Chọn câu trả lời</option>
                      {surveyOptions(question.optionsJson).map(
                        (option) => (
                          <option key={option} value={option}>
                            {option}
                          </option>
                        ),
                      )}
                    </select>
                  ) : (
                    <textarea
                      required={question.required}
                      value={answers[question.id] || ""}
                      onChange={(event) =>
                        setAnswers({
                          ...answers,
                          [question.id]: event.target.value,
                        })
                      }
                      placeholder="Câu trả lời của bạn..."
                    />
                  )}
                </label>
              ))}
              <button className="button button-dark" disabled={busy || survey.completed}>
                {busy ? "ĐANG GỬI..." : <>GỬI CÂU TRẢ LỜI <ArrowUpRight size={16} /></>}
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  );
}
export default App;
