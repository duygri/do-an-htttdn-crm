// @vitest-environment jsdom
import React from 'react';import {render,screen,cleanup,fireEvent} from '@testing-library/react';import {it,expect,vi,afterEach} from 'vitest';
import MembershipCard from './MembershipCard';import {api} from './api';
vi.mock('./api',()=>({api:vi.fn()}));afterEach(()=>{cleanup();vi.resetAllMocks();});
it('shows server tier and progress without redemption controls',async()=>{
 api.mockResolvedValue({tier:'GOLD',label:'Vàng',discountPercent:5,completedValue:6000000,nextThreshold:10000000,remainingValue:4000000});render(<MembershipCard/>);
 expect(await screen.findByText('Hạng Vàng · 5%')).toBeTruthy();expect(screen.getByText(/Còn 4.000.000/)).toBeTruthy();expect(screen.queryByText('Đổi điểm')).toBeNull();
});
it('shows retry on failure and highest tier without progress',async()=>{
 api.mockRejectedValueOnce(new Error('Mất kết nối')).mockResolvedValueOnce({tier:'DIAMOND',label:'Kim cương',discountPercent:10,completedValue:10000000,nextThreshold:null,remainingValue:0});render(<MembershipCard/>);
 await screen.findByRole('alert');fireEvent.click(screen.getByText('Thử lại'));expect(await screen.findByText('Bạn đã đạt hạng cao nhất.')).toBeTruthy();
});
