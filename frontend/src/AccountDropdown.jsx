import React, { useEffect, useRef, useState } from 'react';
import { UserRound, Package, Heart, Bell, MapPin, ClipboardList, KeyRound, LogOut, LogIn, UserPlus, ChevronRight } from 'lucide-react';

export default function AccountDropdown({ user, unreadCount = 0, onAction }) {
  const accountName = user?.fullName?.trim().split(/\s+/).pop() || 'Tài khoản';
  const [open, setOpen] = useState(false);
  const root = useRef(null);
  const trigger = useRef(null);
  const closeTimer = useRef(null);
  const pointerType = useRef('mouse');
  const focusMenu = useRef(false);
  const cancelClose = () => { clearTimeout(closeTimer.current); closeTimer.current = null; };
  const close = () => { cancelClose(); setOpen(false); };
  useEffect(() => {
    if (open && focusMenu.current) {
      root.current?.querySelector('nav button')?.focus();
      focusMenu.current = false;
    }
  }, [open]);
  useEffect(() => {
    const outside = event => { if (!root.current?.contains(event.target)) close(); };
    document.addEventListener('pointerdown', outside);
    return () => { document.removeEventListener('pointerdown', outside); cancelClose(); };
  }, []);
  const items = user ? [
    ['profile', 'Hồ sơ của tôi', UserRound], ['orders', 'Đơn hàng của tôi', Package],
    ['wishlist', 'Sản phẩm yêu thích', Heart], ['notifications', 'Thông báo', Bell],
    ['addresses', 'Địa chỉ giao hàng', MapPin], ['surveys', 'Khảo sát phong cách', ClipboardList],
    ['support', 'Yêu cầu hỗ trợ', ClipboardList],
    ['password', 'Bảo mật tài khoản', KeyRound], ['logout', 'Đăng xuất', LogOut],
  ] : [['login', 'Đăng nhập', LogIn], ['register', 'Tạo tài khoản', UserPlus]];
  return <div className="shop-account" ref={root}
    onPointerEnter={event => {
      if (event.pointerType !== 'mouse') return;
      pointerType.current = 'mouse'; cancelClose(); setOpen(true);
    }}
    onPointerDown={event => { pointerType.current = event.pointerType; cancelClose(); }}
    onPointerLeave={event => {
      if (event.pointerType !== 'mouse') return;
      cancelClose();
      closeTimer.current = setTimeout(() => {
        if (!root.current?.querySelector('.shop-account-panel')?.contains(document.activeElement)) setOpen(false);
      }, 180);
    }}
    onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget)) close(); }}
    onKeyDown={event => {
      if (event.key === 'Escape') { event.preventDefault(); close(); trigger.current?.focus(); }
    }}>
    <button type="button" ref={trigger} className={`icon-button account-button${user ? ' signed-in' : ''}`} aria-label="Tài khoản" aria-expanded={open} aria-controls="shop-account-panel"
      onClick={event => { cancelClose(); if (event.detail > 0 && pointerType.current === 'mouse') setOpen(true); else setOpen(value => !value); }}
      onKeyDown={event => {
        if (event.key === 'ArrowDown') {
          event.preventDefault(); cancelClose(); focusMenu.current = true;
          if (open) { root.current?.querySelector('nav button')?.focus(); focusMenu.current = false; }
          else setOpen(true);
        }
      }}>
      {!user && <UserRound size={20} strokeWidth={1.8}/>}
      <span className="account-btn-label">
        {user ? accountName : 'Tài khoản'}
      </span>
    </button>
    {open && <div className="shop-account-panel" id="shop-account-panel" aria-label="Chức năng tài khoản">
      <div className="shop-account-heading"><span className="shop-account-avatar">{user?.fullName?.slice(0, 1).toUpperCase() || <UserRound size={22}/>}</span><div><small>{user ? 'Xin chào,' : 'Chào mừng đến với'}</small><strong>{user?.fullName || 'ANH LỚN SHOP'}</strong><p>{user?.email || 'Đăng nhập để mua sắm thuận tiện hơn'}</p></div></div>
      <nav aria-label="Menu tài khoản">{items.map(([key, label, Icon]) => <button type="button" key={key} className={key === 'logout' ? 'shop-account-logout' : ''} onClick={() => { close(); onAction(key); }}><Icon size={18}/><span>{label}</span>{key === 'notifications' && unreadCount > 0 ? <b className="shop-account-count">{unreadCount > 99 ? '99+' : unreadCount}</b> : <ChevronRight size={14}/>}</button>)}</nav>
    </div>}
  </div>;
}
