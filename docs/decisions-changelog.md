# Nhật ký cập nhật quyết định

Ngày cập nhật: 25/10/2026
Phạm vi: Giai đoạn B; chỉ sửa tài liệu và thêm mẫu cấu hình, không sửa code ứng dụng.

| Tệp | Cũ | Mới | Bằng chứng code/tài liệu | [CẦN XÁC NHẬN] còn lại |
| --- | --- | --- | --- | --- |
| `docs/prd-htttdn-crm.md` | PRD v1.2 còn ghi email reset và phạm vi Manager audit là điểm mở; FR-28 chưa nêu khác biệt theo vai trò; tự hủy chưa gắn với cấu hình demo/nghiệm thu | Cập nhật PRD v1.3; chốt reset ngoài MVP, mặc định tắt và không bật demo; mô tả audit Admin toàn bộ, Manager chỉ bản ghi actor Manager; demo bật tự hủy với grace 24 giờ và ghi rõ cần biến môi trường | `AuthService` mặc định `DEV_PASSWORD_RESET_ENABLED=false`; `AuditLogQuery.list` lọc theo vai trò người truy vấn ở dòng 27; `OrderService.cancel/confirmReceipt` → `recordCustomerAction` → `AuditTrail.recordAs` ghi `CUSTOMER_CANCEL`/`CUSTOMER_RECEIPT` với actor `CUSTOMER`; `CustomerActivityService.list`; `AbandonedOrderScheduler` mặc định cờ false, grace 24 giờ và chu kỳ 300000 ms | Phân bố actor trong dữ liệu audit lịch sử chưa được kiểm tra |
| `README.md` | Mô tả audit Manager chưa rõ scope; thiếu bảng cấu hình demo/nghiệm thu | Bổ sung scope actor-role, cấu hình môi trường, cảnh báo không đọc `.env`, COD mặc định cho smoke test, PayOS thật chỉ khi người phụ trách xác nhận; không bật reset ở demo | `AuditLogQuery.list`; `AuditTrail.actor`; `CustomerActivityService.list`; `AbandonedOrderScheduler`; cấu hình PayOS được README mô tả | — |
| `docs/env-demo.md` | Chưa có mẫu cấu hình demo cho backend | Thêm bảng giá trị và lệnh PowerShell cho các biến demo, không có secret; nêu rõ phải đặt biến môi trường tiến trình | `AbandonedOrderScheduler` dùng `@Value` với mặc định; `AuthService` dùng `DEV_PASSWORD_RESET_ENABLED`; `.gitignore:4-7` bỏ qua `.env` và `.env.*` | — |
| `docs/refunds-auto-cancellation.md` | Ví dụ bật cờ chưa được xác định rõ là cấu hình demo/nghiệm thu; chưa nêu Spring Boot không đọc `.env` | Ghi rõ mục đích demo/nghiệm thu, grace 24 giờ, scheduler 300000 ms và điều kiện giải phóng tồn kho/voucher | `AbandonedOrderScheduler.run`; `AbandonedOrderWorker.expire/cancel`; `StockService.release`; `VoucherService.release` | — |
| Ba đặc tả Use Case v2 | UC-07 mô tả đơn PayOS quá hạn như tự hủy mặc định; UC-09 chưa ghi rõ không bật demo; actor matrix chưa nêu scope audit | UC-07 ghi điều kiện tự hủy có cờ; UC-09 ghi ngoài MVP/không bật demo/email chưa xác minh; UC-ADM03 và UC-A14 phân biệt audit log với lịch sử hoạt động hồ sơ 360° | `OrderService.create`; `AbandonedOrderScheduler`; `AuditLogQuery.list`; `CustomerActivityService.list` | — |
| `docs/use-cases/CHANGELOG-use-case-v2.md` | Câu hỏi audit và reset còn mở; mục rủi ro ghi cần xác nhận quyền audit Manager | Chuyển audit/reset/tự hủy thành quyết định đã chốt; giữ các câu hỏi độc lập khác; nêu lọc actor role và lịch sử dữ liệu chưa kiểm | `AuditLogQuery.list`; `AuthService`; `AbandonedOrderScheduler` | Phân bố dữ liệu audit lịch sử chưa được kiểm tra |
| `docs/prd-changelog-v1.2.md` | Changelog còn giữ ba điểm đã được nhóm quyết định | Cập nhật trạng thái quyết định; giữ riêng phần chưa kiểm dữ liệu lịch sử | Quyết định người dùng; bằng chứng code tương ứng tại các hàng PRD phía trên | Dữ liệu audit lịch sử chưa được kiểm tra |

