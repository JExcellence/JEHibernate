package de.jexcellence.jehibernate.tenant;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Immutable multi-tenancy configuration. Multi-tenancy is <b>off by default</b>
 * ({@link MultiTenancyStrategy#NONE}); consumers opt in via one of the factory methods or the
 * builder.
 *
 * @param strategy          the isolation strategy
 * @param resolver          how the current tenant is resolved (defaults to {@link TenantContext})
 * @param strict            throw when no tenant is resolved (leak guard); default on for DISCRIMINATOR
 * @param defaultTenantId   tenant used when not strict and none resolved (may be null)
 * @param defaultSchema     schema restored on connection release for the SCHEMA strategy
 * @param tenantDataSources tenant → DataSource map for the DATABASE strategy (unmodifiable copy)
 * @since 4.0
 */
public record MultiTenancyConfig(
    MultiTenancyStrategy strategy,
    TenantResolver resolver,
    boolean strict,
    String defaultTenantId,
    String defaultSchema,
    Map<String, DataSource> tenantDataSources
) {

    /** Default schema connections are reset to on release for the SCHEMA strategy. */
    public static final String DEFAULT_SCHEMA = "PUBLIC";

    public MultiTenancyConfig {
        if (strategy == null) {
            throw new IllegalArgumentException("MultiTenancyStrategy must not be null");
        }
        if (resolver == null) {
            throw new IllegalArgumentException("TenantResolver must not be null");
        }
        if (defaultSchema == null || defaultSchema.isBlank()) {
            throw new IllegalArgumentException("defaultSchema must not be null or blank");
        }
        tenantDataSources = Map.copyOf(tenantDataSources == null ? Map.of() : tenantDataSources);
        if (strategy == MultiTenancyStrategy.DATABASE && tenantDataSources.isEmpty()) {
            throw new IllegalArgumentException(
                "DATABASE strategy requires a non-empty tenant -> DataSource map");
        }
    }

    /** @return multi-tenancy disabled (single-tenant). */
    public static MultiTenancyConfig disabled() {
        return new MultiTenancyConfig(
            MultiTenancyStrategy.NONE, TenantResolver.fromContext(), false, null, DEFAULT_SCHEMA, Map.of());
    }

    /** @return DISCRIMINATOR strategy (strict leak guard on), resolving the tenant from {@link TenantContext}. */
    public static MultiTenancyConfig discriminator() {
        return new MultiTenancyConfig(
            MultiTenancyStrategy.DISCRIMINATOR, TenantResolver.fromContext(), true, null, DEFAULT_SCHEMA, Map.of());
    }

    /**
     * @param defaultSchema schema to reset connections to on release (e.g. {@code "PUBLIC"})
     * @return SCHEMA strategy resolving the tenant from {@link TenantContext}
     */
    public static MultiTenancyConfig schema(String defaultSchema) {
        return new MultiTenancyConfig(
            MultiTenancyStrategy.SCHEMA, TenantResolver.fromContext(), true, null, defaultSchema, Map.of());
    }

    /**
     * @param tenantDataSources tenant identifier → DataSource
     * @return DATABASE strategy resolving the tenant from {@link TenantContext}
     */
    public static MultiTenancyConfig database(Map<String, DataSource> tenantDataSources) {
        return new MultiTenancyConfig(
            MultiTenancyStrategy.DATABASE, TenantResolver.fromContext(), true, null, DEFAULT_SCHEMA, tenantDataSources);
    }

    /**
     * @param resolver custom tenant resolver
     * @return a copy of this configuration using the given resolver
     */
    public MultiTenancyConfig withResolver(TenantResolver resolver) {
        return new MultiTenancyConfig(strategy, resolver, strict, defaultTenantId, defaultSchema, tenantDataSources);
    }

    /**
     * @param strict        whether to throw when no tenant is resolved
     * @param defaultTenantId tenant used when not strict (may be null)
     * @return a copy of this configuration with the given strictness
     */
    public MultiTenancyConfig withStrict(boolean strict, String defaultTenantId) {
        return new MultiTenancyConfig(strategy, resolver, strict, defaultTenantId, defaultSchema, tenantDataSources);
    }
}
