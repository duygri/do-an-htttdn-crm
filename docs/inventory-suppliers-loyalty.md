# Tồn kho, nhà cung cấp, tích điểm và tính năng bổ trợ

Tài liệu mô tả các module được bổ sung cho đồ án HTTT: thẻ kho và cảnh báo tồn thấp, kiểm kê, giá trị tồn kho, chi tiết/công nợ/lưu trữ nhà cung cấp, tích điểm khách hàng, cấu hình hệ thống cho Admin, thông báo vận hành cho Manager và xuất CSV báo cáo.

## 1. Kiến trúc và nguyên tắc chung

- Backend Spring Boot 3.4.3 (Java 21) + JPA/JDBC + PostgreSQL 16; test dùng H2 chế độ PostgreSQL `create-drop`.
- Mọi thay đổi `products.quantity_remaining` đi qua `StockService` và sinh đúng một dòng `stock_movements`. Không còn chỗ nào khác ghi trực tiếp số tồn (trừ tạo sản phẩm mới, sau đó gọi `initial`).
- Tiền VND dùng `BigDecimal`, giới hạn 2 chữ số thập phân trong DB; thời gian nghiệp vụ báo cáo theo `Asia/Ho_Chi_Minh`.
- Hành động Manager/Admin ghi nhật ký qua `AuditTrail`; hành động khách ghi qua sổ cái nghiệp vụ (`stock_movements`, `point_transactions`).
- Migration duy nhất: `database/migration_inventory_suppliers_loyalty.sql` (idempotent, `IF NOT EXISTS`), nội dung được append vào `database/schema.sql`.

## 2. Mô hình dữ liệu mới

| Bảng | Vai trò |
| --- | --- |
| `app_settings` | Cấu hình key-value toàn hệ thống (`setting_key`, `setting_value`, `updated_at`, `updated_by`) |
| `stock_movements` | Sổ cái biến động kho: loại, số thay đổi, tồn trước/sau, tham chiếu, người thao tác |
| `stocktakes` | Phiếu kiểm kê: mã `KK-<id>`, trạng thái `DRAFT`/`COMPLETED`/`CANCELLED` |
| `stocktake_items` | Dòng kiểm kê: số hệ thống, số đếm, chênh lệch; duy nhất theo (phiếu, sản phẩm) |
| `supplier_payments` | Thanh toán công nợ gắn từng phiếu nhập; nhiều lần cho một phiếu |
| `point_transactions` | Sổ điểm khách: `EARN`/`REDEEM`/`ADJUST`; unique EARN theo đơn |
| `internal_notices` | Thông báo vận hành gửi tài khoản MANAGER; unique (người nhận, khóa chống trùng) |

Cột mới: `products.low_stock_threshold` (nullable, `>= 0`).

## 3. Tồn kho

### 3.1 Ngưỡng tồn thấp

- Hiệu lực = `products.low_stock_threshold`; nếu null lấy `inventory.lowStockDefault` (mặc định 5).
- Cảnh báo khi `stock <= ngưỡng hiệu lực`.
- Manager sửa ngưỡng riêng qua `PATCH /api/manager/products/{id}/stock-threshold`, body `{ "threshold": 3 }` hoặc `{ "threshold": null }` để quay về mặc định. Giá trị hợp lệ 0..100000.

### 3.2 Thẻ kho

Mọi biến động ghi `movement_type`:

| Loại | Ý nghĩa |
| --- | --- |
| `SALE` | Trừ kho khi tạo đơn |
| `CANCEL_RELEASE` | Hoàn kho khi hủy đơn (chỉ một lần nhờ cờ `stock_released`) |
| `ADJUST` | Manager đặt lại số tồn tuyệt đối tại trang sản phẩm |
| `INITIAL` | Tồn khởi tạo khi tạo sản phẩm |
| `STOCKTAKE` | Điều chỉnh theo phiếu kiểm kê hoàn tất |

`stock_after` không được âm; vượt ngưỡng/không đủ tồn trả `409` với mã `INSUFFICIENT_STOCK`.

### 3.3 Kiểm kê

- Tạo phiếu: gửi danh sách `productIds`; rỗng nghĩa là chọn mọi sản phẩm đang hiển thị có tồn > 0 (tối đa 2.000 dòng, vượt trả `400 STOCKTAKE_TOO_LARGE`).
- Phiếu `DRAFT` chụp `system_quantity`; Manager nhập `counted_quantity` cho từng dòng.
- Hoàn tất yêu cầu mọi dòng đã đếm (thiếu trả `409 STOCKTAKE_INCOMPLETE`); mỗi dòng lệch sinh movement `STOCKTAKE` với delta = số đếm − số hệ thống, khóa sản phẩm theo thứ tự id tăng dần để tránh deadlock.
- Hủy chỉ áp dụng cho phiếu `DRAFT`, không đổi kho. Hoàn tất/hủy lần hai trả `409`.

