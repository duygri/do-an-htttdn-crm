# CRM Admin Backend

Java 21 / Spring Boot REST backend for the admin CRM and sales module (issues V-01 through V-08). PostgreSQL is accessed through Spring Data JPA; Hibernate creates/updates the development schema.

## Run locally

```powershell
$env:DB_URL='jdbc:postgresql://localhost:5432/crm'
$env:DB_USERNAME='postgres'
$env:DB_PASSWORD='postgres'
mvn spring-boot:run
```

Set `ADMIN_GUARD_ENABLED=true` when the shared authentication module is available. In that mode, `/api/admin/**` requires the upstream auth layer to provide `X-User-Role: ADMIN` (the temporary development default is disabled).

## Admin API

- `GET /api/admin/users`, `GET/PUT /api/admin/users/{id}`, `PATCH /api/admin/users/{id}/lock`
- `GET /api/admin/feedback`, `PATCH /api/admin/feedback/{id}`
- `GET/POST /api/admin/surveys`, `PATCH /api/admin/surveys/{id}/publish`, `DELETE /api/admin/surveys/{id}`
- `GET/POST /api/admin/products`, `PUT/DELETE /api/admin/products/{id}`, `PATCH /api/admin/products/{id}/stock`
- `GET /api/admin/orders`, `GET /api/admin/orders/{id}`, `PATCH /api/admin/orders/{id}/status`
- `GET /api/admin/reports/revenue`, `GET /api/admin/reports/users`, `GET /api/admin/surveys/stats`

All list endpoints support `page` and `size`; users and products also support `q`, while feedback/survey/order lists support `status`.
