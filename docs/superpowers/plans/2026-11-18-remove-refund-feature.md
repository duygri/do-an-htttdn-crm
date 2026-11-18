# Remove refund feature plan

## Scope

Remove the application refund workflow from manager and customer experiences, API, order cancellation automation, and cashflow reporting. Keep return processing, PayOS reconciliation, explicit COD collection confirmation, and existing database refund records intact. Do not drop historical columns or tables.

## Tasks

- [x] Replace refund workflow tests with checks for unavailable refund routes, cancellation behavior, COD collection, and cashflow receipts.
- [x] Split manager order payments from the refund page and remove refund UI, routes, DTOs, service logic, and automatic cancellation requests.
- [x] Remove refund calculations and refund schema from the fresh-install schema while retaining old migration files as historical records.
- [x] Update user-facing and technical documentation and diagrams to describe supported payment, return, and cashflow behavior.
- [x] Compile backend production and test sources, build the frontend bundle, and inspect the final references and diff. Automated test suites were not run.
