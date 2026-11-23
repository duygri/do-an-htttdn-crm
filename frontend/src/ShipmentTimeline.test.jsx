// @vitest-environment jsdom
import React from 'react';
import {render,screen,cleanup} from '@testing-library/react';
import {afterEach,it,expect} from 'vitest';
import ShipmentTimeline from './ShipmentTimeline';
afterEach(cleanup);
const shipment={carrierCode:'GHN',carrierName:'Giao Hàng Nhanh',trackingCode:'GHN123',status:'IN_TRANSIT',shippedAt:'2026-11-20T02:00:00Z',events:[{status:'CREATED',note:'Tạo vận đơn',occurredAt:'2026-11-20T02:00:00Z'},{status:'IN_TRANSIT',note:'Đến kho',occurredAt:'2026-11-21T02:00:00Z'}]};
it('renders carrier, tracking code and every event',()=>{
  render(<ShipmentTimeline shipment={shipment}/>);
  expect(screen.getByText('Giao Hàng Nhanh')).toBeTruthy();
  expect(screen.getByText(/GHN123/)).toBeTruthy();
  expect(screen.getByText('Đến kho')).toBeTruthy();
  expect(screen.getByText('Đang trung chuyển')).toBeTruthy();
});
it('renders nothing without a shipment',()=>{const {container}=render(<ShipmentTimeline shipment={null}/>);expect(container.innerHTML).toBe('');});
