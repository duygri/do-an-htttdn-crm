# Vá ba lỗi P1

**Mục tiêu:** bảo vệ API nội bộ theo handler thực tế, tránh ghi đè tồn kho khi cập nhật sản phẩm và chặn giao đơn PayOS chưa thanh toán.

**Phạm vi đã duyệt:** ba phát hiện P1 trong review ngày 31/10/2026. Không mở rộng các P2; không đổi schema, cấu hình bí mật hoặc dữ liệu thật.

## Thực hiện

- [x] Phân quyền: test URL chuẩn/matrix/context path, khách chưa đăng nhập/sai quyền/tài khoản khóa hoặc xóa; dùng route đã được Spring phân giải thay vì prefix URI thô. Giữ actor cho audit và các API đăng nhập công khai.
- [x] Tồn kho: test hai transaction cạnh tranh; thống nhất khóa bản ghi sản phẩm với checkout/hoàn kho. Tách cập nhật mô tả khỏi số tồn cũ trên form, giữ thao tác chỉnh kho rõ ràng.
- [x] PayOS: test đơn PENDING thực tế, PAID/chưa trả, COD và cảnh báo đối soát; chặn tiến trình giao hàng nếu chưa đủ bằng chứng thanh toán. Giữ hủy an toàn hiện có.
- [x] Review độc lập diff; chạy backend, frontend, build, whitespace; đồng bộ đúng bản vá về workspace gốc, giữ nguyên application.yml.

## Kiểm tra xung đột

| Phần | Ranh giới | Kết luận |
|---|---|---|
| Phân quyền | Guard/WebConfig/test bảo mật | Không sửa dịch vụ sản phẩm hoặc đơn |
| Tồn kho | Product service/test và UI sản phẩm nếu cần | Khóa tương thích checkout; không đổi quy tắc đơn |
| PayOS | Order service/test | Không thay provider hoặc kho |
| Tích hợp | Bộ test đầy đủ | Chạy tuần tự để tránh quá tải máy |

Đợt vá ban đầu được kiểm chứng local. Theo yêu cầu tiếp theo của chủ repository, phát hành riêng bản vá lên nhánh `codex/free-demo-deployment`, deploy chỉ `anhlonshop` và xác nhận commit Live. Không đưa `application.yml` local vào commit, không chạy migration, không tạo PR.
