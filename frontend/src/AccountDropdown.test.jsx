// @vitest-environment jsdom
import React from 'react';
import { render, fireEvent, screen, cleanup, act } from '@testing-library/react';
import { beforeEach, afterEach, it, expect, vi } from 'vitest';
import AccountDropdown from './AccountDropdown';
beforeEach(() => {
  vi.stubGlobal('PointerEvent', class extends MouseEvent {
    constructor(type, options={}) { super(type, options); this.pointerType=options.pointerType || 'mouse'; }
  });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.useRealTimers(); vi.unstubAllGlobals(); });
it.each([
  ['Trần Min Pha', 'Pha'],
  ['  Nguyễn   Văn Kiệt  ', 'Kiệt'],
  ['An', 'An'],
  ['', 'Tài khoản'],
  ['   ', 'Tài khoản'],
  [undefined, 'Tài khoản'],
])('shows only the last name without an icon or status dot: %s', (fullName, expected) => {
  const {container}=render(<AccountDropdown user={{fullName}} onAction={vi.fn()}/>);
  const button=screen.getByLabelText('Tài khoản');
  expect(button.textContent.trim()).toBe(expected);
  expect(button.querySelector('svg')).toBeNull();
  expect(container.querySelector('.account-status-dot')).toBeNull();
});
it('opens customer shortcuts on hover and closes after an action', () => {
  const action=vi.fn(); const {container}=render(<AccountDropdown user={{fullName:'Khách thử',email:'test@example.com'}} onAction={action}/>);
  fireEvent.pointerEnter(container.firstChild,{pointerType:'mouse'});
  expect(screen.getByText('Hồ sơ của tôi')).toBeTruthy();
  fireEvent.click(screen.getByText('Đơn hàng của tôi'));
  expect(action).toHaveBeenCalledWith('orders');
  expect(screen.queryByText('Đơn hàng của tôi')).toBeNull();
});
it('opens on mouse hover even if a hybrid browser reports no hover capability', () => {
  vi.stubGlobal('matchMedia',vi.fn(()=>({matches:false})));
  const {container}=render(<AccountDropdown onAction={vi.fn()}/>);
  fireEvent.pointerEnter(container.firstChild,{pointerType:'mouse'});
  expect(screen.getByText('Đăng nhập')).toBeTruthy();
  fireEvent.click(screen.getByLabelText('Tài khoản'),{detail:1});
  expect(screen.getByText('Đăng nhập')).toBeTruthy();
});
it('does not open for touch hover but toggles on taps', () => {
  const {container}=render(<AccountDropdown onAction={vi.fn()}/>);
  const button=screen.getByLabelText('Tài khoản');
  fireEvent.pointerEnter(container.firstChild,{pointerType:'touch'});
  expect(button.getAttribute('aria-expanded')).toBe('false');
  fireEvent.pointerDown(button,{pointerType:'touch'}); fireEvent.click(button,{detail:1});
  expect(button.getAttribute('aria-expanded')).toBe('true');
  fireEvent.pointerDown(button,{pointerType:'touch'}); fireEvent.click(button,{detail:1});
  expect(button.getAttribute('aria-expanded')).toBe('false');
});
it('waits 180ms on leaving and cancels closing when the pointer reaches the menu', () => {
  vi.useFakeTimers();
  const {container}=render(<AccountDropdown onAction={vi.fn()}/>);
  const root=container.firstChild, button=screen.getByLabelText('Tài khoản');
  fireEvent.pointerEnter(root,{pointerType:'mouse'});
  fireEvent.pointerLeave(root,{pointerType:'mouse'});
  act(()=>vi.advanceTimersByTime(179)); expect(button.getAttribute('aria-expanded')).toBe('true');
  fireEvent.pointerEnter(screen.getByLabelText('Chức năng tài khoản'),{pointerType:'mouse'});
  act(()=>vi.advanceTimersByTime(200)); expect(button.getAttribute('aria-expanded')).toBe('true');
  fireEvent.pointerLeave(root,{pointerType:'mouse'});
  act(()=>vi.advanceTimersByTime(180)); expect(button.getAttribute('aria-expanded')).toBe('false');
});
it('keeps keyboard focus in the menu and cleans pending timers on unmount', () => {
  vi.useFakeTimers();
  const {container,unmount}=render(<AccountDropdown onAction={vi.fn()}/>);
  const button=screen.getByLabelText('Tài khoản');
  fireEvent.keyDown(button,{key:'ArrowDown'});
  expect(document.activeElement).toBe(screen.getByText('Đăng nhập').closest('button'));
  fireEvent.pointerLeave(container.firstChild,{pointerType:'mouse'});
  act(()=>vi.advanceTimersByTime(180)); expect(button.getAttribute('aria-expanded')).toBe('true');
  fireEvent.keyDown(document.activeElement,{key:'Escape'});
  expect(document.activeElement).toBe(button); expect(button.getAttribute('aria-expanded')).toBe('false');
  fireEvent.pointerEnter(container.firstChild,{pointerType:'mouse'});
  const schedule=vi.spyOn(globalThis,'setTimeout'), cancel=vi.spyOn(globalThis,'clearTimeout');
  fireEvent.pointerLeave(container.firstChild,{pointerType:'mouse'});
  const timerIndex=schedule.mock.calls.findIndex(([,delay])=>delay===180);
  expect(timerIndex).toBeGreaterThanOrEqual(0);
  const pending=schedule.mock.results[timerIndex].value;
  unmount(); expect(cancel).toHaveBeenCalledWith(pending);
});
it('supports tap, Escape, outside click and guest registration', () => {
  const action=vi.fn(); render(<AccountDropdown onAction={action}/>);
  const button=screen.getByLabelText('Tài khoản');
  fireEvent.click(button); fireEvent.click(screen.getByText('Tạo tài khoản'));
  expect(action).toHaveBeenCalledWith('register');
  fireEvent.click(button); fireEvent.keyDown(button,{key:'Escape'});
  expect(button.getAttribute('aria-expanded')).toBe('false');
  fireEvent.click(button); fireEvent.pointerDown(document.body);
  expect(button.getAttribute('aria-expanded')).toBe('false');
});
it('shows all authenticated actions and unread count', () => {
  render(<AccountDropdown user={{fullName:'Khách'}} unreadCount={3} onAction={vi.fn()}/>);
  fireEvent.keyDown(screen.getByLabelText('Tài khoản'),{key:'ArrowDown'});
  for (const label of ['Địa chỉ giao hàng','Khảo sát phong cách','Bảo mật tài khoản','Đăng xuất']) expect(screen.getByText(label)).toBeTruthy();
  expect(screen.getByText('3')).toBeTruthy();
});
