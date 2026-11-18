// @vitest-environment jsdom
import React from 'react';
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import ManagerOrderPayments from './ManagerOrderPayments';
import {adminApi} from './manager-api';

vi.mock('./manager-api',()=>({adminApi:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();});

it('keeps PayOS reconciliation available without a refund workflow',async()=>{
  const onUpdated=vi.fn();
  adminApi.mockResolvedValue({id:9,finance:{reviewRequired:false}});
  render(<ManagerOrderPayments order={{id:9,status:'CANCELLED',paymentMethod:'PAYOS',finance:{reviewRequired:true,reviewReason:'MISSING_LINK'}}} onUpdated={onUpdated}/>);

  expect(screen.getByText('Thanh toán cần kiểm tra')).toBeTruthy();
  fireEvent.click(screen.getByRole('button',{name:'Đối chiếu lại PayOS'}));
  await waitFor(()=>expect(adminApi).toHaveBeenCalledWith('/api/manager/orders/9/payment/reconcile',{method:'POST'}));
  expect(onUpdated).toHaveBeenCalledWith({id:9,finance:{reviewRequired:false}});
  expect(screen.queryByText(/hoàn tiền/i)).toBeNull();
});

it('keeps explicit COD collection confirmation for delivered orders',async()=>{
  const onUpdated=vi.fn();
  adminApi.mockResolvedValue({id:10,finance:{codCollectedAt:'2026-11-18T00:00:00Z'}});
  const {rerender}=render(<ManagerOrderPayments order={{id:10,status:'PENDING',paymentMethod:'COD',totalAmount:50000,finance:{}}} onUpdated={onUpdated}/>);
  expect(screen.queryByText('Xác nhận đã thu COD')).toBeNull();

  rerender(<ManagerOrderPayments order={{id:10,status:'DELIVERED',paymentMethod:'COD',totalAmount:50000,finance:{}}} onUpdated={onUpdated}/>);
  fireEvent.click(screen.getByRole('button',{name:'Xác nhận đã thu COD'}));
  fireEvent.change(screen.getByLabelText('Ghi chú'),{target:{value:'Đã kiểm tra tiền thu'}});
  fireEvent.submit(screen.getByRole('form',{name:'Xác nhận thu COD'}));
  await waitFor(()=>expect(adminApi).toHaveBeenCalledWith('/api/manager/orders/10/cod-collection',{method:'PATCH',body:{note:'Đã kiểm tra tiền thu'}}));
  expect(onUpdated).toHaveBeenCalledWith({id:10,finance:{codCollectedAt:'2026-11-18T00:00:00Z'}});
});
