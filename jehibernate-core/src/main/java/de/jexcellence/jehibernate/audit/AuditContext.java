package de.jexcellence.jehibernate.audit;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the active {@link AuditUserResolver}.
 * <p>
 * Envers instantiates the revision listener itself (no dependency injection), so the listener reads
 * the resolver from here. Set it once at startup (e.g. to a resolver backed by your security
 * context or plugin session), or per-request if your resolver is request-scoped.
 *
 * @since 4.0
 */
public final class AuditContext {

    private static final AtomicReference<AuditUserResolver> RESOLVER =
        new AtomicReference<>(AuditUserResolver.none());

    private AuditContext() {
    }

    /**
     * Sets the active resolver. A {@code null} argument resets to {@link AuditUserResolver#none()}.
     *
     * @param resolver the resolver to use
     */
    public static void setResolver(AuditUserResolver resolver) {
        RESOLVER.set(resolver != null ? resolver : AuditUserResolver.none());
    }

    /**
     * @return the active resolver, never {@code null}
     */
    public static AuditUserResolver resolver() {
        return RESOLVER.get();
    }
}
