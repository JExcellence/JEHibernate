package de.jexcellence.jehibernate.tenant;

/**
 * Multi-tenancy isolation strategy, switchable via configuration.
 *
 * @since 4.0
 */
public enum MultiTenancyStrategy {
    /** Multi-tenancy disabled — classic single-tenant behaviour (the default). */
    NONE,
    /** One physical database per tenant. Highest isolation, highest ops complexity. */
    DATABASE,
    /** One schema per tenant within a single database. Medium isolation, one server. */
    SCHEMA,
    /** A {@code @TenantId} discriminator column on each entity. Lowest isolation, simplest setup. */
    DISCRIMINATOR
}
