# Testing guide (`jehibernate-testing`)

The `jehibernate-testing` module provides JUnit 5 support for testing repositories against H2
(fast, no Docker) or a real database via Testcontainers.

```kotlin
testImplementation("de.jexcellence.hibernate:jehibernate-testing:4.0.0")
testRuntimeOnly("org.postgresql:postgresql:42.7.7")   // the driver for your chosen container
```

## `@JEHibernateTest`

Annotate the test class. The extension boots JEHibernate, injects it (or its
`EntityManagerFactory`) as a test parameter, and resets the database after each test.

```java
@JEHibernateTest(scanPackages = "com.example")               // H2, TRUNCATE_ALL by default
class UserRepositoryTest {

    @Test
    void persistsUser(JEHibernate je) {
        var repo = je.repositories().get(UserRepository.class);
        repo.create(new User("alice"));
        assertThat(repo.count()).isEqualTo(1);
    }
}
```

Attributes:

| Attribute | Default | Meaning |
|---|---|---|
| `database` | `H2` | `H2` (in-memory) or `POSTGRES`/`MYSQL`/`MARIADB`/`MSSQL` (Testcontainers) |
| `reset` | `TRUNCATE_ALL` | `NONE`, `TRUNCATE_ALL`, `DROP_CREATE`, `ROLLBACK_TX` |
| `scanPackages` | `{}` | entity/repository packages |
| `migration` | `false` | run Flyway at bootstrap |
| `ddlAuto` | `create-drop` | Hibernate `hbm2ddl.auto` |

Reset runs after every test method (FK-aware, via Hibernate's `SchemaManager`), so each test starts
from a clean database — no state leak across a large suite. `ROLLBACK_TX` is not supported with
JEHibernate's per-operation repositories and falls back to `TRUNCATE_ALL` (logged).

## Testcontainers

```java
@JEHibernateTest(database = TestDatabase.POSTGRES, migration = true, scanPackages = "com.example")
class OrderRepositoryIT {
    @Test void persistsOrder(JEHibernate je) { /* ... */ }
}
```

Container-backed tests are **skipped (not failed) when Docker is unavailable**, so an H2-only
environment keeps the build green. With Docker present the first run starts within ~15s of the
image pull.

## Fixtures

```java
User dave = Fixtures.persist(emf, new User("dave"));

// Fluent builder pattern (extend Fixtures.Builder):
final class TestEntities {
    static UserBuilder aUser() { return new UserBuilder(); }
    static final class UserBuilder extends Fixtures.Builder<User> {
        private String role = "USER";
        UserBuilder withRole(String role) { this.role = role; return this; }
        @Override protected User build() { return new User(role); }
    }
}
User admin = TestEntities.aUser().withRole("ADMIN").persist(emf);
```

## Spring Boot test slice

A `@JEHibernateRepositoryTest` slice analogous to Spring's `@DataJpaTest` is **deferred** until the
`jehibernate-spring-boot` auto-configuration is built — a slice needs that auto-config to bootstrap
JEHibernate inside a Spring `ApplicationContext`. For non-Spring repository tests, `@JEHibernateTest`
covers the same need today.
