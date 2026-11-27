# Hạng thành viên và ưu đãi tự động

Đồng dưới 2.000.000đ (0%), Bạc từ 2.000.000đ (3%), Vàng từ 5.000.000đ (5%), Kim cương từ 10.000.000đ (10%). Tính toàn thời gian bằng tổng `total_amount` của đơn COMPLETED, chưa trừ hoàn tiền; đơn hoàn thành có đổi trả vẫn tính. Nhóm vòng đời CRM không thay đổi.

Giảm hạng trên tổng giá sản phẩm đang bán (đã gồm giá sale), làm tròn xuống đồng, không trần. Voucher kiểm tra đơn tối thiểu và tính giảm trên tiền sau giảm hạng. Backend báo giá và kiểm tra lại khi đặt; COD/PayOS dùng số tiền sau cả hai khoản giảm. Đơn đang đặt chưa đóng góp để lên hạng.

## API

- GET `/api/account/membership`: hạng của CUSTOMER đăng nhập, tỷ lệ, giá trị tích lũy, mốc kế tiếp và số còn thiếu.
- POST `/api/orders/preview`: `{items:[{productId,quantity,size,color,variantId?}],voucherCode?}`; trả `subtotal`, `membership`, `memberDiscountAmount`, `afterMemberAmount`, `voucher`, `discountAmount` và `totalAmount`. Chỉ xem trước, không giữ tồn/lượt voucher hoặc tạo thanh toán.
- Danh sách/hồ sơ Manager thêm `membership`; dùng cùng quy tắc và số tổng hợp hiện có, không thêm truy vấn mỗi khách.
- DTO đơn/hóa đơn thêm `memberTier`, `memberDiscountPercent`, `memberDiscountAmount`. `discountAmount` vẫn chỉ là giảm voucher. Các API điểm cũ yêu cầu đúng quyền như trước rồi trả 410 `POINTS_RETIRED`; không tự chuyển điểm thành tiền.

## Dữ liệu và triển khai

`database/migration_membership.sql` chỉ thêm ba cột snapshot trên orders, không thêm bảng. Đơn cũ giữ tiền, hạng trống và giảm hạng bằng 0. Không thay lịch sử điểm, voucher đã cấp, tồn kho hay thanh toán cũ. Các khóa cấu hình loyalty cũ được giữ trong database nhưng không được hiển thị/cập nhật qua màn hình cấu hình.

Local: sao lưu và áp dụng migration trên database phù hợp trước khi chạy backend mới; không chạy schema chuẩn thay migration. Nhánh deploy đóng gói migration sau các migration CRM hiện tại, chạy trước Hibernate validate khi chủ dự án Manual Deploy dịch vụ anhlonshop. Không tự chạy SQL lên database đang sử dụng, không gửi email hoặc thanh toán thật để thử.

## Kết quả kiểm tra — 27/11/2026

- Nhánh phát hành: `codex/free-demo-deployment`. `npm test`: 65 file, 430 test qua. `npm run build` với `VITE_SAME_ORIGIN=true`: qua.
- Backend full suite: 482 test, 0 lỗi/thất bại, 10 test tùy môi trường bỏ qua (472 chạy thành công), dùng giới hạn cache context 8 và heap 1 GB. `mvn package -DskipTests`: qua.
- Sáu test tích hợp hạng/checkout chạy lại trên PostgreSQL 18.4 riêng với `ddl-auto=validate`: qua, dữ liệu thử rollback. Bao gồm sale → giảm hạng → voucher cho COD/PayOS giả lập; đơn hoàn thành có đổi trả, nhiều dòng không nhân tổng; voucher cũ đổi điểm, điều kiện tối thiểu sau giảm hạng và đơn 0đ.
- Playwright giả lập API: 1440 và 390px, sáng/tối; badge hạng, giảm hạng riêng và voucher cho tổng đúng 81.000đ từ tạm tính 100.000đ; không tràn ngang/lỗi JavaScript và không gọi API tạo đơn. Sửa tương phản thông báo voucher trong dark mode.
- Jar đóng gói frontend và chín migration khởi động bằng cấu hình deploy trên PostgreSQL thử từ schema phiên bản cũ. Khởi động lại thành công; vẫn 48 bảng, đơn cũ 2.000.000đ giữ tier NULL/giảm hạng 0; số dư điểm 500 và voucher DIEM cũ active/0 lượt dùng được giữ. Hai dòng kiểm kê biến thể và outbox sentinel cũng không đổi.
- `/healthz`, root, checkout và hai trang login hoạt động; API không tồn tại trả 404 JSON, membership chưa đăng nhập trả 401 JSON.
- Không gửi email, chuyển tiền, chạy migration lên database đang dùng hoặc deploy. Máy chưa có Docker daemon; đã kiểm tra jar với frontend/migration đóng gói tương ứng, còn Render image/runtime cần xác nhận sau Manual Deploy.
