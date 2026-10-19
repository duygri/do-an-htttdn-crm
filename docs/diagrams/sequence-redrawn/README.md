# Source-based sequence diagrams

This folder contains regenerated PlantUML sources and PNG exports for the CRM repository.

- `admin/`: 12 Admin flows, aligned to `Admin*Controller` and `Admin*Service` classes.
- `customer/`: 8 Customer flows, based on the existing customer sequence sources and checked against the current controllers.
- `images/`: rendered PNG files are generated from the PlantUML sources.

## PNG index

### Admin

- `01-view-customer-information.png` — `GET /api/manager/users/{id}`
- `02-update-customer.png` — `PUT /api/manager/users/{id}`
- `03-feedback-handling.png` — `GET/PATCH /api/manager/feedback`
- `04-lock-unlock-account.png` — `PATCH /api/manager/users/{id}/lock`
- `05-customer-report.png` — `GET /api/manager/reports/customers`
- `06-product-management.png` — product CRUD/archive/stock endpoints
- `07-survey-create-send.png` — survey create and publish endpoints
- `08-survey-analytics.png` — `GET /api/manager/surveys/stats`
- `09-sales-order-approval.png` — order listing and status update endpoints
- `10-add-customer.png` — `POST /api/manager/users`
- `11-delete-customer.png` — `DELETE /api/manager/users/{id}`
- `12-sales-revenue-reporting.png` — `GET /api/manager/reports/revenue`

### Customer

- `III-01` registration
- `III-02` login, refresh rotation and logout
- `III-03` profile and preference update
- `III-04` verified-purchase feedback and rating
- `III-05` survey response
- `III-06` product catalog search and detail
- `III-07` cart, checkout and payOS webhook
- `III-08` order history and status tracking

Repository checked out from:
`https://github.com/duygri/do-an-htttdn-crm`

The diagrams are documentation artifacts; they do not change application code.
