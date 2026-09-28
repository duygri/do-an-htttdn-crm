import React, { useEffect, useRef, useState } from 'react';
import { api, endpoints, signOut } from './api';

export default function CustomerPassword({ initialMode, onNavigate, onNotice }) {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', email: '', code: '' });
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const lock = useRef(false), alive = useRef(true);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => { setError(''); setForm(current => ({ ...current, currentPassword: '', newPassword: '' })); }, [initialMode]);
  const title = initialMode === 'change' ? 'Đổi mật khẩu' : initialMode === 'forgot' ? 'Quên mật khẩu' : 'Đặt lại mật khẩu';
  const submit = async event => {
    event.preventDefault(); if (lock.current) return;
    lock.current = true; setBusy(true); setError('');
    try {
      if (initialMode === 'change') {
        await api(endpoints.changePassword, { method: 'POST', body: { currentPassword: form.currentPassword, newPassword: form.newPassword } });
        await signOut().catch(() => {});
        window.dispatchEvent(new Event('shop-session-expired'));
        if (alive.current) { onNotice('Đã đổi mật khẩu. Vui lòng đăng nhập lại.'); onNavigate('/dang-nhap'); }
      } else if (initialMode === 'forgot') {
        const result = await api(endpoints.forgotPassword, { method: 'POST', body: { email: form.email } });
        if (alive.current) {
          setForm(current => ({ ...current, code: result.resetCode || '' }));
          onNotice(result.message || 'Kiểm tra hướng dẫn khôi phục mật khẩu.');
          onNavigate('/dat-lai-mat-khau');
        }
      } else {
        await api(endpoints.resetPassword, { method: 'POST', body: { email: form.email, code: form.code, newPassword: form.newPassword } });
        if (alive.current) { onNotice('Đặt lại mật khẩu thành công.'); onNavigate('/dang-nhap'); }
      }
    } catch (err) { if (alive.current) setError(err.message); }
    finally { lock.current = false; if (alive.current) setBusy(false); }
  };
  const field = (name, label, props = {}) => <label>{label}<input required {...props} value={form[name]} onChange={event => setForm(current => ({ ...current, [name]: event.target.value }))}/></label>;
  return <div className="customer-page-body"><section className="auth-modal password-modal">
    <p className="kicker">BẢO MẬT TÀI KHOẢN</p><h1>{title}</h1>
    <p>Mật khẩu mới cần có ít nhất 8 ký tự, gồm chữ và số.</p>
    {error && <p role="alert" className="shop-inline-error">{error}</p>}
    <form onSubmit={submit}><fieldset disabled={busy}>
      {initialMode === 'change' ? field('currentPassword', 'Mật khẩu hiện tại', { type: 'password', autoComplete: 'current-password' }) : field('email', 'Email', { type: 'email', autoComplete: 'email' })}
      {initialMode === 'reset' && field('code', 'Mã 6 chữ số', { inputMode: 'numeric', pattern: '[0-9]{6}', autoComplete: 'one-time-code' })}
      {initialMode !== 'forgot' && field('newPassword', 'Mật khẩu mới', { type: 'password', minLength: 8, autoComplete: 'new-password' })}
      <button className="button button-dark" disabled={busy}>{busy ? 'Đang xử lý...' : initialMode === 'forgot' ? 'Nhận mã đặt lại' : title}</button>
    </fieldset></form>
    <div className="auth-switch"><button onClick={() => onNavigate(initialMode === 'change' ? '/quen-mat-khau' : '/dang-nhap')}>{initialMode === 'change' ? 'Quên mật khẩu?' : 'Về đăng nhập'}</button></div>
  </section></div>;
}
