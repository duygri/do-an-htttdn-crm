# Brevo cho bản demo chưa có tên miền

Dùng HTTPS API, không dùng SMTP (Render Free chặn các cổng SMTP thông dụng). Không tự đồng bộ danh sách khách sang Contacts hoặc gửi marketing hàng loạt. Chỉ email giao dịch: khôi phục mật khẩu, sự kiện đơn hàng, phiếu hỗ trợ, hoàn tiền, khóa tài khoản, xác nhận đổi/đặt lại mật khẩu và voucher khảo sát.

## Cấu hình trên Render/anhlonshop

1. Chủ tài khoản tự xác minh địa chỉ người gửi trong Brevo, hoàn tất duyệt tài khoản nếu được yêu cầu.
2. Tự tạo và nhập `BREVO_API_KEY` vào Environment. Không gửi khóa vào chat, Git, log hoặc frontend.
3. Đặt:

```text
MAIL_PROVIDER=brevo
BREVO_ENABLED=true
BREVO_SENDER_EMAIL=sender@example.com
BREVO_SENDER_NAME=ANH LON SHOP
BREVO_TEST_MODE=true
BREVO_TEST_RECIPIENT=recipient@example.com
PUBLIC_ORIGIN=https://anhlonshop.onrender.com
DEV_PASSWORD_RESET_ENABLED=false
```

Transport chỉ hỗ trợ Brevo; các biến `RESEND_*` không còn quyết định gửi; không tự xóa khóa Resend. Provider không hợp lệ hoặc cấu hình thiếu thì không gửi, không tự fallback sang nhà cung cấp khác.

## Bật cho mọi user

Email khôi phục giữ nguyên luồng AuthService đồng bộ và liên kết chứa token một lần. Payload có cả `textContent` và `htmlContent`; liên kết website trong HTML được escape và gắn `disable-tracking="true"` để Brevo không viết lại thành link theo dõi. Không chia sẻ liên kết khôi phục cho hỗ trợ. `BREVO_SENDER_EMAIL` phải là địa chỉ email thuần, không dùng dạng `Tên <email>`.

Sau khi gửi thử thành công, đặt `BREVO_TEST_MODE=false` và triển khai lại. Email luôn tới đúng customer của nghiệp vụ, không chuyển về địa chỉ thử, không gửi lại lịch sử. Cần thử thêm một hộp thư khác được chủ hộp thư đồng ý trước khi khẳng định hoạt động với người nhận khác.

Brevo có thể thay địa chỉ gửi Gmail bằng miền của Brevo theo chính sách giao hàng; tên miền riêng được xác thực vẫn là lựa chọn lâu dài. Gói miễn phí hiện có hạn mức 300 email/ngày; cần kiểm tra hạn mức thực tế trong tài khoản. Không tự mua gói hoặc nâng cấp.

Giữ nguyên luồng AuthService phát hành token: email khôi phục gửi đồng bộ, token ngẫu nhiên một lần, hạn dùng/cooldown/thu hồi refresh token như trước. Token và URL khôi phục không được đưa vào outbox. Email thông báo đổi mật khẩu chỉ có nội dung thông báo, không có token.

## Outbox bền vững

Migration riêng `database/migration_email_outbox.sql` chỉ thêm bảng `email_outbox` và index. Không chạy tự động và không sửa `schema.sql`. Cần người vận hành review và áp dụng migration trước khi khởi động phiên bản mới với Hibernate validate. Không backfill hoặc tự gửi lịch sử.

NotificationService ghi thông báo và outbox trong cùng transaction nghiệp vụ; enqueue bắt buộc có transaction. Rollback xóa cả hai. Lỗi DB khi enqueue làm rollback nghiệp vụ để không âm thầm mất email. Không còn event AFTER_COMMIT hoặc hàng đợi RAM. Sandbox kiểm tra người nhận độc lập cấu hình gửi: địa chỉ ngoài allowlist bị bỏ qua có chủ ý, không đổi người nhận; cấu hình gửi thiếu (kể cả allowlist chưa cấu hình) tạo FAILED/CONFIG_UNAVAILABLE, cần retry thủ công sau khi sửa. `BREVO_TEST_RECIPIENT` hỗ trợ danh sách phân cách dấu phẩy, so khớp nguyên địa chỉ không phân biệt hoa thường. Kiểm tra lại sandbox ngay trước HTTP để email tồn đọng không vượt allowlist mới.