### 3.4 Giá trị tồn kho

- Đơn giá vốn = `unit_cost` của dòng phiếu nhập gần nhất theo thứ tự `invoice_date DESC, receipt.id DESC, item.id DESC`.
- Giá trị = `stock × đơn giá vốn`. Sản phẩm chưa từng nhập có giá vốn 0 và được đếm vào `missingCostProducts`.
- Báo cáo trả tổng giá trị, tổng số lượng, số sản phẩm thiếu giá vốn, số sản phẩm tồn thấp, tổng theo danh mục và danh sách sản phẩm.

## 4. Nhà cung cấp

### 4.1 Chi tiết

`GET /api/manager/suppliers/{id}` trả thông tin liên hệ, trạng thái, số sản phẩm, số phiếu nhập, tổng mua, đã trả, còn nợ, ngày nhập gần nhất, 50 sản phẩm và 10 phiếu nhập gần nhất.

### 4.2 Lưu trữ (ngừng hợp tác)

- `PATCH /api/manager/suppliers/{id}/status` body `{ "active": false }`; thao tác idempotent, ghi audit `SUPPLIER_STATUS`.
- Nhà cung cấp ngừng hợp tác vẫn xem chi tiết và thanh toán công nợ cũ, nhưng **chặn** gán sản phẩm mới và tạo phiếu nhập mới với `409 SUPPLIER_INACTIVE`.

### 4.3 Công nợ phải trả

- `paid = SUM(supplier_payments.amount)` theo phiếu; `outstanding = total_amount − paid`.
- Cho trả nhiều lần; không cho trả vượt `outstanding` (`409 PAYMENT_EXCEEDS_OUTSTANDING`); phiếu của nhà cung cấp khác trả `404`.
- Phiếu nhập **không** thay đổi tồn kho (giữ đúng quyết định trước đây); đây chỉ là chứng từ chi phí và công nợ.

## 5. Tích điểm khách hàng

- Điểm chỉ cộng khi đơn chuyển `COMPLETED` qua xác nhận nhận hàng của khách (`POST /api/orders/{id}/confirm-receipt`).
- Công thức: `floor(totalAmount / 10.000) × loyalty.pointsPer10000`. Điểm <= 0 thì bỏ qua.
- Idempotent theo đơn: unique index `uq_point_earn_order` trên `(order_id)` cho `EARN`.
- Đổi điểm: trừ `loyalty.redeemPoints`, tạo `StoreVoucher` loại `FIXED_AMOUNT` trị giá `loyalty.redeemValue`, thuộc sở hữu khách, hạn 30 ngày, dùng một lần; thiếu điểm trả `409 INSUFFICIENT_POINTS`.
- Manager điều chỉnh thủ công qua `POST /api/manager/customers/{id}/points` với `points` khác 0 và `note` bắt buộc (<= 500 ký tự); ghi audit `POINTS_ADJUST`.
- Số dư = tổng `points` trong `point_transactions`. Điểm không tự trừ khi đổi trả.

## 6. Cấu hình hệ thống (Admin)

Bảng `app_settings` lưu đè lên giá trị mặc định; API `GET/PUT /api/admin/settings` chỉ dành cho ADMIN.

| Khóa | Mặc định | Giới hạn |
| --- | --- | --- |
| `invoice.seller.name` | `Anh Lon Shop` | chuỗi <= 150 |
| `invoice.seller.address` | rỗng | chuỗi <= 1000 |
| `invoice.seller.phone` | rỗng | chuỗi <= 40 |
| `invoice.seller.taxCode` | rỗng | chuỗi <= 40 |
| `inventory.lowStockDefault` | `5` | số nguyên 0..100000 |
| `loyalty.pointsPer10000` | `1` | số nguyên 0..1000 |
| `loyalty.redeemPoints` | `100` | số nguyên 1..1000000 |
| `loyalty.redeemValue` | `20000` | số thực 0..100000000 |

Hóa đơn bán hàng chụp thông tin người bán tại thời điểm phát hành; đổi cấu hình sau đó không làm thay đổi hóa đơn cũ. Khóa lạ/giá trị sai trả `400 INVALID_SETTING`.

## 7. Thông báo vận hành (Manager)

