# Audit guide (Hibernate Envers)

JEHibernate records a change history for entities you mark `@Audited`: every committed
insert/update/delete writes a row into a companion `*_AUD` table plus a revision record capturing
**who** changed it, **when**, and **which tenant** was active.

## Enable it

1. Add Envers to the runtime classpath:
   ```kotlin
   implementation("org.hibernate.orm:hibernate-envers:7.1.4.Final")
   ```
2. Register the revision entity and supply who is acting:
   ```java
   AuditContext.setResolver(() -> currentUserId());      // e.g. SecurityContext username / player UUID

   var je = JEHibernate.builder()
       .configuration(c -> c.database(POSTGRESQL).url(...).enableAudit())  // registers the revision entity
       .scanPackages("com.example.domain")
       .build();
   ```
3. Mark entities to audit:
   ```java
   @Entity
   @org.hibernate.envers.Audited
   class Account extends LongIdEntity { /* ... */ }
   ```

`@Audited` is opt-in per entity; everything else stays unaudited. With `ddl-auto`/migrations the
`account_AUD` and `jehibernate_revision` tables are created automatically.

## Query history

```java
List<Account> history = Audit.getRevisions(emf, Account.class, accountId);   // oldest → newest states
List<Number> revs     = Audit.getRevisionNumbers(emf, Account.class, accountId);
JEHibernateRevisionEntity info = Audit.getRevisionInfo(emf, revs.get(0));     // user, timestamp, tenant
```

Roll back by re-saving an old state:
```java
List<Account> history = Audit.getRevisions(emf, Account.class, id);
Account previous = history.get(history.size() - 2);   // state before the last change
accountRepo.update(previous);                          // writes a new revision restoring it
```

## What each revision records

`JEHibernateRevisionEntity`: `revision_id`, `revision_timestamp`, `user_id` (from
`AuditUserResolver`), `source_ip` (opt-in, from the resolver), `tenant_id` (from `TenantContext`).

## DSGVO — important

Audit rows are themselves personal data and **outlive the source row** (deleting an entity does not
erase its `_AUD` history). You must:

- **Retention (Art. 5 Abs. 1 lit. e):** purge `*_AUD` + `jehibernate_revision` rows older than your
  retention period. JEHibernate does not ship a purge job — it is schema/DB-specific; run a
  scheduled delete (delete `_AUD` rows first, then orphaned revisions).
- **Erasure (Art. 17):** when erasing a data subject, also remove or anonymise their audit history.
- **Minimisation (Art. 32 / § 26 BDSG):** collect `source_ip` only with a lawful basis (it defaults
  to `null`), and restrict access to audit tables.
