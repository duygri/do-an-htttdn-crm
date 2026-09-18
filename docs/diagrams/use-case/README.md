# Use case diagrams

Bộ này chuyển 11 sequence diagram hiện có thành 11 use case diagram tương ứng. Chức năng sửa thông tin khách hàng đã được loại bỏ theo yêu cầu nghiệp vụ. Use case chỉ mô tả mục tiêu của actor và quan hệ `include`/`extend`; các chi tiết Boundary–Control–Entity vẫn nằm trong sequence diagram.

| Mã | Sequence | Use case source | Image |
| --- | --- | --- | --- |
| 01 | Tiếp nhận và xử lý phản hồi của khách hàng | `preview/01-feedback-handling.puml` | `images/01-feedback-handling.png` |
| 02 | Xem thông tin khách hàng | `preview/02-view-customer-information.puml` | `images/02-view-customer-information.png` |
| 04 | Khóa/mở tài khoản khách hàng | `preview/04-lock-unlock-account.puml` | `images/04-lock-unlock-account.png` |
| 05 | Báo cáo khách hàng | `preview/05-customer-report.puml` | `images/05-customer-report.png` |
| 06 | Quản lý danh mục và thông tin sản phẩm | `preview/06-product-management.puml` | `images/06-product-management.png` |
| 07 | Tạo và gửi bảng khảo sát đến khách hàng | `preview/07-survey-create-send.puml` | `images/07-survey-create-send.png` |
| 08 | Thống kê và phân tích kết quả khảo sát | `preview/08-survey-analytics.puml` | `images/08-survey-analytics.png` |
| 09 | Quản lý và duyệt đơn hàng bán | `preview/09-sales-order-approval.puml` | `images/09-sales-order-approval.png` |
| 10 | Thêm khách hàng | `preview/10-add-customer.puml` | `images/10-add-customer.png` |
| 11 | Xóa khách hàng | `preview/11-delete-customer.puml` | `images/11-delete-customer.png` |
| 12 | Thống kê và báo cáo doanh thu | `preview/12-sales-revenue-reporting.puml` | `images/12-sales-revenue-reporting.png` |

## Render

Render từng file bằng PlantUML, xuất PNG vào thư mục `images`:

```powershell
java -jar plantuml.jar -tpng -o ..\images preview\*.puml
```
