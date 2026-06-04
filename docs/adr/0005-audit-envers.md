# ADR-0005 — Audit trail via Hibernate Envers

- Status: Accepted
- Date: 2026-06-04
- Deciders: JExcellence

## Context

Enterprise/multi-tenant deployments need a change history (who changed what, when) for compliance
and forensics. Hibernate Envers is the mature, native solution; the requirement is to make it
opt-in, capture the acting user and tenant, and keep Envers an optional dependency.

## Decision

- **Envers is the audit mechanism.** Entities opt in per-class with the standard
  `@org.hibernate.envers.Audited` (no custom marker — don't reinvent it). Audit is **off** until an
  entity is annotated.
- **Optional dependency.** `hibernate-envers` is `compileOnly` in core. The audit classes compile
  without it and are only exercised when it is on the runtime classpath. Envers auto-activates via
  its integrator when present.
- **`JEHibernateRevisionEntity`** is the `@RevisionEntity`: `revision_id`, `revision_timestamp`,
  `user_id`, `source_ip`, `tenant_id`. Registered via `ConfigurationBuilder.enableAudit()` (or by
  scanning the `audit` package).
- **`AuditUserResolver`** SPI supplies the acting user / source IP; **`AuditContext`** holds the
  active resolver because Envers instantiates the `RevisionListener` itself (no DI). The listener
  also reads the current tenant from `TenantContext`.
- **Query API:** `Audit.getRevisions(emf, class, id)` (historical states),
  `getRevisionNumbers(...)`, `getRevisionInfo(...)` (revision metadata).

### Not done (deliberately)

- The spec's additional non-Envers `AuditEventListener` hook is **not** shipped. Envers covers the
  audit use-case; a second, unwired listener API would be dead code. It can be added later if a
  no-Envers INSERT/erasure-log path is genuinely needed.
- A built-in retention/purge job is **not** shipped (see DSGVO note) — it is documented as an
  operator responsibility because a correct purge is schema/DB-specific.

## Consequences

- `source_ip` defaults to `null` (opt-in) — it is personal data under Art. 4/32 DSGVO.
- Audit rows outlive the source row: deleting an entity does not erase its `_AUD` history. This is
  intentional (that's the point of an audit trail) but has DSGVO Art. 17 implications.

## DSGVO

- **Art. 5 Abs. 1 lit. e (Speicherbegrenzung):** audit data must have a retention limit; implement a
  periodic purge of `*_AUD` and `jehibernate_revision` rows older than your retention period.
- **Art. 17 (Löschung):** erasing a data subject's records must also address their audit history;
  Envers does not cascade source deletes into `_AUD`.
- **Art. 32 / § 26 BDSG:** `user_id`/`source_ip`/`tenant_id` are personal/employee data — restrict
  access to audit tables and collect `source_ip` only with a lawful basis.
