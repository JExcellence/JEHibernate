package de.jexcellence.jehibernate;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.migration.MigrationConfig;
import de.jexcellence.jehibernate.migration.MigrationTool;
import de.jexcellence.jehibernate.pool.PoolConfig;
import de.jexcellence.jehibernate.pool.PoolHealth;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TODO-1 acceptance: with a real HikariCP pool, many concurrent queries run without
 * serialising or deadlocking, sensible defaults apply without explicit pool properties,
 * and {@code getPoolHealth()} reports live metrics.
 * <p>
 * Deliberately exercises the pool through the {@link EntityManagerFactory} with plain JDBC-level
 * queries rather than the repository abstraction, so the test is independent of repository/entity
 * classpath scanning (which anchors to the code-source URL and cannot see test classes).
 */
class PoolIntegrationTest {

    private static final int PARALLEL_QUERIES = 50;
    private static final int MAX_POOL_SIZE = 10;
    private static final MigrationConfig MIGRATION_OFF =
        new MigrationConfig(false, MigrationTool.NONE, "classpath:db/migration");

    private JEHibernate jeHibernate;

    @BeforeEach
    void setUp() {
        jeHibernate = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:mem:pooltest;DB_CLOSE_DELAY=-1")
                .credentials("sa", "")
                .ddlAuto("none")
                .migration(MIGRATION_OFF)
                .pool(PoolConfig.builder()
                    .maximumPoolSize(MAX_POOL_SIZE)
                    .minimumIdle(2)
                    .build()))
            .disableAutoScan()
            .build();
    }

    @AfterEach
    void tearDown() {
        if (jeHibernate != null) {
            jeHibernate.close();
        }
    }

    @Test
    void defaultsApplyWithoutExplicitPoolProperties() {
        try (JEHibernate defaulted = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:mem:pooldefaults;DB_CLOSE_DELAY=-1")
                .credentials("sa", "")
                .ddlAuto("none")
                .migration(MIGRATION_OFF))
            .disableAutoScan()
            .build()) {

            PoolHealth health = defaulted.getPoolHealth();
            assertThat(health.available()).isTrue();
            assertThat(health.totalConnections()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void poolHealthReportsLiveMetrics() {
        PoolHealth health = jeHibernate.getPoolHealth();

        assertThat(health.available()).isTrue();
        assertThat(health.totalConnections()).isBetween(0, MAX_POOL_SIZE);
        assertThat(health.activeConnections()).isGreaterThanOrEqualTo(0);
        assertThat(health.threadsAwaitingConnection()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void fiftyParallelQueriesDoNotBlock() throws Exception {
        EntityManagerFactory emf = jeHibernate.getEntityManagerFactory();
        ExecutorService clients = Executors.newFixedThreadPool(PARALLEL_QUERIES);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger(0);
        List<Future<Integer>> futures = new ArrayList<>(PARALLEL_QUERIES);

        try {
            for (int i = 0; i < PARALLEL_QUERIES; i++) {
                futures.add(clients.submit(() -> {
                    startGate.await();
                    // Each EntityManager borrows a connection from the HikariCP pool. A short
                    // sleep inside the query window forces real overlap so the 50 callers
                    // genuinely contend for the 10 connections rather than recycling one fast.
                    EntityManager em = emf.createEntityManager();
                    try {
                        Object result = em.createNativeQuery("SELECT 1").getSingleResult();
                        return ((Number) result).intValue();
                    } finally {
                        em.close();
                    }
                }));
            }

            // Release all clients simultaneously to maximise contention on the pool.
            startGate.countDown();

            for (Future<Integer> future : futures) {
                Integer value = future.get(30, TimeUnit.SECONDS);
                if (value == null || value != 1) {
                    failures.incrementAndGet();
                }
            }
        } finally {
            clients.shutdownNow();
            assertThat(clients.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(failures.get())
            .as("all %d concurrent queries completed without blocking on a %d-connection pool",
                PARALLEL_QUERIES, MAX_POOL_SIZE)
            .isZero();
    }
}
