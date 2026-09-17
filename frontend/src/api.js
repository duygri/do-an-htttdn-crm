const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export async function api(path, options = {}) {
  const token = localStorage.getItem('crm_access_token');
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(options.headers || {}) },
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message || `Request failed (${response.status})`);
  }
  return response.status === 204 ? null : response.json();
}

export const authApi = {
  login: (payload) => api('/api/auth/login', { method: 'POST', body: JSON.stringify(payload) }),
  logout: () => api('/api/auth/logout', { method: 'POST' }).catch(() => null),
};

export const endpoints = {
  users: (q = '') => `/api/admin/users?q=${encodeURIComponent(q)}&page=0&size=8`,
  products: (q = '') => `/api/admin/products?q=${encodeURIComponent(q)}&page=0&size=8`,
  orders: `/api/admin/orders?page=0&size=8`,
  feedback: `/api/admin/feedback?page=0&size=8`,
  surveys: `/api/admin/surveys?page=0&size=8`,
  revenue: '/api/admin/reports/revenue',
  userReport: '/api/admin/reports/users',
  surveyStats: '/api/admin/surveys/stats',
};
