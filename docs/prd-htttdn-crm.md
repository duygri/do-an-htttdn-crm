# Product Requirements Document (PRD)

## Hệ thống CRM và Quản lý bán hàng

| Thuộc tính | Nội dung |
| --- | --- |
| Môn học | Hệ thống thông tin doanh nghiệp (HTTTDN) |
| Phiên bản | 1.0 |
| Trạng thái | Draft for team review |
| Ngày | 14/09/2026 |
| Repository | [duygri/do-an-htttdn-crm](https://github.com/duygri/do-an-htttdn-crm) |
| Phạm vi | MVP trong khoảng 2–3 tuần |

## 1. Tóm tắt sản phẩm

### 1.1. Mục đích

Xây dựng hệ thống CRM kết hợp quản lý bán hàng giúp doanh nghiệp quản lý tập trung khách hàng, phản hồi, khảo sát, sản phẩm và đơn hàng. Hệ thống có hai khu vực:

- **Customer Portal**: đăng ký, đăng nhập, quản lý hồ sơ, xem sản phẩm, gửi feedback, trả lời khảo sát và đặt hàng.
- **Admin Portal**: quản lý khách hàng, feedback, khảo sát, sản phẩm, tồn kho, đơn hàng và báo cáo.

### 1.2. Vấn đề cần giải quyết

Dữ liệu khách hàng và bán hàng thường bị phân tán trong nhiều file hoặc công cụ. Doanh nghiệp khó theo dõi lịch sử khách hàng, đo lường mức độ hài lòng, kiểm soát đơn hàng và tổng hợp doanh thu. Sản phẩm tạo một luồng dữ liệu thống nhất từ tương tác của Customer đến hoạt động quản trị và phân tích.

## 2. Mục tiêu và tiêu chí thành công

### 2.1. Mục tiêu

1. Cho phép Customer thực hiện các luồng CRM và bán hàng cơ bản.
2. Cho phép Admin quản lý dữ liệu và nghiệp vụ tương ứng.
3. Duy trì mô hình dữ liệu quan hệ thống nhất cho bảy nhóm dữ liệu cốt lõi.
4. Thể hiện đầy đủ quy trình phân tích, thiết kế, triển khai, kiểm thử và báo cáo của một hệ thống thông tin doanh nghiệp.

### 2.2. Tiêu chí thành công

| Tiêu chí | Kết quả cần đạt |
| --- | --- |
| Phạm vi | Hoàn thành deliverable của 38 Issue đang mở |
| Tài liệu | Có phân tích doanh nghiệp, khảo sát, yêu cầu số hóa, ERD, data dictionary, chuẩn hóa và sequence diagram |
| Chức năng | Luồng Customer và Admin chính chạy được với dữ liệu mẫu |
| Dữ liệu | Bảy bảng cốt lõi có quan hệ, khóa và ràng buộc rõ ràng |
| Chất lượng | Có kiểm thử luồng thành công, dữ liệu không hợp lệ, phân quyền và lỗi phổ biến |
| Quy trình | Mọi thay đổi code đi qua branch và Pull Request có review |
| Trình bày | Có báo cáo, ảnh minh chứng, demo và hướng dẫn chạy |

## 3. Phạm vi sản phẩm

### 3.1. Trong phạm vi MVP

- Phân tích doanh nghiệp, khảo sát hệ thống hiện tại và yêu cầu số hóa.
- ERD, data dictionary và mô hình quan hệ chuẩn hóa 3NF.
- Sequence diagram cho các luồng Customer và Admin.
- Đăng ký, đăng nhập và xác thực người dùng.
- Quản lý hồ sơ và sở thích khách hàng.
- Feedback và rating sản phẩm.
- Tạo, phát hành, trả lời và thống kê khảo sát.
- Danh mục sản phẩm và tồn kho.
- Giỏ hàng, đặt hàng, lịch sử đơn hàng và trạng thái giao hàng.
- Thanh toán trực tuyến qua payOS, Return URL/cancelUrl và webhook.
- Báo cáo khách hàng, khảo sát và doanh thu.
- Kiểm thử, tài liệu kỹ thuật và demo.

### 3.2. Ngoài phạm vi MVP

- Thanh toán production với tiền thật, merchant go-live, refund production và đối soát thật.
- Tích hợp đơn vị vận chuyển thực tế.
- Social login, xác thực đa yếu tố và password reset qua email.
- Ứng dụng native cho Android hoặc iOS.
- Dự báo doanh thu bằng machine learning.
- Triển khai production, autoscaling và vận hành 24/7.

## 4. Người dùng và vai trò

| Vai trò | Mục đích | Quyền chính |
| --- | --- | --- |
| Customer | Mua hàng và tương tác với doanh nghiệp | Đăng ký, đăng nhập, sửa hồ sơ, xem sản phẩm, feedback, khảo sát, đặt hàng, xem đơn hàng của chính mình |
| Admin | Quản trị CRM và bán hàng | Quản lý khách hàng, feedback, khảo sát, sản phẩm, tồn kho, đơn hàng và báo cáo |
| Project team | Phát triển và kiểm tra | Làm Issue, tạo branch, mở PR, review và ghi nhận bằng chứng |

Customer chỉ được truy cập dữ liệu của chính mình. Backend phải kiểm tra quyền, không chỉ ẩn chức năng trên frontend.

## 5. Luồng nghiệp vụ chính

### 5.1. Customer

1. Đăng ký tài khoản và đăng nhập.
2. Xem hoặc cập nhật hồ sơ và sở thích.
3. Xem, tìm kiếm và lọc sản phẩm.
4. Thêm sản phẩm vào giỏ hàng và đặt hàng.
5. Xem lịch sử và trạng thái đơn hàng.
6. Gửi feedback/rating.
7. Xem khảo sát khả dụng và gửi câu trả lời.

### 5.2. Admin CRM

1. Đăng nhập Admin Portal.
2. Xem, tìm kiếm, sửa hoặc khóa tài khoản khách hàng.
3. Tiếp nhận và cập nhật trạng thái feedback.
4. Tạo câu hỏi, tạo khảo sát và phát hành đến Customer.
5. Xem biểu đồ và chỉ số từ câu trả lời khảo sát.
6. Lập báo cáo nhân khẩu học, độ tuổi và sở thích khách hàng.

### 5.3. Admin Sales

1. Tạo, sửa, xóa sản phẩm và cập nhật tồn kho.
2. Xem và xử lý đơn hàng.
3. Cập nhật trạng thái giao hàng.
4. Xem doanh thu theo thời gian, sản phẩm bán chạy và hiệu quả bán hàng.

## 6. Yêu cầu chức năng

| Mã | Nhóm | Yêu cầu | Tiêu chí nghiệm thu | Issue |
| --- | --- | --- | --- | --- |
| FR-01 | Account | Customer đăng ký tài khoản | Email không trùng, trường bắt buộc được kiểm tra, mật khẩu không lưu plaintext | #23 |
| FR-02 | Authentication | Customer đăng nhập, refresh phiên và đăng xuất | Access token ngắn hạn trả trong response body; refresh token lưu dạng hash và gửi bằng HttpOnly/Secure/SameSite cookie; rotation, reuse detection và logout được phân biệt | #24 |
| FR-03 | Profile | Customer xem và cập nhật hồ sơ/sở thích | Chỉ tài khoản hiện tại được cập nhật; dữ liệu được validate | #25 |
| FR-04 | Feedback | Customer gửi feedback và rating sau khi đã mua sản phẩm | Rating trong khoảng 1–5; backend xác nhận Customer có đơn hợp lệ chứa sản phẩm; mỗi Customer chỉ feedback một lần cho một sản phẩm | #26 |
| FR-05 | Survey | Customer xem và trả lời khảo sát | Chỉ khảo sát đã phát hành được trả lời; câu trả lời bắt buộc được kiểm tra | #27 |
| FR-06 | Catalog | Customer xem, tìm kiếm và lọc sản phẩm | Hiển thị đúng tên, giá, tồn kho và bộ lọc | #28 |
| FR-07 | Order/Payment | Customer quản lý giỏ hàng, tạo đơn và thanh toán qua payOS | Tồn kho được khóa trong transaction; đơn chuyển `PENDING_PAYMENT`; Return URL/cancelUrl chỉ hiển thị kết quả, webhook xác thực chữ ký và cập nhật trạng thái thanh toán idempotent | #21, #29 |
| FR-08 | Tracking | Customer xem lịch sử và trạng thái đơn | Không xem được đơn của Customer khác | #30 |
| FR-09 | Customer Admin | Admin quản lý tài khoản Customer | Có tìm kiếm, phân trang, sửa và khóa tài khoản | #31 |
| FR-10 | Feedback Admin | Admin xem và xử lý feedback | Có danh sách, chi tiết, cập nhật trạng thái và ghi nhận xử lý | #32 |
| FR-11 | Survey Builder | Admin tạo câu hỏi, khảo sát và phát hành | Khảo sát có thể lưu, publish và gửi đến Customer | #33 |
| FR-12 | Survey Analytics | Admin xem thống kê câu trả lời và mức hài lòng | Số liệu lấy từ response thực tế, có bảng hoặc biểu đồ | #34 |
| FR-13 | Customer Reporting | Admin xem báo cáo nhân khẩu học và sở thích | Tổng hợp đúng theo data dictionary | #35 |
| FR-14 | Product/Inventory | Admin quản lý sản phẩm, giá và tồn kho | Thay đổi hợp lệ được lưu; tồn kho không âm nếu nghiệp vụ không cho phép | #36 |
| FR-15 | Sales Orders | Admin phê duyệt và cập nhật đơn hàng | Trạng thái chuyển theo luồng hợp lệ | #37 |
| FR-16 | Revenue Analytics | Admin xem báo cáo doanh thu | Có thời gian, tổng doanh thu, sản phẩm bán chạy và bằng chứng dữ liệu | #38 |

## 7. Yêu cầu phân tích và thiết kế

| Giai đoạn | Deliverable | Issue |
| --- | --- | --- |
| Phân tích doanh nghiệp | Doanh nghiệp, cơ cấu, quy trình bán hàng và chăm sóc khách hàng | #1 |
| Khảo sát | Tối thiểu 15 câu hỏi và dữ liệu phản hồi | #2 |
| Yêu cầu số hóa | Phân tích bottleneck và yêu cầu kinh doanh | #3 |
| Mô hình dữ liệu | ERD của users, feedback, surveys, survey_responses, products, orders, order_items | #4 |
| Data dictionary | Kiểu dữ liệu, PK, FK, nullable và ý nghĩa trường | #5 |
| Chuẩn hóa | Mô hình 3NF và ràng buộc toàn vẹn | #6 |
| Admin sequence | PlantUML và PNG cho tám luồng Admin CRM/Sales | #7–#14 |
| Customer sequence | PlantUML và PNG cho tám luồng Customer CRM/Sales | #15–#22 |

## 8. Dữ liệu và quy tắc nghiệp vụ

### 8.1. Bảy nhóm dữ liệu cốt lõi

| Nhóm | Nội dung |
| --- | --- |
| `users` | Tài khoản, vai trò, trạng thái và thông tin nhận diện |
| `feedback` | Nội dung feedback, rating và thời điểm gửi |
| `surveys` | Thông tin khảo sát, trạng thái phát hành và thời gian áp dụng |
| `survey_responses` | Câu trả lời của Customer |
| `products` | Sản phẩm, giá, mô tả và tồn kho |
| `orders` | Customer, tổng tiền, trạng thái đơn hàng và trạng thái thanh toán |
| `order_items` | Sản phẩm, số lượng và đơn giá trong đơn hàng |
| `refresh_tokens` | Hash refresh token, user, token family, thời hạn và trạng thái revoke |
| `payment_events` | Sự kiện webhook, mã giao dịch và khóa idempotency của payment gateway |

### 8.2. Quy tắc chính

- Email tài khoản phải duy nhất.
- Mật khẩu phải được hash bằng BCrypt hoặc cơ chế tương đương.
- Access token nên có thời hạn ngắn và chỉ nằm trong memory của frontend. Refresh token phải lưu dưới dạng hash cùng `user_id`, `expires_at`, token family, trạng thái và `revoke_reason`; token được gửi bằng `HttpOnly`, `Secure`, `SameSite` cookie, không lưu trong localStorage hoặc truy cập được bởi JavaScript.
- Mỗi lần refresh hợp lệ phải revoke token cũ với lý do `ROTATED` và phát token mới trong cùng family. Token hết hạn hoặc bị revoke bởi logout/admin chỉ trả lỗi, không tự động revoke cả family.
- Nếu token đã ở trạng thái `ROTATED`/`USED` nhưng bị gửi lại, đó là reuse detection: revoke toàn bộ token family, ghi security event và trả `401 REFRESH_TOKEN_REUSE_DETECTED`.
- Logout bình thường chỉ revoke refresh token hiện tại với lý do `LOGOUT` và xóa cookie. Logout tất cả thiết bị, nếu có, là thao tác riêng để revoke toàn bộ family.
- Đổi mật khẩu, khóa tài khoản và security reset phải có khả năng revoke refresh token đang hoạt động.
- Customer chỉ truy cập dữ liệu thuộc tài khoản của mình.
- Rating nằm trong khoảng 1–5.
- Customer chỉ được feedback/rating sản phẩm khi có `order_items` tương ứng trong một đơn hàng hợp lệ. MVP xem đơn hàng có trạng thái `CONFIRMED`, `SHIPPED`, `DELIVERED` hoặc `COMPLETED` là đã mua; đơn `CANCELLED` không đủ điều kiện.
- Database phải có unique constraint trên cặp `(customer_id, product_id)` trong feedback. Sau khi kiểm tra quyền mua, backend insert trực tiếp; không dùng bước check-trước làm cơ chế chống race condition. Bắt lỗi constraint và trả `409 DUPLICATE_FEEDBACK`.
- Không tạo đơn với sản phẩm không tồn tại hoặc số lượng không hợp lệ.
- Tổng tiền lấy từ `order_items`, không tin giá trị do frontend gửi lên.
- Đơn hàng phải lưu `payment_method`, `payment_status`, `gateway_txn_ref`, `gateway_transaction_no`, `payment_response_code` và thời điểm thanh toán nếu có.
- `PENDING_PAYMENT` chưa được xem là thanh toán thành công. Chỉ webhook payOS hợp lệ mới được chuyển payment status sang `PAID` và order status sang `CONFIRMED`.
- Xử lý webhook phải idempotent: khóa bản ghi order/payment bằng `SELECT ... FOR UPDATE` và lưu event với unique key như `(order_id, webhook_type, gateway_reference)`; callback lặp lại không được tạo giao dịch hoặc trừ tồn kho lần thứ hai.
- Webhook hợp lệ lần đầu hoặc đã xử lý phải trả HTTP 2xx; chữ ký, `orderCode` hoặc số tiền không hợp lệ phải bị từ chối và không cập nhật đơn.
- Scheduled job phải hủy đơn `PENDING_PAYMENT` quá thời hạn, chuyển payment sang `EXPIRED`, chuyển order sang `CANCELLED` và release tồn kho đúng một lần.
- Khóa ngoại không được tạo bản ghi mồ côi.
- Trạng thái đơn hàng chỉ chuyển theo luồng đã thống nhất.
- Survey chỉ nhận response khi đã phát hành và còn hiệu lực.

Nếu cần bảng phụ cho câu hỏi khảo sát, lựa chọn hoặc lịch sử trạng thái, nhóm phải cập nhật ERD, data dictionary và schema trước khi merge.

## 9. Yêu cầu phi chức năng

| Nhóm | Yêu cầu |
| --- | --- |
| Security | JWT hoặc session thống nhất, BCrypt, RBAC, không trả password hash, kiểm tra quyền ở backend |
| Validation | Validate ở frontend và backend |
| Integrity | PK, FK, unique, not-null, check constraint và transaction phù hợp |
| Performance | Phân trang danh sách và filter báo cáo; tránh tải dữ liệu thừa |
| Usability | Có loading, empty, success và error state |
| Reliability | Error response nhất quán; log đủ để debug nhưng không ghi secret |
| Maintainability | Java 21/Spring Boot, React, PostgreSQL; code chia theo module |
| Testability | Test happy path, validation, quyền, lỗi và dữ liệu biên |
| Documentation | README, API examples, ERD, hướng dẫn chạy, test evidence và demo |

## 10. Kiến trúc dự kiến

```text
React Customer Portal       React Admin Portal
          |                         |
          +------ REST/JSON --------+
                        |
              Java 21 Spring Boot API
       Controller -> Service -> Repository
                        |
                    PostgreSQL
```

- Frontend chịu trách nhiệm giao diện, điều hướng, validate cơ bản và hiển thị trạng thái.
- Backend chịu trách nhiệm authentication, authorization, business rules, transaction và API.
- Database chịu trách nhiệm lưu trữ quan hệ, khóa, constraint, seed data và backup documentation.
- Documentation chứa requirements, ERD, sequence diagram, test evidence, report và demo.

API dùng JSON và status code phù hợp `200`, `201`, `400`, `401`, `403`, `404`, `409`, `500`. Lỗi nên có mã lỗi và thông báo có thể hiển thị trên frontend.

### 10.1. Tích hợp thanh toán payOS

MVP sử dụng payOS cho thanh toán chuyển khoản/VietQR. payOS hiện không có môi trường Sandbox/Staging riêng; kiểm thử phải dùng tài khoản payOS đã xác thực và giao dịch thật giá trị nhỏ. Backend tạo payment link, chuyển Customer sang checkoutUrl, nhận kết quả hiển thị qua Return URL/cancelUrl và cập nhật trạng thái giao dịch qua webhook.

| Thành phần | Quy định |
| --- | --- |
| API base URL | `https://api-merchant.payos.vn` |
| Tạo payment link | `POST /v2/payment-requests` với `orderCode` số nguyên duy nhất, `amount`, `description`, `items`, `returnUrl`, `cancelUrl`, `expiredAt` |
| Cấu hình bắt buộc | `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`, `PAYOS_RETURN_URL`, `PAYOS_CANCEL_URL`, `PAYOS_WEBHOOK_URL` |
| Tạo giao dịch | Backend tạo order trước, lưu `PENDING_PAYMENT`, tạo `orderCode`/payment link duy nhất và trả `checkoutUrl` cho frontend |
| Số tiền | Gửi trực tiếp theo số tiền VND của đơn hàng |
| Chữ ký tạo link | Dùng HMAC-SHA256 với checksum key, chuỗi dữ liệu sắp xếp theo alphabet; secret chỉ nằm ở backend |
| Return/cancel URL | Nhận query params để hiển thị kết quả cho Customer; không dùng làm nguồn duy nhất để chốt đơn |
| Webhook | Endpoint public HTTPS kiểm tra `signature`, `orderCode`, `amount`, `data.code` và trạng thái trước khi cập nhật order |
| Webhook acknowledgment | Webhook hợp lệ hoặc đã xử lý trả HTTP 2xx; chữ ký/dữ liệu sai trả lỗi và không cập nhật order |
| Thành công | `success=true`, webhook `code=00` và giao dịch hợp lệ → `PAID`/`CONFIRMED` |
| Thất bại/hủy/hết hạn | Cập nhật `FAILED`, `CANCELLED` hoặc `EXPIRED`, giải phóng phần tồn kho đã reserve |
| Webhook concurrency | Khóa order/payment bằng `SELECT ... FOR UPDATE` và lưu event bằng unique key để chống check-then-insert race |
| Dọn đơn treo | Scheduled job định kỳ tìm `PENDING_PAYMENT` quá timeout hoặc payment link hết hạn, chuyển `EXPIRED`/`CANCELLED` và release tồn kho một lần |
| Local demo | Dùng HTTPS tunnel hoặc môi trường có URL public để payOS gọi được webhook |

Không commit `PAYOS_CLIENT_ID`, `PAYOS_API_KEY` hoặc `PAYOS_CHECKSUM_KEY` vào repository. Dùng biến môi trường hoặc file local bị `.gitignore` loại trừ. Tài liệu tham khảo: [payOS API](https://payos.vn/docs/api/), [payOS webhook](https://payos.vn/docs/du-lieu-tra-ve/webhook/), [payOS signature](https://payos.vn/docs/tich-hop-webhook/kiem-tra-du-lieu-voi-signature/), [payOS test environment](https://payos.vn/docs/moi-truong-test/).

## 11. Dependency và kế hoạch triển khai

```text
#1–#6 Analysis, ERD, data dictionary, 3NF
              |
              +--> #7–#14 Admin sequence diagrams
              |
              +--> #15–#22 Customer sequence diagrams
                              |
                              +--> #23–#30 Customer implementation
                              +--> #31–#38 Admin implementation
```

| Giai đoạn | Thời gian | Kết quả |
| --- | --- | --- |
| Phase I | Ngày 1–5 | #1–#6 và database contract |
| Phase II | Ngày 4–7 | #7–#14 |
| Phase III | Ngày 4–7 | #15–#22 |
| Phase IV | Ngày 8–13 | #23–#30 và Customer integration |
| Phase V | Ngày 8–13 | #31–#38 và Admin integration |
| Final QA | Ngày 14–15 | Kiểm thử, sửa lỗi, báo cáo, demo và review cuối |

Mọi Issue làm trên branch riêng và kết thúc bằng Pull Request. `main` chỉ nhận thay đổi sau khi PR được review theo branch protection của repository.

## 12. Kiểm thử và nghiệm thu

### 12.1. Nhóm kiểm thử

- **Unit test**: validation, tính tổng tiền, chuyển trạng thái, rating và survey rules.
- **Integration test**: API, database, authentication, authorization và transaction.
- **UI test**: form, loading, empty state, lỗi và thông báo thành công.
- **End-to-end smoke test**: đăng ký → đăng nhập → xem sản phẩm → đặt hàng → xem trạng thái → feedback/khảo sát.
- **Security test**: Customer không truy cập dữ liệu Customer khác; endpoint Admin từ chối role không phù hợp.
- **Data test**: khóa ngoại, dữ liệu trùng, NULL, rating ngoài khoảng và tồn kho âm.

### 12.2. Acceptance checklist

- [ ] Làm đúng Objective và dependency của Issue.
- [ ] Deliverable nằm trong repository hoặc có liên kết rõ ràng.
- [ ] Có ảnh, log hoặc test evidence phù hợp.
- [ ] Không commit secret, password thật hoặc file generated lớn.
- [ ] Pull Request có `Closes #number`.
- [ ] Pull Request có ít nhất một approval.
- [ ] Tất cả conversation đã được xử lý.
- [ ] Tính năng được kiểm tra trên dữ liệu mẫu.
- [ ] Issue và Project được cập nhật sau khi merge.

## 13. Rủi ro và phương án xử lý

| Rủi ro | Ảnh hưởng | Xử lý |
| --- | --- | --- |
| Schema thay đổi sau khi code | Conflict và sửa nhiều module | Chốt ERD/data dictionary trước; thay đổi schema phải cập nhật tài liệu |
| Hai người tạo project skeleton | Xung đột cấu trúc | Một PR tạo skeleton chung; các thành viên kéo `main` trước khi code |
| Thiếu dữ liệu khảo sát/mẫu | Thiếu bằng chứng báo cáo | Dùng dữ liệu mẫu và ghi rõ đây là dữ liệu mẫu |
| Phạm vi quá rộng | Không hoàn thành MVP | Ưu tiên auth, Customer/Admin cơ bản, order và báo cáo tối thiểu |
| Push trực tiếp lên `main` | Mất kiểm soát review | Duy trì branch protection và bắt buộc PR approval |
| API không thống nhất | Tích hợp chậm | Ghi API contract trong Issue/PR trước khi code |

## 14. Định nghĩa hoàn thành sản phẩm

Sản phẩm hoàn thành khi:

1. Deliverable phân tích và thiết kế của #1–#22 đã được review.
2. Chức năng Customer và Admin của #23–#38 có code, test và bằng chứng chạy.
3. Database schema, seed data và tài liệu khớp với chức năng.
4. Lỗi nghiêm trọng về authentication, authorization, dữ liệu và đặt hàng đã được xử lý.
5. README hướng dẫn cài đặt, chạy, test và demo đầy đủ.
6. Pull Request đã được review và merge vào `main`.
7. Báo cáo và demo phản ánh đúng phạm vi triển khai.
