# ADR-0002 — Flyway as default migration tool; `ddl-auto` defaults to `validate`

- Status: Accepted
- Date: 2026-06-02
- Deciders: JExcellence

## Context

3.x relied on Hibernate `hbm2ddl.auto` (default `update`) to manage schema. That is convenient for
plugins but unsafe for enterprise/multi-tenant production: `update` cannot drop or rename, applies
no versioning, and produces drift between environments. We need first-class, versioned migrations
while keeping the plugin use-case painless and the migration dependency optional.

## Decision

1. **Flyway is the default migration tool.** SQL-first (`V001__init.sql`) is the lowest-friction
   format for the plugin audience and the most common in the target market. Migrations live under
   `classpath:db/migration` (Flyway convention), reconfigurable via
   `jehibernate.migration.location`.
2. **Liquibase is opt-in** (`jehibernate.migration.tool=liquibase`) for teams that prefer
   XML/YAML/JSON changelogs.
3. **Both are optional dependencies.** `MigrationSupport` guards each runner behind a
   `Class.forName` check; if the selected tool is absent, migration is a **silent no-op**, never an
   error. This keeps Flyway/Liquibase off the plugin classpath unless the consumer adds them.
4. **Migrations run before `SessionFactory.build()`**, against the resolved `DataSource`, so the
   schema exists before Hibernate inspects it.
5. **`ddl-auto` now defaults to `validate`** (was `update`). Migrations own the schema; Hibernate
   only verifies it matches the mapping.
6. `jehibernate.migration.enabled=false` disables the step entirely for owners who manage DDL
   another way.

## Consequences

- **Breaking:** a 3.x consumer that did not set `ddlAuto(...)` and relied on the implicit `update`
  default will now get `validate` and must either (a) provide migrations, (b) set
  `ddlAuto("update")` explicitly to keep old behaviour, or (c) set
  `jehibernate.migration.enabled=false`. Documented in CHANGELOG and README.
- `FlywayMigrationRunner` uses `baselineOnMigrate(true)` so an existing, DDL-managed plugin database
  is baselined rather than rejected on first migrated start.
- Spring Boot applications that already use Spring's Flyway auto-configuration should disable
  JEHibernate's migration step (handled in the `jehibernate-spring-boot` module) to avoid double
  execution.

## DSGVO / data-protection note

Migration tooling itself stores no personal data, but it is the mechanism by which audit and
tenant tables (later TODOs) are created. Retention and erasure obligations
(Art. 5 Abs. 1 lit. e, Art. 17 DSGVO) are addressed in the audit ADR, not here.
