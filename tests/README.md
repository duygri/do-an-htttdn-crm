# Tests

Automated tests live beside the application they cover:

- Backend: `backend/src/test/java` (JUnit and Spring Boot tests).
- Frontend: `frontend/src` (`*.test.js` and `*.test.jsx`, run with Vitest).

Run the backend suite from `backend` with `mvn test -q`. Run the frontend suite from `frontend` with `npm test`; run `npm run build` there to verify the production bundle.

Database backup and restore exercises are operational procedures, not automated test cases. Keep evidence and test-specific fixtures with the owning module unless they need to be shared across the backend and frontend suites.
