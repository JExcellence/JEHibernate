package de.jexcellence.audittest;

import de.jexcellence.jehibernate.audit.Audit;
import de.jexcellence.jehibernate.audit.AuditContext;
import de.jexcellence.jehibernate.audit.AuditUserResolver;
import de.jexcellence.jehibernate.audit.JEHibernateRevisionEntity;
import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.migration.MigrationConfig;
import de.jexcellence.jehibernate.migration.MigrationTool;
import de.jexcellence.jehibernate.tenant.TenantContext;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance for Hibernate Envers auditing: an {@code @Audited} entity records a revision per
 * committed change; each revision captures the resolved user and the active tenant; the history
 * is queryable. Runs on H2.
 * <p>
 * Entity/repository live in their own package so {@code @Audited} never bleeds into other suites.
 */
class AuditIntegrationTest {

    private static final MigrationConfig MIGRATION_OFF =
        new MigrationConfig(false, MigrationTool.NONE, "classpath:db/migration");

    private JEHibernate jeHibernate;
    private AuditedAccountRepository accounts;

    @BeforeEach
    void setUp() {
        AuditContext.setResolver(() -> "admin");

        jeHibernate = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:mem:audittest;DB_CLOSE_DELAY=-1")
                .credentials("sa", "")
                .ddlAuto("create-drop")
                .migration(MIGRATION_OFF)
                .enableAudit())
            .scanPackages("de.jexcellence.audittest")
            .build();

        accounts = jeHibernate.repositories().get(AuditedAccountRepository.class);
    }

    @AfterEach
    void tearDown() {
        if (jeHibernate != null) {
            jeHibernate.close();
        }
        AuditContext.setResolver(AuditUserResolver.none());
    }

    @Test
    void recordsARevisionPerChange() {
        Long id;
        try (var ignored = TenantContext.open("acme")) {
            AuditedAccount account = accounts.create(new AuditedAccount("alice", 100));
            id = account.getId();
            account.setBalance(200);
            accounts.update(account);
        }

        List<AuditedAccount> history =
            Audit.getRevisions(jeHibernate.getEntityManagerFactory(), AuditedAccount.class, id);

        assertThat(history).hasSize(2);
        assertThat(history).extracting(AuditedAccount::getBalance).containsExactly(100, 200);
    }

    @Test
    void revisionCapturesUserAndTenant() {
        EntityManagerFactory emf = jeHibernate.getEntityManagerFactory();
        Long id;
        try (var ignored = TenantContext.open("acme")) {
            id = accounts.create(new AuditedAccount("bob", 50)).getId();
        }

        List<Number> revisions = Audit.getRevisionNumbers(emf, AuditedAccount.class, id);
        assertThat(revisions).hasSize(1);

        JEHibernateRevisionEntity info = Audit.getRevisionInfo(emf, revisions.get(0));
        assertThat(info.getUserId()).isEqualTo("admin");
        assertThat(info.getTenantId()).isEqualTo("acme");
        assertThat(info.getTimestamp()).isPositive();
    }
}
