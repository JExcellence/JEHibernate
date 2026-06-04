package de.jexcellence.jehibernate.audit;

/**
 * SPI for resolving who is responsible for the current change, recorded on each audit revision.
 * <p>
 * A plugin supplies the acting player's UUID; a Spring Boot app supplies the
 * {@code SecurityContext} username. Set the active resolver via {@link AuditContext}.
 * <p>
 * <b>DSGVO note:</b> the values returned here (user id, source IP) are personal data and are stored
 * on every revision. Collect {@code sourceIp} only when you have a lawful basis (Art. 5/6 DSGVO),
 * and apply a retention limit to audit data (Art. 5 Abs. 1 lit. e).
 *
 * @since 4.0
 */
@FunctionalInterface
public interface AuditUserResolver {

    /**
     * @return an identifier for the acting user (e.g. UUID or username), or {@code null} if unknown
     */
    String resolveUserId();

    /**
     * @return the source IP of the request, or {@code null}. Default: {@code null} (opt-in).
     */
    default String resolveSourceIp() {
        return null;
    }

    /**
     * @return a no-op resolver that records no user information
     */
    static AuditUserResolver none() {
        return () -> null;
    }
}
