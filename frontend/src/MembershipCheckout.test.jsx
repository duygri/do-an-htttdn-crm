// @vitest-environment jsdom
import {renderHook,act,waitFor,cleanup} from '@testing-library/react';import {it,expect,vi,afterEach} from 'vitest';
import useMembershipCheckout from './useMembershipCheckout';import {api} from './api';
vi.mock('./api',()=>({api:vi.fn()}));afterEach(()=>{cleanup();vi.resetAllMocks();});
const cart={subtotal:100000,items:[{productId:1,quantity:1,size:'M',color:'Đen'}]};
const quote={subtotal:100000,membership:{tier:'DIAMOND',discountPercent:10},memberDiscountAmount:10000,afterMemberAmount:90000,totalAmount:90000};
it('loads authoritative quote and checks voucher on post-tier amount',async()=>{
 api.mockResolvedValueOnce(quote).mockResolvedValueOnce({...quote,voucher:{code:'SHOP',discountAmount:9000},discountAmount:9000,totalAmount:81000});
 const {result}=renderHook(()=>useMembershipCheckout(cart));await waitFor(()=>expect(result.current.quote?.totalAmount).toBe(90000));
 act(()=>result.current.change('shop'));act(()=>result.current.apply());await waitFor(()=>expect(result.current.quote?.totalAmount).toBe(81000));
 expect(result.current.subtotal).toBe(90000);expect(api).toHaveBeenLastCalledWith('/api/orders/preview',expect.objectContaining({body:{items:cart.items,voucherCode:'SHOP'}}));
});
it('ignores stale quotes, preserves code on error and supports retry',async()=>{
 let resolve;api.mockImplementationOnce(()=>new Promise(r=>resolve=r)).mockRejectedValueOnce(new Error('Voucher không hợp lệ')).mockResolvedValueOnce(quote);
 const {result}=renderHook(()=>useMembershipCheckout(cart));act(()=>result.current.change('SHOP'));act(()=>result.current.apply());
 await waitFor(()=>expect(result.current.error).toBe('Voucher không hợp lệ'));await act(async()=>resolve(quote));expect(result.current.quote).toBeNull();expect(result.current.code).toBe('SHOP');
 act(()=>result.current.retry());await waitFor(()=>expect(result.current.quote?.totalAmount).toBe(90000));
});
