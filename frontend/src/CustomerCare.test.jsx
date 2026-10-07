// @vitest-environment jsdom
import React from 'react';
import {act,cleanup,fireEvent,render,screen,within} from '@testing-library/react';
import {beforeEach,afterEach,it,vi,expect} from 'vitest';
import ManagerApp from './ManagerApp';
import {careDeadline} from './CustomerCare';
import {adminApi,adminRefreshSession} from './manager-api';
vi.mock('./manager-api',async original=>({...await original(),adminApi:vi.fn(),adminRefreshSession:vi.fn()}));
const customer={id:7,fullName:'Khách A',email:'customer@test.vn'};
const staff={id:3,fullName:'Quản lý B',email:'staff@test.vn'};
const task={id:8,customerId:7,customerName:'Khách A',assigneeId:3,assigneeName:'Quản lý B',assigneeActive:true,title:'Gọi chăm sóc',description:'Hỏi trải nghiệm',status:'TODO',overdue:true,dueAt:'2026-01-01T10:00:00Z',createdAt:'2026-01-01T09:00:00Z',updatedAt:'2026-01-01T09:00:00Z',version:0};
const page=content=>({content,totalElements:content.length,totalPages:content.length?1:0});
const flush=async()=>act(async()=>{await import('./CustomerCare');await import('./CustomerProfile');await vi.advanceTimersByTimeAsync(0);});
const response=async(url,options)=>{
 if(options?.method)return task;
 if(url.includes('/care/staff'))return page([staff]);
 if(url.endsWith('/profile'))return {customer,summary:{totalOrders:0,completedOrders:0,completedValue:0,cancelledOrders:0,returnOrders:0}};
 if(url.includes('/care/notes'))return page([{id:1,content:'Ghi chú cũ',authorName:'Quản lý B',createdAt:task.createdAt}]);
 if(url.includes('/care/tasks'))return page([task]);
 return page([customer]);
};
beforeEach(()=>{vi.useFakeTimers();vi.spyOn(document,'visibilityState','get').mockReturnValue('visible');adminRefreshSession.mockResolvedValue({manager:staff});adminApi.mockReset();adminApi.mockImplementation(response);window.history.replaceState({},'','/manager/care');});
afterEach(()=>{cleanup();vi.useRealTimers();vi.restoreAllMocks();});
it('opens the care route and restores filters through URL and popstate',async()=>{
 render(<ManagerApp/>);await flush();expect(screen.getByText('Gọi chăm sóc')).toBeTruthy();expect(screen.getByText('Quá hạn',{selector:'span'})).toBeTruthy();
 fireEvent.change(screen.getByLabelText('Lọc trạng thái chăm sóc'),{target:{value:'IN_PROGRESS'}});await flush();expect(window.location.search).toContain('status=IN_PROGRESS');expect(adminApi.mock.calls.at(-1)[0]).toContain('status=IN_PROGRESS');
 window.history.replaceState({},'','/manager/care?overdue=true&page=2');fireEvent.popState(window);await flush();expect(screen.getByLabelText('Lọc quá hạn').value).toBe('true');expect(adminApi.mock.calls.at(-1)[0]).toContain('page=2');
});
it('creates a task using selected customer, assignee and Vietnam deadline; blocks repeated submits',async()=>{
 let resolve;adminApi.mockImplementation((url,options)=>options?.method==='POST'?new Promise(r=>{resolve=r;}):response(url,options));
 render(<ManagerApp/>);await flush();fireEvent.click(screen.getByText('Tạo công việc chăm sóc'));await flush();
 fireEvent.change(screen.getByLabelText('Khách hàng'),{target:{value:'7'}});fireEvent.change(screen.getByLabelText('Người phụ trách'),{target:{value:'3'}});
 fireEvent.change(screen.getByLabelText('Tiêu đề công việc'),{target:{value:'Gọi lại khách'}});fireEvent.change(screen.getByLabelText('Hạn xử lý'),{target:{value:'2026-10-10T09:00'}});
 const form=screen.getByRole('form',{name:'Tạo công việc chăm sóc'});fireEvent.submit(form);fireEvent.submit(form);await flush();
 const posts=adminApi.mock.calls.filter(([,o])=>o?.method==='POST');expect(posts).toHaveLength(1);expect(posts[0][1].body).toMatchObject({customerId:7,assigneeId:3,dueAt:'2026-10-10T02:00:00.000Z'});
 await act(async()=>resolve(task));await flush();expect(screen.getByText('Đã lưu công việc chăm sóc.')).toBeTruthy();
});
it('preserves drafts on errors and pauses polling while a task is edited',async()=>{
 adminApi.mockImplementation((url,options)=>options?.method?Promise.reject(new Error('Mất kết nối')):response(url,options));
 render(<ManagerApp/>);await flush();fireEvent.click(screen.getByText('Xử lý công việc'));await flush();
 fireEvent.change(screen.getByLabelText('Tiêu đề công việc'),{target:{value:'Giữ nội dung'}});
 const count=adminApi.mock.calls.length;await act(async()=>vi.advanceTimersByTimeAsync(10000));expect(adminApi.mock.calls).toHaveLength(count);
 fireEvent.submit(screen.getByRole('form',{name:'Sửa công việc chăm sóc'}));await flush();expect(screen.getByRole('alert').textContent).toContain('Mất kết nối');expect(screen.getByLabelText('Tiêu đề công việc').value).toBe('Giữ nội dung');
});
it('shows notes in profile and preserves original list filters when care filters change',async()=>{
 window.history.replaceState({},'','/manager/users/7?q=Alice&page=2&stage=LOYAL&tab=care');
 render(<ManagerApp/>);await flush();expect(screen.getByText('Ghi chú cũ')).toBeTruthy();
 fireEvent.change(screen.getByLabelText('Lọc quá hạn'),{target:{value:'true'}});await flush();
 expect(window.location.search).toContain('tab=care');expect(window.location.search).toContain('care_overdue=true');expect(window.location.search).toContain('q=Alice');
 fireEvent.change(screen.getByLabelText('Nội dung ghi chú'),{target:{value:'Ghi chú mới'}});fireEvent.click(screen.getByText('Lưu ghi chú'));await flush();
 expect(adminApi.mock.calls.some(([url,o])=>url==='/api/manager/users/7/care/notes'&&o?.body?.content==='Ghi chú mới')).toBe(true);
 fireEvent.click(screen.getByText('← Danh sách khách hàng'));await flush();expect(window.location.search).toContain('stage=LOYAL');
});
it('keeps the last successful task list on polling failure and completed tasks cannot be edited',async()=>{
 adminApi.mockResolvedValue(page([{...task,status:'COMPLETED',overdue:false,completedAt:task.updatedAt}]));render(<ManagerApp/>);await flush();expect(screen.queryByText('Xử lý công việc')).toBeNull();
 adminApi.mockRejectedValue(new Error('Offline'));await act(async()=>vi.advanceTimersByTimeAsync(5000));expect(screen.getByText('Gọi chăm sóc')).toBeTruthy();expect(screen.getByText('Chưa cập nhật được dữ liệu mới')).toBeTruthy();
});
it('converts the deadline explicitly from Vietnam time',()=>expect(careDeadline('2026-10-06T00:00')).toBe('2026-10-05T17:00:00.000Z'));
