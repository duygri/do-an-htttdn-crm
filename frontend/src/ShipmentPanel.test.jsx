// @vitest-environment jsdom
import React from 'react';
import {render,screen,fireEvent,cleanup,waitFor} from '@testing-library/react';
import {afterEach,it,expect,vi} from 'vitest';
import ShipmentPanel from './ShipmentPanel';
import {adminApi} from './manager-api';
vi.mock('./manager-api',async original=>({...await original(),adminApi:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();});
const detail={id:9,orderCode:9009,status:'PREPARING',returnStatus:'NONE',items:[],shipment:null};
function mockCarriers(){adminApi.mockImplementation(async(url)=>(url==='/api/manager/shipping/carriers'?[{code:'GHN',name:'Giao Hàng Nhanh'}]:{...detail,status:'SHIPPED',shipment:{carrierName:'Giao Hàng Nhanh',trackingCode:'GHN1',status:'CREATED',events:[]}}));}
it('creates a shipment and passes the updated order back',async()=>{
  mockCarriers();const onUpdated=vi.fn();
  render(<ShipmentPanel detail={detail} onUpdated={onUpdated}/>);
  await waitFor(()=>expect(screen.getByText('Giao Hàng Nhanh')).toBeTruthy());
  fireEvent.change(screen.getByLabelText('Hãng vận chuyển'),{target:{value:'GHN'}});
  fireEvent.change(screen.getByLabelText('Mã vận đơn'),{target:{value:'GHN1'}});
  fireEvent.click(screen.getByRole('button',{name:'Tạo vận đơn'}));
  await waitFor(()=>expect(adminApi).toHaveBeenLastCalledWith('/api/manager/orders/9/shipments',{method:'POST',body:{carrierCode:'GHN',trackingCode:'GHN1',note:''}}));
  expect(onUpdated).toHaveBeenCalled();
});
it('keeps the draft and shows the error when creation fails',async()=>{
  mockCarriers();adminApi.mockImplementation(async(url)=>{if(url==='/api/manager/shipping/carriers')return [{code:'GHN',name:'Giao Hàng Nhanh'}];throw new Error('Đơn hàng đã có vận đơn.');});
  render(<ShipmentPanel detail={detail} onUpdated={()=>{}}/>);
  await waitFor(()=>expect(screen.getByLabelText('Hãng vận chuyển')).toBeTruthy());
  fireEvent.change(screen.getByLabelText('Hãng vận chuyển'),{target:{value:'GHN'}});
  fireEvent.change(screen.getByLabelText('Mã vận đơn'),{target:{value:'GHN1'}});
  fireEvent.click(screen.getByRole('button',{name:'Tạo vận đơn'}));
  expect(await screen.findByText('Đơn hàng đã có vận đơn.')).toBeTruthy();
  expect(screen.getByLabelText('Mã vận đơn').value).toBe('GHN1');
});

// --- gating: create only for CONFIRMED/PREPARING, no return in progress, no shipment ---
const shipped=(status,extra={})=>({...detail,status:'SHIPPED',returnStatus:'NONE',shipment:{id:3,carrierName:'Giao Hàng Nhanh',trackingCode:'GHN1',status,events:[]},...extra});
function mockDetail(row){adminApi.mockImplementation(async(url)=>(url==='/api/manager/shipping/carriers'?[{code:'GHN',name:'Giao Hàng Nhanh'}]:row));}

it('hides the create form when returnStatus is APPROVED',async()=>{
  mockDetail({...detail,returnStatus:'APPROVED',shipment:null});
  const {container}=render(<ShipmentPanel detail={{...detail,returnStatus:'APPROVED',shipment:null}} onUpdated={()=>{}}/>);
  expect(screen.queryByRole('button',{name:'Tạo vận đơn'})).toBeNull();
  expect(container.innerHTML).toBe('');
  expect(adminApi).not.toHaveBeenCalledWith('/api/manager/shipping/carriers');
});
it('hides the create form when a shipment already exists',async()=>{
  mockDetail(shipped('CREATED'));
  render(<ShipmentPanel detail={shipped('CREATED')} onUpdated={()=>{}}/>);
  expect(await screen.findByText('Mã vận đơn:')).toBeTruthy();
  expect(screen.queryByRole('button',{name:'Tạo vận đơn'})).toBeNull();
});
it('hides the create form for order statuses other than CONFIRMED/PREPARING',async()=>{
  mockDetail({...detail,status:'PENDING',shipment:null});
  const {container}=render(<ShipmentPanel detail={{...detail,status:'PENDING',shipment:null}} onUpdated={()=>{}}/>);
  expect(screen.queryByRole('button',{name:'Tạo vận đơn'})).toBeNull();
  expect(container.innerHTML).toBe('');
  expect(adminApi).not.toHaveBeenCalledWith('/api/manager/shipping/carriers');
});

// --- event form: progression, frozen states, note guard, payload ---
const optionValues=select=>Array.from(select.querySelectorAll('option')).map(o=>o.value);
it('only offers statuses after the current one',async()=>{
  mockDetail(shipped('CREATED'));
  render(<ShipmentPanel detail={shipped('CREATED')} onUpdated={()=>{}}/>);
  const select=await screen.findByLabelText('Cập nhật hành trình');
  expect(optionValues(select)).toEqual(['PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED']);
  expect(select.value).toBe('PICKED_UP');
});
it('does not offer earlier steps after IN_TRANSIT',async()=>{
  mockDetail(shipped('IN_TRANSIT'));
  render(<ShipmentPanel detail={shipped('IN_TRANSIT')} onUpdated={()=>{}}/>);
  const select=await screen.findByLabelText('Cập nhật hành trình');
  const values=optionValues(select);
  expect(values).toEqual(['OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED']);
  expect(values).not.toContain('PICKED_UP');
  expect(values).not.toContain('IN_TRANSIT');
});
it('hides the event form once the shipment is DELIVERED',async()=>{
  mockDetail(shipped('DELIVERED',{status:'DELIVERED'}));
  render(<ShipmentPanel detail={shipped('DELIVERED',{status:'DELIVERED'})} onUpdated={()=>{}}/>);
  expect(await screen.findByText('Mã vận đơn:')).toBeTruthy();
  expect(screen.queryByLabelText('Cập nhật hành trình')).toBeNull();
  expect(screen.queryByRole('button',{name:'Thêm cập nhật'})).toBeNull();
});
it.each(['CANCELLED','COMPLETED'])('hides the event form when the order is %s',async orderStatus=>{
  mockDetail(shipped('IN_TRANSIT',{status:orderStatus}));
  render(<ShipmentPanel detail={shipped('IN_TRANSIT',{status:orderStatus})} onUpdated={()=>{}}/>);
  expect(await screen.findByText('Mã vận đơn:')).toBeTruthy();
  expect(screen.queryByRole('button',{name:'Thêm cập nhật'})).toBeNull();
});
it.each(['APPROVED','COMPLETED'])('hides the event form when returnStatus is %s',async returnStatus=>{
  mockDetail(shipped('IN_TRANSIT',{status:'RETURN_REQUESTED',returnStatus}));
  render(<ShipmentPanel detail={shipped('IN_TRANSIT',{status:'RETURN_REQUESTED',returnStatus})} onUpdated={()=>{}}/>);
  expect(await screen.findByText('Mã vận đơn:')).toBeTruthy();
  expect(screen.queryByRole('button',{name:'Thêm cập nhật'})).toBeNull();
});
it('requires a note before submitting FAILED without calling the API',async()=>{
  mockDetail(shipped('IN_TRANSIT'));
  render(<ShipmentPanel detail={shipped('IN_TRANSIT')} onUpdated={()=>{}}/>);
  const select=await screen.findByLabelText('Cập nhật hành trình');
  fireEvent.change(select,{target:{value:'FAILED'}});
  fireEvent.click(screen.getByRole('button',{name:'Thêm cập nhật'}));
  expect(await screen.findByText('Ghi chú là bắt buộc khi giao thất bại hoặc hoàn hàng.')).toBeTruthy();
  const eventCalls=adminApi.mock.calls.filter(([url])=>String(url).includes('/events'));
  expect(eventCalls).toHaveLength(0);
});
it('posts the event to the shipment endpoint and passes the order back',async()=>{
  mockDetail(shipped('PICKED_UP'));
  const onUpdated=vi.fn();
  render(<ShipmentPanel detail={shipped('PICKED_UP')} onUpdated={onUpdated}/>);
  const select=await screen.findByLabelText('Cập nhật hành trình');
  fireEvent.change(select,{target:{value:'FAILED'}});
  fireEvent.change(screen.getByLabelText('Ghi chú'),{target:{value:'Khách không nghe máy'}});
  fireEvent.click(screen.getByRole('button',{name:'Thêm cập nhật'}));
  await waitFor(()=>expect(adminApi).toHaveBeenLastCalledWith('/api/manager/orders/9/shipments/3/events',{method:'POST',body:{status:'FAILED',note:'Khách không nghe máy'}}));
  expect(onUpdated).toHaveBeenCalled();
});
