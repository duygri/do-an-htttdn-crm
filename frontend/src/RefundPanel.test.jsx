// @vitest-environment jsdom
import React from 'react';
import {render,screen,fireEvent,cleanup,waitFor} from '@testing-library/react';
import {afterEach,it,expect,vi} from 'vitest';
import RefundPanel from './RefundPanel';
import {adminApi} from './manager-api';
vi.mock('./manager-api',async original=>({...await original(),adminApi:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();});
const detail={id:9,orderCode:9009,status:'DELIVERED',returnStatus:'APPROVED',refunds:[],
  finance:{refundedAmount:0,refundPendingAmount:0,refundableAmount:300000,refundStatus:'NONE'}};
it('creates a pending refund with the remaining amount',async()=>{
  adminApi.mockResolvedValue({...detail,refunds:[{id:1,amount:100000,status:'PENDING',method:'BANK_TRANSFER',requestedAt:'2026-11-22T00:00:00Z'}]});
  const onUpdated=vi.fn(); render(<RefundPanel detail={detail} onUpdated={onUpdated}/>);
  fireEvent.change(screen.getByLabelText('Số tiền hoàn'),{target:{value:'100000'}});
  fireEvent.change(screen.getByLabelText('Mã giao dịch'),{target:{value:'FT-1'}});
  fireEvent.change(screen.getByLabelText('Ghi chú hoàn tiền'),{target:{value:'Hoàn áo'}});
  fireEvent.click(screen.getByRole('button',{name:'Tạo phiếu hoàn'}));
  await waitFor(()=>expect(adminApi).toHaveBeenCalledWith('/api/manager/orders/9/refunds',{method:'POST',body:{
    amount:100000,method:'BANK_TRANSFER',bankReference:'FT-1',note:'Hoàn áo',evidenceUrl:'',requestKey:''}}));
  expect(onUpdated).toHaveBeenCalled();
});
it('completes a pending refund and requires a bank reference',async()=>{
  const pending={...detail,refunds:[{id:1,amount:100000,status:'PENDING',method:'BANK_TRANSFER',requestedAt:'2026-11-22T00:00:00Z'}]};
  adminApi.mockResolvedValue({...pending,refunds:[{...pending.refunds[0],status:'COMPLETED'}]});
  render(<RefundPanel detail={pending} onUpdated={()=>{}}/>);
  fireEvent.click(screen.getByRole('button',{name:'Hoàn tất'}));
  fireEvent.change(screen.getByLabelText('Ghi chú xử lý'),{target:{value:'Đã chuyển'}});
  fireEvent.click(screen.getByRole('button',{name:'Xác nhận hoàn tất'}));
  expect(screen.getByText('Nhập mã giao dịch ngân hàng để hoàn tất.')).toBeTruthy();
  fireEvent.change(screen.getByLabelText('Mã giao dịch'),{target:{value:'FT-9'}});
  fireEvent.click(screen.getByRole('button',{name:'Xác nhận hoàn tất'}));
  await waitFor(()=>expect(adminApi).toHaveBeenLastCalledWith('/api/manager/orders/9/refunds/1',{method:'PATCH',body:{status:'COMPLETED',processingNote:'Đã chuyển',bankReference:'FT-9',evidenceUrl:''}}));
});
