// @vitest-environment jsdom
import React from 'react';
import {act,cleanup,fireEvent,render,screen} from '@testing-library/react';
import {beforeEach,afterEach,it,expect,vi} from 'vitest';
import AuditLogs from './AuditLogs';
import {adminApi as managerApi} from './manager-api';
import {adminApi} from './api';
vi.mock('./manager-api',async original=>({...await original(),adminApi:vi.fn()}));
vi.mock('./api',async original=>({...await original(),adminApi:vi.fn()}));
const data={content:[{id:1,actorName:'Quản lý',actorRole:'MANAGER',actorId:2,action:'ORDER_STATUS',entityType:'ORDER',targetId:7,createdAt:'2026-10-15T10:00:00Z',beforeData:{status:'PENDING'},afterData:{status:'CONFIRMED'}}],totalElements:21,totalPages:2};
const flush=async()=>{await act(async()=>{});};
beforeEach(()=>{vi.useFakeTimers();vi.spyOn(document,'visibilityState','get').mockReturnValue('visible');window.history.replaceState({},'','/manager/audit-logs');managerApi.mockReset();adminApi.mockReset();managerApi.mockResolvedValue(data);adminApi.mockResolvedValue(data);});
afterEach(()=>{cleanup();vi.useRealTimers();vi.restoreAllMocks();});
it('shows before/after, filters before pagination and restores URL',async()=>{
 render(<AuditLogs manager/>);await flush();expect(screen.getByText('Đổi trạng thái đơn · Đơn hàng #7')).toBeTruthy();expect(screen.getByText('PENDING')).toBeTruthy();expect(screen.getByText('CONFIRMED')).toBeTruthy();
 fireEvent.click(screen.getByText('Trang sau'));await flush();expect(window.location.search).toContain('page=1');
 fireEvent.change(screen.getByLabelText('Chức năng'),{target:{value:'PRODUCT'}});await flush();expect(window.location.search).not.toContain('page=1');expect(managerApi.mock.calls.at(-1)[0]).toContain('type=PRODUCT');
 fireEvent.change(screen.getByLabelText('Tìm nhật ký'),{target:{value:'Quản lý'}});fireEvent.click(screen.getByText('Tìm kiếm'));await flush();expect(window.location.search).toContain('q=');
 window.history.replaceState({},'','/manager/audit-logs?action=LOCK');act(()=>window.dispatchEvent(new PopStateEvent('popstate')));await flush();expect(screen.getByLabelText('Thao tác').value).toBe('LOCK');
});
it('polls Manager and retains recent data on network errors',async()=>{
 const view=render(<AuditLogs manager/>);await flush();managerApi.mockRejectedValueOnce(new Error('Offline'));await act(async()=>vi.advanceTimersByTimeAsync(5000));expect(screen.getByRole('alert').textContent).toContain('dữ liệu gần nhất');expect(screen.getByText('CONFIRMED')).toBeTruthy();fireEvent.click(screen.getByText('Thử lại'));await flush();expect(screen.queryByRole('alert')).toBeNull();view.unmount();const calls=managerApi.mock.calls.length;await act(async()=>vi.advanceTimersByTimeAsync(10000));expect(managerApi).toHaveBeenCalledTimes(calls);
});
it('uses isolated Admin API without Manager polling and supports retry/empty state',async()=>{
 adminApi.mockRejectedValueOnce(new Error('Offline'));render(<AuditLogs/>);await flush();expect(screen.getByRole('alert').textContent).toContain('Offline');adminApi.mockResolvedValue({content:[],totalElements:0,totalPages:0});fireEvent.click(screen.getByText('Thử lại'));await flush();expect(screen.getByText('Chưa có nhật ký phù hợp.')).toBeTruthy();await act(async()=>vi.advanceTimersByTimeAsync(10000));expect(adminApi).toHaveBeenCalledTimes(2);expect(managerApi).not.toHaveBeenCalled();
});
it('filters care history and labels metadata without introducing a second audit store',async()=>{
 managerApi.mockResolvedValue({...data,content:[{...data.content[0],entityType:'CARE_TASK',action:'UPDATE',targetId:42,beforeData:{assigneeId:2,status:'TODO'},afterData:{assigneeId:3,status:'COMPLETED',outcome:'RESOLVED',parentTaskId:10}}]});
 render(<AuditLogs manager/>);await flush();expect(screen.getByText('Cập nhật · Công việc chăm sóc #42')).toBeTruthy();expect(screen.getByText('ID người phụ trách')).toBeTruthy();expect(screen.getByText('Công việc trước')).toBeTruthy();fireEvent.change(screen.getByLabelText('Chức năng'),{target:{value:'CARE_TASK'}});await flush();expect(managerApi.mock.calls.at(-1)[0]).toContain('type=CARE_TASK');
});