Worker mặc định bật ở runtime; `EMAIL_OUTBOX_WORKER_ENABLED=false` tắt polling. `EMAIL_OUTBOX_POLL_MS` mặc định 5000. Toàn bộ Spring test tắt worker và Brevo qua test properties; test HTTP chỉ tới loopback hoặc transport giả, không dùng khóa thật. Worker claim từng bản ghi bằng compare-and-set trong transaction ngắn riêng, tăng attempts, cấp claim token và lease 60 giây. Chỉ worker thắng claim được gửi. HTTP connect timeout 5 giây/request timeout 10 giây; không giữ transaction/row lock khi gọi HTTP. Hoàn tất chỉ được cập nhật bằng claim token hiện hành còn lease.

Trạng thái: PENDING chờ gửi, PROCESSING đang giữ lease, ACCEPTED provider chấp nhận request (không khẳng định delivered), FAILED lỗi chắc chắn/cấu hình/quyền gửi hoặc hết lượt, UNKNOWN kết quả không rõ. Kết nối chắc chắn chưa thiết lập, HTTP 429 và 5xx được retry tự động với backoff 30/60/120/240 giây, tối đa 5 lần gửi tổng cộng, kể cả retry thủ công. 401/403 và lỗi 4xx khác không tự retry. Read timeout, IO không rõ, ngắt luồng và lease PROCESSING hết hạn chuyển UNKNOWN, tuyệt đối không tự gửi lại. Crash sau provider nhận nhưng trước lưu kết quả cũng trở thành UNKNOWN. Lease hết hạn không phải bằng chứng provider chưa nhận.

`GET /api/admin/emails?status=FAILED&page=0` và `POST /api/admin/emails/{id}/retry` được AdminAccessGuard bảo vệ role ADMIN. DTO phân trang 20 dòng chỉ có ID, trạng thái, attempts, thời gian, reason cố định và recipientHint chỉ hiện ký tự đầu phần tên và domain. Không trả body/subject/địa chỉ gốc/event key/claim token/API key. Retry chỉ cho FAILED/UNKNOWN dưới 5 lượt; UNKNOWN bắt buộc body `{"acknowledgeUnknown":true}` sau khi người vận hành kiểm tra Brevo và chấp nhận rủi ro gửi trùng. ACCEPTED/PENDING/PROCESSING không được retry. Retry ghi audit EMAIL_RETRY/EMAIL_OUTBOX gồm trạng thái, số lượt và xác nhận, không có nội dung/recipient. Không cung cấp API tùy ý gửi mail hoặc sửa người nhận.

Brevo request dùng `POST https://api.brevo.com/v3/smtp/email`, header `api-key`, payload `sender/to/subject/textContent/htmlContent`, `headers.IdempotencyKey`. Khóa provider là UUID ổn định suy từ event key, tái sử dụng khi retry. [API reference](https://developers.brevo.com/reference/send-transac-email) mô tả header này; [changelog 2021-11-10](https://developers.brevo.com/changelog/2021/11/10) ghi TTL 15 phút; [tài liệu batch hiện tại](https://developers.brevo.com/docs/heterogenous-versions-batch-emails) ghi 30 phút cho batch. Không dựa vào TTL để quyết định retry UNKNOWN và không bảo đảm exactly-once qua mạng hoặc ngoài cửa sổ provider. Provider duplicate_parameter cũng là lỗi 4xx cần kiểm tra thủ công, không được coi delivered.

Outbox chứa địa chỉ và nội dung thông báo cần thiết để gửi lại, không có khóa/token reset. Chỉ DB/backend được truy cập dữ liệu này; admin UI không đọc body. Giới hạn quyền DB, sao lưu và thời gian lưu theo chính sách vận hành; không có endpoint xuất body. Mã gửi không giữ/log/hiển thị response body của provider, exception cause hay khóa. Không thay đổi cấu hình runtime trong application.yml.

P0 gửi thêm email phiếu hỗ trợ, thanh toán thất bại/hết hạn, yêu cầu/từ chối hoàn tiền, khóa tài khoản, xác nhận đổi/đặt lại mật khẩu và voucher khảo sát. Chỉ nội dung công khai được gửi; ghi chú nội bộ của phiếu không vào email; lý do khóa tài khoản không đưa vào email.


Tài liệu: https://developers.brevo.com/reference/send-transac-email

## Chẩn đoán lỗi gửi

Xem trạng thái outbox và nhãn cố định: CONFIG_UNAVAILABLE, CONFIG_OR_RECIPIENT_BLOCKED, PERMISSION_DENIED, HTTP_429/HTTP_5xx, CONNECTION_FAILED, AMBIGUOUS_IO, EXPIRED_LEASE, INTERRUPTED. Không log hay lưu response body của Brevo. 403 cần chủ tài khoản kiểm tra quyền, sender và hạn chế IP với Brevo, không tự đổi khóa/nới bảo vệ. Không coi thông báo trên form hoặc ACCEPTED là xác nhận thư đến hộp thư.
