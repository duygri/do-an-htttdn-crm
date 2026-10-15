// Read-only browser QA with intercepted APIs: never creates real orders/payments.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try{
  for(const width of [1440,768,390])for(const theme of ['light','dark'])for(const portal of (process.env.QA_CARE_ONLY||process.env.QA_CARE_DASHBOARD?['manager']:['user','manager'])){
   const origin=portal==='user'?'http://localhost:5173':'http://localhost:5175';
   const context=await browser.newContext({viewport:{width,height:1000}});
   await context.addInitScript(({portal,theme})=>localStorage.setItem(portal==='user'?'anh-lon-shop-theme':'manager-theme',theme),{portal,theme});
   const page=await context.newPage(),errors=[];page.on('pageerror',error=>errors.push(error.message));
   const customer={id:1,fullName:'Khách kiểm thử',email:'customer@example.test'};
   const lifecycleCustomer={...customer,completedOrders:5,completedValue:5000000,lastCompletedOrderAt:'2026-09-01T08:00:00Z',segmentation:{stage:'LOYAL',dormant:true,daysSinceLastPurchase:35}};
   const careTask={id:1,customerId:1,customerName:customer.fullName,assigneeId:2,assigneeName:'Quản lý kiểm thử',assigneeActive:true,title:'Liên hệ khách lâu chưa mua lại',description:'Hỏi trải nghiệm mua hàng và nhu cầu hiện tại.',status:'TODO',overdue:true,dueAt:'2026-10-01T08:00:00Z',createdAt:'2026-09-30T08:00:00Z',updatedAt:'2026-09-30T08:00:00Z',version:0};
   const product={id:1,name:'Sản phẩm kiểm thử bố cục',price:499000,stock:10,category:'Áo thun',imageUrl:'/missing-test-image.jpg'};
   const order={id:9,orderCode:9009,status:'DELIVERED',returnStatus:'REQUESTED',returnReason:'Sai kích thước',paymentMethod:'COD',paymentStatus:'COD',totalAmount:499000,createdAt:'2026-10-01T08:00:00Z',customer,items:[]};
   await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname;let body={content:[],totalElements:0,totalPages:0};
    if(path.endsWith('/auth/refresh'))body={accessToken:'test-only',manager:customer,customer};
    else if(path.endsWith('/care/dashboard')){const counts={total:3,todo:1,inProgress:1,completed:1,overdue:2};body={counts,staff:[{id:2,name:'Quản lý kiểm thử',active:true,counts}],dormantWithoutOpenTask:1,asOf:'2026-10-06T08:00:00Z'};}
    else if(path.endsWith('/care/dormant'))body={content:[{id:1,fullName:customer.fullName,lastPurchaseAt:'2026-09-01T08:00:00Z'}],totalElements:1,totalPages:1};
    else if(path.endsWith('/care/tasks'))body={content:[careTask],totalElements:1,totalPages:1};
    else if(path.endsWith('/care/staff'))body={content:[{id:2,fullName:'Quản lý kiểm thử',email:'staff@example.test'}],totalElements:1,totalPages:1};
    else if(path.endsWith('/care/notes'))body={content:[{id:1,content:'Khách muốn được tư vấn thêm về size.',authorName:'Quản lý kiểm thử',createdAt:'2026-10-01T08:00:00Z'}],totalElements:1,totalPages:1};
    else if(path.endsWith('/users'))body={content:[lifecycleCustomer],totalElements:1,totalPages:1};
    else if(path.endsWith('/users/1/profile'))body={customer,segmentation:lifecycleCustomer.segmentation,summary:{totalOrders:5,completedOrders:5,cancelledOrders:0,returnOrders:0,completedValue:5000000,lastCompletedOrderAt:'2026-09-01T08:00:00Z'}};
    else if(path.endsWith('/orders/summary'))body={ALL:1,RETURN:1,PENDING:0,CANCELLED:0};
    else if(path.endsWith('/orders'))body={content:[order],totalElements:1,totalPages:1};
    else if(path.endsWith('/orders/9'))body=order;
    else if(path.endsWith('/products'))body={content:[product],totalElements:1,totalPages:1};
    else if(path.endsWith('/categories'))body=['Áo thun'];
    else if(path.endsWith('/wishlist'))body=[];
    else if(path.endsWith('/cart'))body={items:[],subtotal:0,itemCount:0};
    else if(path.endsWith('/password-recovery'))body={enabled:false};
    await route.fulfill({status:route.request().method()==='OPTIONS'?204:200,contentType:'application/json',headers:{'Access-Control-Allow-Origin':origin,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'Authorization,Content-Type'},body:route.request().method()==='OPTIONS'?'':JSON.stringify(body)});
   });
   const routes=process.env.QA_CARE_DASHBOARD?['/manager/care-dashboard']:process.env.QA_CARE_ONLY?['/manager/care?overdue=true','/manager/users/1?tab=care']:portal==='user'?['/','/san-pham','/don-hang?tab=RETURN','/tai-khoan/voucher','/quen-mat-khau']:['/manager/orders','/manager/products','/manager/users?stage=LOYAL&dormant=true','/manager/users/1?stage=LOYAL&dormant=true','/manager/care?overdue=true','/manager/users/1?tab=care'];
   for(const route of routes){
    await page.goto(origin+route);await page.locator(portal==='user'?'.site-shell':'.manager-shell').waitFor();
    await page.waitForTimeout(600);
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,`${portal} ${width} ${theme} ${route}: overflow`);
    assert.deepEqual(errors,[],`${portal} ${route}: console errors`);
    if(route==='/san-pham')assert.equal(await page.getByLabel('Tìm kiếm sản phẩm',{exact:true}).count(),1);
    if(route==='/manager/orders')assert.equal(await page.getByText('Đã yêu cầu đổi trả — chờ xử lý',{exact:true}).filter({visible:true}).count(),1);
    if(route.includes('/manager/users')){
     assert.equal(await page.getByText('Khách thân thiết',{exact:true}).filter({visible:true}).count(),1);
     assert.equal(await page.getByText('Lâu chưa mua lại',{exact:true}).filter({visible:true}).count(),1);
     if(process.env.QA_SCREENSHOT_DIR)await page.screenshot({path:`${process.env.QA_SCREENSHOT_DIR}/lifecycle-${route.includes('/users/1')?'profile':'list'}-${width}-${theme}.png`,fullPage:true});
    }
    if(route.includes('care-dashboard')){
     await page.getByRole('button',{name:'Khách chưa có chăm sóc: 1'}).click();await page.getByRole('button',{name:'Tạo chăm sóc',exact:true}).waitFor();
     assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,`${width} ${theme}: dashboard overflow`);
    }
    if(route.includes('/care?')||route.includes('tab=care')){
     assert.equal(await page.getByText(careTask.title,{exact:true}).count(),1);
     await page.getByRole('button',{name:'Tạo công việc chăm sóc',exact:true}).click();
     await page.getByLabel('Tiêu đề công việc',{exact:true}).fill('Công việc kiểm thử bố cục');
     await page.getByLabel('Người phụ trách',{exact:true}).selectOption('2');
     assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,`${width} ${theme}: care form overflow`);
     if(process.env.QA_SCREENSHOT_DIR)await page.screenshot({path:`${process.env.QA_SCREENSHOT_DIR}/care-${route.includes('tab=care')?'profile':'page'}-${width}-${theme}.png`,fullPage:true});
     await page.getByRole('button',{name:'Hủy',exact:true}).click();
     assert.deepEqual(errors,[],`${portal} ${route}: care form errors`);
    }
    if(process.env.QA_SCREENSHOT_DIR && route==='/san-pham')await page.screenshot({path:`${process.env.QA_SCREENSHOT_DIR}/catalog-${width}-${theme}.png`,fullPage:true});
   }
   if(process.env.QA_SCREENSHOT_DIR)await page.screenshot({path:`${process.env.QA_SCREENSHOT_DIR}/${portal}-${width}-${theme}.png`,fullPage:true});
   console.log(`${portal} ${width}px ${theme}: PASS (${routes.length} routes)`);await context.close();
  }
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
