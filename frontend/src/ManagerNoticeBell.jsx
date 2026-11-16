import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Bell } from 'lucide-react';
import { adminApi } from './manager-api';
import { useManagerResource } from './useManagerResource';
import './manager-notices.css';

const targetOf = link => (link || 'dashboard').replace(/^\/manager\/?/, '') || 'dashboard';

export default function ManagerNoticeBell({ go, location }) {
  const [open, setOpen] = useState(false), [page, setPage] = useState(0), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const lock = useRef(false), root = useRef(null), trigger = useRef(null), alive = useRef(true), route = useRef(location);
  route.current = location;
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => { setOpen(false); setError(''); }, [location]);
  const summaryFetch = useCallback(signal => adminApi('/api/manager/notices/summary', { signal }), []);
  const fetcher = useCallback(signal => adminApi(`/api/manager/notices?page=${page}&size=10`, { signal }), [page]);
  const summary = useManagerResource(summaryFetch, { keepPreviousData: true });
  const list = useManagerResource(fetcher, { enabled: open, keepPreviousData: true });
  const data = list.data;
  useEffect(() => {
    if (!open) return;
    const outside = event => { if (!root.current?.contains(event.target)) setOpen(false); };
    const escape = event => { if (event.key === 'Escape') { setOpen(false); trigger.current?.focus(); } };
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape); };
  }, [open]);
  const read = async item => {
    if (lock.current) return;
    const startedOn = route.current;
    lock.current = true;
    setBusy(true);
    setError('');
    try {
      await adminApi(item ? `/api/manager/notices/${item.id}/read` : '/api/manager/notices/read-all', { method: 'PATCH' });
      if (!alive.current || route.current !== startedOn) return;
      summary.load();
      if (item) { setOpen(false); go(targetOf(item.link)); } else list.load();
    } catch (current) {
      if (alive.current && route.current === startedOn) setError(current.message);
    } finally {
      lock.current = false;
      if (alive.current) setBusy(false);
    }
  };
  const failure = error || list.error || list.backgroundError || summary.error || summary.backgroundError;
  return <div className="manager-notice-bell" ref={root}>
    <button ref={trigger} className="admin-action" aria-label={`Thông báo vận hành: ${summary.data?.unreadCount ?? 0} chưa đọc`} aria-expanded={open} aria-controls="manager-notice-panel" onClick={() => { setOpen(value => !value); setPage(0); }}>
      <Bell size={18} /><span>{summary.data?.unreadCount ?? 0}</span>
      {(summary.error || summary.backgroundError) && <span aria-label="Lỗi tải thông báo">!</span>}
    </button>
    {open && <section id="manager-notice-panel" className="manager-notice-panel" aria-label="Thông báo vận hành">
      <h2>Thông báo vận hành</h2>
      <div className="care-actions">
        <button className="admin-action" disabled={busy} onClick={() => read(null)}>Đánh dấu tất cả đã đọc</button>
        <button className="admin-action" onClick={() => { setOpen(false); trigger.current?.focus(); }}>Đóng</button>
      </div>
      {failure && <p role="alert">{failure} <button className="admin-action" disabled={busy} onClick={() => { setError(''); summary.load(); list.load(); }}>Thử lại</button></p>}
      {list.loading && !data && <p role="status">Đang tải thông báo…</p>}
      {data && !data.page.content.length && <p>Chưa có thông báo vận hành.</p>}
      <ul>{data?.page.content.map(item => <li key={item.id}>
        <button className="manager-notification-item" disabled={busy} onClick={() => read(item)}>
          <strong>{item.title}</strong><span>{item.content}</span>
          <small>{new Date(item.createdAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })} · {item.readAt ? 'Đã đọc' : 'Chưa đọc'}</small>
        </button>
      </li>)}</ul>
      <div className="admin-pagination">
        <button className="admin-action" disabled={busy || list.loading || page === 0} onClick={() => setPage(n => n - 1)}>Trước</button>
        <span>Trang {page + 1}/{Math.max(1, data?.page.totalPages || 0)}</span>
        <button className="admin-action" disabled={busy || list.loading || !data || page + 1 >= data.page.totalPages} onClick={() => setPage(n => n + 1)}>Sau</button>
      </div>
    </section>}
  </div>;
}
