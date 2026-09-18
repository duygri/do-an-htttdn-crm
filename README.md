# Enterprise Information System Project

CRM simulation project for the Enterprise Information Systems course.

## Project scope

The project covers three areas from the assessment requirements:

- Part I: analysis, system design, documentation, testing, and reporting.
- Part II: database implementation and the Admin interface.
- Part III: CRM features for managers and customers.

The complete task list is tracked in [GitHub Issues](https://github.com/duygri/do-an-htttdn-crm/issues) and the [GitHub Project](https://github.com/users/duygri/projects/1).

## Planned technology stack

- Frontend: React
- Backend: Java 21 with Spring Boot REST API
- Database: PostgreSQL
- Design and diagrams: Google Stitch or Figma, draw.io, and related tools

## Repository structure

```text
docs/       Requirements, diagrams, architecture, and user documentation
database/   Schema, seed data, and backup or restore documentation
backend/    Java Spring Boot backend application
frontend/   React frontend application
tests/      Test cases, scenarios, and test evidence
```

## Development workflow

1. Select an assigned GitHub Issue.
2. Create a branch from `main` using the Issue ID, for example `feature/III-CRM-01-add-customer`.
3. Implement and test the task.
4. Open a Pull Request and link the Issue with `Closes #number`.
5. Ask another member to review the Pull Request.
6. Merge only after review and update the Issue status to `Done`.

## Local setup

Create a PostgreSQL database named `htttdn`, then run `database/schema.sql` and `database/seed_data.sql`. If the database was created from an older version of this project, run `database/cleanup_legacy.sql` once between the schema and seed scripts.

Start the backend:

```powershell
cd backend
$env:DB_URL='jdbc:postgresql://localhost:5432/htttdn'
$env:DB_USERNAME='postgres'
$env:DB_PASSWORD='mat-khau-postgres-cua-ban'
$env:SERVER_PORT='8082'
mvn spring-boot:run
```

Start the customer storefront in another terminal:

```powershell
cd frontend
npm install
$env:VITE_API_BASE_URL='http://localhost:8082'
npm run dev
```

The default payOS mode is a local checkout link. Add `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`, `PAYOS_RETURN_URL`, and `PAYOS_CANCEL_URL` to enable the real provider.
