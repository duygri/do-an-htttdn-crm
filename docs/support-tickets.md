# Phiếu hỗ trợ khách hàng

## Cài đặt và dữ liệu

- Chỉ thêm một bảng nghiệp vụ `support_tickets`; dùng lại `customers`, `orders`, `customer_notifications`, `audit_logs`.
- Thêm một cột nullable `support_ticket_id` có khóa ngoại vào thông báo cũ để mở đúng phiếu. Không chứa ID phiếu trong `order_id` hay `care_task_id`.
- Người vận hành chạy `database/migration_support_tickets.sql` sau các migration nhật ký/chăm sóc/nhắc việc trước đây, rồi khởi động lại backend. Đợt này không tự chạy migration hoặc sửa `application.yml`.
- Khách mở `/ho-tro`, `/ho-tro/{id}`. Manager mở `/manager/support-tickets`, chi tiết dùng `?id=...`; tìm kiếm, trạng thái, khách/người phụ trách và phân trang nằm trong query string. Hồ sơ 360° có liên kết tới phiếu của khách.

## Quy tắc bản đầu

- Khách gửi phiếu cho chính mình; Manager có thể tạo hộ CUSTOMER chưa xóa, kể cả khách đang khóa. Nhân viên/ADMIN không được chọn làm khách.
- Đơn liên quan không bắt buộc nhưng phải thuộc đúng khách của phiếu. Nội dung ban đầu hiển thị cho khách, không được coi là ghi chú nội bộ.
- NEW → IN_PROGRESS → WAITING_CUSTOMER hoặc RESOLVED. WAITING_CUSTOMER → IN_PROGRESS hoặc RESOLVED; khách trả lời khi chờ khách sẽ đưa về IN_PROGRESS. Không đổi ngược về NEW, không mở lại phiếu đã giải quyết.
- Trước khi xử lý phải phân công; người mới được giao phải là MANAGER đang hoạt động. Có thể giữ người phụ trách cũ không còn hoạt động để kết thúc lịch sử; Manager có thể giao lại cho người đang hoạt động.
- Đổi sang Chờ khách/Đã giải quyết phải có nội dung công khai gửi khách. Ghi chú nội bộ và trả lời của Manager có thể ghi riêng; ghi chú nội bộ không tạo thông báo cho khách.
- Phiếu đã giải quyết chỉ xem; nếu cần hỗ trợ thêm, tạo phiếu mới. Không có xóa cứng/xóa mềm hoặc tự đóng theo thời gian ở bản này.
- Tiêu đề 1–150, nội dung/tin nhắn 1–4.000 ký tự sau trim. Tối đa 200 tin nhắn trao đổi/ghi chú, cho phép thêm một tin chốt khi giải quyết. Hội thoại lưu dạng JSON text trong cùng phiếu; ảnh đính kèm dùng dịch vụ private upload và SLA/CSAT theo phần mở rộng bên dưới; chưa có chat realtime.
- Mọi thay đổi khóa phiếu trong transaction. Cập nhật dùng `version`; xung đột trả 409 và giữ bản nháp trên web. Mã UUID cho lần tạo và mỗi tin nhắn chống trùng khi gửi lại sau mất phản hồi. Không tự sửa/hủy/thu tiền/hoàn tiền đơn liên quan hoặc đổi trạng thái việc chăm sóc.

## API

- Customer: `GET/POST /api/support-tickets`, `GET /api/support-tickets/{id}`, `POST /api/support-tickets/{id}/messages`.
- Manager: `GET/POST /api/manager/support-tickets`, `GET/PATCH /api/manager/support-tickets/{id}`, `POST /api/manager/support-tickets/{id}/messages`.
- Danh sách: `q`, `status`, `page`/`size`; Manager thêm `customerId`, `assigneeId`. Lọc tại database trước phân trang; không tải hội thoại hoặc truy vấn entity từng dòng trong danh sách.
- Tạo: `title`, `description`, `orderId` tùy chọn, `requestId` UUID; Manager thêm `customerId` và `assigneeId` tùy chọn. Customer không được gửi các trường chỉ định khách/nhân viên.
- Tin nhắn: `content`, `internal`, `requestId`; customer không có quyền `internal=true`.
- Cập nhật Manager: `status`, `assigneeId`, `version`, `reply` công khai và `requestId` nếu có reply. Customer không được PATCH trạng thái.
- DTO khách không trả danh tính/phân công nội bộ hoặc ghi chú nội bộ; chỉ Manager thấy thông tin khách và lịch sử nội bộ. Customer khác nhận 404 khi đoán ID.

## Thông báo, nhật ký và giao diện

- Dùng thông báo sẵn có cho khách khi tiếp nhận/có trả lời/cập nhật. Người được giao nhận thông báo khi phân công và khi khách trả lời. Phiếu chưa phân công xuất hiện trong hàng chờ Manager, không phát thông báo hàng loạt cho mọi nhân viên.
- Chuông Manager hiển thị chung nhắc việc và thông báo phiếu; hết áp dụng khi giải quyết/đổi người phụ trách. Thông báo cũ vẫn giữ lịch sử.
- Khi cấu hình nhà cung cấp email, khách nhận email lúc tạo phiếu, khi shop trả lời công khai hoặc đổi trạng thái; manager phụ trách nhận email khi được phân công và khi khách trả lời. Email chỉ chứa nội dung công khai, kèm liên kết `/ho-tro/{id}` hoặc `/manager/support-tickets?id={id}`; ghi chú nội bộ không bao giờ được gửi. Email đi sau commit; lỗi gửi không làm mất thông báo trong web.
- Nhật ký ghi CREATE/UPDATE, loại SUPPORT_TICKET, chỉ metadata (ID khách/đơn/người phụ trách, trạng thái, phiên bản, số tin). Không lưu nội dung hội thoại hoặc ghi chú vào audit. Khách gửi tin không bị ghi giả thành thao tác Manager.
- Chỉ màn hình đang mở polling 5 giây; dừng khi ẩn tab, khi nhập bản nháp hoặc đang gửi. Giữ nội dung khi lỗi, không gửi chồng và bỏ kết quả sau khi rời trang. Có trạng thái tải/rỗng/lỗi/thử lại, form và thẻ responsive sáng/tối.

