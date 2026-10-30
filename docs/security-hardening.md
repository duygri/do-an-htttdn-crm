# Security headers và cookie

## Security headers

`security/SecurityHeadersFilter.java` chạy ở `HIGHEST_PRECEDENCE` cho mọi response, kể cả `413`/`429` sinh ra trước controller:

| Header | Giá trị |
|---|---|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `no-referrer` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()` |
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'` |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` (chỉ khi request HTTPS) |

Backend chỉ trả JSON API; frontend do Vite/Render phục vụ riêng. Vì vậy CSP `default-src 'none'` không ảnh hưởng giao diện. Nếu sau này backend phục vụ HTML/ảnh trực tiếp, cần nới CSP tương ứng.

## Cookie refresh

- `HttpOnly`, `SameSite=Lax`, path giới hạn theo từng cổng đăng nhập.
- `Secure` bật khi `app.auth.secure-cookie=true` **hoặc** request đến qua HTTPS (`request.isSecure()` / `X-Forwarded-Proto: https`). Nhờ vậy bản deploy sau Render (TLS kết thúc ở proxy) vẫn nhận cookie `Secure` mà không cần sửa `application.yml`.
- Local HTTP vẫn đăng nhập được vì `SECURE_REFRESH_COOKIE` mặc định `false`.

## Tin cậy proxy

- `server.forward-headers-strategy=framework`: Spring đọc `X-Forwarded-*`, cập nhật scheme/host và ẩn các header này khỏi filter phía sau.
- Rate limiter lấy IP khách từ hop cuối `X-Forwarded-For` (xem `docs/rate-limiting.md`). Chỉ bật `TRUST_FORWARDED_FOR` khi app nằm sau proxy tin cậy.

## Kiểm thử

`ApiPayloadValidationIntegrationTest` kiểm tra header trên `/api/products`; `RateLimitFilterIntegrationTest` kiểm tra cookie có/không có `Secure` theo `X-Forwarded-Proto`.
