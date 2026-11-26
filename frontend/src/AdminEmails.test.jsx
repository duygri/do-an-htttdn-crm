// @vitest-environment jsdom
import React from 'react';
import {render,screen,fireEvent,waitFor,cleanup} from '@testing-library/react';
import {it,expect,vi,afterEach,beforeEach} from 'vitest';
import AdminEmails from './AdminEmails';
import {adminApi} from './api';
vi.mock('./api',()=>({adminApi:vi.fn()}));
afterEach(cleanup);
beforeEach(()=>{
  vi.resetAllMocks();
  HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','');};
  HTMLDialogElement.prototype.close=function(){this.removeAttribute('open');};
});
const row=(id,status,attempts=1)=>({id,status,attempts,recipientHint:'***@***',reason:'SAFE_REASON',updatedAt:'2026-11-01T00:00:00Z'});
const page=content=>({content,totalPages:2});
it('explains accepted semantics, filters and paginates, and exposes no retry for accepted or exhausted mail',async()=>{
  adminApi.mockResolvedValue(page([row(1,'ACCEPTED'),row(2,'FAILED',5)]));
  render(<AdminEmails/>);
  await screen.findByText('#1');
  expect(screen.getByText(/chưa xác nhận thư/)).toBeTruthy();
  expect(screen.queryByText('Thử lại #1')).toBeNull();expect(screen.queryByText('Thử lại #2')).toBeNull();
  fireEvent.change(screen.getByLabelText('Trạng thái'),{target:{value:'FAILED'}});
  await waitFor(()=>expect(adminApi).toHaveBeenLastCalledWith('/api/admin/emails?page=0&status=FAILED',expect.anything()));
  await screen.findByText('#1');fireEvent.click(screen.getByText('Trang sau'));
  await waitFor(()=>expect(adminApi).toHaveBeenLastCalledWith('/api/admin/emails?page=1&status=FAILED',expect.anything()));
});
it('requires explicit duplicate-risk confirmation to retry unknown and refreshes after success',async()=>{
  adminApi.mockImplementation((url,options)=>Promise.resolve(options?.method?'':page([row(7,'UNKNOWN')])));
  render(<AdminEmails/>);fireEvent.click(await screen.findByText('Thử lại #7'));
  expect(screen.getByRole('dialog')).toBeTruthy();expect(screen.getByText(/chấp nhận nguy cơ gửi trùng/)).toBeTruthy();
  expect(adminApi.mock.calls.filter(([,options])=>options?.method==='POST')).toHaveLength(0);
  fireEvent.click(screen.getByText('Xác nhận thử lại'));
  await waitFor(()=>expect(adminApi).toHaveBeenCalledWith('/api/admin/emails/7/retry',{method:'POST',body:{acknowledgeUnknown:true}}));
  await waitFor(()=>expect(screen.queryByRole('dialog')).toBeNull());
});
it('shows load errors and an empty state after reload',async()=>{
  adminApi.mockRejectedValueOnce(new Error('Không thể tải')).mockResolvedValue(page([]));render(<AdminEmails/>);
  expect(await screen.findByRole('alert')).toBeTruthy();fireEvent.click(screen.getByText('Thử tải lại'));
  expect(await screen.findByText('Chưa có email phù hợp.')).toBeTruthy();
});
it('retains retry dialog when backend rejects retry',async()=>{
  adminApi.mockImplementation((url,options)=>options?.method?Promise.reject(new Error('Không thể thử lại')):Promise.resolve(page([row(9,'FAILED')])));
  render(<AdminEmails/>);fireEvent.click(await screen.findByText('Thử lại #9'));fireEvent.click(screen.getByText('Xác nhận thử lại'));
  expect(await screen.findByRole('alert')).toBeTruthy();expect(screen.getByRole('dialog')).toBeTruthy();
  expect(adminApi).toHaveBeenCalledWith('/api/admin/emails/9/retry',{method:'POST',body:{acknowledgeUnknown:false}});
});

it('Escape closes the native dialog and restores trigger focus',async()=>{
  adminApi.mockResolvedValue(page([row(11,'UNKNOWN')]));render(<AdminEmails/>);
  const trigger=await screen.findByText('Thử lại #11');trigger.focus();fireEvent.click(trigger);
  const dialog=screen.getByRole('dialog');fireEvent(dialog,new Event('cancel',{cancelable:true}));
  await waitFor(()=>expect(screen.queryByRole('dialog')).toBeNull());
  expect(document.activeElement).toBe(trigger);
});
it('blocks Escape and repeat submission while a retry is pending',async()=>{
  let finish;const pending=new Promise(resolve=>{finish=resolve;});
  adminApi.mockImplementation((url,options)=>options?.method?pending:Promise.resolve(page([row(12,'UNKNOWN')])));
  render(<AdminEmails/>);fireEvent.click(await screen.findByText('Thử lại #12'));
  fireEvent.click(screen.getByText('Xác nhận thử lại'));
  fireEvent(screen.getByRole('dialog'),new Event('cancel',{cancelable:true}));
  expect(screen.getByRole('dialog')).toBeTruthy();expect(screen.getByText('Xác nhận thử lại').disabled).toBe(true);
  finish('');await waitFor(()=>expect(screen.queryByRole('dialog')).toBeNull());
});
