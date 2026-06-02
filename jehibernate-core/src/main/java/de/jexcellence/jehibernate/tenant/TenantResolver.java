package de.jexcellence.jehibernate.tenant;

/**
 * SPI for resolving the current tenant identifier.
 * <p>
 * The default implementation reads {@link TenantContext} (a thread-local). Consumers override it
 * to derive the tenant from their environment instead — a plugin from the server/world id, a
 * Spring Boot app from a subdomain, JWT claim, or request header.
 *
 * @since 4.0
 */
@FunctionalInterface
public interface TenantResolver {

    /**
     * @return the current tenant identifier, or {@code null} if none can be determined
     */
    String resolveTenantId();

    /**
     * The default resolver, backed by {@link TenantContext}.
     *
     * @return a resolver reading the thread-bound tenant
     */
    static TenantResolver fromContext() {
        return TenantContext::currentOrNull;
    }
}
