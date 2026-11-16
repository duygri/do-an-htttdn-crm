# Kế hoạch triển khai: Tồn kho, Nhà cung cấp, Tích điểm và tính năng bổ trợ

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Mỗi task kết thúc bằng commit riêng; chỉ push/deploy khi người dùng yêu cầu.

**Goal:** Bổ sung module tồn kho đầy đủ (thẻ kho, ngưỡng cảnh báo, kiểm kê, giá trị tồn), hoàn thiện module nhà cung cấp (chi tiết, lưu trữ, công nợ), tích điểm khách hàng, cấu hình hệ thống cho Admin, thông báo nội bộ cho Manager và xuất CSV báo cáo — phục vụ đồ án HTTT.

**Architecture:** Giữ nguyên kiến trúc hiện tại: Spring Boot 3.4.3 + JPA/JDBC + PostgreSQL (H2 cho test), React/Vite cho 3 portal (User / Manager / System Admin). Mọi thay đổi schema đi qua một migration SQL idempotent mới, chạy tự động lúc boot trên môi trường deploy (`spring.sql.init`) trước khi Hibernate validate. Nghiệp vụ kho tập trung vào `StockService` để mọi biến động đều sinh `stock_movements`; điểm thưởng dùng sổ cái `point_transactions`; thông báo nội bộ dùng bảng riêng `internal_notices`.

**Tech Stack:** Java 21, Spring Boot 3.4.3 (Web, Data JPA, JDBC, Validation), PostgreSQL 16 (prod), H2 PostgreSQL-mode (test), React + Vite + Vitest, Playwright không bắt buộc.

**Spec:** Các quyết định thiết kế đã chốt với người dùng ngày 2026-11-16 nằm ở mục 2 của plan này (brainstorm chưa tách file riêng). Khi bắt đầu thực thi, sao chép plan này vào `docs/superpowers/plans/2026-11-16-inventory-suppliers-loyalty.md`.

---

## 1. Bối cảnh hiện trạng (đã khảo sát)

- `products.quantity_remaining` là nguồn tồn kho duy nhất; `AdminProductService.stock` đặt số tuyệt đối; `OrderService.create` trừ kho trực tiếp; `StockService.release` hoàn kho khi hủy đơn. Không có lịch sử biến động, ngưỡng cảnh báo hay kiểm kê.
- Nhà cung cấp đã có: danh mục CRUD (`DirectoryController` + `ManagerSuppliers.jsx`), gán NCC cho sản phẩm (`DirectoryService.assign`), phiếu nhập hàng chỉ ghi chi phí (`SupplierReceiptService`, không đổi tồn kho). Thiếu: trang chi tiết, khóa/lưu trữ có kiểm soát, công nợ phải trả.
- Doanh thu thực thu đã có (`CashflowReportService`), hóa đơn đã có (`SalesInvoiceService` dùng `@Value("${app.invoice.seller-*}")`). Hoàn tiền đã bị gỡ; chỉ còn COD + PayOS.
- Thông báo hiện có: `customer_notifications` + chuông chăm sóc `ManagerCareInboxController`; chưa có thông báo vận hành (tồn kho thấp, đơn treo).
- Không có xuất CSV ở đâu.

## 2. Thiết kế đã chốt

### 2.1 Quyết định nghiệp vụ

- **Tồn kho:** mọi thay đổi `products.quantity_remaining` phải đi qua `StockService` và sinh một dòng `stock_movements` (SALE, CANCEL_RELEASE, ADJUST, INITIAL, STOCKTAKE). Phiếu nhập hàng **vẫn không** đổi tồn kho (đúng quyết định trước đây).
- **Ngưỡng tồn thấp:** mỗi sản phẩm có `low_stock_threshold` (nullable). Hiệu lực = giá trị riêng, nếu null lấy `inventory.lowStockDefault` (mặc định 5). Cảnh báo khi `stock <= ngưỡng hiệu lực`.
- **Kiểm kê:** phiếu DRAFT chụp `system_quantity`; Manager nhập số đếm; COMPLETE sinh điều chỉnh `STOCKTAKE` cho từng dòng lệch; CANCELLED không đổi kho. Bắt buộc đếm đủ mọi dòng trước khi COMPLETE.
- **Giá trị tồn kho:** `stock × đơn giá nhập gần nhất` (từ `supplier_receipt_items` theo `invoice_date DESC, receipt.id DESC, item.id DESC`). Sản phẩm chưa từng nhập: giá vốn 0 và được đếm vào `missingCostProducts`.
- **Nhà cung cấp:** lưu trữ = `active=false`. Vẫn được xem chi tiết và ghi nhận thanh toán công nợ, nhưng **chặn** gán sản phẩm mới và tạo phiếu nhập mới.
- **Công nợ:** `supplier_payments` gắn với từng phiếu nhập; `paid = SUM(payments)`, `outstanding = total_amount - paid`; không cho trả vượt outstanding; cho trả nhiều lần.
- **Tích điểm:** chỉ cộng khi đơn chuyển COMPLETED qua xác nhận của khách (`OrderService.confirmReceipt`), idempotent theo đơn. Điểm = `floor(totalAmount / 10.000) × loyalty.pointsPer10000`. Đổi điểm tạo `StoreVoucher` loại `FIXED_AMOUNT` thuộc sở hữu khách, trừ điểm qua giao dịch REDEEM. Manager được điều chỉnh thủ công có ghi chú; Admin cấu hình tỷ lệ.
- **Cấu hình hệ thống:** bảng key-value `app_settings`; 8 khóa ở mục 2.3; thay thế cấu hình người bán hóa đơn đang đọc từ `app.invoice.*` (giữ default tương đương).
- **Thông báo nội bộ:** bảng `internal_notices` gửi cho tài khoản MANAGER đang hoạt động; dùng `dedupe_key` theo từng người nhận. Nguồn: tồn kho vượt ngưỡng xuống (ngay khi phát sinh) và đơn PENDING/CONFIRMED/PREPARING treo > 24 giờ (scheduler mỗi giờ, 1 thông báo/ngày).
- **CSV:** UTF-8 có BOM, phân tách dấu phẩy, escape theo RFC 4180; chỉ Manager tải được.

### 2.2 Mô hình dữ liệu mới (một migration duy nhất)

Bảng mới: `app_settings`, `stock_movements`, `stocktakes`, `stocktake_items`, `supplier_payments`, `point_transactions`, `internal_notices`. Cột mới: `products.low_stock_threshold`.

### 2.3 Khóa cấu hình và mặc định

| Key | Default | Kiểu/giới hạn |
|---|---|---|
| `invoice.seller.name` | `Anh Lon Shop` | chuỗi ≤150 |
| `invoice.seller.address` | `` | chuỗi ≤1000 |
| `invoice.seller.phone` | `` | chuỗi ≤40 |
| `invoice.seller.taxCode` | `` | chuỗi ≤40 |
| `inventory.lowStockDefault` | `5` | int 0..100000 |
| `loyalty.pointsPer10000` | `1` | int 0..1000 |
| `loyalty.redeemPoints` | `100` | int 1..1000000 |
| `loyalty.redeemValue` | `20000` | decimal 0..100000000 |

## 3. Cấu trúc file

**Backend mới**
- `entity/AppSetting.java`, `entity/StockMovement.java`, `entity/Stocktake.java`, `entity/StocktakeItem.java`, `entity/SupplierPayment.java`, `entity/PointTransaction.java`, `entity/InternalNotice.java`
- `repository/AppSettingRepository.java`, `StockMovementRepository.java`, `StocktakeRepository.java`, `SupplierPaymentRepository.java`, `PointTransactionRepository.java`, `InternalNoticeRepository.java`
- `service/SettingsService.java`, `InventoryQueryService.java`, `StocktakeService.java`, `InventoryReportService.java`, `SupplierAccountService.java`, `LoyaltyService.java`, `InternalNoticeService.java`, `CsvReportService.java`, `util/Csv.java`
- `controller/AdminSettingsController.java`, `ManagerInventoryController.java`, `SupplierAccountController.java`, `LoyaltyController.java`, `ManagerNoticeController.java`

**Backend sửa**
- `StockService` (thêm apply/sell/adjust/initial + repo movement), `OrderService` (bỏ trừ kho trực tiếp, gọi `stock.sell`, cộng điểm khi confirmReceipt), `AdminProductService` (dùng `stock.adjust`/`stock.initial`, threshold), `SupplierReceiptService` (chặn NCC ngừng hợp tác), `DirectoryService.assign` (chặn NCC ngừng hợp tác), `SalesInvoiceService` (đọc SettingsService), `OrderRepository` (đếm đơn treo), `UserRepository` (danh sách manager hoạt động), `CashflowReportService` (giữ nguyên, dùng lại cho CSV).

**Frontend mới**
- `ManagerInventory.jsx`, `ManagerNoticeBell.jsx`, `AdminSettings.jsx`, `CustomerPoints.jsx`
- `manager-inventory.css`, `manager-notices.css`, `admin-settings.css`
- Test: `ManagerInventory.test.jsx`, `ManagerSupplierDetail.test.jsx`, `ManagerNoticeBell.test.jsx`, `AdminSettings.test.jsx`, `CustomerPoints.test.jsx`

**Frontend sửa**
- `ManagerApp.jsx` (nav `inventory`, render trang, gắn chuông vận hành, điểm trong hồ sơ 360°), `ManagerSuppliers.jsx` (drawer chi tiết + công nợ + lưu trữ), `manager-api.js` (endpoint mới + `adminDownload`), `SystemAdminApp.jsx` (menu `settings`), `App.jsx` (thẻ điểm trong ProfileModal), `RevenueReport.jsx` (nút CSV), `ManagerOrders` trong `ManagerApp.jsx` (nút CSV).

