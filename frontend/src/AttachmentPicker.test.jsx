// @vitest-environment jsdom
import React, {useState} from 'react';
import {render,screen,fireEvent,waitFor,cleanup} from '@testing-library/react';
import {it,expect,vi,beforeEach,afterEach} from 'vitest';
import AttachmentPicker,{AttachmentLinks} from './AttachmentPicker';
import ReturnRequestForm from './ReturnRequestForm';
import {api} from './api';
import {adminApi as managerApi} from './manager-api';
vi.mock('./api',()=>({api:vi.fn(),adminApi:vi.fn(()=>{throw new Error('SYSTEM ADMIN CLIENT MUST NOT BE USED');})}));
vi.mock('./manager-api',()=>({adminApi:vi.fn()}));
const upload={id:'opaque-id',name:'evidence.png',contentType:'image/png',size:100,url:'/api/attachments/opaque-id/content'};
beforeEach(()=>{api.mockReset();managerApi.mockReset();api.mockImplementation(async url=>url.endsWith('/config')?{enabled:true}:upload);managerApi.mockImplementation(async url=>url.endsWith('/config')?{enabled:true}:upload);});
afterEach(()=>{cleanup();vi.restoreAllMocks();});
function Picker(props){const [value,onChange]=useState([]);return <AttachmentPicker value={value} onChange={onChange} {...props}/>;}
const png=()=>new File(['png'],'evidence.png',{type:'image/png'});
it('uploads FormData with customer client and removes an image locally',async()=>{
 render(<Picker/>);await waitFor(()=>expect(screen.getByLabelText('Ảnh đính kèm').disabled).toBe(false));fireEvent.change(screen.getByLabelText('Ảnh đính kèm'),{target:{files:[png()]}});
 await screen.findByText('evidence.png');expect(api).toHaveBeenCalledWith('/api/attachments',expect.objectContaining({method:'POST',body:expect.any(FormData)}));expect(managerApi).not.toHaveBeenCalled();
 fireEvent.click(screen.getByRole('button',{name:'Bỏ ảnh evidence.png'}));expect(screen.queryByText('evidence.png')).toBeNull();
});
it('uses manager client exclusively for manager uploads',async()=>{
 render(<Picker manager/>);await waitFor(()=>expect(screen.getByLabelText('Ảnh đính kèm').disabled).toBe(false));fireEvent.change(screen.getByLabelText('Ảnh đính kèm'),{target:{files:[png()]}});await screen.findByText('evidence.png');expect(managerApi).toHaveBeenCalledWith('/api/manager/attachments',expect.anything());expect(api).not.toHaveBeenCalled();
});
it('rejects batch count and oversized files before upload',async()=>{
 render(<Picker/>);await waitFor(()=>expect(screen.getByLabelText('Ảnh đính kèm').disabled).toBe(false));fireEvent.change(screen.getByLabelText('Ảnh đính kèm'),{target:{files:Array.from({length:6},png)}});expect(screen.getByRole('alert').textContent).toContain('5 ảnh');expect(api.mock.calls.filter(([,o])=>o?.method==='POST')).toHaveLength(0);
 const huge=new File(['png'],'huge.png',{type:'image/png'});Object.defineProperty(huge,'size',{value:5*1024*1024+1});fireEvent.change(screen.getByLabelText('Ảnh đính kèm'),{target:{files:[huge]}});expect(screen.getByRole('alert').textContent).toContain('5 MB');expect(api.mock.calls.filter(([,o])=>o?.method==='POST')).toHaveLength(0);
});
it('reports upload failure and clears busy state',async()=>{
 const busy=vi.fn();api.mockImplementation(async url=>{if(url.endsWith('/config'))return {enabled:true};throw new Error('Upload failed');});render(<Picker onBusyChange={busy}/>);await waitFor(()=>expect(screen.getByLabelText('Ảnh đính kèm').disabled).toBe(false));fireEvent.change(screen.getByLabelText('Ảnh đính kèm'),{target:{files:[png()]}});await screen.findByText('Upload failed');expect(busy).toHaveBeenLastCalledWith(false);expect(screen.getByLabelText('Ảnh đính kèm').disabled).toBe(false);
});
it('downloads manager images through authenticated manager blob transport',async()=>{
 managerApi.mockResolvedValue(new Blob(['png']));URL.createObjectURL=vi.fn(()=> 'blob:test');URL.revokeObjectURL=vi.fn();const click=vi.spyOn(HTMLAnchorElement.prototype,'click').mockImplementation(()=>{});
 render(<AttachmentLinks attachments={[upload]} manager/>);fireEvent.click(screen.getByRole('button',{name:'evidence.png'}));await waitFor(()=>expect(click).toHaveBeenCalled());expect(managerApi).toHaveBeenCalledWith('/api/manager/attachments/opaque-id/content',{responseType:'blob'});expect(api).not.toHaveBeenCalled();expect(document.querySelector('a[href*="cloudinary"]')).toBeNull();
});
it('blocks return submit during upload then submits IDs only',async()=>{
 let done;api.mockImplementation(url=>url.endsWith('/config')?Promise.resolve({enabled:true}):new Promise(resolve=>{done=resolve;}));const submit=vi.fn();render(<ReturnRequestForm onSubmit={submit} onCancel={()=>{}}/>);
 await waitFor(()=>expect(screen.getByLabelText('Ảnh đính kèm').disabled).toBe(false));fireEvent.change(screen.getByLabelText('Lý do đổi/trả'),{target:{value:'Damaged'}});fireEvent.change(screen.getByLabelText('Ảnh đính kèm'),{target:{files:[png()]}});
 expect(screen.getByRole('button',{name:'Gửi yêu cầu'}).disabled).toBe(true);fireEvent.submit(screen.getByRole('button',{name:'Gửi yêu cầu'}).closest('form'));expect(submit).not.toHaveBeenCalled();
 done(upload);await screen.findByText('evidence.png');fireEvent.click(screen.getByRole('button',{name:'Gửi yêu cầu'}));expect(submit).toHaveBeenCalledWith({reason:'Damaged',attachmentIds:['opaque-id']});
});

it('disables uploads when Cloudinary is not configured without blocking text forms',async()=>{
 api.mockResolvedValue({enabled:false});render(<Picker/>);
 await screen.findByText(/Tải ảnh chưa được cấu hình/);
 expect(screen.getByLabelText('Ảnh đính kèm').disabled).toBe(true);
 expect(api.mock.calls.filter(([,o])=>o?.method==='POST')).toHaveLength(0);
});
