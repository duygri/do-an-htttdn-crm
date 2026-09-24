const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8082';
let accessToken = null;
let refreshing = null;
let adminAccessToken = null;
let adminRefreshing = null;

async function parseResponse(response) {
  if (response.ok) return response.status === 204 ? null : response.json();
  const data = await response.json().catch(() => ({}));
  const error = new Error(data.message || `Yêu cầu thất bại (${response.status})`);
  error.code = data.code;
  error.status = response.status;
  error.fields = data.fields;
  throw error;
}

export async function api(path, options = {}, canRefresh = true) {
  const headers = { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(options.headers || {}) };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  const body = options.body && typeof options.body !== 'string' ? JSON.stringify(options.body) : options.body;
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers, body, credentials: 'include' });
  if (response.status === 401 && canRefresh && !path.startsWith('/api/auth/')) {
    const session = await refreshSession();
    if (session) return api(path, options, false);
  }
  return parseResponse(response);
}

export async function refreshSession() {
  if (!refreshing) {
    refreshing = fetch(`${API_BASE}/api/auth/refresh`, { method: 'POST', credentials: 'include' })
      .then(parseResponse)
      .then(data => { accessToken = data.accessToken; return data; })
      .catch(() => null)
      .finally(() => { refreshing = null; });
  }
  return refreshing;
}

export async function signIn(payload) {
  const data = await api('/api/auth/login', { method: 'POST', body: payload }, false);
  accessToken = data.accessToken;
  return data;
}

export async function signUp(payload) { return api('/api/auth/register', { method: 'POST', body: payload }, false); }

export async function signOut() {
  await api('/api/auth/logout', { method: 'POST' }, false).catch(() => null);
  accessToken = null;
}

export async function adminRefreshSession() {
  if (!adminRefreshing) {
    adminRefreshing = fetch(`${API_BASE}/api/admin/auth/refresh`, { method: 'POST', credentials: 'include' })
      .then(parseResponse)
      .then(data => { adminAccessToken = data.accessToken; return data; })
      .catch(() => null)
      .finally(() => { adminRefreshing = null; });
  }
  return adminRefreshing;
}

export async function adminApi(path, options = {}, canRefresh = true) {
  const headers = { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(options.headers || {}) };
  if (adminAccessToken) headers.Authorization = `Bearer ${adminAccessToken}`;
  const body = options.body && typeof options.body !== 'string' ? JSON.stringify(options.body) : options.body;
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers, body, credentials: 'include' });
  if (response.status === 401 && canRefresh && !path.startsWith('/api/admin/auth/')) {
    const session = await adminRefreshSession();
    if (session) return adminApi(path, options, false);
  }
  return parseResponse(response);
}

export async function adminSignIn(payload) {
  const data = await adminApi('/api/admin/auth/login', { method: 'POST', body: payload }, false);
  adminAccessToken = data.accessToken;
  return data;
}

export async function adminSignOut() {
  await adminApi('/api/admin/auth/logout', { method: 'POST' }, false).catch(() => null);
  adminAccessToken = null;
}

export const endpoints = {
  catalog: ({ keyword = '', category = '', minPrice = '', maxPrice = '', gender = 'NAM', sort = 'newest', page = 0, size = 8 } = {}) => {
    const params = new URLSearchParams({ keyword, category, gender, sort, page, size });
    if (minPrice !== '') params.set('minPrice', minPrice);
    if (maxPrice !== '') params.set('maxPrice', maxPrice);
    return `/api/products?${params}`;
  },
  product: id => `/api/products/${id}`,
  categories: '/api/products/categories',
  profile: '/api/customers/me',
  cart: '/api/cart',
  addCart: '/api/cart/items',
  wishlist: '/api/wishlist',
  wishlistItem: id => `/api/wishlist/${id}`,
  addresses: '/api/customers/me/addresses',
  address: id => `/api/customers/me/addresses/${id}`,
  defaultAddress: id => `/api/customers/me/addresses/${id}/default`,
  validateVoucher: (code, amount) => `/api/vouchers/validate?code=${encodeURIComponent(code)}&amount=${encodeURIComponent(amount)}`,
  orders: ({ page = 0, size = 10 } = {}) => `/api/orders/user?page=${page}&size=${size}`,
  order: id => `/api/orders/${id}`,
  cancelOrder: id => `/api/orders/${id}/cancel`,
  returnOrder: id => `/api/orders/${id}/return`,
  notifications: '/api/notifications',
  notificationRead: id => `/api/notifications/${id}/read`,
  notificationsReadAll: '/api/notifications/read-all',
  feedback: id => `/api/products/${id}/feedback`,
  surveys: '/api/surveys',
  mySurveys: '/api/surveys/mine',
  survey: id => `/api/surveys/${id}`,
  surveyResponses: id => `/api/surveys/${id}/responses`,
  surveySubmit: '/api/surveys/submit',
  changePassword: '/api/auth/change-password',
  forgotPassword: '/api/auth/forgot-password',
  resetPassword: '/api/auth/reset-password',
};

export const adminEndpoints = {
  revenue: '/api/admin/reports/revenue',
  userReport: '/api/admin/reports/users',
  surveyStats: '/api/admin/surveys/stats',
  users: ({ q = '', page = 0, size = 10 } = {}) => `/api/admin/users?q=${encodeURIComponent(q)}&page=${page}&size=${size}`,
  user: id => `/api/admin/users/${id}`,
  lockUser: id => `/api/admin/users/${id}/lock`,
  products: ({ q = '', page = 0, size = 10 } = {}) => `/api/admin/products?q=${encodeURIComponent(q)}&page=${page}&size=${size}`,
  product: id => `/api/admin/products/${id}`,
  productStock: id => `/api/admin/products/${id}/stock`,
  orders: ({ status = '', page = 0, size = 10 } = {}) => `/api/admin/orders${status ? `?status=${encodeURIComponent(status)}&page=${page}&size=${size}` : `?page=${page}&size=${size}`}`,
  order: id => `/api/admin/orders/${id}`,
  orderStatus: id => `/api/admin/orders/${id}/status`,
  feedback: ({ status = '', page = 0, size = 10 } = {}) => `/api/admin/feedback${status ? `?status=${encodeURIComponent(status)}&page=${page}&size=${size}` : `?page=${page}&size=${size}`}`,
  feedbackItem: id => `/api/admin/feedback/${id}`,
  surveys: ({ status = '', page = 0, size = 10 } = {}) => `/api/admin/surveys${status ? `?status=${encodeURIComponent(status)}&page=${page}&size=${size}` : `?page=${page}&size=${size}`}`,
  survey: id => `/api/admin/surveys/${id}`,
  surveyPublish: id => `/api/admin/surveys/${id}/publish`,
};

export const currentApiBase = API_BASE;