**Database & deploy**
- `database/migration_inventory_suppliers_loyalty.sql` (mới, idempotent)
- `database/schema.sql` (append cùng block)
- Deploy worktree `C:/Users/admin/Desktop/crm-deploy-audit-check`: `Dockerfile`, `.dockerignore`, `deploy/application.yml` (thêm file migration thứ hai)

## 4. Global Constraints (áp dụng cho mọi task)

- Java 21 / Spring Boot 3.4.3; không thêm dependency ngoài (dùng BigDecimal, JDBC, Jackson sẵn có).
- Prod `ddl-auto: validate`; mọi DDL phải nằm trong migration idempotent (`IF NOT EXISTS`) và append vào `database/schema.sql`.
- H2 test chạy `create-drop`; entity dùng naming mặc định camelCase→snake_case; tên bảng/cột đặc biệt khai báo `@Table/@Column`.
- Tiền VND: `BigDecimal`, scale 2 trong DB, hiển thị không phần thập phân; thời gian nghiệp vụ báo cáo theo `Asia/Ho_Chi_Minh`.
- Phân quyền: `/api/manager/**` chỉ MANAGER (AdminAccessGuard), `/api/admin/**` chỉ ADMIN, API khách dùng `AuthService.requireCustomer`. Không nới quyền cho endpoint mới.
- Không đụng tới nghiệp vụ hoàn tiền (đã gỡ) và không commit `backend/src/main/resources/application.yml`.
- Audit: hành động Manager/Admin ghi qua `AuditTrail`; hành động khách ghi qua ledger nghiệp vụ (movement/point). Không dùng `AuditTrail.record` khi actor có thể null.
- Mọi task: viết test thất bại trước, chạy test đỏ, implement tối thiểu, chạy xanh, commit. Backend: `mvn -q -f backend/pom.xml test`. Frontend: `cd frontend && npx vitest run --no-file-parallelism <file>` (chạy song song dễ timeout).
- Không push/deploy trong lúc thực thi trừ Task 20 và chỉ khi người dùng đồng ý bấm Manual Deploy.

---

## 5. Các giai đoạn và task

### Giai đoạn 1 — Cấu hình hệ thống (nền tảng)

#### Task 1: Migration + AppSetting + SettingsService

**Files:**
- Create: `database/migration_inventory_suppliers_loyalty.sql`
- Modify: `database/schema.sql` (append)
- Create: `backend/src/main/java/com/htttdn/crm/entity/AppSetting.java`
- Create: `backend/src/main/java/com/htttdn/crm/repository/AppSettingRepository.java`
- Create: `backend/src/main/java/com/htttdn/crm/service/SettingsService.java`
- Test: `backend/src/test/java/com/htttdn/crm/service/SettingsServiceTest.java`

**Interfaces (Produces):**
- `SettingsService.DEFAULTS: Map<String,String>`; `String get(String key)`; `int getInt(String key)`; `BigDecimal getDecimal(String key)`; `Map<String,String> all()`; `Map<String,String> update(Map<String,String> values, AuditTrail.Actor actor)`.
- `AppSetting` với `getKey()/setKey()/getValue()/setValue()/getUpdatedAt()/getUpdatedBy()`.

- [ ] **Step 1: Viết test thất bại** — `SettingsServiceTest` (`@SpringBootTest` H2 giống `CashflowReportIntegrationTest`, `@Transactional`): assert default khi bảng rỗng; update 3 khóa rồi đọc lại; update khóa lạ/giá trị sai ném `ApiException` code `INVALID_SETTING`; audit có actor null → `IllegalStateException` (bắt buộc actor).

```java
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:settings;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","crm.care-reminders.enabled=false"})
@Transactional class SettingsServiceTest {
  @Autowired SettingsService settings;
  @Test void defaultsAndOverrides(){
    assertEquals("Anh Lon Shop",settings.get("invoice.seller.name"));
    assertEquals(5,settings.getInt("inventory.lowStockDefault"));
    var actor=new AuditTrail.Actor(1L,"Admin","ADMIN");
    settings.update(Map.of("inventory.lowStockDefault","8","loyalty.redeemPoints","150"),actor);
    assertEquals(8,settings.getInt("inventory.lowStockDefault"));assertEquals(150,settings.getInt("loyalty.redeemPoints"));
    assertTrue(settings.all().get("invoice.seller.name").equals("Anh Lon Shop"));
  }
  @Test void rejectsUnknownKeysAndBadValues(){
    var actor=new AuditTrail.Actor(1L,"Admin","ADMIN");
    assertThrows(ApiException.class,()->settings.update(Map.of("unknown.key","1"),actor));
    assertThrows(ApiException.class,()->settings.update(Map.of("inventory.lowStockDefault","-1"),actor));
    assertThrows(ApiException.class,()->settings.update(Map.of("loyalty.redeemValue","abc"),actor));
    assertThrows(ApiException.class,()->settings.update(Map.of("loyalty.redeemPoints","10"),null));
  }
}
```

- [ ] **Step 2: Chạy test đỏ** — `mvn -q -f backend/pom.xml -Dtest=SettingsServiceTest test` → FAIL (chưa có class).

- [ ] **Step 3: Viết migration đầy đủ cho toàn bộ kế hoạch** (một file, idempotent; đây là nguồn DDL duy nhất, các giai đoạn sau không tạo file migration mới):

```sql
BEGIN;
CREATE TABLE IF NOT EXISTS app_settings (
  setting_key VARCHAR(60) PRIMARY KEY,
  setting_value VARCHAR(2000) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT
);

ALTER TABLE products ADD COLUMN IF NOT EXISTS low_stock_threshold INTEGER
  CHECK (low_stock_threshold IS NULL OR low_stock_threshold >= 0);

CREATE TABLE IF NOT EXISTS stock_movements (
  id BIGSERIAL PRIMARY KEY,
  product_id BIGINT NOT NULL REFERENCES products(product_id),
  movement_type VARCHAR(30) NOT NULL,
  quantity INTEGER NOT NULL,
  stock_before INTEGER NOT NULL,
  stock_after INTEGER NOT NULL CHECK (stock_after >= 0),
  reference_type VARCHAR(30),
  reference_id BIGINT,
  note VARCHAR(1000),
  actor_id BIGINT,
  actor_name VARCHAR(150),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_stock_movements_product
  ON stock_movements(product_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_stock_movements_type
  ON stock_movements(movement_type, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_stock_movements_created
  ON stock_movements(created_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS stocktakes (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(40) UNIQUE,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  note VARCHAR(1000),
  created_by BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_by BIGINT,
  completed_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS stocktake_items (
  id BIGSERIAL PRIMARY KEY,
  stocktake_id BIGINT NOT NULL REFERENCES stocktakes(id) ON DELETE CASCADE,
  product_id BIGINT NOT NULL REFERENCES products(product_id),
  system_quantity INTEGER NOT NULL,
  counted_quantity INTEGER,
  difference INTEGER,
  note VARCHAR(500)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_stocktake_item_product ON stocktake_items(stocktake_id, product_id);

CREATE TABLE IF NOT EXISTS supplier_payments (
  id BIGSERIAL PRIMARY KEY,
  supplier_id BIGINT NOT NULL REFERENCES suppliers(id),
  receipt_id BIGINT NOT NULL REFERENCES supplier_receipts(id),
  amount NUMERIC(15,2) NOT NULL CHECK (amount > 0),
  paid_at DATE NOT NULL,
  method VARCHAR(30) NOT NULL,
  note VARCHAR(500),
  created_by BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_supplier_payments_supplier
  ON supplier_payments(supplier_id, paid_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_supplier_payments_receipt
  ON supplier_payments(receipt_id);

CREATE TABLE IF NOT EXISTS point_transactions (
  id BIGSERIAL PRIMARY KEY,
  customer_id BIGINT NOT NULL REFERENCES customers(customer_id),
  order_id BIGINT REFERENCES orders(order_id),
  txn_type VARCHAR(20) NOT NULL,
  points INTEGER NOT NULL CHECK (points <> 0),
  note VARCHAR(500),
  created_by BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_point_transactions_customer
  ON point_transactions(customer_id, created_at DESC, id DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_point_earn_order
  ON point_transactions(order_id) WHERE txn_type='EARN' AND order_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS internal_notices (
  id BIGSERIAL PRIMARY KEY,
  recipient_id BIGINT NOT NULL REFERENCES customers(customer_id),
  notice_type VARCHAR(40) NOT NULL,
  title VARCHAR(150) NOT NULL,
  content VARCHAR(2000) NOT NULL,
  link VARCHAR(200),
  dedupe_key VARCHAR(120) NOT NULL,
  read_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (recipient_id, dedupe_key)
);
CREATE INDEX IF NOT EXISTS idx_internal_notices_recipient
  ON internal_notices(recipient_id, created_at DESC, id DESC);
COMMIT;
```

- [ ] **Step 4: Append y hệt block trên vào cuối `database/schema.sql`.**

- [ ] **Step 5: Viết entity + repository + service.**

```java
@Entity @Table(name="app_settings")
public class AppSetting {
    @Id @Column(name="setting_key",length=60) private String key;
    @Column(name="setting_value",nullable=false,length=2000) private String value;
    @Column(name="updated_at",nullable=false) private Instant updatedAt=Instant.now();
    @Column(name="updated_by") private Long updatedBy;
    public AppSetting(){} public AppSetting(String key,String value,Long updatedBy){this.key=key;this.value=value;this.updatedBy=updatedBy;}
    public String getKey(){return key;} public void setKey(String v){key=v;}
    public String getValue(){return value;} public void setValue(String v){value=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
    public Long getUpdatedBy(){return updatedBy;} public void setUpdatedBy(Long v){updatedBy=v;}
}
```

```java
public interface AppSettingRepository extends JpaRepository<AppSetting,String> {}
```

