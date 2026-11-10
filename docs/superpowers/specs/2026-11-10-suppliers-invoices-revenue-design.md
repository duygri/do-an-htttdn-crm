# Thiết kế: nhà cung cấp, phiếu nhập hàng, hóa đơn bán hàng, doanh thu

Ngày: 2026-11-10
Nhánh: `feat/api-rate-limiting`
Phạm vi đã chốt với người dùng:

- Bỏ phần nhập kho, thay bằng quản lý nhà cung cấp.
- Thêm phiếu nhập hàng gắn nhà cung cấp.
- Thêm hóa đơn bán hàng cho đơn khách.
- Thống kê doanh thu theo tháng/quý/năm theo tiền đã thu thực tế.
- Gỡ UI/API hoàn tiền, giữ xác nhận thu COD và đối soát PayOS.
- Redeploy Render sau khi hoàn tất.

## 1. Quy ước nghiệp vụ

### Nhà cung cấp

Tận dụng bảng `suppliers` và API hiện có. Bổ sung giao diện Manager để xem, tìm, tạo, sửa, bật/tắt nhà cung cấp và gán nhà cung cấp cho sản phẩm. Không thêm bảng mới cho nhà cung cấp.

### Phiếu nhập hàng

Là chứng từ mua hàng gắn nhà cung cấp, gồm:

- Số hóa đơn nhà cung cấp, ngày hóa đơn, ghi chú.
- Nhiều dòng hàng: sản phẩm, số lượng, đơn giá vốn; thành tiền từng dòng và tổng tiền.
- Người tạo, thời điểm tạo.

Quan trọng: phiếu nhập hàng **không cộng tồn kho** vì người dùng đã chọn bỏ phần nhập kho. Phiếu chỉ phục vụ ghi nhận chi phí mua hàng và đối chiếu hóa đơn nhà cung cấp. Nếu sau này cần cộng tồn, có thể bổ sung một cột trạng thái và luồng duyệt riêng.

### Hóa đơn bán hàng

Lập từ một đơn hàng, mỗi đơn tối đa một hóa đơn:

- Số hóa đơn duy nhất dạng `HD-<orderCode>`.
- Snapshot người bán (cấu hình), người mua, địa chỉ giao, danh sách hàng hóa, đơn giá, số lượng, giảm giá, tổng tiền, phương thức/trạng thái thanh toán tại thời điểm lập.
- Manager lập và in; khách xem hóa đơn của đơn mình.
- In bằng HTML/CSS trình duyệt, không thêm thư viện PDF.

### Doanh thu

- Chỉ tính tiền đã thu thực tế:
  - PayOS: `payment_status = 'PAID'` và có `paid_at`.
  - COD: có `cod_collected_at` và `cod_collected_amount > 0`.
- Nhóm theo ngày/tháng/quý/năm, múi giờ `Asia/Ho_Chi_Minh`.
- Kèm chi phí nhập hàng theo ngày hóa đơn nhà cung cấp và chênh lệch thu - chi phí.
- Không còn khái niệm hoàn tiền trong báo cáo.

### Gỡ hoàn tiền

- Xóa `RefundService`, `OrderRefund`, `OrderRefundRepository`, `ManagerRefundController`, UI sổ hoàn tiền và các tham chiếu.
- Giữ xác nhận thu COD và đối soát PayOS trong màn hình chi tiết đơn.
- Giữ bảng/cột hoàn tiền cũ trong database để không mất dữ liệu lịch sử; mã nguồn không đọc/ghi các cột này nữa.

## 2. Schema mới

`database/migration_supplier_receipts_invoices.sql` và cập nhật `database/schema.sql`:

- `supplier_receipts`: id, supplier_id, invoice_number, invoice_date, total_amount, note, created_by, created_at.
- `supplier_receipt_items`: id, receipt_id, product_id, quantity, unit_cost, line_total.
- `sales_invoices`: id, invoice_number unique, order_id unique, customer_id, issued_at, seller snapshot, buyer snapshot, delivery_address, subtotal, discount_amount, total_amount, payment_method, payment_status, note, created_by, created_at.
- `sales_invoice_items`: id, invoice_id, product_id, product_name, size, color, quantity, unit_price, line_total.

