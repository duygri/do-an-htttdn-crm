# Biến môi trường cho demo/nghiệm thu

Spring Boot không tự đọc file `.env`. Các giá trị dưới đây phải được đặt trong môi trường của tiến trình backend trước khi khởi động. Có thể dùng cấu hình Run/Debug của IDE hoặc đặt trực tiếp trong PowerShell. Repository đã đưa `.env` và `.env.*` vào `.gitignore`; file này là tài liệu hướng dẫn, không phải file cấu hình được backend tự nạp.

| Biến môi trường | Giá trị demo/nghiệm thu | Mặc định code | Mục đích |
| --- | --- | --- | --- |
| `PAYOS_AUTO_CANCEL_ENABLED` | `true` | `false` | Bật tự hủy đơn PayOS chưa thanh toán. |
| `PAYOS_AUTO_CANCEL_GRACE_HOURS` | `24` | `24` | Chờ 24 giờ sau khi link PayOS hết hạn trước khi tự hủy. Số nguyên từ 1 đến 720 giờ; giá trị ngoài khoảng này khiến backend không khởi động. |
| `DEV_PASSWORD_RESET_ENABLED` | `false` | `false` | Giữ chức năng mã đặt lại mật khẩu tắt ở demo/nghiệm thu. |
| `APP_PAYOS_AUTO_CANCEL_MS` | `300000` | `300000` | Chạy chu kỳ kiểm tra tự hủy mỗi 5 phút cho thuộc tính `app.payos.auto-cancel-ms`. |

Ví dụ đặt biến cho backend trong PowerShell:

```powershell
$env:PAYOS_AUTO_CANCEL_ENABLED='true'
$env:PAYOS_AUTO_CANCEL_GRACE_HOURS='24'
$env:DEV_PASSWORD_RESET_ENABLED='false'
$env:APP_PAYOS_AUTO_CANCEL_MS='300000'
```

Các biến này chỉ có hiệu lực trong tiến trình được khởi động từ terminal đó. Khởi động lại backend sau khi thay đổi. Không đưa thông tin xác thực PayOS vào file mẫu hoặc repository. PayOS không có sandbox; COD là luồng smoke test mặc định. Chỉ thử PayOS thật sau khi người phụ trách xác nhận tài khoản, số tiền giới hạn và thời điểm thực hiện.

Cờ tự hủy mặc định `false` trong code. Bật cờ ở demo/nghiệm thu với thời gian chờ 24 giờ để đơn PayOS bỏ dở không giữ tồn kho vô thời hạn. Xem thêm [hướng dẫn hoàn tiền và tự hủy](refunds-auto-cancellation.md).