```java
@Service
public class SettingsService {
    public static final Map<String,String> DEFAULTS = Map.of(
        "invoice.seller.name","Anh Lon Shop","invoice.seller.address","","invoice.seller.phone","","invoice.seller.taxCode","",
        "inventory.lowStockDefault","5","loyalty.pointsPer10000","1","loyalty.redeemPoints","100","loyalty.redeemValue","20000");
    private final AppSettingRepository repo; private final AuditTrail audit;
    public SettingsService(AppSettingRepository repo,AuditTrail audit){this.repo=repo;this.audit=audit;}
    public String get(String key){return repo.findById(key).map(AppSetting::getValue).orElseGet(()->DEFAULTS.get(key));}
    public int getInt(String key){return Integer.parseInt(get(key));}
    public BigDecimal getDecimal(String key){return new BigDecimal(get(key));}
    public Map<String,String> all(){var map=new TreeMap<>(DEFAULTS);repo.findAll().forEach(s->map.put(s.getKey(),s.getValue()));return map;}
    @Transactional public Map<String,String> update(Map<String,String> values,AuditTrail.Actor actor){
        if(actor==null)throw new IllegalStateException("Verified audit actor required");
        for(var e:values.entrySet())validate(e.getKey(),e.getValue());
        for(var e:values.entrySet()){
            var before=repo.findById(e.getKey()).map(AppSetting::getValue).orElse(DEFAULTS.get(e.getKey()));
            if(before.equals(e.getValue()))continue;
            repo.save(new AppSetting(e.getKey(),e.getValue(),actor.id()));
            audit.recordAs(actor,"SETTINGS_UPDATE","SETTING",null,Map.of("value",before),Map.of("value",e.getValue()));
        }
        return all();
    }
    private void validate(String key,String value){
        if(!DEFAULTS.containsKey(key)||value==null)throw bad();
        switch(key){
            case "inventory.lowStockDefault" -> intRange(value,0,100000);
            case "loyalty.pointsPer10000" -> intRange(value,0,1000);
            case "loyalty.redeemPoints" -> intRange(value,1,1000000);
            case "loyalty.redeemValue" -> decimalRange(value,BigDecimal.ZERO,new BigDecimal("100000000"));
            case "invoice.seller.name" -> length(value,150);
            case "invoice.seller.address" -> length(value,1000);
            default -> length(value,40);
        }
    }
    private static void intRange(String v,int min,int max){try{int n=Integer.parseInt(v);if(n<min||n>max)throw bad();}catch(NumberFormatException e){throw bad();}}
    private static void decimalRange(String v,BigDecimal min,BigDecimal max){try{var d=new BigDecimal(v);if(d.compareTo(min)<0||d.compareTo(max)>0)throw bad();}catch(NumberFormatException e){throw bad();}}
    private static void length(String v,int max){if(v.length()>max)throw bad();}
    private static ApiException bad(){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SETTING","Cấu hình không hợp lệ.");}
}
```

`audit.recordAs` đang yêu cầu chạy trong transaction (MANDATORY) — `SettingsService.update` có `@Transactional` nên hợp lệ. `AuditTrail.recordAs` nhận `type`/`id`; dùng `"SETTING"` và id null (AuditLog.id có thể null? nếu không, dùng `id=0L`; kiểm tra entity AuditLog trước khi code — nếu cột NOT NULL, dùng `0L`).

- [ ] **Step 6: Chạy test xanh** — `mvn -q -f backend/pom.xml -Dtest=SettingsServiceTest test`.

- [ ] **Step 7: Commit** — `git add database backend/src/main/java/com/htttdn/crm/entity/AppSetting.java backend/src/main/java/com/htttdn/crm/repository/AppSettingRepository.java backend/src/main/java/com/htttdn/crm/service/SettingsService.java backend/src/test/java/com/htttdn/crm/service/SettingsServiceTest.java && git commit -m "feat: add app settings foundation and incremental-migration schema"`.

#### Task 2: Admin Settings API + UI

**Files:**
- Create: `backend/src/main/java/com/htttdn/crm/controller/AdminSettingsController.java`
- Create: `frontend/src/AdminSettings.jsx`, `frontend/src/admin-settings.css`
- Modify: `frontend/src/SystemAdminApp.jsx` (menu `settings`)
- Test: `backend/src/test/java/com/htttdn/crm/service/AdminSettingsIntegrationTest.java`, `frontend/src/AdminSettings.test.jsx`

**Interfaces:**
- `GET /api/admin/settings` → `Map<String,String>` (đầy đủ default + override).
- `PUT /api/admin/settings` body `Map<String,String>` → `Map<String,String>`; 400 `INVALID_SETTING`; 403 nếu không phải ADMIN.
- Consumes: `SettingsService.all()/update(values, actor)`.

- [ ] **Step 1: Test backend đỏ** — ADMIN đọc 200 và có 8 khóa; MANAGER/CUSTOMER 403; PUT đổi `loyalty.redeemPoints` rồi GET thấy giá trị mới; PUT khóa lạ 400.

```java
mvc.perform(get("/api/admin/settings").header("Authorization","Bearer "+jwt.issue(admin.getId(),"ADMIN")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(8));
mvc.perform(put("/api/admin/settings").header("Authorization","Bearer "+jwt.issue(admin.getId(),"ADMIN"))
   .contentType(MediaType.APPLICATION_JSON).content("{\"loyalty.redeemPoints\":\"150\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$['loyalty.redeemPoints']").value("150"));
```

- [ ] **Step 2: Chạy đỏ.**

- [ ] **Step 3: Implement controller.**

```java
@RestController @RequestMapping("/api/admin/settings")
public class AdminSettingsController {
    private final SettingsService settings;
    public AdminSettingsController(SettingsService settings){this.settings=settings;}
    @GetMapping public Map<String,String> get(){return settings.all();}
    @PutMapping public Map<String,String> update(@RequestBody Map<String,String> values,
        @RequestAttribute(AuditTrail.ACTOR_ATTRIBUTE) AuditTrail.Actor actor){return settings.update(values,actor);}
}
```

- [ ] **Step 4: UI** — `AdminSettings.jsx`: fetch GET khi mount; form chia 3 nhóm (Hóa đơn, Tồn kho, Điểm thưởng); nút "Lưu cấu hình" gọi PUT toàn bộ map; hiển thị lỗi role=alert; dùng `adminApi` từ `./api`. Thêm vào `SystemAdminApp.jsx`: `settings: 'Cấu hình'` trong `menu`, icon `Settings`, và render `{section === 'settings' && <AdminSettings/>}` trong khối nội dung (theo đúng pattern các section hiện có).

```jsx
export default function AdminSettings(){
  const [values,setValues]=useState(null),[error,setError]=useState(''),[saved,setSaved]=useState('');
  useEffect(()=>{adminApi('/api/admin/settings').then(setValues).catch(e=>setError(e.message));},[]);
  const change=(key,value)=>setValues(current=>({...current,[key]:value}));
  const save=async event=>{event.preventDefault();setError('');setSaved('');
    try{setValues(await adminApi('/api/admin/settings',{method:'PUT',body:values}));setSaved('Đã lưu cấu hình.');}
    catch(e){setError(e.message);}};
  if(error&&!values)return <p role="alert">{error}</p>;
  if(!values)return <p role="status">Đang tải cấu hình…</p>;
  return <section aria-label="Cấu hình hệ thống"><h2>Cấu hình hệ thống</h2>
    <form onSubmit={save}>
      <fieldset><legend>Hóa đơn</legend>
        <label>Tên người bán<input maxLength={150} value={values['invoice.seller.name']} onChange={e=>change('invoice.seller.name',e.target.value)}/></label>
        <label>Địa chỉ<input maxLength={1000} value={values['invoice.seller.address']} onChange={e=>change('invoice.seller.address',e.target.value)}/></label>
        <label>Điện thoại<input maxLength={40} value={values['invoice.seller.phone']} onChange={e=>change('invoice.seller.phone',e.target.value)}/></label>
        <label>Mã số thuế<input maxLength={40} value={values['invoice.seller.taxCode']} onChange={e=>change('invoice.seller.taxCode',e.target.value)}/></label>
      </fieldset>
      <fieldset><legend>Tồn kho</legend>
        <label>Ngưỡng tồn thấp mặc định<input type="number" min="0" max="100000" value={values['inventory.lowStockDefault']} onChange={e=>change('inventory.lowStockDefault',e.target.value)}/></label>
      </fieldset>
      <fieldset><legend>Điểm thưởng</legend>
        <label>Điểm mỗi 10.000đ<input type="number" min="0" max="1000" value={values['loyalty.pointsPer10000']} onChange={e=>change('loyalty.pointsPer10000',e.target.value)}/></label>
        <label>Điểm cần để đổi<input type="number" min="1" max="1000000" value={values['loyalty.redeemPoints']} onChange={e=>change('loyalty.redeemPoints',e.target.value)}/></label>
        <label>Giá trị voucher đổi (đ)<input type="number" min="0" max="100000000" value={values['loyalty.redeemValue']} onChange={e=>change('loyalty.redeemValue',e.target.value)}/></label>
      </fieldset>
      {error&&<p role="alert">{error}</p>}{saved&&<p role="status">{saved}</p>}
      <button className="admin-primary">Lưu cấu hình</button>
    </form>
  </section>;
}
```

- [ ] **Step 5: Test frontend** — mock `adminApi` trả map; render `SystemAdminApp` tại `/admin/settings` (mock `adminRefreshSession` trả `{admin:{}}`); assert 8 input; sửa `loyalty.redeemPoints`; bấm lưu; assert PUT body chứa giá trị mới; assert thông báo đã lưu. Chạy: `cd frontend && npx vitest run --no-file-parallelism src/AdminSettings.test.jsx`.

- [ ] **Step 6: Commit** — `git commit -m "feat: add admin system settings API and page"`.

#### Task 3: Hóa đơn đọc cấu hình người bán từ SettingsService