- Nguồn 1: tồn kho vượt ngưỡng xuống (`before > ngưỡng && after <= ngưỡng`), ngay khi phát sinh movement; khóa chống trùng `LOW_STOCK:<productId>:<ngày VN>`.
- Nguồn 2: đơn `PENDING`/`CONFIRMED`/`PREPARING` treo quá 24 giờ; scheduler chạy mỗi giờ (mặc định trễ lần đầu 10 phút), một thông báo/ngày.
- Mỗi tài khoản MANAGER đang hoạt động, không khóa, chưa xóa nhận một bản ghi riêng; unique `(recipient_id, dedupe_key)`.
- Chuông vận hành trên thanh công cụ Manager: đếm chưa đọc, mở danh sách, đánh dấu đã đọc/đã đọc tất cả, bấm thông báo để điều hướng tới tồn kho hoặc đơn hàng.
- Tắt scheduler trong test/môi trường đặc biệt bằng `app.notices.enabled=false`.

## 8. Xuất CSV

- Định dạng: UTF-8 có BOM, phân tách dấu phẩy, xuống dòng CRLF, escape theo RFC 4180 (bao ngoặc kép khi có dấu phẩy/ngoặc kép/xuống dòng).
- Chỉ Manager tải được; giới hạn 10.000 dòng cho đơn hàng và khách hàng.
- Danh sách tệp: thẻ kho, giá trị tồn, doanh thu, đơn hàng, khách hàng, công nợ nhà cung cấp.

## 9. API mới và quyền

| Phương thức/đường dẫn | Quyền | Chức năng |
| --- | --- | --- |
| `GET/PUT /api/admin/settings` | ADMIN | Đọc/cập nhật cấu hình |
| `GET /api/manager/inventory/movements` | MANAGER | Thẻ kho có lọc loại/thời gian |
| `GET /api/manager/inventory/low-stock` | MANAGER | Danh sách tồn thấp |
| `PATCH /api/manager/products/{id}/stock-threshold` | MANAGER | Sửa ngưỡng tồn |
| `GET /api/manager/inventory/movements.csv` | MANAGER | CSV thẻ kho |
| `POST/GET /api/manager/stocktakes`, `GET/PATCH /api/manager/stocktakes/{id}` | MANAGER | Tạo, xem, nhập số đếm |
| `POST /api/manager/stocktakes/{id}/complete`, `/cancel` | MANAGER | Hoàn tất/hủy phiếu |
| `GET /api/manager/reports/inventory` và `.csv` | MANAGER | Giá trị tồn kho |
| `GET /api/manager/suppliers/{id}`, `/payments` | MANAGER | Chi tiết và công nợ |
| `PATCH /api/manager/suppliers/{id}/status` | MANAGER | Lưu trữ/kích hoạt lại |
| `POST /api/manager/suppliers/{id}/payments` | MANAGER | Ghi nhận thanh toán |
| `GET /api/manager/suppliers/{id}/reports/summary.csv` | MANAGER | CSV công nợ |
| `GET /api/account/points`, `POST /api/account/points/redeem` | CUSTOMER | Xem/đổi điểm |
| `GET/POST /api/manager/customers/{id}/points` | MANAGER | Xem/điều chỉnh điểm |
| `GET /api/manager/notices`, `/summary`, `PATCH /{id}/read`, `/read-all` | MANAGER | Thông báo vận hành |
| `GET /api/manager/reports/revenue.csv`, `/orders.csv`, `/customers.csv` | MANAGER | CSV báo cáo |

## 10. Migration và triển khai

- Áp dụng `database/migration_inventory_suppliers_loyalty.sql` trên database đã có dữ liệu, hoặc dùng `database/schema.sql` cho database mới. Cả hai chia sẻ cùng một block DDL idempotent.
- Trên môi trường deploy chạy bằng `spring.sql.init`, khai báo thêm file migration thứ hai trong `schema-locations` và COPY vào image (xem Dockerfile của nhánh deploy).
- Prod giữ `ddl-auto: validate`; thiếu bảng/cột nghĩa là migration chưa chạy — không chuyển sang `update`.

## 11. Giới hạn đã biết

- Phiếu nhập hàng không cộng tồn kho; muốn tăng tồn phải điều chỉnh tại trang sản phẩm hoặc qua kiểm kê.
- Điểm không tự trừ khi đổi trả; chưa có quy đổi điểm thành tiền mặt.
- CSV giới hạn 10.000 dòng, không phân trang trong tệp.
- Giá vốn lấy theo lần nhập gần nhất, không phải bình quân gia quyền.
- Scheduler thông báo chạy trong tiến trình backend; nhiều instance sẽ gửi trùng nếu không cấu hình khóa phân tán.
