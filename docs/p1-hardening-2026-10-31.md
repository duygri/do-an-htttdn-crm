# Bản vá P1 ngày 31/10/2026

## Phạm vi

Ba lỗi ưu tiên đã được duyệt: phân quyền API nội bộ, cập nhật sản phẩm cạnh tranh với trừ/hoàn kho và xử lý đơn PayOS chưa thanh toán. Không thay schema, bí mật cấu hình hoặc dữ liệu lịch sử.

## Hành vi sau sửa

- API Admin/Manager kiểm tra quyền trên route đã được Spring MVC phân giải. URL có matrix parameter hoặc context path không bỏ qua kiểm tra. Vẫn kiểm tra JWT, vai trò hiện tại, khóa/xóa tài khoản trong database. Actor audit chỉ tồn tại trong thời gian xử lý request.
- Cập nhật/ẩn/hiển thị/chỉnh kho khóa và làm mới bản ghi sản phẩm bằng cùng cơ chế checkout/hoàn kho. Sửa thông tin sản phẩm không ghi lại lượng tồn cũ của form. Tạo sản phẩm giữ tồn ban đầu; thay đổi tồn có chủ ý dùng API stock riêng.
- PayOS chỉ được tiến sang xử lý/giao hàng khi backend đã ghi `PAID` và không còn cảnh báo đối soát. COD vẫn được giao trước khi xác nhận thu tiền. Đơn 0 đồng dùng trạng thái `PAID` mà checkout đã xác lập; không suy ra thanh toán từ tổng tiền bằng 0. Giữ quy tắc hủy và thao tác lặp hiện có.

## Kiểm chứng

- Phân quyền: test độc lập đã tái hiện bảy thất bại trước bản vá; sau sửa kiểm tra URL chuẩn, matrix parameter, context path, sai vai trò, tài khoản khóa/xóa/không tồn tại và dọn actor khi lỗi.
- Test tích hợp dùng MockMvc với cấu hình Spring thực tế, không chỉ cấu hình guard thủ công; xác nhận chặn trước controller/validation và giữ thao tác ẩn/khôi phục.
- PayOS: bộ test đơn hàng tái hiện mười thất bại trước bản vá và đạt 48 test sau sửa; gồm tiến trình giao hàng, cảnh báo, COD, đơn 0 đồng và hủy lặp.
- Tồn kho: năm test backend dùng H2 kiểm tra khóa thực tế giữa hai transaction, entity đã nạp cũ, dữ liệu tồn và audit; trước sửa đều thất bại, sau sửa đều đạt. Form frontend kiểm tra không gửi lại tồn cũ, chỉnh tồn có chủ ý, tạo mới và lỗi/thử lại.
- `mvn test -q`: 317 test tổng cộng, 307 đạt, 10 test PostgreSQL tùy chọn bỏ qua; không có lỗi. Chưa đối chiếu PostgreSQL thật.
- Vòng frontend đầy đủ đầu tiên: 305 đạt, một test focus cũ bị lỗi timing. Sửa duy nhất assertion để chờ effect focus bằng `waitFor`, giữ nguyên hành vi đánh giá. Vòng cuối `npm test -- --maxWorkers=1`: 306/306 test, 42/42 file đạt.
- `npm run build`: thành công. Còn cảnh báo directive `use client` từ dependency `lucide-react`, không chặn build; không nâng dependency ngoài phạm vi.
- `git diff --check` trên phạm vi bản vá và kiểm tra whitespace của các file mới: đạt. Review độc lập bản vá phân quyền/đơn hàng/tồn kho và nhánh lỗi/thử lại: không còn phát hiện cần sửa.
- Kiểm tra lại trên nhánh phát hành `codex/free-demo-deployment`: backend 333 test tổng cộng, 323 đạt, 10 test PostgreSQL tùy chọn bỏ qua; không có lỗi.
- Frontend nhánh phát hành: 320/320 test trong 42 file đạt; `npm run build` thành công.

Thông tin sản phẩm và điều chỉnh tồn có chủ ý dùng hai request riêng. Nếu bước tồn kho lỗi sau khi lưu thông tin, form thông báo rõ phần đã lưu, giữ nội dung và mở khóa để thử lại; không thông báo thành công toàn bộ.

`PUT /api/manager/products/{id}` chỉ sửa thông tin sản phẩm, không áp dụng trường `stock` trong payload cũ. Chỉnh tồn dùng `PATCH /api/manager/products/{id}/stock` với `{ "quantity": 12 }`. `POST /api/manager/products` vẫn nhận tồn ban đầu như trước.

Không thử khai thác quyền, sửa sản phẩm hoặc xử lý thanh toán trên production. Theo yêu cầu phát hành tiếp theo của chủ repository, commit bản vá lên `codex/free-demo-deployment` và triển khai chỉ dịch vụ `anhlonshop`. Frontend và backend được phát hành cùng một Docker image; không chạy migration hoặc thay đổi bí mật.

## Kiểm tra trình duyệt khi phát hành

Smoke test sau rollout phát hiện policy `default-src 'none'` từ bản hardening trước đó áp dụng cả trang HTML và chặn JavaScript của SPA. Tạm rollback về bản hoạt động trước để khôi phục giao diện, rồi sửa filter: API vẫn dùng CSP nghiêm ngặt; trang/tài nguyên web dùng policy tương thích với React, giữ chống nhúng, object, base và form không cùng nguồn. Các header `nosniff`, HSTS và quyền thiết bị không đổi.

Test hồi quy mới tái hiện sáu lỗi trước sửa, đạt đủ mười trường hợp sau sửa (trang User/Admin/Manager, asset, API, context path và matrix parameter). Kiểm tra lại validation, rate limit, phân quyền và route deployment cùng bản vá; frontend không đổi sau bộ 320 test/build đã đạt. Review độc lập bản sửa CSP không có phát hiện cần sửa.