**Files:**
- Modify: `backend/src/main/java/com/htttdn/crm/service/SalesInvoiceService.java`
- Modify: `frontend/src/InvoiceDocument.jsx` (chỉ nếu tiêu đề cứng; giữ nguyên nếu đã lấy từ dữ liệu)
- Test: bổ sung case trong `CashflowReportIntegrationTest`

**Interfaces:**
- `SalesInvoiceService` constructor chuyển từ 4 `@Value` sang `(SalesInvoiceRepository, OrderRepository, AuditTrail, SettingsService)`; hành vi `issue` giữ nguyên, seller lấy từ `settings.get("invoice.seller.*")` với `blankToNull`.

- [ ] **Step 1: Test đỏ** — trong `CashflowReportIntegrationTest`: cập nhật settings (`invoice.seller.name` = "Shop Test", địa chỉ "1 Đường Test"), phát hành hóa đơn, assert `sellerName/sellerAddress` trong response theo settings; đổi settings sau đó và assert hóa đơn cũ **không** đổi (snapshot).
- [ ] **Step 2: Chạy đỏ.**
- [ ] **Step 3: Sửa service** — bỏ 4 tham số `@Value`, thêm `SettingsService`; trong `issue`:

```java
invoice.setSellerName(settings.get("invoice.seller.name"));
invoice.setSellerAddress(blankToNull(settings.get("invoice.seller.address")));
invoice.setSellerPhone(blankToNull(settings.get("invoice.seller.phone")));
invoice.setSellerTaxCode(blankToNull(settings.get("invoice.seller.taxCode")));
```

Grep toàn repo các chỗ `new SalesInvoiceService(` và `app.invoice.seller` để cập nhật (hiện chỉ Spring inject; nếu test nào khởi tạo tay thì sửa theo constructor mới). Cập nhật `README.md`/`docs/suppliers-invoices.md`: cấu hình người bán nay sửa trong Admin → Cấu hình; các biến `app.invoice.seller-*` chỉ còn là default cũ trong cấu hình mẫu.
- [ ] **Step 4: Chạy xanh** cả `SettingsServiceTest` và `CashflowReportIntegrationTest`.
- [ ] **Step 5: Commit** — `git commit -m "feat: read invoice seller details from system settings"`.

---

### Giai đoạn 2 — Thẻ kho và cảnh báo tồn thấp

#### Task 4: StockMovement + tập trung hóa StockService

**Files:**
- Create: `entity/StockMovement.java`, `repository/StockMovementRepository.java`
- Modify: `service/StockService.java`, `service/OrderService.java` (~68-77, 283-286), `service/AdminProductService.java` (~44, 53)
- Test: `backend/src/test/java/com/htttdn/crm/service/StockLedgerIntegrationTest.java`; sửa `OrderConfirmationTest` (constructor `StockService`)

**Interfaces (Produces):**
- `StockService(ProductRepository, EntityManager, StockMovementRepository)`
- `void sell(Order order)` — MANDATORY; ghi SALE cho từng item, reference ORDER + order.id.
- `void release(Order order)` — giữ idempotent qua `order.isStockReleased()`, ghi CANCEL_RELEASE.
- `void adjust(Product product, int newQuantity, String note)` — ghi ADJUST với delta = new − before; nếu delta 0 không ghi movement.
- `void initial(Product product)` — ghi INITIAL (delta = stock hiện tại, before = 0).
- Mọi method dùng `apply(product, delta, type, refType, refId, note)` nội bộ; kiểm tra `stock_after >= 0`.

- [ ] **Step 1: Test đỏ** — dựng sản phẩm stock 10; gọi service tạo đơn qua `OrderService` sẽ phức tạp — thay vào đó test trực tiếp: `stock.sell(order)` (tạo Order + OrderItem trong persistence context, order đã persist) → product stock giảm, có movement SALE với stockBefore/After đúng; `release` lần 1 hoàn kho + movement CANCEL_RELEASE, lần 2 không thêm movement; `adjust(p, 3, "...")` → movement ADJUST quantity = 3 − before; `initial` → movement INITIAL; `adjust` xuống âm ném `ApiException` code `INSUFFICIENT_STOCK` và không đổi kho.
- [ ] **Step 2: Chạy đỏ.**
- [ ] **Step 3: Implement.**

```java
@Entity @Table(name="stock_movements",indexes={@Index(name="idx_stock_movements_product",columnList="product_id,created_at")})
public class StockMovement {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="product_id") private Product product;
    @Column(name="movement_type",nullable=false,length=30) private String movementType;
    @Column(nullable=false) private int quantity;
    @Column(name="stock_before",nullable=false) private int stockBefore;
    @Column(name="stock_after",nullable=false) private int stockAfter;
    @Column(name="reference_type",length=30) private String referenceType;
    @Column(name="reference_id") private Long referenceId;
    @Column(length=1000) private String note;
    @Column(name="actor_id") private Long actorId;
    @Column(name="actor_name",length=150) private String actorName;
    @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
    // getters/setters đầy đủ cho mọi field
}
```

```java
public interface StockMovementRepository extends JpaRepository<StockMovement,Long>, JpaSpecificationExecutor<StockMovement> {}
```

Trong `StockService`:

```java
public StockService(ProductRepository products,EntityManager em,StockMovementRepository movements){...}
@Transactional(propagation=Propagation.MANDATORY)
public void apply(Product product,int delta,String type,String refType,Long refId,String note){
    int before=product.getStock(); int after;
    try{after=Math.addExact(before,delta);}catch(ArithmeticException e){throw new ApiException(HttpStatus.CONFLICT,"STOCK_INVALID","Số lượng tồn không hợp lệ.");}
    if(after<0)throw new ApiException(HttpStatus.CONFLICT,"INSUFFICIENT_STOCK","Sản phẩm "+product.getName()+" không đủ tồn kho.");
    product.setStock(after);product.setUpdatedAt(Instant.now());products.save(product);
    var m=new StockMovement();m.setProduct(product);m.setMovementType(type);m.setQuantity(delta);
    m.setStockBefore(before);m.setStockAfter(after);m.setReferenceType(refType);m.setReferenceId(refId);m.setNote(note);
    var actor=AuditTrail.actor(); if(actor!=null){m.setActorId(actor.id());m.setActorName(actor.name());}
    movements.save(m);
}
@Transactional(propagation=Propagation.MANDATORY) public void sell(Order order){
    order.getItems().forEach(i->apply(i.getProduct(),-i.getQuantity(),"SALE","ORDER",order.getId(),"Bán đơn #"+order.getOrderCode()));
}
@Transactional(propagation=Propagation.MANDATORY) public void adjust(Product p,int newQuantity,String note){
    if(newQuantity<0)throw new ApiException(HttpStatus.BAD_REQUEST,"STOCK_INVALID","Số lượng tồn không được âm.");
    int delta=newQuantity-p.getStock(); if(delta==0)return;
    apply(p,delta,"ADJUST","PRODUCT",p.getId(),note);
}
@Transactional(propagation=Propagation.MANDATORY) public void initial(Product p){
    var m=new StockMovement();m.setProduct(p);m.setMovementType("INITIAL");m.setQuantity(p.getStock());
    m.setStockBefore(0);m.setStockAfter(p.getStock());m.setReferenceType("PRODUCT");m.setReferenceId(p.getId());m.setNote("Tồn kho khởi tạo");
    var actor=AuditTrail.actor();if(actor!=null){m.setActorId(actor.id());m.setActorName(actor.name());}
    movements.save(m);
}
```

`release` dùng `apply(product, quantity, "CANCEL_RELEASE","ORDER",order.getId(),"Hủy đơn #"+order.getOrderCode())` và giữ cờ `stockReleased`.

`OrderService.create`: bỏ dòng `product.setStock(...); products.save(product);` (line 74) và thay bằng kiểm tra cộng dồn:

```java
Map<Long,Integer> wanted=new LinkedHashMap<>();
... trong vòng lặp, sau khi lock + validate variant:
int total=wanted.merge(product.getId(),line.quantity(),Math::addExact);
if(total>product.getStock())throw new ApiException(HttpStatus.CONFLICT,"OUT_OF_STOCK","Sản phẩm "+product.getName()+" không đủ số lượng.");
... sau orders.save(order): stock.sell(order);
```

`AdminProductService`: `stock(...)` → `stockService.adjust(product, request.quantity(), "Điều chỉnh thủ công tại trang sản phẩm")`; `create(...)` sau `products.save(product)` gọi `stockService.initial(product)`.

- [ ] **Step 4: Sửa constructor test** — `OrderConfirmationTest` khởi tạo `new StockService(products, mock(EntityManager.class), movements)` với `StockMovementRepository movements = mock(StockMovementRepository.class)`. Grep `new StockService(` toàn repo và cập nhật.
- [ ] **Step 5: Chạy test xanh** — `StockLedgerIntegrationTest`, `OrderConfirmationTest`, `AdminProductConcurrencyTest`, `ProductArchiveIntegrationTest`.
- [ ] **Step 6: Commit** — `git commit -m "feat: centralize stock changes behind a movement ledger"`.

#### Task 5: API tồn kho (thẻ kho, cảnh báo, ngưỡng)

**Files:**
- Create: `service/InventoryQueryService.java`, `controller/ManagerInventoryController.java`
- Test: `backend/src/test/java/com/htttdn/crm/service/InventoryQueryIntegrationTest.java`

**Interfaces:**
- `GET /api/manager/inventory/movements?productId&type&from&to&page&size` → `Page<MovementView>`; `MovementView(Long id, Long productId, String productName, String type, int quantity, int stockBefore, int stockAfter, String referenceType, Long referenceId, String note, String actorName, Instant createdAt)`.
- `GET /api/manager/inventory/low-stock?supplierId&category&page&size` → `Page<LowStockRow>`; `LowStockRow(Long productId, String name, String category, Long supplierId, int stock, int threshold, Instant updatedAt)`.
- `PATCH /api/manager/products/{id}/stock-threshold` body `{ "threshold": 3 }` hoặc `{ "threshold": null }` → `Product` (200), 400 `INVALID_THRESHOLD` khi <0 hoặc >100000.
- Validation: `size` 1..100, `page>=0`, `type ∈ {SALE,CANCEL_RELEASE,ADJUST,INITIAL,STOCKTAKE}` hoặc rỗng; `from<=to`.

