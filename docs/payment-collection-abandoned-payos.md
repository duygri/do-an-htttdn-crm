# Xác nhận thu COD và tự hủy PayOS

## Triển khai an toàn

1. Sao lưu database và kiểm thử trên bản sao trước khi cập nhật.
2. Với database hiện có, áp dụng `database/migration_payment_collection_abandoned_payos.sql` nếu chưa có các cột xác nhận COD, đối soát PayOS và chỉ mục đơn PayOS bỏ dở. Migration không thay đổi dữ liệu thanh toán lịch sử.
3. Áp dụng `database/migration_cashflow_voucher_customer_limit.sql` nếu cần cập nhật mốc thu PayOS và giới hạn voucher cá nhân.
4. Khởi động backend với `DB_PASSWORD` và `JWT_SECRET` hợp lệ. Hibernate `validate` yêu cầu schema đã cập nhật.
5. Tự hủy mặc định tắt. Với cấu hình demo/nghiệm thu, bật tự hủy sau khi đã kiểm tra schema và dữ liệu. Spring Boot không tự đọc file `.env`; đặt biến trong môi trường của tiến trình backend, rồi khởi động lại. Chỉ giải phóng tồn kho và lượt voucher sau khi xác minh và hủy đơn thành công. Đơn chưa xác minh được vẫn giữ tồn kho/voucher và cần được đối chiếu. Tham khảo [mẫu biến môi trường demo/nghiệm thu](env-demo.md):

```powershell
$env:PAYOS_AUTO_CANCEL_ENABLED='true'
$env:PAYOS_AUTO_CANCEL_GRACE_HOURS='24'
$env:DEV_PASSWORD_RESET_ENABLED='false'
$env:APP_PAYOS_AUTO_CANCEL_MS='300000'
```

`APP_PAYOS_AUTO_CANCEL_MS` mặc định 300000 ms; scheduler xét hủy sau thời gian chờ 24 giờ tính từ hạn mới nhất của đơn/link/lần thử thanh toán. `PAYOS_AUTO_CANCEL_GRACE_HOURS` nhận số nguyên từ 1 đến 720 giờ. Muốn tắt, đặt cờ thành `false` rồi khởi động lại. Không bật ở database thật chỉ để thử nghiệm.

## Xác nhận thu COD

Không suy đoán đã thu từ trạng thái giao hàng. Chỉ Manager xác nhận toàn bộ giá trị đơn COD đã giao/hoàn thành, kèm ghi chú. Hệ thống lưu số tiền, thời điểm và người xác nhận rồi cập nhật thanh toán `PAID`. Gửi lại không ghi nhận thu thêm. Chưa hỗ trợ thu một phần/công nợ; chưa có thao tác sửa/xóa chứng cứ thu.

## Tự hủy và đối soát PayOS

- QR vẫn có hạn 15 phút. Mốc bắt đầu xét tự hủy là 24 giờ sau hạn mới nhất của đơn/link/lần thử thanh toán; tạo lại QR hợp lệ kéo dài mốc tương ứng.
- Chỉ xét đơn PayOS `PENDING`/`PENDING_PAYMENT` chưa thanh toán. Không tự hủy COD hoặc đơn đã xác nhận.
- Scheduler chạy mỗi 5 phút, đọc ID từng lô và xử lý transaction riêng từng đơn. Một đơn lỗi không ngăn đơn sau; nhiều tiến trình dựa vào khóa đơn và trạng thái hiện tại để chống lặp.
- Kiểm tra tất cả link đã biết, kể cả link cũ; link còn hiệu lực phải hủy rồi kiểm tra lại. Timeout kết nối 5 giây, đọc 10 giây. Không có chứng cứ rõ ràng thì không giải phóng kho/voucher.
- Đã trả đủ: cập nhật thanh toán, không tự hủy. Thiếu thông tin, tiền một phần/thừa, nhiều link đã trả, dữ liệu lệch hoặc lỗi mạng: giữ đơn và cảnh báo **Thanh toán cần kiểm tra**.
- Manager lọc cảnh báo trong Đơn hàng và bấm **Đối chiếu lại PayOS** tại chi tiết. Không có nút bỏ cảnh báo/cưỡng ép hủy. Chỉ đối chiếu thành công mới gỡ cảnh báo.
- Webhook có thể đến muộn sau khi link vừa được hủy. Ghi nhận trạng thái thanh toán và cảnh báo phù hợp; không trừ lại kho đã giải phóng.

## API Manager

- `PATCH /api/manager/orders/{id}/cod-collection`: ghi nhận Manager đã xác nhận thu COD; body gồm `note`.
- `POST /api/manager/orders/{id}/payment/reconcile`: đối chiếu lại trạng thái PayOS; không tự hủy đơn.
- Danh sách và tổng hợp đơn hỗ trợ `paymentReview=true|false` để lọc cảnh báo.

Chỉ Manager đang hoạt động được truy cập; hệ thống lấy người thao tác từ phiên đăng nhập, không nhận ID nhân viên do trình duyệt gửi lên.

## Giới hạn và dữ liệu cũ

Quy trình này không tự động chuyển tiền. Đơn đã thanh toán bị hủy vẫn giữ bằng chứng thanh toán; cần liên hệ cửa hàng để được hỗ trợ về thanh toán. Database hiện có thể còn bảng/cột ghi nhận hoàn tiền từ migration cũ; ứng dụng hiện tại không đọc hoặc ghi các trường này và không xóa dữ liệu lịch sử.
