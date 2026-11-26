# Điểm thưởng, hỗ trợ và tồn kho màu–size

## Triển khai có kiểm soát

Đây là thay đổi local, chưa phát hành. Không chạy backend mới vào database chưa có migration.
Giữ một bản sao lưu và kiểm tra phục hồi trên database thử riêng trước khi nâng cấp.
Không tự chuyển tồn kho cũ, gán biến thể cho đơn cũ hoặc gửi lại email lịch sử.

Sau các migration hiện có (bao gồm kho, vận chuyển, đổi trả và hoàn tiền), áp dụng theo thứ tự:

1. `database/migration_loyalty_idempotency.sql`
2. `database/migration_email_outbox.sql`
3. `database/migration_support_experience.sql`
4. `database/migration_attachment_metadata.sql`
5. `database/migration_product_variants.sql`

Schema chuẩn cũng chứa các thay đổi này để tạo database demo mới. Không chạy seed hoặc schema chuẩn thay cho migration trên database có dữ liệu.

## Cấu hình dịch vụ ngoài

Đặt khóa trong biến môi trường của máy chạy/Render, không đưa vào Git hay nội dung chat.
Không cần thay `application.yml` đang dùng để cung cấp các biến này.

Brevo dùng `MAIL_PROVIDER=brevo`, `BREVO_ENABLED=true`, `BREVO_API_KEY`,
`BREVO_SENDER_EMAIL`, `BREVO_SENDER_NAME`, `PUBLIC_ORIGIN` là origin HTTPS.
Giữ `BREVO_TEST_MODE=true` và `BREVO_TEST_RECIPIENT` là địa chỉ thử do chủ hệ thống kiểm soát.
Chỉ tắt chế độ thử sau khi xác minh quyền gửi và người gửi. Không thay người nhận thực bằng email thử.

Hàng đợi phân biệt nhà cung cấp chấp nhận với email đến hộp thư; không có webhook xác nhận giao thư trong đợt này.
Email không xác định kết quả cần đối chiếu log nhà cung cấp trước khi Admin quyết định gửi lại.
Khóa chống trùng của Brevo có thời hạn, không thay thế kiểm soát gửi lặp của ứng dụng.
Email khôi phục mật khẩu chạy riêng, không lưu link/token vào bảng hàng đợi.

Cloudinary dùng tài khoản riêng và các biến `CLOUDINARY_CLOUD_NAME`,
`CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`.
Ảnh hỗ trợ là dữ liệu riêng: backend kiểm tra quyền trước khi tải nội dung;
không dùng URL ảnh public của catalog để lưu ảnh khiếu nại.
Không cấu hình Cloudinary vẫn dùng được phiếu hỗ trợ và đổi trả bằng văn bản.
API cấu hình ảnh chỉ trả cờ bật/tắt cho người đã đăng nhập, không trả tên cloud hoặc khóa.
Giới hạn mỗi ảnh 5 MB, mỗi lần gửi 5 ảnh; JPEG/PNG được giải mã kiểm tra, WebP được kiểm tra cấu trúc và kích thước.
Ảnh tạm có bản ghi giữ chỗ trước khi gọi nhà cung cấp để có thể dọn sau 24 giờ, kể cả khi tiến trình bị dừng.
Ảnh Cloudinary dùng loại `authenticated`; nội dung được tải qua backend kiểm tra quyền và không lưu cache.
Worker email và dọn ảnh chạy khi backend hoạt động; dịch vụ bị ngủ/dừng sẽ xử lý trễ sau khi khởi động lại.

## Các API bổ sung

| API | Quyền/chức năng |
| --- | --- |
| `GET /api/admin/emails`, `POST /api/admin/emails/{id}/retry` | Admin tra cứu và yêu cầu thử lại; UNKNOWN cần xác nhận nguy cơ gửi trùng |
| `GET /api/attachments/config`, `POST /api/attachments`, `GET /api/attachments/{id}/content` | User đã đăng nhập xem khả dụng, upload và tải ảnh có quyền; Manager dùng các đường dẫn tương ứng dưới `/api/manager/attachments` |
| `POST /api/support-tickets/{id}/csat` | Khách sở hữu phiếu đã giải quyết gửi đánh giá một lần |
| `GET /api/manager/support-tickets/report` | Manager xem số liệu theo ngày giải quyết Việt Nam |
| `GET /api/manager/products/{id}` | Manager tải sản phẩm cùng biến thể và phiên bản tồn |
| `POST /api/manager/products/{id}/variants/allocate` | Phân bổ tồn chung; yêu cầu số tồn và phiên bản đang xem |
| `PATCH /api/manager/products/{id}/variants/stock` | Điều chỉnh nhiều biến thể trong một transaction |
| `GET /api/manager/products/{id}/variants/legacy-lines`, `POST /api/manager/products/{id}/variants/reconcile` | Tra cứu và đối soát dòng đơn cũ có nhật ký |

