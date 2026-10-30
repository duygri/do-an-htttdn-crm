# Brevo cho bản demo chưa có tên miền

## Thử nhận email ở local (PowerShell)

Tại thư mục gốc repo chạy:

```powershell
.\backend\run-brevo-local.ps1
```

Script dùng Java/Maven hiện có, giữ cấu hình DB của anh, chạy backend cổng `8082`, chỉ gửi tới email thử. Nếu chưa có `BREVO_API_KEY` trong terminal hiện tại, script hỏi key bằng ô nhập ẩn; không ghi vào file hay lịch sử lệnh. Không lấy key từ Render. Biến tạm được phục hồi khi backend dừng; key nhập qua prompt được xóa khỏi môi trường tiến trình. Không dùng `setx`, không bật lưu transcript terminal có bí mật.

Nếu terminal chưa có `JWT_SECRET`, script sinh khóa ngẫu nhiên 32 byte cho riêng phiên chạy local và không in/lưu khóa. Vì khóa thay đổi sau mỗi lần chạy, cần đăng nhập lại frontend; không đưa cơ chế này lên production. Nếu thiếu `DB_PASSWORD`, script hỏi ẩn mật khẩu PostgreSQL đang dùng trên máy (không phải mật khẩu Neon/Render); không tự đổi mật khẩu DB. Nếu đã có các biến bí mật trong terminal, giữ nguyên giá trị hiện tại. Mọi biến bí mật được script tạo/nhập sẽ được xóa khi thoát, kể cả backend khởi động lỗi. `-CheckOnly` chỉ kiểm tra cấu trúc cấu hình/công cụ, không xác nhận DB hoặc thông tin đăng nhập hợp lệ.

Frontend local đang cấu hình `VITE_API_BASE_URL=http://localhost:8082`; khởi động riêng bằng `npm run dev:user` trong `frontend`, mở `http://localhost:5173/quen-mat-khau`. Email thử phải thuộc Customer chưa khóa/xóa trong **database local**. Không tự tạo customer, sửa DB hoặc migration bằng script. Chờ ít nhất 60 giây giữa các lần thử và xem log backend (`status`, `reason`).

**Chỉ kiểm tra nhận email:** `PUBLIC_ORIGIN` vẫn là URL HTTPS Render để phù hợp validation hiện tại; token được lưu ở DB local nên không dùng liên kết đó để đổi mật khẩu. Không đổi sang HTTP localhost vì mail service hiện yêu cầu HTTPS. Không tắt bảo vệ này chỉ để thử email. Không cấu hình tự hủy PayOS; schema chỉ được validate, không tự tạo/sửa.

Kiểm tra cấu hình không chạy backend/không hỏi key:

```powershell
.\backend\run-brevo-local.ps1 -CheckOnly
```

Nếu local cũng nhận `403 / PERMISSION_DENIED`, đây là thêm bằng chứng để Brevo kiểm tra quyền gửi; không coi thông báo chung trên form là xác nhận nhà cung cấp đã gửi thành công.

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

P0 gửi thêm email phiếu hỗ trợ, thanh toán thất bại/hết hạn, yêu cầu/từ chối hoàn tiền, khóa tài khoản, xác nhận đổi/đặt lại mật khẩu và voucher khảo sát. Chỉ nội dung công khai được gửi; ghi chú nội bộ của phiếu không vào email; lý do khóa tài khoản không đưa vào email.

Tài liệu: https://developers.brevo.com/reference/send-transac-email

## Chẩn đoán lỗi gửi

Log chỉ có HTTP status và nhãn lý do cố định (`IP_NOT_AUTHORIZED`, `ACCOUNT_NOT_ACTIVATED_OR_SUSPENDED`, `API_KEY_REJECTED`, `SENDER_NOT_VERIFIED`, `PERMISSION_DENIED`, `PROVIDER_UNAUTHORIZED`, `EDGE_ACCESS_DENIED`...). Nhãn là phân loại từ phản hồi, không thay thế xác nhận của Brevo. Phản hồi không nhận diện được trả `UNKNOWN`/`NON_JSON_RESPONSE`; không log nội dung nguyên văn, địa chỉ, khóa, token hay URL reset. Chỉ đọc tối đa 16 KiB cộng một byte phát hiện quá giới hạn rồi hủy đọc, áp dụng timeout request hiện có. Không tự nới quyền, tắt bảo vệ IP hoặc đổi khóa khi gặp 403.
