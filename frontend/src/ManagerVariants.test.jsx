// @vitest-environment jsdom
import React from 'react';
import {render,screen,fireEvent,waitFor,cleanup} from '@testing-library/react';
import {describe,it,expect,vi,afterEach} from 'vitest';
import ManagerVariants from './ManagerVariants';
import {adminApi} from './manager-api';
vi.mock('./manager-api',()=>({adminApi:vi.fn(),adminDownload:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();});
describe('manager variants',()=>{
 it('opens a selected product and shows allocation balance',async()=>{adminApi.mockResolvedValue({id:1,name:'Shirt',stock:5,stockVersion:0,colors:'Black',sizes:'M',variantsAllocated:false});render(<ManagerVariants productId={1}/>);await screen.findByText(/Tổng nhập: 0/);expect(screen.getByText('Phân bổ tồn chung').disabled).toBe(true);fireEvent.change(screen.getByLabelText('Tồn Black M'),{target:{value:'5'}});expect(screen.getByText('Phân bổ tồn chung').disabled).toBe(false);fireEvent.click(screen.getByText('Phân bổ tồn chung'));await waitFor(()=>expect(adminApi).toHaveBeenCalledWith('/api/manager/products/1/variants/allocate',expect.objectContaining({body:expect.objectContaining({expectedStock:5,expectedVersion:0})})));});
 it('sends all changes in one atomic request and preserves drafts on conflict',async()=>{const p={id:1,name:'Shirt',stock:5,stockVersion:2,variantsAllocated:true,variants:[{id:3,color:'Black',size:'M',stock:2},{id:4,color:'Blue',size:'L',stock:3}]};adminApi.mockResolvedValueOnce(p).mockResolvedValueOnce([]).mockRejectedValueOnce(new Error('Stock changed'));render(<ManagerVariants productId={1}/>);await screen.findByLabelText('Tồn Black M');fireEvent.change(screen.getByLabelText('Tồn Black M'),{target:{value:'4'}});fireEvent.change(screen.getByLabelText('Tồn Blue L'),{target:{value:'6'}});fireEvent.change(screen.getByLabelText('Lý do điều chỉnh / đối soát'),{target:{value:'Count'}});fireEvent.click(screen.getByText('Lưu điều chỉnh'));await screen.findByRole('alert');expect(adminApi).toHaveBeenCalledTimes(3);expect(adminApi).toHaveBeenLastCalledWith('/api/manager/products/1/variants/stock',{method:'PATCH',body:{expectedStock:5,expectedVersion:2,note:'Count',items:[{variantId:3,quantity:4},{variantId:4,quantity:6}]}});expect(screen.getByLabelText('Tồn Black M').value).toBe('4');});
});
