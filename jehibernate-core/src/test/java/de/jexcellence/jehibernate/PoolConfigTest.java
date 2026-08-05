package de.jexcellence.jehibernate;

import de.jexcellence.jehibernate.pool.PoolConfig;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pool configuration binding, including backwards compatibility with the legacy Hibernate
 * built-in-pool property.
 */
class PoolConfigTest {

    @Test
    void legacyHibernatePoolSizeMapsToMaximumPoolSize() {
        Properties props = new Properties();
        props.setProperty("hibernate.connection.pool_size", "100");

        PoolConfig config = PoolConfig.fromProperties(props, PoolConfig.defaults());

        assertThat(config.maximumPoolSize()).isEqualTo(100);
    }

    @Test
    void explicitJEHibernateKeyOverridesLegacyPoolSize() {
        Properties props = new Properties();
        props.setProperty("hibernate.connection.pool_size", "100");
        props.setProperty("jehibernate.pool.maximumPoolSize", "20");

        PoolConfig config = PoolConfig.fromProperties(props, PoolConfig.defaults());

        assertThat(config.maximumPoolSize()).isEqualTo(20);
    }

    @Test
    void fallsBackToDefaultWhenNoPoolSizeGiven() {
        PoolConfig config = PoolConfig.fromProperties(new Properties(), PoolConfig.defaults());

        assertThat(config.maximumPoolSize()).isEqualTo(PoolConfig.defaults().maximumPoolSize());
    }
}