API danh sách phiếu hỗ trợ nhận thêm `priority`, `unanswered`, `overdue`, `sort=due|updated`;
cập nhật ưu tiên phải có lý do và phiên bản. DTO kho/giỏ/đơn bổ sung biến thể mà vẫn giữ các trường cũ.

## Quy tắc điểm thưởng

Mọi ghi điểm dùng cùng khóa tài khoản khách hàng. Đổi điểm và phát voucher là một transaction.
Client mới gửi `Idempotency-Key` dạng UUID vào `POST /api/account/points/redeem`.
Giữ nguyên khóa khi thử lại sau lỗi; tạo khóa mới sau khi đã nhận kết quả thành công.
Lặp cùng khóa trả đúng voucher/kết quả đã chốt, kể cả khi cấu hình đổi điểm đã thay đổi.
API cũ không gửi khóa vẫn tương thích nhưng không thể chống trùng giữa hai yêu cầu độc lập.
Không thay đổi cách xử lý điểm khi đổi trả/hoàn tiền.

## Quy tắc hỗ trợ

Phiếu mới mặc định Bình thường (24 giờ); Khẩn 4 giờ và Thấp 48 giờ.
Hạn tính từ lúc tạo theo giờ liên tục; đổi ưu tiên không khởi động lại đồng hồ.
Chỉ phản hồi công khai đầu tiên của Manager được tính, ghi chú nội bộ không tính.
Phiếu cũ không có mốc được gắn chưa đủ dữ liệu, không dựng lại lịch sử.
Khách được chấm 1–5 sao một lần sau khi giải quyết; phiếu chưa chấm không phải 0 sao.

## Chuyển tồn kho

Chọn từng sản phẩm, phân bổ số tồn vào các tổ hợp màu–size; tổng phải bằng tồn chung tại thời điểm lưu.
Nếu có giao dịch làm đổi tồn, tải lại và phân bổ lại. Không chia đều hoặc cộng tồn khởi tạo lần nữa.
Sau chuyển đổi, điều chỉnh và kiểm kê theo biến thể; tổng sản phẩm là tổng tồn các biến thể.
Danh sách sắp hết hàng và thông báo ngưỡng thấp kiểm tra từng màu–size, không chỉ tổng tồn sản phẩm.
Thẻ kho giữ số trước/sau là **tổng tồn sản phẩm**, có thêm mã biến thể, màu và size để tra cứu; đây không phải số dư riêng của biến thể.
Đơn và hóa đơn cũ giữ tên màu/size đã lưu. Hoàn kho khớp đúng tổ hợp;
trường hợp thiếu dữ liệu phải đối soát có lý do và nhật ký trước khi tiếp tục.
Phiếu nhập nhà cung cấp vẫn chỉ ghi chi phí, không tự cộng kho.

## Kiểm tra trước khi mở tính năng

- Dùng tài khoản thử riêng cho cả ba vai trò; xác minh không truy cập chéo ảnh hoặc đánh giá hộ khách.
- Thử hai yêu cầu đổi điểm/đặt hàng đồng thời; số điểm và tồn không được âm hoặc trừ hai lần do retry.
- Thử rollback nghiệp vụ: không còn voucher hoặc email chờ gửi của transaction đã rollback.
- Kiểm tra phiếu cũ/mới, phản hồi nội bộ/công khai và đánh giá lặp.
- Kiểm tra phân bổ, giỏ cũ, hủy và nhập hàng trả nhiều dòng cùng sản phẩm.
- Kiểm tra Admin email với lỗi quyền, giới hạn, timeout và khởi động lại worker bằng provider giả lập.
- Chạy test backend/frontend, build, whitespace; ghi riêng các ca PostgreSQL hoặc provider thật chưa kiểm chứng.

Tham chiếu giao thức: [Brevo gửi thư giao dịch](https://developers.brevo.com/reference/send-transac-email),
[Cloudinary kiểm soát truy cập](https://cloudinary.com/documentation/control_access_to_media).