Indexes: supplier/date trên phiếu nhập, order/customer trên hóa đơn.

## 3. API

### Nhà cung cấp (đã có, giữ nguyên)

- `GET /api/manager/suppliers`
- `POST /api/manager/suppliers`
- `PUT /api/manager/suppliers/{id}`
- `PATCH /api/manager/products/{id}/supplier`

Bổ sung `supplierId` vào payload sản phẩm để gán ngay khi tạo/sửa sản phẩm.

### Phiếu nhập hàng

- `GET /api/manager/supplier-receipts?q=&supplierId=&fromDate=&toDate=&page=&size=`
- `GET /api/manager/supplier-receipts/{id}`
- `POST /api/manager/supplier-receipts`

### Hóa đơn bán hàng

- `GET /api/manager/invoices?q=&page=&size=`
- `GET /api/manager/invoices/{id}`
- `GET /api/manager/orders/{id}/invoice`
- `POST /api/manager/orders/{id}/invoice` (idempotent)
- `GET /api/orders/{id}/invoice` (khách sở hữu đơn)

### Doanh thu

- `GET /api/manager/reports/revenue?from=&to=&groupBy=day|month|quarter|year`
- Giữ alias `/api/manager/reports/cashflow` trong thời gian chuyển đổi.

### COD / đối soát

- `PATCH /api/manager/orders/{id}/cod-collection`
- `POST /api/manager/orders/{id}/payment/reconcile`

## 4. Backend

- `SupplierReceiptService`: validate, tính tổng, audit; không sửa tồn kho.
- `SalesInvoiceService`: lập idempotent, snapshot, phân quyền Manager/khách sở hữu.
- `RevenueReportService` (thay `CashflowReportService`): SQL nhóm ngày, rollup tháng/quý/năm trong Java; chi phí nhập hàng từ `supplier_receipts.invoice_date`.
- `CodCollectionService`: chuyển `confirmCod` khỏi `RefundService`.
- `ManagerOrderFinanceController`: COD + reconcile.
- `OrderRepository.collectedRevenue()`: tổng tiền đã thu cho dashboard.

## 5. Frontend

- Manager nav: thêm `Nhà cung cấp`, `Phiếu nhập hàng`; bỏ `Hoàn tiền`.
- Màn hình nhà cung cấp: danh sách, tìm kiếm, tạo/sửa, bật/tắt.
- Màn hình phiếu nhập hàng: danh sách, tạo nhiều dòng, xem chi tiết.
- Sản phẩm: thêm chọn nhà cung cấp.
- Đơn hàng: bỏ bộ lọc hoàn tiền; giữ xác nhận COD/đối soát PayOS; thêm nút lập/in hóa đơn.
- Báo cáo: tab Doanh thu với chọn tháng/quý/năm, bảng và biểu đồ cột; bỏ cột hoàn tiền.
- Khách: xem/in hóa đơn trong chi tiết đơn; bỏ khối hoàn tiền.

## 6. Kiểm thử

- Backend: phiếu nhập hàng (tạo/liệt kê/validate/không đổi tồn), hóa đơn (idempotent/snapshot/quyền), doanh thu (tháng/quý/năm/chi phí), COD vẫn hoạt động, refund endpoint không còn.
- Frontend: màn hình nhà cung cấp/phiếu nhập, hóa đơn, báo cáo doanh thu; gỡ test hoàn tiền.
- Chạy `mvn -B -ntp test`, `npm test`, `npm run build`.

## 7. Deploy

- Áp migration mới lên database deploy trước khi khởi động backend mới.
- Build Docker từ worktree deploy `codex/free-demo-deployment`.
- Push nhánh deploy, trigger Render service `anhlonshop` (auto-deploy đang tắt).
- Kiểm tra `/healthz`, đăng nhập Manager, mở các màn hình mới.
- Không commit `backend/src/main/resources/application.yml` chứa PayOS secret.
