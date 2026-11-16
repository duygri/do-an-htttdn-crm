# Quy trình sao lưu và phục hồi PostgreSQL

Tài liệu này mô tả phương án sao lưu/phục hồi cho cơ sở dữ liệu PostgreSQL của Nova Shop, đáp ứng yêu cầu II-04 và II-05. Cấu hình mẫu theo hướng dẫn chạy ứng dụng trong repo: `localhost:5432`, database `htttdn`, user `postgres`. Hãy đổi các giá trị này nếu môi trường cài đặt khác.

## Phạm vi và yêu cầu

- Sao lưu toàn bộ một database bằng `pg_dump` định dạng custom. Bản dump gồm schema, dữ liệu, sequence và các đối tượng thuộc database tại thời điểm sao lưu; bao gồm những migration đã được áp dụng vào database đó.
- `pg_dump` không sao lưu các role cấp PostgreSQL cluster. Khi phục hồi sang một máy chủ mới, tạo trước role ứng dụng và cấp quyền theo cấu hình môi trường.
- Cài PostgreSQL Command Line Tools (`pg_dump`, `pg_restore`, `createdb`, `psql`) tương thích với phiên bản PostgreSQL của máy chủ và thêm thư mục `bin` vào `PATH`.
- Không ghi mật khẩu vào lệnh, Git, log hoặc tài liệu. Có thể để PostgreSQL hỏi mật khẩu trong phiên tương tác; với tác vụ tự động, dùng `%APPDATA%\postgresql\pgpass.conf` và giới hạn quyền đọc file cho tài khoản Windows chạy tác vụ.

## Sao lưu thủ công

Ví dụ PowerShell dưới đây tạo file ngoài repository để tránh đưa dữ liệu khách hàng lên GitHub. Thư mục sao lưu nên nằm trên ổ đĩa được giới hạn quyền truy cập; nếu có thể, đồng bộ thêm một bản tới thiết bị hoặc kho lưu trữ riêng.

```powershell
$env:PGHOST = 'localhost'
$env:PGPORT = '5432'
$env:PGDATABASE = 'htttdn'
$env:PGUSER = 'postgres'

$backupRoot = 'D:\CRM-Backups\Daily'
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupFile = Join-Path $backupRoot "htttdn-$timestamp.dump"

pg_dump --format=custom --no-owner --no-acl --file="$backupFile" $env:PGDATABASE
if ($LASTEXITCODE -ne 0) { throw 'pg_dump thất bại; không sử dụng file sao lưu này.' }

Get-FileHash -Algorithm SHA256 -LiteralPath $backupFile
pg_restore --list "$backupFile" | Select-Object -First 20
if ($LASTEXITCODE -ne 0) { throw 'Không đọc được danh mục trong file dump.' }
```

Ghi nhận thời điểm, tên file, dung lượng và mã SHA-256 vào nhật ký vận hành. Kiểm tra lệnh `pg_dump` thành công và `pg_restore --list` đọc được danh mục trước khi xem bản sao lưu là hợp lệ. Tùy chọn `--no-owner --no-acl` giúp phục hồi độc lập với role/ACL của máy nguồn; các quyền ứng dụng phải được cấu hình lại theo môi trường đích.

### Lịch và thời hạn lưu đề xuất

- Tạo một bản sao lưu mỗi ngày vào khung giờ ít sử dụng (ví dụ 02:00), bằng Windows Task Scheduler chạy dưới tài khoản được cấp quyền truy cập PostgreSQL.
- Giữ 7 bản sao lưu hằng ngày gần nhất và 4 bản sao lưu hằng tuần gần nhất. Xóa bản cũ hơn sau khi đã xác nhận có đủ các bản trong thời hạn lưu.
- Tạo thêm một bản sao lưu ngay trước khi áp dụng migration, nâng cấp phần mềm hoặc thay đổi dữ liệu hàng loạt.
- Không lưu dump trong repo, thư mục public hoặc cùng vị trí duy nhất với database đang chạy. Giới hạn quyền đọc vì dump chứa dữ liệu khách hàng.

Đây là lịch lưu giữ đề xuất cho đồ án; lịch tác vụ và nơi lưu ngoài máy cần được cấu hình theo môi trường triển khai thực tế.

## Diễn tập phục hồi trên database riêng

Luôn phục hồi thử vào database mới, không dùng database đang chạy của ứng dụng. Lệnh dưới đây giả định `$backupFile` trỏ tới file dump cần kiểm tra và PostgreSQL đang chạy local.

```powershell
$restoreDb = 'htttdn_restore_check'

createdb --maintenance-db=postgres --template=template0 $restoreDb
if ($LASTEXITCODE -ne 0) { throw 'Không tạo được database kiểm tra; hãy xử lý tên database đã tồn tại trước.' }

pg_restore --exit-on-error --no-owner --no-acl --dbname=$restoreDb "$backupFile"
if ($LASTEXITCODE -ne 0) { throw 'Phục hồi thất bại; không dùng database kiểm tra này.' }

psql --dbname=$restoreDb --command='\dt'
psql --dbname=$restoreDb --command="SELECT 'customers' AS table_name, COUNT(*) AS row_count FROM customers UNION ALL SELECT 'products', COUNT(*) FROM products UNION ALL SELECT 'orders', COUNT(*) FROM orders;"
```

Đánh giá phục hồi đạt khi `pg_restore` kết thúc với mã 0, các bảng được liệt kê, các truy vấn kiểm tra chạy được và dữ liệu mẫu/định lượng phù hợp với bản sao lưu. Sau đó có thể khởi động ứng dụng trỏ tạm tới database kiểm tra để xác nhận các màn hình chính đọc dữ liệu được. Ghi ngày giờ, file dump, mã SHA-256, người thực hiện, kết quả kiểm tra và lỗi (nếu có) vào biên bản. Xóa database kiểm tra sau khi hoàn tất và xác nhận không còn cần đối chiếu:

```powershell
dropdb --maintenance-db=postgres $restoreDb
```

### Nhật ký diễn tập phục hồi

| Ngày thực hiện | File dump / SHA-256 | Kiểm tra | Kết quả |
| --- | --- | --- | --- |
| Chưa thực hiện | Chưa có | Cập nhật sau lần diễn tập đầu tiên | Chưa xác nhận phục hồi thành công |

Không ghi nhận một lần phục hồi thành công cho tới khi đã thực hiện các bước trên và cập nhật nhật ký bằng kết quả thực tế.

## Phục hồi khi cần vận hành lại hệ thống

1. Thông báo bảo trì và dừng backend để tránh ghi thêm dữ liệu trong lúc chuyển đổi.
2. Sao lưu database hiện tại trước khi thay đổi.
3. Tạo một database đích mới, rỗng; phục hồi file dump đã xác minh vào database đó theo các lệnh trong phần diễn tập.
4. Tạo/cấp quyền role ứng dụng trên database đích, cập nhật `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` trong cấu hình triển khai, rồi khởi động backend.
5. Kiểm tra đăng nhập và các luồng chính như xem khách hàng, sản phẩm, đơn hàng; chỉ chuyển người dùng sang database đã phục hồi sau khi kiểm tra đạt.

Không chạy `pg_restore --clean` trên database đang dùng như một bước thử nghiệm: thao tác đó có thể xóa các đối tượng hiện hữu. Giữ database nguồn và bản sao lưu hiện tại cho tới khi việc chuyển đổi được xác nhận.
