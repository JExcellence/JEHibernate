package de.jexcellence.jehibernate.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Adapts a JEHibernate {@link TenantResolver} to Hibernate's
 * {@link CurrentTenantIdentifierResolver}, with a strict-mode leak guard.
 * <p>
 * <b>Leak guard (strict mode):</b> when no tenant can be resolved, a strict resolver throws instead
 * of letting a query run untenanted. For the {@code DISCRIMINATOR} strategy this is the difference
 * between an exception and a silent cross-tenant data leak, so strict mode is the default there.
 *
 * @since 4.0
 */
public final class TenantContextResolver implements CurrentTenantIdentifierResolver<String> {

    private final TenantResolver resolver;
    private final boolean strict;
    private final String defaultTenantId;

    /**
     * @param resolver        the tenant resolver (must not be null)
     * @param strict          if true, throw when no tenant is resolved (leak guard)
     * @param defaultTenantId tenant used when not strict and none is resolved (may be null)
     */
    public TenantContextResolver(TenantResolver resolver, boolean strict, String defaultTenantId) {
        if (resolver == null) {
            throw new IllegalArgumentException("TenantResolver must not be null");
        }
        this.resolver = resolver;
        this.strict = strict;
        this.defaultTenantId = defaultTenantId;
    }

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantId = resolver.resolveTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        if (strict) {
            throw new IllegalStateException(
                "No tenant bound to the current context — refusing to run a query that could leak "
                    + "across tenants. Open a TenantContext scope (or configure a default tenant).");
        }
        if (defaultTenantId != null) {
            return defaultTenantId;
        }
        throw new IllegalStateException(
            "No current tenant resolved and no default tenant configured");
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
