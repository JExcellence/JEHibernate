package de.jexcellence.mtenant;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.jehibernate.migration.MigrationConfig;
import de.jexcellence.jehibernate.migration.MigrationTool;
import de.jexcellence.jehibernate.pool.PoolHealth;
import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import de.jexcellence.jehibernate.tenant.MultiTenancyConfig;
import de.jexcellence.jehibernate.tenant.TenantContext;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Entity
@Table(name = "tenant_note")
class TenantNote extends LongIdEntity {

    @TenantId
    private String tenantId;

    private String text;

    protected TenantNote() {}

    TenantNote(String text) {
        this.text = text;
    }

    String getTenantId() { return tenantId; }
    String getText() { return text; }
}

class TenantNoteRepository extends AbstractCrudRepository<TenantNote, Long> {
    TenantNoteRepository(ExecutorService executor, EntityManagerFactory emf, Class<TenantNote> entityClass) {
        super(executor, emf, entityClass);
    }
}

/**
 * Acceptance for the DISCRIMINATOR strategy:
 * <ul>
 *   <li>two tenants share one entity class; tenant A's rows are invisible to tenant B;</li>
 *   <li>a forgotten tenant context throws (leak guard) instead of leaking across tenants;</li>
 *   <li>tenant switch within one thread via try-with-resources restores cleanly;</li>
 *   <li>the connection pool is shared across tenants (no pool-per-tenant).</li>
 * </ul>
 * Entities live in their own package so the {@code @TenantId} mapping never leaks into other test
 * suites' package scans.
 */
class DiscriminatorMultiTenancyTest {

    private static final MigrationConfig MIGRATION_OFF =
        new MigrationConfig(false, MigrationTool.NONE, "classpath:db/migration");

    private JEHibernate jeHibernate;
    private TenantNoteRepository noteRepo;

    @BeforeEach
    void setUp() {
        jeHibernate = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:mem:disctenant;DB_CLOSE_DELAY=-1")
                .credentials("sa", "")
                .ddlAuto("create-drop")
                .migration(MIGRATION_OFF)
                .multiTenancy(MultiTenancyConfig.discriminator()))
            .scanPackages("de.jexcellence.mtenant")
            .build();

        noteRepo = jeHibernate.repositories().get(TenantNoteRepository.class);
    }

    @AfterEach
    void tearDown() {
        if (jeHibernate != null) {
            jeHibernate.close();
        }
    }

    @Test
    void tenantsAreIsolated() {
        try (var ignored = TenantContext.open("acme")) {
            noteRepo.create(new TenantNote("acme note"));
        }
        try (var ignored = TenantContext.open("globex")) {
            noteRepo.create(new TenantNote("globex note"));
        }

        try (var ignored = TenantContext.open("acme")) {
            var notes = noteRepo.findAll();
            assertThat(notes).extracting(TenantNote::getText).containsExactly("acme note");
            assertThat(notes).allMatch(n -> "acme".equals(n.getTenantId()));
        }
        try (var ignored = TenantContext.open("globex")) {
            assertThat(noteRepo.findAll()).extracting(TenantNote::getText).containsExactly("globex note");
        }
    }

    @Test
    void missingTenantContextThrows() {
        // Strict resolver refuses to run untenanted — exception, not a silent cross-tenant read.
        assertThatThrownBy(noteRepo::findAll).isInstanceOf(Exception.class);
    }

    @Test
    void threadSwitchRestoresPreviousTenant() {
        assertThat(TenantContext.currentOrNull()).isNull();
        try (var outer = TenantContext.open("acme")) {
            assertThat(TenantContext.currentOrNull()).isEqualTo("acme");
            try (var inner = TenantContext.open("globex")) {
                assertThat(TenantContext.currentOrNull()).isEqualTo("globex");
            }
            assertThat(TenantContext.currentOrNull()).isEqualTo("acme");
        }
        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test
    void poolIsSharedAcrossTenants() {
        try (var ignored = TenantContext.open("acme")) {
            noteRepo.create(new TenantNote("a"));
        }
        try (var ignored = TenantContext.open("globex")) {
            noteRepo.create(new TenantNote("b"));
        }
        PoolHealth health = jeHibernate.getPoolHealth();
        assertThat(health.available()).isTrue();
        // One shared pool, bounded by the default maximumPoolSize (10) — not one pool per tenant.
        assertThat(health.totalConnections()).isBetween(0, 10);
    }
}
