# CRM Admin Backend

Java 21 / Spring Boot REST backend for the Vietnamese men's clothing customer portal and the existing admin CRM module. PostgreSQL is accessed through Spring Data JPA; Hibernate creates/updates the development schema. The default database is `htttdn`.

## Package structure

```text
com.htttdn.crm
├── config/       # application and infrastructure configuration
├── controller/   # REST controllers and HTTP mapping
├── dto/          # request/response contracts (grouped by feature)
├── entity/       # JPA entities and relationships
├── exception/    # API exception handling and error responses
├── repository/   # Spring Data repositories
├── security/     # authentication/authorization filters
└── service/      # business use cases (ready for feature services)
```

## Run locally

```powershell
$env:DB_URL='jdbc:postgresql://localhost:5432/htttdn'
$env:DB_USERNAME='postgres'
$env:DB_PASSWORD='mat-khau-postgres-cua-ban'
$env:SERVER_PORT='8082'
mvn spring-boot:run
```

Customer APIs follow the sequence diagrams: `/api/auth/*`, `/api/customers/me`, `/api/products`, `/api/cart`, `/api/orders`, `/api/payments/payos/*`, `/api/products/{id}/feedback`, and `/api/surveys/*`.

When payOS credentials are omitted, checkout returns a local development payment URL so the complete cart/order flow can be tested without external credentials. Refresh tokens are BCrypt-independent SHA-256 hashes in the database and are sent only as HttpOnly cookies; passwords use BCrypt.

Set `ADMIN_GUARD_ENABLED=true` when the shared authentication module is available. In that mode, `/api/admin/**` requires the upstream auth layer to provide `X-User-Role: ADMIN` (the temporary development default is disabled).

## Admin API

- `GET /api/admin/users`, `GET/PUT /api/admin/users/{id}`, `PATCH /api/admin/users/{id}/lock`
- `GET /api/admin/feedback`, `PATCH /api/admin/feedback/{id}`
- `GET/POST /api/admin/surveys`, `PATCH /api/admin/surveys/{id}/publish`, `DELETE /api/admin/surveys/{id}`
- `GET/POST /api/admin/products`, `PUT/DELETE /api/admin/products/{id}`, `PATCH /api/admin/products/{id}/stock`
- `GET /api/admin/orders`, `GET /api/admin/orders/{id}`, `PATCH /api/admin/orders/{id}/status`
- `GET /api/admin/reports/revenue`, `GET /api/admin/reports/users`, `GET /api/admin/surveys/stats`

All list endpoints support `page` and `size`; users and products also support `q`, while feedback/survey/order lists support `status`.
