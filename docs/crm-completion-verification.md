# Ma trận kiểm tra đợt hoàn thiện CRM

Ngày kiểm tra: 24/11/2026. Thực hiện local trên `feat/admin-refresh`.
Không gửi email/ảnh tới nhà cung cấp thật, không thử thanh toán thật, không nâng cấp database ứng dụng.

## Kết quả cuối

- Backend: `mvn test -q` (giới hạn heap/context cache cho máy local) **exit 0**; **462 test: 452 qua, 10 bỏ qua**, không failure/error. Các test bỏ qua là nhóm PostgreSQL opt-in riêng của dự án.
- Frontend: `npm test` **exit 0**, **409/409 test qua**, 63 file test.
- `npm run build`: **exit 0**. Còn cảnh báo directive của thư viện Lucide và PageImpl của Spring, không phải lỗi test/build.
- `git diff --check`: qua. Không commit, push, tạo PR hoặc thay cấu hình bí mật.
- Fingerprint `application.yml` trước/sau giống nhau; giữ nguyên thay đổi local có sẵn.

Vitest dùng tối đa hai thread worker và khoảng chờ phù hợp cho portal lazy-load để tránh timeout do tải máy, không bỏ assertion hoặc tắt test.

| Nhóm | Ca kiểm tra chính | Bằng chứng |
| --- | --- | --- |
| Điểm | Hai yêu cầu đồng thời, cùng khóa trả cùng voucher, thiếu điểm, rollback cả voucher và điểm | `LoyaltyConcurrencyTest`, `LoyaltyServiceTest` |
| Điểm User | Giữ khóa sau mất kết nối/remount, cập nhật số dư, giữ lỗi và chống bấm lặp | `CustomerPoints.test.jsx` |
| Email | Transaction rollback, hai worker tranh claim, lease hết hạn, chờ tăng dần và dừng ở 5 lần, UNKNOWN, quyền Admin | `EmailOutboxIntegrationTest` |
| Brevo | Gửi đúng người nhận sandbox, 2xx/403/429/5xx, timeout/kết nối, payload không lộ khóa | `TransactionalMailHttpTest`, provider HTTP giả lập |
| Hỗ trợ | 4/24/48 giờ, đổi ưu tiên có lý do, phản hồi nội bộ/công khai, dữ liệu cũ không đoán SLA, CSAT một lần | `SupportTicketIntegrationTest`, `SupportTickets.test.jsx` |
| Báo cáo hỗ trợ | Mẫu số phiếu giải quyết, loại khách xóa/đổi quyền, giữ khách khóa, mốc ngày Việt Nam | `SupportTicketIntegrationTest` |
| Ảnh | JPEG/PNG thực, WebP cấu trúc, tệp quá lớn/sai định dạng, tối đa 5, giữ chỗ và dọn ảnh tạm | `AttachmentValidationTest`, `AttachmentServiceTest` |
| Quyền ảnh | Không đăng nhập, đổi quyền/khóa, truy cập chéo, ảnh nội bộ, proxy không trả URL nhà cung cấp | `AttachmentControllerTest`, `AttachmentServiceTest` |
| Giao diện ảnh | Đúng API client Manager/User, FormData, upload lỗi giữ nội dung, chưa cấu hình vẫn gửi văn bản | `AttachmentPicker.test.jsx` |
| Biến thể | Phân bổ đúng tổng, phiên bản tồn cũ, tổ hợp trùng, điều chỉnh batch atomic, hoàn kho đúng một lần, đối soát legacy | `ProductVariantIntegrationTest` |
| Kho | Gộp dòng, khóa ID tăng dần, thiếu sản phẩm rollback, tồn thấp theo từng biến thể | `StockServiceTest`, `ProductVariantIntegrationTest` |
| User mua hàng | Chọn tổ hợp có tồn, bỏ qua biến thể ẩn/hết hàng, giữ giỏ chưa chọn, kiểm tra lại tại checkout | `product-variants.test.js`, bộ test giỏ/checkout và đơn hàng |
| PostgreSQL | Schema chuẩn, nâng cấp từ schema cũ, schema validate và các test dịch vụ mục tiêu | Hai database QA riêng, PostgreSQL 18.4 |
| Responsive | Kho biến thể, xác nhận email và CSAT ở 1440/768/390 px, sáng/tối | 12 lượt kiểm tra Playwright, không tràn ngang, không lỗi JavaScript |

