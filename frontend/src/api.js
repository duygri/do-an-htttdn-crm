const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8082';
let accessToken = null;
let refreshing = null;

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
  orders: ({ page = 0, size = 10 } = {}) => `/api/orders?page=${page}&size=${size}`,
  order: id => `/api/orders/${id}`,
  feedback: id => `/api/products/${id}/feedback`,
  surveys: '/api/surveys',
  survey: id => `/api/surveys/${id}`,
  surveyResponses: id => `/api/surveys/${id}/responses`,
};

export const currentApiBase = API_BASE;
