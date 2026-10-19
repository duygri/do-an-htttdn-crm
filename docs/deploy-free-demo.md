# Deploy demo miễn phí: Render + Neon

## Trạng thái và phạm vi

Cấu hình trong repo chuẩn bị một website HTTPS có cả User, Admin và Manager. Chưa có website công khai cho tới khi chủ tài khoản tạo dịch vụ và triển khai. Không dùng database local, không chạy seed chứa tài khoản mẫu, không dùng PayOS thật. Chỉ phù hợp đồ án/demo, không cam kết chạy liên tục.

## 1. Chọn đúng phiên bản code

Các file Dockerfile, render.yaml, deploy/application.yml và thay đổi Java/React phải có trên cùng commit GitHub được chọn để deploy. Không deploy main cũ nếu chưa có các file này. Việc push/merge phải được anh cho phép riêng; không cần cấp quyền cho toàn bộ repository không liên quan.

Docker chỉ copy mã Java, frontend và cấu hình deploy an toàn. File backend/src/main/resources/application.yml và frontend/.env.local bị loại khỏi image/context; không được đưa file cấu hình máy cá nhân lên dịch vụ.

## 2. Tạo database demo mới trên Neon Free

1. Đăng nhập https://console.neon.tech và tạo project riêng, ví dụ `anh-lon-crm-demo`. Chọn Free, vùng gần vùng Render nếu có.
2. Mở SQL Editor của database mới. Xác nhận đúng database demo rỗng, rồi chạy nội dung `database/schema.sql` của cùng phiên bản code. Không chạy đồng thời các migration cũ lên schema mới đã đầy đủ.
3. Không chạy `database/seed_data.sql` vì có tài khoản/mật khẩu minh họa. Sau khi có Manager, tạo danh mục và sản phẩm giả qua giao diện. Dùng ảnh HTTPS bền vững được phép sử dụng, không dùng localhost hoặc file trên máy cá nhân.
4. Trong Connect lấy hostname, database, username, password. Dùng kết nối trực tiếp cho schema, có thể dùng hostname pooled cho ứng dụng.

DB_URL phải là JDBC, ví dụ `jdbc:postgresql://<hostname>/<database>?sslmode=require`; không dán nguyên chuỗi `postgresql://user:password@...`. Username/password đặt thành hai biến riêng. Không gửi thông tin kết nối hoặc mật khẩu vào chat, issue hay PR.

## 3. Tạo Render Free Web Service

Đăng nhập https://dashboard.render.com bằng tài khoản của anh, kết nối đúng repo. Có thể dùng New → Blueprint với `render.yaml`, hoặc New → Web Service và chọn:

- Runtime: Docker; Dockerfile: `./Dockerfile`; build context: gốc repository.
- Plan: **Free**, không chọn trả phí nếu Free không khả dụng.
- Branch/commit: phiên bản đã chuẩn bị ở bước 1; tắt tự deploy để tránh thay đổi ngoài ý muốn.
- Health check: `/healthz` (chỉ trả trạng thái ứng dụng, không kiểm tra kết nối database mỗi lượt gọi).
- Tên dịch vụ gợi ý `anh-lon-crm-demo`; địa chỉ thực tế do Render cấp có thể khác.

Đặt các biến trong Environment của Render:

| Biến | Giá trị |
| --- | --- |
| DB_URL | JDBC Neon có SSL như bước 2 |
| DB_USERNAME | Username Neon |
| DB_PASSWORD | Mật khẩu Neon, nhập riêng trong dashboard |
| JWT_SECRET | Khóa ngẫu nhiên riêng tối thiểu 32 byte; Blueprint có thể sinh tự động |
| PUBLIC_ORIGIN | Chính xác URL HTTPS Render cấp, không có dấu `/` cuối |
| BOOTSTRAP_ADMIN_ENABLED | `true` chỉ cho lần khởi tạo database mới |
| BOOTSTRAP_ADMIN_EMAIL | Email riêng của anh cho tài khoản admin |
| BOOTSTRAP_ADMIN_PASSWORD | Mật khẩu riêng 16 ký tự trở lên, tối đa 72 byte UTF-8 |

