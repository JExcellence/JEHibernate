package de.jexcellence.jehibernate.audit;

import de.jexcellence.jehibernate.tenant.TenantContext;
import org.hibernate.envers.RevisionListener;

/**
 * Populates each {@link JEHibernateRevisionEntity} when Envers opens a new revision: the acting
 * user and source IP from {@link AuditContext}, and the current tenant from {@link TenantContext}.
 *
 * @since 4.0
 */
public final class JEHibernateRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        if (revisionEntity instanceof JEHibernateRevisionEntity revision) {
            AuditUserResolver resolver = AuditContext.resolver();
            revision.setUserId(resolver.resolveUserId());
            revision.setSourceIp(resolver.resolveSourceIp());
            revision.setTenantId(TenantContext.currentOrNull());
        }
    }
}
