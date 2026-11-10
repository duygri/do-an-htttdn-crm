# Báo cáo doanh thu và giới hạn voucher theo khách

## Triển khai

Sao lưu và kiểm tra trên môi trường thử trước khi triển khai. Với database hiện có:

1. Áp dụng `database/migration_refunds_abandoned_payos.sql` của phần COD/đối soát trước, nếu chưa chạy; migration này vẫn cung cấp `orders.paid_at`, các cột thu COD và cảnh báo đối soát.
2. Áp dụng `database/migration_cashflow_voucher_customer_limit.sql`.
3. Áp dụng `database/migration_supplier_receipts_invoices.sql` cho nhà cung cấp, phiếu nhập hàng và hóa đơn bán hàng.
4. Build và khởi động lại backend, sau đó build frontend cùng phiên bản.

Database mới dùng schema chuẩn `database/schema.sql`. Không cần chạy lại toàn bộ schema lên database đang có dữ liệu. Backend không tự chạy các file migration này. Các bảng/cột hoàn tiền cũ được giữ lại để bảo toàn dữ liệu nhưng backend mới không đọc/ghi.

Không điền ngày thanh toán cho dữ liệu cũ, không sửa lượt voucher lịch sử. Không tự chạy migration hoặc thao tác thanh toán thật để kiểm thử.

## Báo cáo doanh thu

Manager mở `/manager/reports?tab=revenue`. Ngày bắt đầu, ngày kết thúc và nhóm ngày/tháng/quý/năm được giữ trong URL. Chỉ tab đang mở cập nhật nền mỗi 5 giây; khi lỗi giữ số liệu gần nhất và cho thử lại.

API chỉ dành cho MANAGER: `GET /api/manager/reports/revenue?from=...&to=...&groupBy=month`. Alias `/cashflow` vẫn được giữ để tương thích.

- `from` bao gồm đầu kỳ, `to` không bao gồm cuối kỳ; phải gửi cả hai hoặc bỏ cả hai.
- Bỏ cả hai tham số: mặc định từ đầu năm dương lịch hiện tại tới đầu năm sau, nhóm theo `month`. Tối đa 5 năm.
- `groupBy` nhận `day`, `month`, `quarter`, `year`. Khoảng không phát sinh được bổ sung bằng 0.
- Doanh thu là **tiền đã thu thực tế**, không phải tổng giá trị đơn. PayOS chỉ tính khi `payment_status='PAID'`, dùng tổng tiền đơn và mốc `paid_at` lưu lần đầu backend xác nhận. COD tính theo `cod_collected_amount`/`cod_collected_at` do Manager xác nhận; đơn 0 đồng không tính một lượt thu.
- Đơn đã thu rồi hủy vẫn giữ khoản thu. Báo cáo không trừ hoàn tiền và không đọc sổ hoàn tiền cũ.
- Chi phí nhập hàng lấy từ `supplier_receipts.total_amount` theo `invoice_date` trong kỳ. Chênh lệch = doanh thu đã thu − chi phí nhập, có thể âm khi kỳ chỉ phát sinh chi phí.

Đây là thời điểm **hệ thống xác nhận**, không phải khẳng định ngày giao dịch ngân hàng. PayOS cũ đã PAID nhưng thiếu mốc nằm riêng trong số liệu thiếu mốc toàn thời gian, không lấy `updated_at` hoặc lần đối chiếu hiện tại thay thế.

Top 10 sản phẩm xếp theo số lượng thuộc đơn đã thu tiền trong kỳ và chưa hủy. Giá trị dòng sản phẩm là trước giảm giá cấp đơn và chưa trừ hàng đổi trả. Đây không phải doanh thu ròng sản phẩm.

API trả `collected`, `purchaseCost`, `difference`, `collectedOrders`, `series` (mỗi kỳ có `collected`, `purchaseCost`, `difference`, `collectedOrders`), `topProducts`, `unknownMilestone`, phạm vi thời gian và `updatedAt`. Tổng hợp tiền thu/chi riêng, không nhân tiền theo số dòng sản phẩm.

Dashboard Manager dùng `GET /api/manager/reports/summary` trả `totalRevenue` (tiền đã thu thực tế) và `orders`; “Giá trị đơn chưa hủy” và chỉ số phân nhóm CRM vẫn là phép tính riêng.

## Giới hạn voucher cá nhân

- Voucher chung tạo mới mặc định một lượt mỗi khách; Manager có thể chọn số nguyên dương hoặc để trống để không giới hạn.
- API tạo mới thiếu `perCustomerLimit` mặc định 1; gửi `null` là không giới hạn. Voucher cũ giữ giá trị null, không tự áp giới hạn mới.
- Giảm giới hạn chỉ áp dụng lượt đặt mới, không sửa đơn hoặc lịch sử trước đó. Giao diện cảnh báo khi giảm giới hạn mã đã sử dụng.
- Lượt cá nhân là số bản ghi sử dụng của khách chưa được giải phóng (`released_at IS NULL`), gồm cả đơn chờ thanh toán.
- Xem trước và tạo đơn đều kiểm tra giới hạn. Tạo đơn khóa voucher tới hết transaction để hai đơn không cùng vượt lượt cuối.
- Lỗi tạo đơn rollback lượt; hủy đơn giải phóng đúng một lần theo cơ chế hiện có. Đổi trả/hoàn tiền không tự giải phóng lượt.
- Mã có giới hạn yêu cầu đăng nhập khi xem trước. Mã khảo sát luôn riêng đúng chủ và một lượt, không bị nới bởi cấu hình mã chung.

Ví vẫn hiện mã chung đang hiệu lực/còn tổng lượt cửa hàng khi khách đã hết lượt cá nhân, nhưng ghi “Bạn đã dùng hết lượt” và khóa nút chọn. Điều này khác với hết tổng lượt cửa hàng. Backend kiểm tra lại khi đặt hàng dù ví vừa được tải.

DTO ví bổ sung `perCustomerLimit`, `customerUses`, `customerRemaining`, `selectable`, `unavailableReason`. Lượt sử dụng được tính theo nhóm cho cả trang ví, không trả lịch sử người khác. DTO xem trước có giới hạn, lượt đang tính và lượt còn lại; DTO quản lý có cấu hình giới hạn.

## Kiểm thử

Đợt triển khai này: backend 189 test đạt, 10 test PostgreSQL tùy chọn bỏ qua; frontend 279 test đạt; build frontend và kiểm tra whitespace đạt. PostgreSQL chưa được đối chiếu vì không có cấu hình môi trường thử riêng; migration chưa chạy vào database vận hành.

Test hồi quy bao gồm ranh giới ngày Việt Nam, thu/hoàn khác kỳ, ròng âm, dữ liệu thiếu mốc, COD chưa thu, nhiều dòng sản phẩm, phân quyền báo cáo; giới hạn 1/nhiều/không giới hạn, tranh chấp lượt cuối, hủy giải phóng lượt, lỗi tạo đơn rollback, giảm giới hạn và ví hết lượt cá nhân.
