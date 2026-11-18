# CRM Playwright E2E Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Exercise the CRM rubric across Admin, Manager, and Customer portals on Chromium, Firefox, and WebKit using Playwright full-stack tests.

**Architecture:** Extend the existing Playwright webServer setup with the Admin portal and CORS origin. Seed only E2E credentials and a product in the guarded `crm_e2e` database; attach completed-order fixtures to test customers through a narrowly scoped helper. Drive each CRM role through its UI and use unique data per browser project.

**Tech Stack:** Playwright, React/Vite, Spring Boot, PostgreSQL, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-18-crm-playwright-e2e-design.md`

## Global Constraints

- CRM tests cover only Admin account creation/role/deactivation; Manager customer add/delete/lock, feedback, age/preferences reports, survey publishing and statistics; and Customer registration/login, profile editing, feedback, and survey completion.
- Use Chromium, Firefox, and WebKit projects.
- Use the real backend and a local loopback PostgreSQL database named exactly `crm_e2e`.
- Never send product feedback without a completed order fixture.
- Keep E2E test data unique per project; do not mock the CRM APIs.
- Do not add HRM/MRP or unrelated storefront commerce scenarios.

## Review Focus

- Database safety: reject any non-loopback or non-`crm_e2e` test database; verify this through the existing `getE2EDatabaseSettings` guard.
- Browser isolation: no duplicate customer, survey, or order identifiers when Chromium, Firefox, and WebKit run concurrently; use project-specific run IDs.
- Feedback eligibility: the fixture produces a completed order containing the tested product; the browser submits feedback through the customer UI.
- Role separation: Admin actions use `/admin`, manager actions use `/manager`, and customer actions use the storefront; assert each portal's own authenticated view.
- Survey result integrity: only a submitted customer answer contributes to the Manager result count; assert one response and the selected option.

---

### Task 1: Add isolated CRM test fixtures

**Files:**
- Modify: `frontend/e2e/credentials.js`
- Modify: `frontend/e2e/global-setup.js`
- Create: `frontend/e2e/fixtures.js`

**Interfaces:**
- Export dedicated Admin and Manager test email/password constants from `credentials.js`.
- Export `createCompletedOrderForCustomer(email, orderCode)` from `fixtures.js`; it validates DB access with `getE2EDatabaseSettings`, inserts one completed order and one line item for the seeded E2E product, and returns the order ID.
- Global setup seeds Admin, Manager, and one stable E2E category/product in the guarded database.

- [x] Seed the Admin/Manager accounts and stable product idempotently in `global-setup.js`.
- [x] Implement the completed-order fixture with a guarded `pg` connection and unique order code.
- [x] Run the existing database guard tests, including its rejection cases, before any browser flow uses the helper.

### Task 2: Cover the Admin account portal

**Files:**
- Create: `frontend/e2e/admin-portal.spec.js`

**Interfaces:**
- Test login uses the E2E Admin credentials from `credentials.js`.
- The test uses the Admin portal's visible account form and confirms soft deletion through its browser confirmation.

- [x] Write an E2E test that signs in at `/admin/login`, verifies the distinct Admin console, creates an internal account with role `MANAGER`, and verifies its displayed role.
- [x] Extend the scenario to deactivate the created account and assert it no longer appears as active/listed.

### Task 3: Cover the Manager and Customer CRM journey

**Files:**
- Create: `frontend/e2e/crm-roles.spec.js`
- Modify: `frontend/e2e/auth.spec.js`

**Interfaces:**
- Manager login uses the E2E Manager credentials from `credentials.js`.
- The CRM journey uses separate customer and manager browser contexts, a unique customer email, and `createCompletedOrderForCustomer`.

- [x] Write a role journey that registers a customer, edits age/preferences, and verifies the profile save.
- [x] In the Manager portal, add a customer, lock the account with a reason, delete it, and verify age/preferences report labels and the registered customer's profile values.
- [x] Publish a unique survey to all customer accounts; return to the customer portal, complete it, then verify Manager survey statistics show one response and the chosen answer.
- [x] Attach a completed order to the registered customer, submit a product review through the order UI, and verify Manager can see it and reply.
- [x] Keep existing session-refresh and dashboard smoke assertions, updating shared helpers only when they reduce duplication.

### Task 4: Run all browser engines in local and CI workflows

**Files:**
- Modify: `frontend/playwright.config.js`
- Modify: `.github/workflows/backend-check.yml`
- Modify: `frontend/README.md`

**Interfaces:**
- Playwright projects are named `chromium`, `firefox`, and `webkit`.
- Playwright starts the customer portal on 5183, Admin portal on 5184, Manager portal on 5185, and the backend on 8182; all three frontend origins are allowed by E2E CORS.
- CI installs all three Playwright browser engines before running `npm run test:e2e`.

- [x] Configure all three browser projects and add the Admin Vite web server and CORS origin.
- [x] Update CI to install Chromium, Firefox, and WebKit.
- [x] Document the disposable PostgreSQL setup and the all-browser command.
- [x] Run the full E2E suite, frontend unit suite, and frontend production build; report any environment blocker separately from code failures.

### Verification results

- Playwright: 12/12 passed across Chromium, Firefox, and WebKit, including the Admin account flow and the full Manager/Customer CRM journey.
- Database safety regression: 4/4 passed; the new test first failed for `?host=...`, then passed after query parameters were rejected.
- Frontend unit suite: 286/291 passed. Five assertions fail in the unchanged `Optimization.test.jsx` and `PortalSplit.test.jsx` files because their expected UI text/link labels differ from the current rendered UI; no E2E-related application UI files were modified.
- Production build: passed. Vite emitted non-blocking `use client` directive warnings from `lucide-react`.
- No environment blockers occurred.
