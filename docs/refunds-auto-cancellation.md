# Thu COD, tự hủy và đối soát PayOS

## Triển khai an toàn

1. Sao lưu database, kiểm thử trên bản sao và đối soát các đơn đang mở.
2. Chạy `database/migration_refunds_abandoned_payos.sql` trước khi khởi động backend mới nếu database chưa có các cột `orders.paid_at`, thu COD và cảnh báo đối soát. Migration cũng tạo bảng `order_refunds`; bảng này được giữ để bảo toàn dữ liệu nhưng backend hiện tại không đọc/ghi.
3. Khởi động backend với `DB_PASSWORD`, `JWT_SECRET` hợp lệ. Hibernate `validate` yêu cầu schema đã cập nhật.
4. Tự hủy **mặc định tắt**. Chỉ bật sau kiểm thử/đối soát, qua biến môi trường rồi khởi động lại backend:

```powershell
$env:PAYOS_AUTO_CANCEL_ENABLED='true'
$env:PAYOS_AUTO_CANCEL_GRACE_HOURS='24'
```

Không cần sửa `application.yml`. Muốn tắt, đặt cờ thành `false` rồi khởi động lại. Không bật ở database thật chỉ để thử nghiệm. Không áp dụng script vá kho hoặc suy đoán tiền đã thu cho đơn cũ.

## Sổ hoàn tiền đã được gỡ

- Giao diện **Hoàn tiền**, API `/api/manager/refunds` và các thao tác tạo/xử lý khoản hoàn đã bị gỡ. Gọi lại các đường dẫn cũ trả `404`.
- Dữ liệu hoàn tiền lịch sử vẫn nằm trong database và không bị xóa. Báo cáo doanh thu mới không trừ số hoàn tiền; chỉ tính tiền đã thu thực tế và chi phí nhập hàng.
- Hủy đơn PayOS đã trả tiền không còn tự tạo khoản chờ hoàn. Đơn giữ `PAID`; thông báo cho khách hướng dẫn liên hệ cửa hàng để được hỗ trợ.
- Việc chuyển tiền hoàn (nếu có) thực hiện ngoài hệ thống và không được ghi nhận trong ứng dụng.

### Xác nhận thu COD

Không suy đoán đã thu từ trạng thái giao hàng. Chỉ Manager xác nhận toàn bộ giá trị đơn COD đã giao/hoàn thành, kèm ghi chú. Hệ thống lưu số tiền, thời điểm, người xác nhận rồi cập nhật thanh toán `PAID`. Gửi lại không ghi nhận thu thêm. Chưa hỗ trợ thu một phần/công nợ; chưa có thao tác sửa/xóa chứng cứ thu.

API: `PATCH /api/manager/orders/{id}/cod-collection` với body `{"note":"..."}`. Chỉ MANAGER đang hoạt động được gọi; không truyền ID nhân viên từ trình duyệt.

## Tự hủy và đối chiếu PayOS

- QR vẫn có hạn 15 phút. Mốc tự hủy là **24 giờ sau hạn mới nhất** của đơn/lần thử thanh toán, không phải sau ngày đặt đơn. Tạo lại QR hợp lệ kéo dài mốc tương ứng.
- Chỉ xét đơn PayOS `PENDING`/`PENDING_PAYMENT` chưa thanh toán. Không tự hủy COD hoặc đơn đã xác nhận.
- Scheduler chạy mỗi 5 phút, đọc ID từng lô và xử lý transaction riêng từng đơn. Một đơn lỗi không ngăn đơn sau; nhiều tiến trình dựa vào khóa đơn và trạng thái hiện tại để chống lặp.
- Kiểm tra tất cả link đã biết, kể cả link cũ; link còn hiệu lực phải hủy rồi kiểm tra lại. Timeout kết nối 5 giây, đọc 10 giây. Không có chứng cứ rõ ràng thì không giải phóng kho/voucher.
- Đã trả đủ: cập nhật thanh toán, không tự hủy. Thiếu thông tin, tiền một phần/thừa, nhiều link đã trả, dữ liệu lệch hoặc lỗi mạng: giữ đơn và cảnh báo **Thanh toán cần kiểm tra**.
- Manager lọc cảnh báo trong Đơn hàng và bấm **Đối chiếu lại PayOS** tại chi tiết. Không có nút bỏ cảnh báo/cưỡng ép hủy. Chỉ đối chiếu thành công mới gỡ cảnh báo; tiền một phần/thừa cần đối soát thực tế bên ngoài, không tự coi là hoàn tất.
- Webhook có thể đến muộn sau khi link vừa được hủy. Giữ chứng cứ đã thu, không trừ lại kho đã giải phóng và thông báo khách liên hệ cửa hàng hỗ trợ.

## API Manager

- `PATCH /api/manager/orders/{id}/cod-collection`: `note` bắt buộc 1–1.000 ký tự.
- `POST /api/manager/orders/{id}/payment/reconcile`: không cần body, không tự hủy đơn.
- Danh sách/tổng hợp đơn nhận thêm `paymentReview=true|false`. Lọc trước phân trang.
- Các đường dẫn hoàn tiền cũ `GET/POST /api/manager/refunds...` không còn tồn tại.

Chỉ MANAGER đang hoạt động được truy cập; không truyền ID nhân viên từ trình duyệt để xác định người thao tác. Nhật ký lưu thao tác COD/đối soát, không đưa ghi chú tự do vào audit.

## Giới hạn

Không nhập kho đổi trả, không chuyển tiền thật, không xử lý hồi tố dữ liệu cũ. Migration được cung cấp để người triển khai chạy; không tự áp dụng vào database đang sử dụng. Khi khôi phục bản backend cũ, không xóa bảng/cột tài chính đã có dữ liệu.
