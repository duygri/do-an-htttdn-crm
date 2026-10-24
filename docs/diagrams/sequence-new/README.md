# Bộ sơ đồ sequence mới

Bộ sơ đồ này được chia theo ba vai trò của hệ thống CRM. Mỗi thư mục chứa
cả mã nguồn PlantUML (`.puml`) và ảnh xuất bản (`.png`) tương ứng, để dễ
đọc, chỉnh sửa và đối chiếu.

## Cấu trúc

| Thư mục | Số sơ đồ | Phạm vi |
| --- | ---: | --- |
| `admin/` | 11 | Quản trị tài khoản, sản phẩm, phản hồi, khảo sát, đơn hàng và báo cáo |
| `customer/` | 8 | Đăng ký, đăng nhập, hồ sơ, sản phẩm, giỏ hàng, thanh toán, khảo sát và đơn hàng |
| `manager/` | 15 | Xác thực, hồ sơ 360°, khách hàng, tồn kho, đơn hàng, đổi trả, CRM, khảo sát, hỗ trợ và báo cáo |

## Quy ước file

- File `.puml` là nguồn PlantUML có thể tiếp tục chỉnh sửa.
- File `.png` là bản xem nhanh đã xuất từ cùng một sơ đồ.
- Tên file giữa hai định dạng được giữ giống nhau.

## Kiểm tra nhanh

Tất cả file `.puml` trong bộ này đều có cặp khai báo `@startuml` và
`@enduml`. Đây là các tài liệu thiết kế, không thay đổi mã nguồn ứng dụng.
