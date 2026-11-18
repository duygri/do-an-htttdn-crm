# CRM Playwright E2E Design

## Goal

Automate the CRM requirements in the course rubric through the real Nova Shop web portals, backend, and a disposable PostgreSQL database.

## Scope from the CRM rubric

- **Admin:** use the separate Admin portal to create, assign a role to, and deactivate an internal account.
- **Manager:** add a customer, lock and delete a customer account, receive and respond to feedback, view age and preference reports, create and publish a survey, and inspect survey statistics.
- **Customer:** register and sign in, edit their profile, submit product feedback for a completed purchase, receive a published survey, and submit a survey response.
- **Browsers:** run the same full-stack suite on Playwright Chromium, Firefox, and WebKit.
- Do not cover HRM, MRP, or general commerce flows beyond the completed-order prerequisite for verified product feedback.

## Design

Use the existing Playwright full-stack harness and its disposable `crm_e2e` PostgreSQL database. Add an Admin web server and its origin to the E2E CORS configuration. Seed dedicated Admin and Manager credentials plus one stable category/product during global setup. A test helper may attach a completed order to a newly registered customer, but it must obtain its connection through the existing database guard, which accepts only a loopback PostgreSQL URL whose database name is exactly `crm_e2e`.

Keep test data isolated across browser projects with unique emails, survey titles, comments, and order codes. Drive each role through its visible portal. Do not mock CRM APIs. The manager survey is published to all customer accounts so the customer flow can verify that publishing makes it available, submit an answer, and then verify the response in the Manager report.

## Verification

- Run all E2E projects on Chromium, Firefox, and WebKit.
- Run the existing frontend unit suite and production build after adding the suite/configuration.
- Keep GitHub Actions E2E installation aligned with the three browser projects.

## Results (2026-11-18)

- Playwright: 12/12 passed across Chromium, Firefox, and WebKit.
- Database guard regression: 4/4 passed, including rejection of connection override parameters.
- Frontend unit suite: 286/291 passed; five existing assertions in `Optimization.test.jsx` and `PortalSplit.test.jsx` expect UI text or links that do not match the current rendered UI.
- Production build passed with non-blocking `lucide-react` directive warnings.

## Risks

- Browser engines must be installed locally or by CI before Playwright can launch them.
- Full-stack execution requires Java 21, Maven, and a local disposable PostgreSQL service.
- Product feedback is allowed only for a completed order, so the test fixture must preserve that real authorization rule.