- [ ] **Step 1: Test đỏ** — tạo product 2 movements (SALE và ADJUST) → list lọc theo type trả đúng, sắp xếp mới nhất trước; low-stock dùng ngưỡng riêng và default từ settings (sản phẩm threshold=null với stock 5, default 5 → xuất hiện; đổi default xuống 2 → biến mất); PATCH threshold 0/`null`; MANAGER ok, CUSTOMER 403.
- [ ] **Step 2: Chạy đỏ.**
- [ ] **Step 3: Implement service** — movements dùng `JpaSpecificationExecutor` (join `product`), map sang record trong `@Transactional(readOnly=true)`; low-stock dùng `NamedParameterJdbcTemplate`:

```java
private static final String LOW_SQL = """
  SELECT p.product_id,p.name,COALESCE(c.name,'') AS category,p.supplier_id,p.quantity_remaining,
         COALESCE(p.low_stock_threshold,:fallback) AS threshold,p.updated_at
  FROM products p LEFT JOIN categories c ON c.category_id=p.category_id
  WHERE p.active=true AND p.quantity_remaining <= COALESCE(p.low_stock_threshold,:fallback)
  """;
// thêm điều kiện supplier/category, count(*) rồi LIMIT/OFFSET; ORDER BY p.quantity_remaining,p.product_id
```

- [ ] **Step 4: Implement controller** — `@RestController @RequestMapping("/api/manager")`; method paths như Interfaces; threshold update dùng `stockService.lockProduct(id)`, set `lowStockThreshold`, save, audit `STOCK_THRESHOLD`.
- [ ] **Step 5: Chạy xanh.**
- [ ] **Step 6: Commit** — `git commit -m "feat: expose stock ledger, low-stock alerts and per-product thresholds"`.

#### Task 6: Trang "Tồn kho" cho Manager

**Files:**
- Create: `frontend/src/ManagerInventory.jsx`, `frontend/src/manager-inventory.css`
- Modify: `frontend/src/ManagerApp.jsx` (nav `["inventory","Tồn kho"]`, icon `Boxes`/`Warehouse`, lazy import, render; 2 tab đầu: `low` + `ledger`)
- Test: `frontend/src/ManagerInventory.test.jsx`

**Interfaces:**
- Dùng `useManagerList` + `useManagerFilters` + `useManagerReadPause` theo pattern `ManagerSuppliers.jsx`.
- Tab `low`: gọi `/api/manager/inventory/low-stock?...`, hiện bảng (Sản phẩm, Danh mục, Tồn, Ngưỡng), nút "Sửa ngưỡng" mở input inline gọi PATCH.
- Tab `ledger`: gọi `/api/manager/inventory/movements?...` với filter type/from/to/page; hiện thời gian, sản phẩm, loại (nhãn tiếng Việt), thay đổi (+/−), trước→sau, người thao tác, ghi chú.

- [ ] **Step 1: Test đỏ** — render ManagerApp tại `/manager/inventory`; mock `low-stock` trả 1 dòng; assert thấy "Tồn kho", dòng sản phẩm, nút tab "Thẻ kho"; chuyển tab gọi đúng URL movements; sửa ngưỡng gọi PATCH `/api/manager/products/7/stock-threshold` body `{threshold:2}`.
- [ ] **Step 2: Chạy đỏ.**
- [ ] **Step 3: Implement component** — tái dùng class CSS hiện có (`refund-card`, `refund-filters`, `refund-ledger`, `admin-action`) và thêm `manager-inventory.css` cho bảng; nhãn loại movement:

```js
export const movementLabels={SALE:'Bán hàng',CANCEL_RELEASE:'Hoàn kho do hủy',ADJUST:'Điều chỉnh',INITIAL:'Khởi tạo',STOCKTAKE:'Kiểm kê'};
```

- [ ] **Step 4: Gắn nav + render** — thêm vào `navItems` giữa `supplier-receipts` và `orders`; render `<ManagerInventory/>` cho `section==='inventory'`. Cập nhật test đếm nav (`CustomerCare.test.jsx` hiện kỳ vọng 15 → 16) và `AdminApp.test.jsx` nếu liệt kê menu.
- [ ] **Step 5: Chạy xanh** — `src/ManagerInventory.test.jsx`, `src/CustomerCare.test.jsx`, `src/AdminApp.test.jsx`.
- [ ] **Step 6: Commit** — `git commit -m "feat: add manager inventory page with low-stock alerts and ledger"`.

---

### Giai đoạn 3 — Kiểm kê, giá trị tồn kho và CSV tồn kho

#### Task 7: Stocktake backend

**Files:**
- Create: `entity/Stocktake.java`, `entity/StocktakeItem.java`, `repository/StocktakeRepository.java`, `service/StocktakeService.java`
- Test: `backend/src/test/java/com/htttdn/crm/service/StocktakeIntegrationTest.java`

**Interfaces:**
- `StocktakeService.create(Long actorId, String note, List<Long> productIds)` → `Detail`; nếu `productIds` rỗng chọn tất cả sản phẩm active có stock > 0 (tối đa 2000, vượt → 400 `STOCKTAKE_TOO_LARGE`).
- `StocktakeService.list(int page,int size)` → `Page<Summary>`.
- `StocktakeService.detail(Long id)` → `Detail`.
- `StocktakeService.updateCounts(Long id, List<CountRequest>)`; `CountRequest(Long itemId, Integer countedQuantity, String note)`; chỉ DRAFT.
- `StocktakeService.complete(Long id, Long actorId)`; `cancel(Long id, Long actorId)`.
- Records: `Summary(Long id,String code,String status,int itemCount,int countedCount,Instant createdAt,Instant completedAt)`; `Detail(Long id,String code,String status,String note,Instant createdAt,Instant completedAt,List<ItemView> items,long differenceValue?)` — `ItemView(Long id,Long productId,String productName,int systemQuantity,Integer countedQuantity,Integer difference,String note)`.
- Quy tắc: complete yêu cầu mọi item `countedQuantity != null`; mỗi dòng lệch gọi `stockService.apply(product, difference, "STOCKTAKE","STOCKTAKE",id,"Kiểm kê #"+code)`; sau đó status COMPLETED, `completedBy/At`; audit `STOCKTAKE_COMPLETE`. Cancel chỉ khi DRAFT; audit `STOCKTAKE_CANCEL`.
- Mã phiếu: sau `saveAndFlush` đặt `code="KK-"+id`; yêu cầu UNIQUE.

- [ ] **Step 1: Test đỏ** — tạo 2 sản phẩm stock 10/4; create với note; code khớp `KK-\d+`; updateCounts 10→7 và 4→4; complete → stock 7/4; sinh 1 movement STOCKTAKE quantity −3; complete lần 2 ném 409; cancel phiếu DRAFT không đổi kho; complete khi còn dòng chưa đếm ném 409 `STOCKTAKE_INCOMPLETE`; create rỗng products chọn đủ sản phẩm active.
- [ ] **Step 2: Chạy đỏ.**
- [ ] **Step 3: Implement entity/repo/service** — `StocktakeItem.product` LAZY, `Stocktake.items` `@OneToMany(mappedBy="stocktake",cascade=ALL,orphanRemoval=true)`; detail map trong transaction; dùng `stockService.lockProduct` theo thứ tự productId tăng dần khi complete để tránh deadlock.
- [ ] **Step 4: Chạy xanh.**
- [ ] **Step 5: Commit** — `git commit -m "feat: add stocktake workflow with stock adjustments"`.

#### Task 8: API kiểm kê + báo cáo giá trị tồn kho

**Files:**
- Create: `service/InventoryReportService.java`
- Modify: `controller/ManagerInventoryController.java` (thêm kiểm kê), hoặc tạo `controller/StocktakeController.java`
- Test: `backend/src/test/java/com/htttdn/crm/service/InventoryReportIntegrationTest.java`

**Interfaces:**
- `POST /api/manager/stocktakes` body `{note, productIds}` → Detail (201).
- `GET /api/manager/stocktakes?page&size` → Page<Summary>.
- `GET /api/manager/stocktakes/{id}` → Detail.
- `PATCH /api/manager/stocktakes/{id}/items` body `[{itemId,countedQuantity,note}]` → Detail.
- `POST /api/manager/stocktakes/{id}/complete` / `cancel` → Detail.
- `GET /api/manager/reports/inventory` → `View(BigDecimal totalValue,long totalUnits,long missingCostProducts,long lowStockCount,Instant generatedAt,List<CategoryRow> categories,List<ProductRow> products)`; `CategoryRow(String category,long units,BigDecimal value)`; `ProductRow(Long productId,String name,String category,Long supplierId,int stock,int threshold,BigDecimal unitCost,BigDecimal value,boolean lowStock)`.
- Đơn giá: truy vấn cửa sổ (H2-safe):

```sql
SELECT product_id, unit_cost FROM (
  SELECT sri.product_id, sri.unit_cost,
         ROW_NUMBER() OVER (PARTITION BY sri.product_id
           ORDER BY sr.invoice_date DESC, sr.id DESC, sri.id DESC) AS rn
  FROM supplier_receipt_items sri JOIN supplier_receipts sr ON sr.id=sri.receipt_id) x
WHERE rn=1
```

