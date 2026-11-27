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
