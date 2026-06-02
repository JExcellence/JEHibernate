# ADR-0003 — JEHibernate owns the HikariCP DataSource (vs. Hibernate's HikariCPConnectionProvider)

- Status: Accepted
- Date: 2026-06-02
- Deciders: JExcellence

## Context

TODO-1 requires HikariCP as the default connection pool, a `getPoolHealth()` health check, clean
shutdown on hot-reload, and — in the Spring Boot module — reuse of an existing `DataSource` bean.

The obvious wiring suggested by the task is Hibernate's
`hibernate.connection.provider_class=org.hibernate.hikaricp.internal.HikariCPConnectionProvider`,
where Hibernate instantiates and owns the pool from `hibernate.hikari.*` properties.

That approach makes three of the four requirements awkward:

- **Health check:** `HikariCPConnectionProvider` does not expose its internal `HikariDataSource`,
  so `getPoolHealth()` would have to scrape JMX MBeans.
- **Shutdown:** the pool's lifecycle is bound to Hibernate's, leaving no clean handle for explicit
  close ordering during plugin hot-reload.
- **External DataSource reuse:** there is no path to hand Hibernate a pre-existing Spring
  `DataSource` through this provider.

## Decision

**JEHibernate creates and owns a `HikariDataSource`** (`HikariDataSourceFactory.create`) and hands
it to Hibernate via `jakarta.persistence.nonJtaDataSource`, so Hibernate uses its
`DatasourceConnectionProviderImpl`. When the consumer supplies an external `DataSource`
(`ConfigurationBuilder.dataSource(...)`, used by the Spring Boot module), JEHibernate uses it as-is
and does **not** close it on shutdown.

This single wiring satisfies all four requirements:

- `getPoolHealth()` reads `HikariDataSource.getHikariPoolMXBean()` directly
  (active/idle/total/threadsAwaiting) — see `PoolHealth`.
- `JEHibernate.close()` closes the owned pool deterministically (and only if owned).
- An external `DataSource` is reused verbatim; ownership flag prevents closing someone else's pool.
- Pool tuning is a dedicated `PoolConfig` record (separate from `DatabaseConfig`, see below).

`PoolConfig` is kept separate from `DatabaseConfig` because folding the seven tuning knobs into the
six-component `DatabaseConfig` record would exceed the seven-component guideline and entangle
connection identity with pool tuning.

## Consequences

- **Behavioural change from 3.x:** the previous `connectionPool(min, max)` wrote
  `hibernate.agroal.*` properties (Agroal). It now configures HikariCP via `PoolConfig`. Agroal
  `compileOnly` deps are dropped. The method signature is unchanged (source-compatible).
- One pool is shared across the application (and, later, across tenants) — no pool-per-tenant
  explosion.
- `hibernate-hikaricp` is **not** a dependency; only `com.zaxxer:HikariCP` is, because connections
  flow through `DatasourceConnectionProviderImpl` (shipped in `hibernate-core`).
- Deviates from the literal `provider_class` instruction in the task; justified above.
