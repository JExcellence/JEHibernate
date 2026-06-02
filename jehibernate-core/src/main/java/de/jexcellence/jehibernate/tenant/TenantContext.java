package de.jexcellence.jehibernate.tenant;

import java.util.Optional;

/**
 * Thread-bound holder for the current tenant identifier.
 * <p>
 * Tenant scope is opened with try-with-resources so it is always cleaned up, and scopes nest
 * (closing restores the previous tenant), which makes switching tenants within one thread safe:
 * <pre>{@code
 * try (var ignored = TenantContext.open("acme")) {
 *     acmeRepo.findAll();              // sees only acme's data
 *     try (var ignored2 = TenantContext.open("globex")) {
 *         globexRepo.findAll();        // sees only globex's data
 *     }
 *     // back to acme here
 * }
 * // no tenant bound here
 * }</pre>
 *
 * @since 4.0
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * Binds {@code tenantId} to the current thread until the returned {@link Scope} is closed.
     *
     * @param tenantId the tenant identifier (must not be null or blank)
     * @return an {@link AutoCloseable} scope that restores the previous tenant on close
     */
    public static Scope open(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
        String previous = CURRENT.get();
        CURRENT.set(tenantId);
        return new Scope(previous);
    }

    /**
     * @return the current tenant identifier, or {@code null} if none is bound
     */
    public static String currentOrNull() {
        return CURRENT.get();
    }

    /**
     * @return the current tenant identifier, if bound
     */
    public static Optional<String> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * @return the current tenant identifier
     * @throws IllegalStateException if no tenant is bound
     */
    public static String require() {
        String current = CURRENT.get();
        if (current == null) {
            throw new IllegalStateException("No tenant bound to the current thread");
        }
        return current;
    }

    /**
     * Removes any tenant binding from the current thread. Prefer {@link Scope} over calling this
     * directly; use it only from framework cleanup hooks (e.g. a servlet filter's finally block).
     */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * An open tenant scope. Closing restores the tenant that was bound before {@link #open(String)}.
     */
    public static final class Scope implements AutoCloseable {

        private final String previous;

        private Scope(String previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