- [ ] **Step 1: Test đỏ** — mọi endpoint kiểm kê trả 401/403 đúng vai trò; complete qua HTTP; inventory report: sản phẩm chưa nhập giá vốn 0 + `missingCostProducts=1`; 2 phiếu nhập cùng sản phẩm, dùng giá của phiếu ngày mới nhất; giá trị = stock × cost; category tổng đúng; lowStock theo ngưỡng riêng/default.
- [ ] **Step 2: Chạy đỏ.**
- [ ] **Step 3: Implement** — JDBC report + service; controller đặt trong `ManagerInventoryController` với `@RequestMapping("/api/manager")` (kiểm kê path `/stocktakes`).
- [ ] **Step 4: Chạy xanh.**
- [ ] **Step 5: Commit** — `git commit -m "feat: add stocktake API and inventory value report"`.

#### Task 9: UI kiểm kê + giá trị tồn + CSV tồn kho

**Files:**
- Modify: `frontend/src/ManagerInventory.jsx` (2 tab mới `stocktake`, `value`)
- Modify: `frontend/src/manager-api.js` (thêm `adminDownload`)
- Modify: `frontend/src/manager-inventory.css`
- Test: `frontend/src/ManagerInventory.test.jsx`

**Interfaces:**
- `adminDownload(path, filename)` tải blob có auth + refresh 401, click anchor, thu hồi object URL; ném lỗi `Không tải được tệp CSV.` nếu response không ok.
- Tab `stocktake`: danh sách phiếu + nút "Tạo phiếu kiểm kê" (gửi productIds rỗng), mở detail, nhập số đếm từng dòng, nút "Hoàn tất"/"Hủy phiếu".
- Tab `value`: bảng sản phẩm + tổng theo danh mục + tổng giá trị + số sản phẩm thiếu giá vốn; nút "Xuất CSV" gọi `adminDownload('/api/manager/reports/inventory.csv','ton-kho.csv')`.
- Backend CSV endpoints (thêm trong task này): `GET /api/manager/inventory/movements.csv` (cùng filter), `GET /api/manager/reports/inventory.csv`; dùng `util/Csv`.

- [ ] **Step 1: Test đỏ frontend** — chuyển tab Kiểm kê thấy danh sách; tạo phiếu gọi POST; nhập số đếm gọi PATCH; hoàn tất gọi POST complete; tab Giá trị hiện tổng vàng; bấm CSV gọi `adminDownload` đúng path.
- [ ] **Step 2: Test đỏ backend CSV** — `GET ...inventory.csv` trả `text/csv`, bắt đầu bằng BOM `\uFEFF`, có header `productId,name,category,stock,unitCost,value`; movements.csv header `id,createdAt,productId,productName,type,quantity,stockBefore,stockAfter,actor,note`; escape dấu phẩy/ngoặc kép bằng test một ghi chú `"A, B"`.
- [ ] **Step 3: Implement `util/Csv`**:

```java
public final class Csv {
    public static String line(Object... values){
        return Arrays.stream(values).map(Csv::cell).collect(Collectors.joining(","));
    }
    private static String cell(Object value){
        String text=value==null?"":value.toString();
        return text.contains(",")||text.contains("\"")||text.contains("\n")||text.contains("\r")
            ? "\""+text.replace("\"","\"\"")+"\"" : text;
    }
    public static ResponseEntity<String> response(String filename,List<String> rows){
        String content="\uFEFF"+String.join("\r\n",rows)+"\r\n";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename,StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8")).body(content);
    }
}
```

- [ ] **Step 4: Implement UI tabs + download helper** (manager-api):

```js
export async function adminDownload(path, filename) {
  let response = await request(`${API_BASE}${path}`, { credentials:'include',
    headers: adminAccessToken ? { Authorization:`Bearer ${adminAccessToken}` } : {} });
  if (response.status === 401) { const session = await adminRefreshSession();
    if (session) response = await request(`${API_BASE}${path}`, { credentials:'include', headers:{ Authorization:`Bearer ${adminAccessToken}` } }); }
  if (!response.ok) throw new Error('Không tải được tệp CSV.');
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement('a'); link.href = url; link.download = filename;
  document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(url);
}
```

- [ ] **Step 5: Chạy xanh** — backend 2 test mới + frontend `ManagerInventory.test.jsx`.
- [ ] **Step 6: Commit** — `git commit -m "feat: add stocktake and inventory value UI with CSV export"`.

---

### Giai đoạn 4 — Nhà cung cấp: chi tiết, lưu trữ, công nợ

#### Task 10: SupplierPayment + SupplierAccountService

**Files:**
- Create: `entity/SupplierPayment.java`, `repository/SupplierPaymentRepository.java`, `service/SupplierAccountService.java`
- Test: `backend/src/test/java/com/htttdn/crm/service/SupplierAccountIntegrationTest.java`

**Interfaces:**
- Entity: `id, supplier(@ManyToOne), receipt(@ManyToOne), amount BigDecimal, paidAt LocalDate, method String, note String, createdBy Long, createdAt Instant`.
- Repo: `List<SupplierPayment> findByReceiptIdOrderByIdAsc(Long)`; `Page<SupplierPayment> findBySupplierIdOrderByPaidAtDescIdDesc(Long, Pageable)`; `@Query("select coalesce(sum(p.amount),0) from SupplierPayment p where p.receipt.id=:receiptId") BigDecimal paidForReceipt(Long)`; `@Query(... where p.supplier.id=:supplierId) BigDecimal paidForSupplier(Long)`.
- Service detail record:

```java
public record SupplierDetail(Long id,String code,String name,String email,String phone,String address,String notes,boolean active,Instant createdAt,
  long productCount,long receiptCount,BigDecimal totalPurchase,BigDecimal totalPaid,BigDecimal outstanding,
  LocalDate lastReceiptDate,List<ProductRow> products,List<ReceiptRow> receipts){}
public record ProductRow(Long id,String name,int stock,BigDecimal price,boolean active){}
public record ReceiptRow(Long id,String invoiceNumber,LocalDate invoiceDate,BigDecimal totalAmount,BigDecimal paidAmount,BigDecimal outstanding){}
public record PaymentRequest(@NotNull Long receiptId,@NotNull @DecimalMin("0.01") BigDecimal amount,@NotNull LocalDate paidAt,@NotBlank @Size(max=30) String method,@Size(max=500) String note){}
public record PaymentView(Long id,Long receiptId,String receiptNumber,BigDecimal amount,LocalDate paidAt,String method,String note,Instant createdAt){}
```

- `detail(Long id)`: supplier + productCount (`ProductRepository.countBySupplierId` — thêm method nếu chưa có) + receipt list 10 gần nhất + tổng mua/đã trả/còn nợ + 50 sản phẩm gần nhất.
- `pay(Long supplierId, PaymentRequest, AuditTrail.Actor)`: lock receipt? Dùng `@Transactional`, đọc receipt + supplier; `receipt.supplier.id == supplierId`; amount ≤ outstanding (outstanding tính trong transaction; chấp nhận race nhỏ — khóa bằng `SELECT ... FOR UPDATE` trên receipt nếu repo hỗ trợ; nếu không, chấp nhận khóa lạc quan bằng cách tính lại và kiểm tra, ghi chú trong code).
- `payments(Long supplierId,int page,int size)`.
- Audit `SUPPLIER_PAYMENT_CREATE` với amount/total.

- [ ] **Step 1: Test đỏ** — phiếu nhập 100.000; trả 40.000 → outstanding 60.000; trả 60.000 → 0; trả vượt 409 `PAYMENT_EXCEEDS_OUTSTANDING`; receipt của NCC khác 404/409; detail trả tổng mua/trả/nợ đúng và 1 product; customer gọi 403.
- [ ] **Step 2: Chạy đỏ.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Chạy xanh.**
- [ ] **Step 5: Commit** — `git commit -m "feat: add supplier payables ledger"`.

#### Task 11: API chi tiết, lưu trữ và chặn nghiệp vụ NCC ngừng hợp tác

**Files:**
- Create: `controller/SupplierAccountController.java`
- Modify: `service/DirectoryService.assign`, `service/SupplierReceiptService.create`
- Test: bổ sung vào `SupplierAccountIntegrationTest`

**Interfaces:**
- `GET /api/manager/suppliers/{id}` → SupplierDetail.
- `PATCH /api/manager/suppliers/{id}/status` body `{active:boolean}` → Supplier; audit `SUPPLIER_STATUS`; idempotent.
- `GET /api/manager/suppliers/{id}/payments?page&size` → Page<PaymentView>.
- `POST /api/manager/suppliers/{id}/payments` body PaymentRequest → PaymentView (201).
- Chặn: `DirectoryService.assign` ném 409 `SUPPLIER_INACTIVE` nếu NCC `active=false`; `SupplierReceiptService.create` ném 409 `SUPPLIER_INACTIVE` nếu NCC ngừng hợp tác (vẫn cho xem/thanh toán công nợ cũ).
- `GET /api/manager/suppliers/{id}/reports/summary.csv` (CSV công nợ): header `receiptId,invoiceNumber,invoiceDate,totalAmount,paidAmount,outstanding`.

- [ ] **Step 1: Test đỏ** — set active=false qua PATCH; assign sản phẩm → 409; tạo phiếu nhập → 409; detail vẫn 200; payment vẫn 201; CSV đúng header + dòng.
- [ ] **Step 2: Chạy đỏ.**
- [ ] **Step 3: Implement controller + validations.**
- [ ] **Step 4: Chạy xanh.**
- [ ] **Step 5: Commit** — `git commit -m "feat: expose supplier detail, archiving and payables APIs"`.

#### Task 12: UI chi tiết NCC + thanh toán + lưu trữ

**Files:**
- Modify: `frontend/src/ManagerSuppliers.jsx` (drawer chi tiết)
- Modify: `frontend/src/manager-refunds.css` hoặc tạo `manager-suppliers.css`
- Test: `frontend/src/ManagerSupplierDetail.test.jsx`

