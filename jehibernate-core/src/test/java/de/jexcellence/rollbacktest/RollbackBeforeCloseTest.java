package de.jexcellence.rollbacktest;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.exception.TransactionException;
import de.jexcellence.jehibernate.migration.MigrationConfig;
import de.jexcellence.jehibernate.migration.MigrationTool;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A failed transaction must be rolled back on the JDBC connection before the EntityManager is
 * closed. Up to 4.2.0 the transactional helpers closed the EntityManager in try-with-resources
 * first, so the rollback hit a closed logical connection, only logged a warning, and the open
 * transaction was left to the pool. With HikariCP the pool happened to roll it back on return;
 * other data sources were not covered.
 * <p>
 * The test feeds JEHibernate an unpooled H2 data source that counts {@code rollback()} calls
 * reaching the physical connection, then makes each transactional entry point fail after a
 * flushed insert.
 */
class RollbackBeforeCloseTest {

    private static final MigrationConfig MIGRATION_OFF =
        new MigrationConfig(false, MigrationTool.NONE, "classpath:db/migration");

    private RollbackCountingDataSource dataSource;
    private JEHibernate jeHibernate;
    private RollbackThingRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:rollbackbeforeclose;DB_CLOSE_DELAY=-1");
        h2.setUser("sa");
        dataSource = new RollbackCountingDataSource(h2);

        jeHibernate = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:mem:rollbackbeforeclose")
                .dataSource(dataSource)
                .ddlAuto("create-drop")
                .migration(MIGRATION_OFF))
            .scanPackages("de.jexcellence.rollbacktest")
            .build();
        repository = jeHibernate.repositories().get(RollbackThingRepository.class);
        dataSource.rollbacks.set(0);
    }

    @AfterEach
    void tearDown() {
        jeHibernate.close();
    }

    @Test
    void transactionTemplateRollsBackBeforeClosing() {
        assertRolledBack(() -> jeHibernate.transactionTemplate().executeVoid(em -> {
            em.persist(new RollbackThing("template"));
            em.flush();
            throw new IllegalStateException("abort");
        }));
    }

    @Test
    void repositoryTransactionRollsBackBeforeClosing() {
        assertRolledBack(() -> repository.insertThenFail("repository"));
    }

    @Test
    void repositorySessionRollsBackBeforeClosing() {
        assertRolledBack(() -> repository.withSession(session -> {
            session.persist(new RollbackThing("repository-session"));
            session.flush();
            throw new IllegalStateException("abort");
        }));
    }

    @Test
    void jeHibernateSessionRollsBackBeforeClosing() {
        assertRolledBack(() -> jeHibernate.withSession(session -> {
            session.persist(new RollbackThing("session"));
            session.flush();
            throw new IllegalStateException("abort");
        }));
    }

    private void assertRolledBack(Executable failingWork) {
        assertThatThrownBy(failingWork::execute).isInstanceOf(TransactionException.class);

        assertThat(dataSource.rollbacks.get())
            .as("rollback() reached the JDBC connection")
            .isEqualTo(1);
        assertThat(repository.count()).isZero();
    }

    /** Unpooled DataSource whose connections count {@code rollback()} calls. */
    private static final class RollbackCountingDataSource implements DataSource {

        private final DataSource delegate;
        private final AtomicInteger rollbacks = new AtomicInteger();

        private RollbackCountingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return counting(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return counting(delegate.getConnection(username, password));
        }

        private Connection counting(Connection target) {
            return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    if ("rollback".equals(method.getName()) && method.getParameterCount() == 0) {
                        rollbacks.incrementAndGet();
                    }
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }
}