## PostgreSQL QA

- Tạo schema chuẩn trên database QA mới: thành công.
- Áp dụng riêng năm migration trên một database legacy QA khác: thành công; số bảng tăng **46 → 48**.
- Đối chiếu fingerprint tên cột/kiểu dữ liệu/nullability giữa schema mới và bản nâng cấp: giống nhau.
- Chạy lại các test mục tiêu với `ddl-auto=validate`: **34 test qua**; điểm thưởng, biến thể và hỗ trợ chạy trên PostgreSQL; outbox chạy trên H2/provider giả lập.
- Các ca của `ProductVariantIntegrationTest` và `SupportTicketIntegrationTest` dùng transaction rollback. Test điểm đồng thời dùng dữ liệu tổng hợp riêng, không đọc dữ liệu khách thật.
- Không coi đây là xác nhận Brevo/Cloudinary đã được cấu hình hoặc giao thư/upload ngoài thực tế thành công.

## Điều kiện bật ngoài local

1. Sao lưu, kiểm tra migration trên database thử và áp dụng đúng thứ tự trong tài liệu triển khai.
2. Cấu hình Brevo và Cloudinary qua biến môi trường; xác minh người gửi/quyền API, giữ sandbox trước.
3. Phân bổ từng sản phẩm từ tồn thực tế; không dùng seed để thay tồn của cửa hàng.
4. Khởi động lại backend sau migration. Chỉ phát hành sau khi đối chiếu đúng commit và cấu hình môi trường.

Chi tiết: [Hướng dẫn triển khai](crm-completion-rollout.md).


## Bản ghép phát hành và hộp chọn sản phẩm — 26/11/2026

- Nhánh kiểm tra: `codex/free-demo-deployment`; gom phần CRM hoàn tất và hộp chọn màu/size trước mua.
- `npm test`: 64 file, 432 test qua. `npm run build` với `VITE_SAME_ORIGIN=true`: qua.
- Backend: 481 test, không lỗi/thất bại, 10 test tùy môi trường bỏ qua (471 chạy thành công); dùng giới hạn cache context 8 và heap 1 GB. `mvn package -DskipTests`: qua.
- Playwright với API giả: 1440×1000 và 390×844, sáng/tối; không tràn ngang, Tab/Shift+Tab, Escape, click ngoài, trả focus/scroll. Mở lại tải size mới và bỏ lựa chọn cũ; không gọi API ghi dữ liệu.
- Bản jar đóng gói frontend và đủ tám migration: khởi động bằng `deploy/application.yml` vào database PostgreSQL 18.4 thử riêng từ schema cũ. Hibernate validate và `/healthz` qua. Root, `/san-pham`, hai trang login trả HTML; `/api/not-found` trả 404 JSON.
- Sau lần đầu, chèn dữ liệu giả: hai biến thể cùng sản phẩm trong một phiếu kiểm kê và một email FAILED hết lượt thử. Khởi động lần hai thành công; 48 bảng, hai dòng kiểm kê, hai biến thể và email FAILED/5 được giữ; index cũ theo sản phẩm không tái tạo. Không gửi thư hoặc chạy SQL trên database đang sử dụng.
- Sửa bộ lọc request để không đọc trước multipart của hai endpoint đính kèm; vẫn giữ rate limit và giới hạn servlet 5 MB/file, 6 MB/request. Test hồi quy qua.
- Không chạy Docker image vì máy chưa có Docker daemon; đã kiểm tra jar với frontend/migration đóng gói tương ứng. Render build/runtime thực tế cần chủ dự án kiểm tra sau Manual Deploy.
- Không gửi email, upload Cloudinary, thanh toán thật, thay đổi sản phẩm thật hoặc deploy trong bước kiểm tra này.
