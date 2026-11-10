# Database resources

Database setup, migration scripts, and feature notes live in this folder. The backend's default Hibernate mode is `validate`, so prepare the schema before starting the application.

## Core scripts

- [`schema.sql`](schema.sql): baseline schema for a new database.
- [`seed_data.sql`](seed_data.sql): development/demo seed data.
- [`cleanup_legacy.sql`](cleanup_legacy.sql): cleanup for legacy data; review its statements before use.
- [`backup/README.md`](backup/README.md): backup and restore notes.

## Migration and feature guides

- [`MANAGER_PORTAL.md`](MANAGER_PORTAL.md): manager portal database changes.
- [`VOUCHERS.md`](VOUCHERS.md): voucher behavior and migration guidance.
- [`REVIEWS_REWARDS.md`](REVIEWS_REWARDS.md): order reviews and survey rewards.

Incremental SQL migrations are named `migration_*.sql`. Apply the migration documented for the database's current schema and feature; the filenames do not define a universal sequence. Back up the target database before applying a migration, and do not rerun the full seed to migrate existing data.

Available migrations:

- [`migration_admin_user_split.sql`](migration_admin_user_split.sql)
- [`migration_audit_logs.sql`](migration_audit_logs.sql)
- [`migration_care_outcomes.sql`](migration_care_outcomes.sql)
- [`migration_care_reminders.sql`](migration_care_reminders.sql)
- [`migration_care_sources.sql`](migration_care_sources.sql)
- [`migration_cashflow_voucher_customer_limit.sql`](migration_cashflow_voucher_customer_limit.sql)
- [`migration_customer_care.sql`](migration_customer_care.sql)
- [`migration_feedback_moderation.sql`](migration_feedback_moderation.sql)
- [`migration_manager_portal.sql`](migration_manager_portal.sql)
- [`migration_payos_attempts.sql`](migration_payos_attempts.sql)
- [`migration_payos_qr.sql`](migration_payos_qr.sql)
- [`migration_refunds_abandoned_payos.sql`](migration_refunds_abandoned_payos.sql)
- [`migration_return_management.sql`](migration_return_management.sql)
- [`migration_review_survey_rewards.sql`](migration_review_survey_rewards.sql)
- [`migration_support_tickets.sql`](migration_support_tickets.sql)
- [`migration_survey_management.sql`](migration_survey_management.sql)
- [`migration_voucher_management.sql`](migration_voucher_management.sql)
