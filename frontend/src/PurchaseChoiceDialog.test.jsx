// @vitest-environment jsdom
import React from 'react';
import { render, screen, fireEvent, cleanup, waitFor, act } from '@testing-library/react';
import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import PurchaseChoiceDialog from './PurchaseChoiceDialog';
import { api } from './api';
vi.mock('./api', async original => ({ ...(await original()), api: vi.fn() }));
const legacy = { id: 1, name: 'Polo', stock: 5, colors: 'Black,White', sizes: 'M,L' };
const allocated = { ...legacy, variantsAllocated: true, variants: [
  { id: 11, color: 'Black', size: 'M', stock: 2 },
  { id: 12, color: 'White', size: 'L', stock: 3 },
  { id: 13, color: 'Red', size: 'M', stock: 8, active: false },
  { id: 14, color: 'Blue', size: 'M', stock: 0 },
] };
beforeEach(() => { vi.resetAllMocks(); api.mockResolvedValue(legacy); });
afterEach(cleanup);
const confirm = () => screen.getByRole('button', { name: /Xác nhận/ });
async function choose() { fireEvent.click(await screen.findByRole('button', { name: 'Black' })); fireEvent.click(screen.getByRole('button', { name: 'M' })); }
it('requires explicit choices and caps common stock without inventing a variant', async () => {
 const onConfirm=vi.fn().mockResolvedValue({items:[]}), onClose=vi.fn();
 render(<PurchaseChoiceDialog productId={1} onConfirm={onConfirm} onClose={onClose}/>);
 await screen.findByText('Polo'); expect(confirm().disabled).toBe(true); expect(onConfirm).not.toHaveBeenCalled();
 fireEvent.click(screen.getByText('Black')); expect(confirm().disabled).toBe(true); fireEvent.click(screen.getByText('M'));
 fireEvent.change(screen.getByLabelText('Số lượng'),{target:{value:'99'}}); expect(screen.getByLabelText('Số lượng').value).toBe('5');
 fireEvent.click(confirm()); await waitFor(()=>expect(onClose).toHaveBeenCalledOnce());
 expect(onConfirm).toHaveBeenCalledWith(legacy,{color:'Black',size:'M'},5);
});
it('only permits stocked active pairs and clears invalid sizes', async()=>{
 api.mockResolvedValue(allocated); render(<PurchaseChoiceDialog productId={1} onClose={vi.fn()} onConfirm={vi.fn()}/>); await choose();
 expect(screen.queryByText('Red')).toBeNull(); expect(screen.queryByText('Blue')).toBeNull(); expect(screen.getByText('L').disabled).toBe(true);
 fireEvent.change(screen.getByLabelText('Số lượng'),{target:{value:'9'}}); expect(screen.getByLabelText('Số lượng').value).toBe('2');
 fireEvent.click(screen.getByText('White')); expect(confirm().disabled).toBe(true); expect(screen.getByText('M').getAttribute('aria-pressed')).toBe('false');
 fireEvent.click(screen.getByText('L')); expect(confirm().disabled).toBe(false);
});
it.each([false,true])('retains selections when confirmation fails (%s)',async reject=>{
 const onConfirm=reject?vi.fn().mockRejectedValue(new Error('Failed')):vi.fn().mockResolvedValue(undefined), onClose=vi.fn();
 render(<PurchaseChoiceDialog productId={1} onConfirm={onConfirm} onClose={onClose}/>); await choose(); fireEvent.click(confirm()); await screen.findByRole('alert');
 expect(onClose).not.toHaveBeenCalled(); expect(screen.getByText('M').getAttribute('aria-pressed')).toBe('true'); expect(confirm().disabled).toBe(false);
});
it('locks submission and dismiss while sending and sends selected variant id',async()=>{
 api.mockResolvedValue(allocated); let finish; const onConfirm=vi.fn(()=>new Promise(resolve=>{finish=resolve;})),onClose=vi.fn();
 const view=render(<PurchaseChoiceDialog productId={1} buy onConfirm={onConfirm} onClose={onClose}/>); await choose();
 fireEvent.submit(view.container.querySelector('form')); fireEvent.submit(view.container.querySelector('form')); fireEvent.keyDown(screen.getByRole('dialog'),{key:'Escape'});
 expect(onConfirm).toHaveBeenCalledOnce(); expect(onClose).not.toHaveBeenCalled(); expect(onConfirm).toHaveBeenCalledWith(allocated,{color:'Black',size:'M',variantId:11},1);
 await act(async()=>finish({items:[]})); expect(onClose).toHaveBeenCalledOnce();
});
it('retries loading, ignores closed responses and resets each new open',async()=>{
 api.mockRejectedValueOnce(new Error('Load failed')); const props={productId:1,onClose:vi.fn(),onConfirm:vi.fn()};
 const view=render(<PurchaseChoiceDialog {...props}/>); await screen.findByText('Load failed'); fireEvent.click(screen.getByText('Thử lại')); await choose(); view.unmount();
 let finish; api.mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve;})); const stale=render(<PurchaseChoiceDialog {...props}/>); expect(screen.getByRole('status')).toBeTruthy(); stale.unmount();
 render(<PurchaseChoiceDialog {...props}/>); await screen.findByText('Polo'); await act(async()=>finish({...legacy,name:'Stale'})); expect(screen.queryByText('Stale')).toBeNull(); expect(confirm().disabled).toBe(true); expect(screen.getByLabelText('Số lượng').value).toBe('1');
});
it('traps and restores focus, locks scroll and dismisses with Escape and outside click',async()=>{
 const opener=document.createElement('button'); document.body.append(opener); opener.focus(); document.body.style.overflow='auto'; const onClose=vi.fn();
 const view=render(<PurchaseChoiceDialog productId={1} onClose={onClose} onConfirm={vi.fn()}/>); await choose(); const close=screen.getByLabelText('Đóng lựa chọn sản phẩm');
 close.focus(); fireEvent.keyDown(close,{key:'Tab',shiftKey:true}); expect(document.activeElement).toBe(confirm()); fireEvent.keyDown(confirm(),{key:'Tab'}); expect(document.activeElement).toBe(close);
 fireEvent.keyDown(close,{key:'Escape'}); fireEvent.click(screen.getByRole('dialog'),{clientX:-10,clientY:-10}); expect(onClose).toHaveBeenCalledTimes(2);
 expect(document.body.style.overflow).toBe('hidden'); view.unmount(); expect(document.activeElement).toBe(opener); expect(document.body.style.overflow).toBe('auto'); opener.remove();
});
