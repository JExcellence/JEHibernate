# ADR-0004 — Multi-tenancy via Hibernate native strategies

- Status: Accepted
- Date: 2026-06-02
- Deciders: JExcellence

## Context

The resellable SaaS target needs tenant isolation with selectable trade-offs. Hibernate 7 supports
multi-tenancy natively; we expose it through a small JEHibernate API without forcing it on the
single-tenant (plugin) use-case.

## Decision

Three strategies, selected by `MultiTenancyConfig`, **off by default**
(`MultiTenancyStrategy.NONE` → unchanged single-tenant behaviour):

| Strategy | Isolation | Wiring |
|---|---|---|
| `DISCRIMINATOR` | lowest | `@TenantId` column on entities + `CurrentTenantIdentifierResolver`. One pool, one schema. |
| `SCHEMA` | medium | `MultiTenantConnectionProvider` switches `Connection.setSchema(tenant)` over **one shared pool**; schema reset on release. |
| `DATABASE` | highest | `MultiTenantConnectionProvider` maps tenant → its own `DataSource`. |

Supporting API:

- **`TenantContext`** — thread-local holder; `open(id)` returns an `AutoCloseable` scope that nests
  and restores the previous tenant on close (safe in-thread tenant switching).
- **`TenantResolver`** — JEHibernate SPI (`String resolveTenantId()`); default reads `TenantContext`.
  Plugins resolve from server/world id; web apps from subdomain/JWT/header.
- **`TenantContextResolver`** — adapts `TenantResolver` to Hibernate's
  `CurrentTenantIdentifierResolver`, with a **strict leak guard**: when no tenant is resolved it
  throws rather than running untenanted. Strict mode is on by default for all enabled strategies.

### Why these choices

- **Shared pool, not pool-per-tenant.** DISCRIMINATOR and SCHEMA route every tenant through the one
  HikariCP pool from ADR-0003; only DATABASE (by nature) uses per-tenant data sources. This avoids
  the pool-per-tenant explosion called out in the acceptance criteria.
- **Strict leak guard.** For DISCRIMINATOR a missing tenant filter is a silent cross-tenant data
  leak. Making the resolver throw turns that into a loud failure at session open.
- **`@TenantId` (not a hand-rolled filter).** Hibernate applies the discriminator automatically on
  every query and populates it on insert, so application/repository code is tenant-agnostic — there
  is no per-query filter to forget.

## Consequences

- Repositories are unchanged: tenant flows via `TenantContext` + resolver, resolved by Hibernate at
  session open. No tenant argument threads through the repository API.
- **Per-tenant migration** (Flyway) is the operator's responsibility for SCHEMA/DATABASE (run Flyway
  per schema/database); DISCRIMINATOR uses a single central migration. JEHibernate's bootstrap
  migration (ADR-0002) targets the default/any data source only.
- DATABASE requires the consumer to supply the tenant → `DataSource` map; JEHibernate does not
  provision tenant databases.

## DSGVO note

Tenant isolation is a technical-organisational measure under **Art. 32 DSGVO**. DISCRIMINATOR gives
the weakest isolation (one row-level mistake exposes another tenant's data) — the strict leak guard
mitigates this but operators handling special-category or high-volume personal data should weigh
SCHEMA/DATABASE. Cross-tenant data export/erasure (Art. 15/17) must be implemented per strategy by
the operator. The planned `tenant_id` column on the audit revision entity (TODO-4, currently
skipped) would itself be tenant-scoped personal data.