## Kiểm tra dữ liệu lịch sử audit

Truy vấn đề xuất để kiểm tra các bản ghi `ACCOUNT` do vai trò khác Admin tạo:

```sql
SELECT actor_role, entity_type, COUNT(*) FROM audit_logs WHERE entity_type='ACCOUNT' AND actor_role<>'ADMIN' GROUP BY actor_role, entity_type;
```

**Trạng thái:** chưa kiểm dữ liệu lịch sử; truy vấn trên chưa được chạy vì không có xác minh kết nối và phạm vi cơ sở dữ liệu lịch sử trong lượt này. Vì vậy không kết luận về các bản ghi lịch sử đã tồn tại.

## Hành vi audit đã đối chiếu

- `backend/src/main/java/com/htttdn/crm/controller/AuditLogController.java:11-12` ánh xạ hai route `/api/admin/audit-logs` và `/api/manager/audit-logs` dùng chung truy vấn, nhận bộ lọc và phân trang.
- `backend/src/main/java/com/htttdn/crm/service/AuditLogQuery.java:22-27`, hàm `list`, lấy actor hiện tại và chỉ thêm điều kiện actor role `MANAGER` khi actor không phải Admin. Vì vậy route Admin không áp dụng lọc role, còn route Manager chỉ trả bản ghi do Manager thực hiện; bộ lọc này không giới hạn theo loại đối tượng.
- Thao tác tài khoản nội bộ nằm dưới `backend/src/main/java/com/htttdn/crm/controller/InternalAccountController.java:8-14` (`/api/admin/accounts`) và được xác thực theo nhánh Admin trong `backend/src/main/java/com/htttdn/crm/security/AdminAccessGuard.java:16-22`. Vì actor là Admin, các thao tác đó không xuất hiện trong truy vấn Manager.
- Với sự kiện Customer, `backend/src/main/java/com/htttdn/crm/service/OrderService.java:261-296` (`cancel`, `recordCustomerAction`) và `:304-312` (`confirmReceipt`) truyền actor `CUSTOMER` tường minh vào `AuditTrail.recordAs`; `backend/src/main/java/com/htttdn/crm/service/AuditTrail.java:42-44` lưu role từ actor được truyền vào. Luồng ghi này không lấy actor từ `AuditTrail.actor()`; do đó Manager có thể không thấy các sự kiện đó.
- Lịch sử hoạt động Customer được tổng hợp riêng bởi `backend/src/main/java/com/htttdn/crm/service/CustomerActivityService.java:14-48`, hàm `list`, trong hồ sơ 360°; đây không phải cùng một danh sách với audit log.

## Bằng chứng cấu hình

- `backend/src/main/java/com/htttdn/crm/service/AuthService.java:11`: mặc định `DEV_PASSWORD_RESET_ENABLED=false`.
- `backend/src/main/java/com/htttdn/crm/service/AbandonedOrderScheduler.java:15-18`: cờ tự hủy mặc định `false`, grace mặc định 24 giờ, scheduler dùng `app.payos.auto-cancel-ms` mặc định 300000 ms và bỏ qua xử lý khi cờ tắt.
- `.gitignore` hiện bỏ qua `.env` và `.env.*`; không tạo hoặc commit file `.env` thật.

## Sơ đồ sequence chưa sửa

Theo yêu cầu, sequence diagram chỉ được ghi nhận để xử lý riêng:

- `docs/diagrams/sequence-new/customer/III-07-shopping-cart-order-payos.puml` còn mô tả `PENDING_PAYMENT`, webhook chuyển đơn sang `CONFIRMED` và giải phóng ngay khi webhook thất bại. Code hiện tạo đơn `PENDING`; webhook hợp lệ chỉ cập nhật `paymentStatus=PAID`; Manager mới chuyển đơn sang `CONFIRMED`; tự hủy và giải phóng tồn kho/voucher là nhánh riêng, chỉ chạy khi bật cờ và hết thời gian chờ.
- `docs/diagrams/sequence-new/manager/M-15-audit-log-query.puml` chưa biểu đạt bộ lọc `actor_role=MANAGER` và khả năng thiếu sự kiện do Customer thực hiện.

## Điểm còn [CẦN XÁC NHẬN]

1. Phân bố `actor_role` thực tế trong `audit_logs` lịch sử, đặc biệt các bản ghi `ACCOUNT` không do Admin tạo, chưa được kiểm tra bằng truy vấn nêu trên.
2. Không có xác nhận dữ liệu audit đã triển khai đang dùng đúng schema/migration tại thời điểm chạy báo cáo; tài liệu mô tả code hiện tại trong repository.
