// @vitest-environment jsdom
import React from 'react';
import {render,screen,fireEvent,cleanup,waitFor} from '@testing-library/react';
import {it,expect,vi,afterEach} from 'vitest';
import SystemAdminApp from './SystemAdminApp';
import {adminApi,adminRefreshSession} from './api';
vi.mock('./api',()=>({adminApi:vi.fn(),adminRefreshSession:vi.fn(),adminSignIn:vi.fn(),adminSignOut:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks()});
it('admin has only system navigation and accounts use admin APIs',async()=>{
 window.history.replaceState({},'', '/admin/accounts');adminRefreshSession.mockResolvedValue({admin:{email:'admin@test.vn'}});
 adminApi.mockImplementation(async url=>url.includes('overview')?{accounts:2,products:3,suppliers:1}:{content:[],totalPages:0});
 render(<SystemAdminApp/>);await screen.findByText('Chưa có dữ liệu phù hợp.');
 expect(screen.queryByRole('link',{name:'Khách hàng'})).toBeNull();expect(screen.queryByRole('link',{name:'Đơn hàng'})).toBeNull();
 expect(adminApi.mock.calls.every(([url])=>url.startsWith('/api/admin/'))).toBe(true);
 fireEvent.click(screen.getByText('Thêm mới'));expect(screen.getByLabelText('Vai trò').value).toBe('MANAGER');
});
it('admin redirects anonymous requests to its login',async()=>{
 window.history.replaceState({},'', '/admin/products');adminRefreshSession.mockResolvedValue(null);render(<SystemAdminApp/>);
 await screen.findByText('Đăng nhập Admin');await waitFor(()=>expect(window.location.pathname).toBe('/admin/login'));
});