Render cấp `PORT` tự động; không cần dùng 5173/5174/5175 trên website. Nếu URL thật khác tên dự kiến, sửa PUBLIC_ORIGIN và redeploy. Không để giá trị mẫu có dấu `<...>` trong môi trường.

Khởi tạo admin chỉ hoạt động khi bật rõ ràng và database chưa có ADMIN; không đổi mật khẩu hoặc quyền tài khoản hiện có. Sau lần deploy đầu thành công, **xóa BOOTSTRAP_ADMIN_PASSWORD, BOOTSTRAP_ADMIN_EMAIL và đặt BOOTSTRAP_ADMIN_ENABLED=false**, rồi redeploy. Không chạy hai lần deploy khởi tạo cùng lúc. Đăng nhập Admin để tạo MANAGER với mật khẩu riêng; không có mật khẩu Manager mặc định.

## 4. Địa chỉ và kiểm tra sau deploy

- User: `https://<dia-chi-render>/`
- Admin: `/admin/login`
- Manager: `/manager/login`
- Kiểm tra hoạt động: `/healthz`

Kiểm tra đăng nhập/refresh/đăng xuất của ba vai trò trong cùng trình duyệt, bảo đảm không dùng lẫn phiên. Mở trực tiếp và refresh `/san-pham`, `/san-pham/{id}`, `/don-hang`, `/tai-khoan`, trang Admin/Manager. API không tồn tại và asset thiếu phải trả lỗi, không trả HTML trang chủ.

Tạo customer và sản phẩm giả, thử giỏ → COD → xử lý đơn → hồ sơ/báo cáo; không tạo giao dịch thanh toán thật. PayOS bị vô hiệu do cấu hình deploy để trống khóa, API tạo QR sẽ báo chưa cấu hình. Tự hủy PayOS và khôi phục mật khẩu development bị tắt.

Kiểm tra màn hình 390px/768px/1440px, sáng/tối, các liên kết sang portal, không còn request localhost, cookie refresh có Secure/HttpOnly và đúng path riêng từng vai trò. Redeploy rồi xác minh dữ liệu demo còn trên Neon.

## 5. Giới hạn và vận hành

- Render Free ngủ khi không có lưu lượng theo chính sách gói. Lần mở đầu có thể chậm hoặc timeout; chờ rồi thử lại, không tự coi lỗi mạng là mất phiên.
- Scheduler nhắc việc không chạy khi server ngủ; không cam kết thông báo đúng phút. Polling trang Manager đang mở tiêu thụ tài nguyên; đóng tab khi không dùng, không tạo công cụ ping để né giới hạn miễn phí.
- Neon có hạn mức compute/storage/network; theo dõi dashboard. Không tự nâng gói hoặc thêm thông tin thanh toán để giải quyết hạn mức.
- Ổ đĩa Render không bền vững: không lưu dữ liệu/ảnh tải lên ở đó. Chức năng sản phẩm hiện dùng URL ảnh; không bổ sung upload trong đợt này.
- Trước khi đổi schema, export database demo bằng pg_dump qua SSL và giữ bản sao riêng, không commit dump. Schema mới không phải bản backup. Chọn lại commit trước trong Render khi rollback ứng dụng; không tự chạy ngược migration.
- Đổi JWT_SECRET làm phiên cũ mất hiệu lực. Không ghi bí mật vào log hoặc chia sẻ ảnh phần Environment.

## Kiểm tra local

Chạy `mvn test -q` trong backend và `npm test`, `npm run build` trong frontend. Khi Docker Desktop đã chạy, tại gốc repo dùng `docker build -t anh-lon-crm-demo .` để kiểm tra image; truyền biến môi trường bằng công cụ riêng, không ghi bí mật vào lệnh chia sẻ. Chưa kết luận deploy thành công chỉ từ việc build local.

Tài liệu nền tảng: https://render.com/docs/free, https://render.com/docs/blueprint-spec, https://neon.com/docs/introduction/plans, https://neon.com/docs/connect/connect-from-any-app.
