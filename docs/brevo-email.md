# Brevo cho bản demo chưa có tên miền

Dùng HTTPS API, không dùng SMTP (Render Free chặn các cổng SMTP thông dụng). Không tự đồng bộ danh sách khách sang Contacts hoặc gửi marketing hàng loạt. Chỉ email khôi phục và sự kiện đơn hàng mới.

## Cấu hình trên Render/anhlonshop

1. Chủ tài khoản tự xác minh địa chỉ người gửi trong Brevo, hoàn tất duyệt tài khoản nếu được yêu cầu.
2. Tự tạo và nhập `BREVO_API_KEY` vào Environment. Không gửi khóa vào chat, Git, log hoặc frontend.
3. Đặt:

```text
MAIL_PROVIDER=brevo
BREVO_ENABLED=true
BREVO_SENDER_EMAIL=anhkiet240205@gmail.com
BREVO_SENDER_NAME=ANH LON SHOP
BREVO_TEST_MODE=true
BREVO_TEST_RECIPIENT=anhkiet240205@gmail.com
PUBLIC_ORIGIN=https://anhlonshop.onrender.com
DEV_PASSWORD_RESET_ENABLED=false
```

Các biến `RESEND_*` không còn quyết định gửi khi chọn Brevo; không tự xóa khóa Resend. Provider không hợp lệ hoặc cấu hình thiếu thì không gửi, không tự fallback sang nhà cung cấp khác.

## Bật cho mọi user

Sau khi gửi thử thành công, đặt `BREVO_TEST_MODE=false` và triển khai lại. Email luôn tới đúng customer của nghiệp vụ, không chuyển về địa chỉ thử, không gửi lại lịch sử. Cần thử thêm một hộp thư khác được chủ hộp thư đồng ý trước khi khẳng định hoạt động với người nhận khác.

Brevo có thể thay địa chỉ gửi Gmail bằng miền của Brevo theo chính sách giao hàng; tên miền riêng được xác thực vẫn là lựa chọn lâu dài. Gói miễn phí hiện có hạn mức 300 email/ngày; cần kiểm tra hạn mức thực tế trong tài khoản. Không tự mua gói hoặc nâng cấp.

Giữ quy tắc token ngẫu nhiên một lần, hạn sử dụng, cooldown theo customer và thu hồi refresh token của luồng Resend. Email đơn hàng chạy sau commit, hàng đợi giới hạn trong RAM, chưa có outbox bền vững. Lỗi mạng/provider không làm rollback đơn; chưa tự retry khi timeout vì không chắc nhà cung cấp đã nhận hay chưa. HTTP status được log nhưng không log key, nội dung email hay liên kết reset. Không cần migration.

Tài liệu: https://developers.brevo.com/reference/send-transac-email