**Interfaces:**
- Bấm "Chi tiết" trên mỗi dòng → gọi `GET /api/manager/suppliers/{id}`; hiện thông tin, thống kê (sản phẩm, phiếu nhập, tổng mua, đã trả, còn nợ, lần nhập gần nhất), 2 danh sách (sản phẩm, phiếu nhập kèm đã trả/còn nợ).
- Nút "Ngừng hợp tác"/"Kích hoạt lại" gọi PATCH status, cập nhật danh sách.
- Form thanh toán trong từng phiếu nhập: amount (mặc định = còn nợ), paidAt (hôm nay), method (`CASH`/`BANK_TRANSFER`), note; POST payments; refresh detail + danh sách.
- Nút "Xuất CSV công nợ" gọi `adminDownload('/api/manager/suppliers/{id}/reports/summary.csv','cong-no-ncc.csv')`.

- [ ] **Step 1: Test đỏ** — mock list + detail; bấm Chi tiết thấy tổng nợ; bấm Ngừng hợp tác gọi PATCH; submit thanh toán gọi POST đúng body; bấm CSV gọi adminDownload.
- [ ] **Step 2: Chạy đỏ.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Chạy xanh.**
- [ ] **Step 5: Commit** — `git commit -m "feat: add supplier detail drawer with payables and archiving"`.

---

### Giai đoạn 5 — Tích điểm khách hàng

#### Task 13: PointTransaction + LoyaltyService

**Files:**
- Create: `entity/PointTransaction.java`, `repository/PointTransactionRepository.java`, `service/LoyaltyService.java`
- Test: `backend/src/test/java/com/htttdn/crm/service/LoyaltyServiceTest.java`

**Interfaces:**
- Entity: `id, customer(@ManyToOne User), orderId Long, txnType String, points int, note String, createdBy Long, createdAt Instant`.
- Repo: `@Query("select coalesce(sum(p.points),0) from PointTransaction p where p.customer.id=:customerId") long balance(Long customerId)`; `boolean existsByOrderIdAndTxnType(Long orderId,String txnType)`; `List<PointTransaction> findTop50ByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId)`.
- Service: `long balance(Long customerId)`; `Summary summary(User customer)`; `void awardForCompletedOrder(Order order)`; `RedeemResult redeem(User customer)`; `Summary adjust(User customer,int points,String note,AuditTrail.Actor actor)`.
- Records: `Entry(Long id,String type,int points,String note,Long orderId,Instant createdAt)`; `Summary(long balance,List<Entry> history)`; `RedeemResult(String code,BigDecimal discountValue,int spentPoints,long balance,Instant expiresAt)`.
- Công thức: `int earned = order.getTotalAmount().divide(BigDecimal.valueOf(10000),0,RoundingMode.FLOOR).intValueExact() * settings.getInt("loyalty.pointsPer10000")`; nếu `earned<=0` bỏ qua; idempotent `existsByOrderIdAndTxnType(orderId,"EARN")`.
- Redeem: cost/value từ settings; balance < cost → 409 `INSUFFICIENT_POINTS`; tạo voucher:

```java
var voucher=new StoreVoucher();
voucher.setCode(nextCode()); voucher.setOwnerCustomerId(customer.getId());
voucher.setDiscountType("FIXED_AMOUNT"); voucher.setDiscountValue(BigDecimal.valueOf(settings.getInt("loyalty.redeemValue")));
voucher.setMinOrderAmount(BigDecimal.ZERO); voucher.setDescription("Đổi "+cost+" điểm thưởng");
voucher.setUsageLimit(1); voucher.setPerCustomerLimit(1); voucher.setActive(true);
voucher.setStartsAt(Instant.now()); voucher.setExpiresAt(Instant.now().plus(Duration.ofDays(30)));
vouchers.save(voucher);
```

`nextCode()` = `"DIEM-" + UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase(Locale.ROOT)`, lặp tối đa 5 lần nếu trùng (`StoreVoucherRepository.existsByCode`; thêm method nếu thiếu).
- Ghi REDEEM `points=-cost`; gọi `NotificationService.createVoucher(customer,"Đổi điểm thành công","Bạn đã đổi "+cost+" điểm, mã "+code+".")`.
- `adjust`: points != 0, note 1..500, target role CUSTOMER; ghi ADJUST; audit `POINTS_ADJUST` (actor bắt buộc).

- [ ] **Step 1: Test đỏ** — đơn COMPLETED 255.000, rate 1 → 25 điểm; gọi award 2 lần chỉ 1 giao dịch; rate 0 → không cộng; đổi 100 điểm tạo voucher FIXED_AMOUNT 20.000, balance giảm 100, history có REDEEM; đổi khi thiếu điểm 409; adjust +50 có lịch sử; adjust 0/note rỗng 400; note >500 400.
- [ ] **Step 2: Chạy đỏ.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Chạy xanh** (`LoyaltyServiceTest`, `SettingsServiceTest`).
- [ ] **Step 5: Commit** — `git commit -m "feat: add loyalty points ledger, earning and redemption"`.

#### Task 14: API điểm + hook xác nhận nhận hàng

**Files:**
- Create: `controller/LoyaltyController.java`
- Modify: `service/OrderService.java` (`confirmReceipt`), constructor `OrderService` (+`LoyaltyService`), `entity/User` (không đổi)
- Test: `backend/src/test/java/com/htttdn/crm/service/LoyaltyIntegrationTest.java`; sửa constructor trong `OrderConfirmationTest`

**Interfaces:**
- `GET /api/account/points` (customer) → Summary.
- `POST /api/account/points/redeem` → RedeemResult (201).
- `GET /api/manager/customers/{id}/points` → Summary.
- `POST /api/manager/customers/{id}/points` body `{points:int,note:String}` → Summary (200); 400 `INVALID_POINTS`.
- `confirmReceipt` gọi `loyalty.awardForCompletedOrder(order)` **sau** `orders.save(order)`; lỗi cộng điểm không được làm hỏng xác nhận? Đặt trong cùng transaction để nhất quán; nếu lỗi bất ngờ, cả giao dịch rollback (chấp nhận).

- [ ] **Step 1: Test đỏ** — customer xác nhận nhận hàng qua `POST /api/orders/{id}/confirm-receipt` (endpoint hiện có) → balance tăng; gọi lại idempotent; GET points 200, MANAGER/CUSTOMER khác 403/401; manager adjust qua HTTP; loyalty controller chặn role sai.
- [ ] **Step 2: Chạy đỏ.**
- [ ] **Step 3: Implement controller + hook**; cập nhật `OrderConfirmationTest` constructor (`new OrderService(..., mock(LoyaltyService.class))` — hoặc instance thật như các mock hiện có).
- [ ] **Step 4: Chạy xanh** — `LoyaltyIntegrationTest`, `OrderConfirmationTest`, `OrderPaymentIntegrationTest`.
- [ ] **Step 5: Commit** — `git commit -m "feat: award loyalty points on order completion and expose points APIs"`.

#### Task 15: UI điểm cho khách + Manager 360°

**Files:**
- Create: `frontend/src/CustomerPoints.jsx`
- Modify: `frontend/src/App.jsx` (ProfileModal), `frontend/src/ManagerApp.jsx` (khu hồ sơ 360°)
- Test: `frontend/src/CustomerPoints.test.jsx`

**Interfaces:**
- `CustomerPoints` fetch `/api/account/points` khi mount; hiện số dư, nút "Đổi 100 điểm lấy voucher" (hiển thị số điểm/giá trị từ một lần fetch settings? Không có API public settings → hiển thị câu chung "Đổi điểm lấy voucher" và sau khi đổi hiện mã/giá trị trả về); danh sách 10 giao dịch gần nhất.
- Manager 360°: component nhỏ `ManagerPoints({customerId})` (đặt trong `ManagerApp.jsx` cạnh hồ sơ 360°) hiện số dư + form điều chỉnh (+/− điểm, ghi chú bắt buộc), gọi manager API.

- [ ] **Step 1: Test đỏ** — mock `/api/account/points` trả balance 120 + 1 entry; render `CustomerPoints`, thấy "120 điểm"; bấm đổi → POST redeem, hiện mã voucher; mock lỗi → alert. Manager: render ManagerApp tại profile 360° (mock endpoint) → thấy điểm + gửi adjust.
- [ ] **Step 2: Chạy đỏ.**
- [ ] **Step 3: Implement component + chèn vào ProfileModal** (thêm `<CustomerPoints/>` sau phần thông tin tài khoản) và Manager 360°.
- [ ] **Step 4: Chạy xanh** + `App` test hiện có (`src/App.test.jsx` nếu có; grep và chạy các test liên quan).
- [ ] **Step 5: Commit** — `git commit -m "feat: show loyalty points to customers and managers"`.

---

### Giai đoạn 6 — Thông báo nội bộ, CSV báo cáo, tài liệu

#### Task 16: InternalNotice + hook tồn kho thấp + scheduler đơn treo

**Files:**
- Create: `entity/InternalNotice.java`, `repository/InternalNoticeRepository.java`, `service/InternalNoticeService.java`, `service/OperationsNoticeScheduler.java`
- Modify: `service/StockService.java` (hook), `repository/OrderRepository.java` (`countStale`), `repository/UserRepository.java` (`findByRoleAndLockedFalseAndDeletedAtIsNull`)
- Test: `backend/src/test/java/com/htttdn/crm/service/InternalNoticeIntegrationTest.java`

