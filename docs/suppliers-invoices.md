# Nhà cung cấp, phiếu nhập hàng và hóa đơn bán hàng

## Migration

Database hiện có chạy `database/migration_supplier_receipts_invoices.sql` trước khi khởi động backend mới. Database mới dùng `database/schema.sql` đã chứa cùng cấu trúc. Backend không tự chạy migration; Hibernate `validate` sẽ dừng khởi động nếu thiếu bảng/cột.

Migration thêm bốn bảng: `supplier_receipts`, `supplier_receipt_items`, `sales_invoices`, `sales_invoice_items`. Không sửa tồn kho và không tạo hóa đơn cho dữ liệu cũ.

## Nhà cung cấp

Manager mở **Nhà cung cấp** (`/manager/suppliers`).

- `GET /api/manager/suppliers?q=&active=&page=0&size=20&sort=name&desc=false`
- `POST /api/manager/suppliers` với `code` (bắt buộc, duy nhất, tối đa 40 ký tự), `name` (bắt buộc, tối đa 150), `email`, `phone`, `address`, `notes`, `active`.
- `PUT /api/manager/suppliers/{id}` sửa cùng cấu trúc.
- `PATCH /api/manager/products/{id}/supplier` với `{ "supplierId": 1 }` để gắn nhanh sản phẩm.
- Sản phẩm cũng nhận `supplierId` trong body tạo/cập nhật sản phẩm; gửi `null` để bỏ liên kết.

## Phiếu nhập hàng

Manager mở **Phiếu nhập hàng** (`/manager/supplier-receipts`). Phiếu ghi nhận chi phí mua theo hóa đơn nhà cung cấp, **không cộng tồn kho**.

- `GET /api/manager/supplier-receipts?supplierId=&from=YYYY-MM-DD&to=YYYY-MM-DD&page=0&size=20`
- `GET /api/manager/supplier-receipts/{id}`
- `POST /api/manager/supplier-receipts`

```json
{
  "supplierId": 1,
  "invoiceNumber": "HDN-001",
  "invoiceDate": "2026-11-01",
  "note": "Nhập hàng đợt 1",
  "items": [
    { "productId": 10, "quantity": 20, "unitCost": 150000 }
  ]
}
```

Backend tự tính `lineTotal` và `totalAmount`; không tin tổng tiền do trình duyệt gửi. `invoiceDate` không được sau hôm nay quá một ngày. `unitCost` tối đa hai chữ số thập phân. `from`/`to` là ngày lịch, không phải thời điểm.

Chi phí này được dùng cho báo cáo doanh thu theo `invoiceDate`. Phiếu đã lập không có thao tác sửa/xóa trong phiên bản hiện tại; cần lập phiếu điều chỉnh khi có sai lệch.

## Hóa đơn bán hàng

Manager mở **Hóa đơn** (`/manager/invoices`) hoặc bấm **Hóa đơn** trong chi tiết đơn. Mỗi đơn có tối đa một hóa đơn; gọi phát hành lặp trả lại hóa đơn đã có.

- `GET /api/manager/invoices?q=&page=0&size=20` tìm theo số hóa đơn hoặc tên khách.
- `GET /api/manager/invoices/{id}`
- `GET /api/manager/orders/{id}/invoice` trả `404` nếu chưa phát hành.
- `POST /api/manager/orders/{id}/invoice` với body tùy chọn `{ "note": "..." }`.
- Khách xem hóa đơn của đơn mình: `GET /api/orders/{id}/invoice`; đơn của người khác trả `404`.

Số hóa đơn là `HD-<orderCode>`. Hóa đơn chụp lại người bán (cấu hình trong **Admin → Cấu hình**, khóa `invoice.seller.*`), người mua, địa chỉ giao, từng dòng sản phẩm, tạm tính, giảm giá và tổng tiền tại thời điểm phát hành. Không phát hành cho đơn `CANCELLED` hoặc đơn không có dòng sản phẩm. Giao diện hỗ trợ in trực tiếp bằng hộp thoại in của trình duyệt.

## Gỡ hoàn tiền

Sổ hoàn tiền thủ công đã được gỡ khỏi menu, API và báo cáo doanh thu. Bảng/cột hoàn tiền cũ vẫn giữ trong database để bảo toàn dữ liệu. Xác nhận thu COD và đối soát PayOS vẫn hoạt động; xem [thu COD, tự hủy và đối soát](refunds-auto-cancellation.md).
