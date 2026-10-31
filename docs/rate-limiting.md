# Rate Limiting

Backend áp dụng 2 tầng giới hạn lượng request qua filter `security/RateLimitFilter.java` (chạy trước `AdminAccessGuard`). Trạng thái lưu **in-memory trong một node** (`security/RateLimitStore.java`), reset khi restart — không dùng Redis.

## Tầng 1 — Toàn bộ API

- Token bucket theo **IP**: mặc định **120 request/phút/IP**, refill dần theo thờI gian.
- IP được lấy từ **hop cuối** của `X-Forwarded-For` (giá trị do proxy tin cậy thêm vào), fallback `remoteAddr`. Hop đầu do client tự gửi nên không được dùng. `ForwardedHeaderFilter` của Spring ẩn các header này với filter phía sau, vì vậy filter đọc lại từ request gốc. Chỉ bật tính năng này khi app nằm sau proxy tin cậy (`app.rate-limit.trust-forwarded-for`, mặc định `false`).
- Path được so khớp ở dạng **đã decode** (giống cách Spring MVC route), nên `POST /api/%61uth/login` vẫn bị tính là `/api/auth/login`.
- State toàn cục bị giới hạn **10.000 IP**; entry cũ nhất bị loại khi đầy, entry idle quá 60 phút bị dọn.
- Khi hết token: `429` + header `Retry-After` (giây).

## Tầng 2 — Route xác thực

- Áp dụng cho POST: `/api/auth/login`, `/api/auth/register`, `/api/auth/forgot-password`, `/api/auth/reset-password`, `/api/admin/auth/login`, `/api/manager/auth/login`.
- Chỉ tính request **thất bại do xác thực**: status `401`/`403`, và riêng `/api/auth/reset-password` tính thêm `400 RESET_CODE_INVALID`. Lỗi validate (`400 VALIDATION_ERROR`), trùng email (`409`) hay lỗi khác không bị tính.
- `/api/auth/change-password` **không** thuộc tầng 2: endpoint không có email trong body nên chỉ có thể khóa theo IP, dễ ảnh hưởng người dùng khác cùng NAT.
- Mặc định tối đa **5 lần thất bại / 15 phút**.
- Key = `IP + email` lấy từ body request (chuẩn hóa lowercase).
- Khi vượt ngưỡng: `429` + `Retry-After` = thờI gian còn lại của cửa sổ.

## Miễn trừ

- `OPTIONS` (CORS preflight).
- `POST /api/payments/payos/webhook` — PayOS retry không bị chặn theo số lượng, nhưng vẫn áp dụng giới hạn kích thước payload.

## Giới hạn kích thước payload

- Body JSON tối đa **1 MB** (`app.request.max-body-bytes` / `MAX_BODY_BYTES`); vượt quá → `413` `PAYLOAD_TOO_LARGE`.
- Request quá lớn **vẫn tiêu tốn token** của tầng 1 trước khi bị trả `413`, nên không thể lách giới hạn bằng cách gửi payload lớn liên tục.
- Multipart (nếu dùng sau này): `max-file-size` / `max-request-size` = 2 MB trong `application.yml`.
- JSON sai định dạng, sai kiểu tham số, thiếu param → `400` (`INVALID_JSON` / `VALIDATION_ERROR`) thay vì `500`; route không tồn tại → `404`; sai method → `405`.

## Response 429/413

```json
{"code":"RATE_LIMITED","message":"Quá nhiều yêu cầu. Vui lòng thử lại sau."}
```

- Response do filter ghi trước MVC, nên filter tự thêm `Access-Control-Allow-Origin` (chỉ origin nằm trong `CORS_ORIGINS`), `Access-Control-Allow-Credentials` và `Vary: Origin`; riêng `429` thêm `Access-Control-Expose-Headers: Retry-After` để trình duyệt đọc được.
- Origin không nằm trong allowlist sẽ không nhận header CORS.

## Cấu hình (application.yml / env)

| Key | Env | Mặc định |
|---|---|---|
| `app.rate-limit.enabled` | `RATE_LIMIT_ENABLED` | `true` |
| `app.rate-limit.per-minute` | `RATE_LIMIT_PER_MINUTE` | `120` |
| `app.rate-limit.auth-max-attempts` | `AUTH_MAX_ATTEMPTS` | `5` |
| `app.rate-limit.auth-window-minutes` | `AUTH_WINDOW_MINUTES` | `15` |
| `app.rate-limit.trust-forwarded-for` | `RATE_LIMIT_TRUST_FORWARDED_FOR` | `false` (bật `true` trên Render) |
| `app.request.max-body-bytes` | `MAX_BODY_BYTES` | `1048576` |

## Lưu ý

- Single-node: nếu scale nhiều instance, cần đổi sang Redis/bucket tập trung.
- Muốn tắt toàn bộ: `RATE_LIMIT_ENABLED=false` (giới hạn body vẫn áp dụng).
- Chỉ bật `trust-forwarded-for` khi ứng dụng nằm sau proxy tự thêm hop cuối. Nếu app lộ trực tiếp ra Internet, để mặc định `false` để dùng `remoteAddr`.