## Kiểm thử và giới hạn xác nhận

- SupportTicketIntegrationTest: chủ sở hữu, vai trò, tạo hộ, đơn đúng khách, ghi chú kín, trạng thái, phiên bản, idempotency, thông báo, audit và rollback.
- SupportTicketConcurrencyTest: tạo lặp đồng thời và hai tin nhắn đồng thời không mất dữ liệu.
- SupportTickets.test.jsx: route đăng nhập/quay về, bộ lọc, tạo/gửi lỗi giữ nháp, phân công, trạng thái, polling và URL trực tiếp.
- `CRM_POSTGRES_QA=true mvn test -q -Dtest=SupportTicketPostgresTest`: chạy SQL/JPA trên bảng tạm PostgreSQL và rollback, không áp dụng migration hoặc sửa bảng public. Không thay thế smoke test sau khi người vận hành cài migration.
- Chưa kiểm thử trực quan đầy đủ trên trình duyệt kết nối backend thật. Không thực hiện thanh toán hoặc xử lý đơn thật để QA.

Kết quả kiểm tra tự động đợt triển khai: 254 test frontend qua (`npm test -- --maxWorkers=2`); `mvn test -q`, build và kiểm tra whitespace qua. Đã chạy riêng hai test PostgreSQL `SupportTicketPostgresTest` và `CareReminderPostgresTest` thành công trên bảng tạm rollback; không cài migration vào database đang dùng.


## Support experience: SLA, CSAT và ảnh

- Migration bổ sung: `database/migration_support_experience.sql`; chỉ ALTER bảng hiện có, không tự chạy. Phụ thuộc migration attachment của dịch vụ upload để có `customers.pending_attachments`.
- Phiếu mới mặc định NORMAL. URGENT = 4 giờ, NORMAL = 24 giờ, LOW = 48 giờ liên tục từ `createdAt`; chờ khách không dừng đồng hồ. Đổi ưu tiên tính lại từ lúc tạo, yêu cầu `version` hiện tại và `priorityReason` không trống (tối đa 1000 ký tự), lưu reason trong audit. Phiếu cũ giữ SLA UNKNOWN, không suy diễn lịch sử.
- PATCH manager bổ sung `priority`, `priorityReason`. `firstResponseAt` chỉ được ghi một lần khi manager gửi trả lời công khai, bao gồm reply khi xử lý trạng thái; ghi chú nội bộ và lời khách không được tính.
- GET danh sách customer và manager đều nhận `priority=URGENT|NORMAL|LOW`, `unanswered=true`, `overdue=true`, `sort=due|updated`. Lọc trước phân trang; unanswered/overdue chỉ phiếu chưa giải quyết có SLA ghi nhận. Due tăng dần, null cuối, ID làm khóa sắp xếp phụ.
- `POST /api/support-tickets/{id}/csat`: `{rating:1..5, comment?:string(max1000), requestId:UUID}`. Chỉ khách sở hữu phiếu đã giải quyết được gửi một lần. Gửi lại cùng requestId và payload trả kết quả cũ; nội dung khác trả 409. Khóa phiếu bảo vệ hai yêu cầu đồng thời.
- `GET /api/manager/support-tickets/report?from=YYYY-MM-DD&to=YYYY-MM-DD`: theo `resolvedAt`, từ ngày đầu đến hết ngày cuối tại Asia/Ho_Chi_Minh (tối đa 367 ngày). Trả `resolvedCount`, `recordedCount`, `unrecordedCount`, `distribution` đủ điểm 1–5, `averageRating`, `responseRatio` (phân số 0–1, `ratioUnit=FRACTION_0_TO_1`, UI đổi sang %), `slaKnownCount`, `slaMetCount`, `slaMetRatio`. Chỉ tính CUSTOMER chưa xóa (vẫn tính khách bị khóa). Trung bình và tỷ lệ làm tròn 2 chữ số thập phân. Trung bình không tính phiếu chưa đánh giá; không có mẫu thì average/ratio null. Báo cáo tổng hợp SQL không tải hội thoại/ảnh.
- Create, Reply và Update có `attachmentIds` (tối đa 5 ID upload). Backend chỉ consume ảnh thuộc người gửi, trong cùng transaction với phiếu/tin nhắn. Create trả `Detail.attachments`, tin nhắn có `attachments` metadata từ backend. UUID gửi lại phải giữ cả danh sách ảnh. Tin nội bộ dùng target `SUPPORT_INTERNAL`; customer không thấy metadata và không tải được ảnh đó. Giao diện dùng AttachmentPicker/AttachmentLinks để tải ảnh có xác thực.
- Frontend có chỉnh ưu tiên và lý do, bộ lọc giữ URL, hạn phản hồi/nhãn UNKNOWN, đánh giá một lần sau giải quyết, báo cáo theo ngày và phân bố điểm. Create/reply/CSAT khôi phục alive ref khi StrictMode replay.