**Interfaces:**
- `InternalNoticeService.notifyManagers(String type,String title,String content,String link,String dedupeKey)` → số người nhận; mỗi MANAGER active/không khóa/chưa xóa; bỏ qua nếu `existsByRecipientIdAndDedupeKey`.
- `notifyLowStock(Product product,int before,int after)`: threshold hiệu lực; chỉ khi `before > threshold && after <= threshold`; dedupe `LOW_STOCK:<productId>:<yyyy-MM-dd>` (giờ VN); title "Sản phẩm sắp hết"; content "… còn N (ngưỡng T)"; link `/manager/inventory?tab=low`.
- `OperationsNoticeScheduler` `@Scheduled(fixedDelay=3600000, initialDelay=600000)`; `long stale = orders.countStale(now-24h)`; nếu >0 `notifyManagers("STALE_ORDERS","Đơn chờ xử lý quá 24 giờ","Có "+stale+" đơn…","/manager/orders","STALE_ORDERS:"+LocalDate.now(ZONE))`; scheduler có thể tắt trong test qua property `app.notices.enabled=false` (thêm `@ConditionalOnProperty` hoặc kiểm tra flag).
- `OrderRepository.countStale`: `@Query("select count(o) from Order o where o.status in ('PENDING','CONFIRMED','PREPARING') and o.createdAt < :before") long countStale(@Param("before") Instant before);`

- [ ] **Step 1: Test đỏ** — tạo 2 manager + 1 customer; notifyManagers tạo 2 dòng, gọi lại cùng dedupeKey không thêm; manager bị khóa không nhận; notifyLowStock chỉ khi vượt ngưỡng; scheduler gọi trực tiếp method tạo notice khi có đơn cũ; `app.notices.enabled=false` không chạy tự động.
- [ ] **Step 2: Chạy đỏ.**
- [ ] **Step 3: Implement**; `StockService` nhận `SettingsService` + `InternalNoticeService` (cập nhật constructor + test cũ).
- [ ] **Step 4: Chạy xanh.**
- [ ] **Step 5: Commit** — `git commit -m "feat: notify managers about low stock and stale orders"`.

#### Task 17: API + bell thông báo vận hành

**Files:**
- Create: `controller/ManagerNoticeController.java`, `frontend/src/ManagerNoticeBell.jsx`, `frontend/src/manager-notices.css`
- Modify: `frontend/src/ManagerApp.jsx` (gắn bell cạnh `ManagerNotificationBell`)
- Test: `backend` bổ sung `InternalNoticeIntegrationTest`; `frontend/src/ManagerNoticeBell.test.jsx`

**Interfaces:**
- `GET /api/manager/notices?page&size` → `{page:{content,totalPages,totalElements}, unread}`; mỗi item `{id,type,title,content,link,readAt,createdAt}`.
- `GET /api/manager/notices/summary` → `{unreadCount}`.
- `PATCH /api/manager/notices/{id}/read`, `PATCH /api/manager/notices/read-all` → 204.
- Chỉ trả notice của chính actor; role khác 403.

- [ ] **Step 1: Test đỏ** — list/summary/read qua HTTP với 2 manager (mỗi người chỉ thấy của mình); customer 403; UI: badge hiện unread, mở panel, bấm item gọi read + điều hướng `link`.
- [ ] **Step 2: Chạy đỏ.**
- [ ] **Step 3: Implement** (`ManagerNoticeBell` có thể sao chép cấu trúc `ManagerNotificationBell`, đổi endpoint + tiêu đề "Thông báo vận hành").
- [ ] **Step 4: Chạy xanh.**
- [ ] **Step 5: Commit** — `git commit -m "feat: add manager operations notice bell"`.

#### Task 18: CSV doanh thu, đơn hàng, khách hàng

**Files:**
- Create: `service/CsvReportService.java`
- Modify: `controller/CashflowReportController.java` (thêm `.csv`), `controller/AdminOrderController.java` hoặc controller mới `ManagerCsvController.java`, `frontend/src/RevenueReport.jsx`, `ManagerApp.jsx` (nút CSV cạnh danh sách đơn + khách)
- Test: `backend/src/test/java/com/htttdn/crm/service/CsvExportIntegrationTest.java`; frontend test nút CSV trong `RevenueReport` (nếu có test riêng thì tạo `RevenueReport.test.jsx`)

**Interfaces:**
- `GET /api/manager/reports/revenue.csv?from&to&groupBy` → CSV header `period,collected,collectedOrders,purchaseCost,difference`; cùng validate như báo cáo (400 `INVALID_REVENUE_RANGE`).
- `GET /api/manager/orders.csv?status&q&from&to` → header `orderCode,customer,phone,status,paymentMethod,paymentStatus,totalAmount,createdAt`; tối đa 10.000 dòng (vượt → chỉ 10.000 đầu, ghi chú trong tài liệu).
- `GET /api/manager/customers.csv?q` → header `fullName,email,phone,orderCount,completedValue,createdAt`; chỉ khách CUSTOMER chưa xóa.
- UI: nút "Xuất CSV" trên `RevenueReport` (dùng state from/to/groupBy hiện có), cạnh nút xuất của danh sách đơn và khách (dùng bộ lọc hiện tại).

- [ ] **Step 1: Test đỏ backend** — mỗi endpoint đúng content-type/BOM/header; doanh thu có 2 dòng series đúng số; orders lọc status; customers có completedValue tính từ đơn COMPLETED; role sai 403.
- [ ] **Step 2: Test đỏ frontend** — bấm nút gọi `adminDownload` với path + tên file.
- [ ] **Step 3: Implement** (dùng `Csv.line/response`; JDBC cho orders/customers; revenue map từ `CashflowReportService.report`).
- [ ] **Step 4: Chạy xanh.**
- [ ] **Step 5: Commit** — `git commit -m "feat: export revenue, orders and customers as CSV"`.

#### Task 19: Tài liệu + kiểm tra toàn bộ

**Files:**
- Create: `docs/inventory-suppliers-loyalty.md`
- Modify: `README.md`, `docs/cashflow-voucher-limits.md`, `docs/suppliers-invoices.md`, `docs/refunds-auto-cancellation.md` (nếu có câu liên quan)

**Nội dung tài liệu:**
- Kiến trúc module, bảng dữ liệu mới, quy tắc nghiệp vụ (ngưỡng, kiểm kê, giá vốn, công nợ, điểm, thông báo).
- Danh sách API mới kèm quyền.
- Cấu hình Admin và 8 khóa.
- Migration + cách chạy trên deploy (spring.sql.init).
- Ghi chú giới hạn: điểm không tự trừ khi đổi trả; CSV giới hạn 10.000 dòng; phiếu nhập không đổi tồn kho.

- [ ] **Step 1: Viết tài liệu + cập nhật README (bảng module, menu Manager 16 mục, Admin có Cấu hình).**
- [ ] **Step 2: Chạy toàn bộ backend** — `mvn -q -f backend/pom.xml test` (kỳ vọng 0 failures; ghi lại tổng test).
- [ ] **Step 3: Chạy toàn bộ frontend serial** — `cd frontend && npx vitest run --no-file-parallelism` (kỳ vọng tất cả pass).
- [ ] **Step 4: Build production** — `cd frontend && npm run build`.
- [ ] **Step 5: Commit** — `git commit -m "docs: document inventory, supplier payables and loyalty modules"`.

#### Task 20: Chuẩn bị deploy Render (chỉ khi người dùng đồng ý)

**Files (worktree deploy `C:/Users/admin/Desktop/crm-deploy-audit-check`):**
- Modify: `.dockerignore` (thêm `!database/migration_inventory_suppliers_loyalty.sql`), `Dockerfile` (COPY file mới), `deploy/application.yml` (`schema-locations` = hai file, phân tách dấu phẩy)

- [ ] **Step 1: Merge `feat/api-rate-limiting` vào `codex/free-demo-deployment`, giải conflict theo hướng giữ cả hai.**
- [ ] **Step 2: Cập nhật infra** — `schema-locations: classpath:db/migration_supplier_receipts_invoices.sql,classpath:db/migration_inventory_suppliers_loyalty.sql`; Dockerfile COPY file mới vào `src/main/resources/db/`; `.dockerignore` whitelist.
- [ ] **Step 3: Verify local bằng Docker** — build image; chạy Postgres nạp schema **trước** migration (`git show <commit-cũ>:database/schema.sql`), boot app với DB đó, chờ `/healthz`, kiểm tra 7 bảng mới + cột `low_stock_threshold`; restart để xác nhận idempotent; kiểm tra endpoint mới trả 401 (không token) và endpoint hoàn tiền vẫn 404. Dọn container sau khi xong.
- [ ] **Step 4: Push nhánh deploy; người dùng bấm Manual Deploy trên Render; sau đó verify** `/healthz`, `/api/manager/reports/revenue` 401, `/api/manager/inventory/movements` 401, `/api/admin/settings` 401, refund 404.
- [ ] **Step 5: Commit** — `git commit -m "deploy: apply inventory, supplier payables and loyalty migrations at boot"` (nhánh deploy).

---

## 6. Thứ tự thực thi & tiêu chí hoàn thành

1. Task 1 → 3 (Settings nền tảng) → chạy toàn bộ backend.
2. Task 4 → 6 (Tồn kho cơ bản) → demo Manager thấy cảnh báo + thẻ kho.
3. Task 7 → 9 (Kiểm kê + giá trị + CSV).
4. Task 10 → 12 (NCC chi tiết/công nợ/lưu trữ).
5. Task 13 → 15 (Tích điểm).
6. Task 16 → 18 (Thông báo + CSV báo cáo).
7. Task 19 (docs + full verification), Task 20 (deploy khi được đồng ý).

**Definition of Done cho toàn kế hoạch:**
- Tất cả test backend + frontend pass; frontend build thành công.
- Không còn đường nào đổi `products.quantity_remaining` ngoài `StockService` (grep `setStock(` chỉ còn trong StockService/AdminProductService tạo mới).
- Migration idempotent chạy được trên DB đã có dữ liệu và trên DB mới; `schema.sql` và migration khớp nhau.
- Mọi endpoint mới tuân thủ phân quyền; CSV đúng BOM/escape; hóa đơn phản ánh cấu hình mới nhưng hóa đơn cũ giữ snapshot.
- Tài liệu mô tả đủ để đưa vào báo cáo môn HTTT.
