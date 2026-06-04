package de.jexcellence.jehibernate.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import java.util.Objects;

/**
 * Envers revision entity recording, for every audited change: the revision number, timestamp, the
 * acting user and (optionally) source IP, and the tenant in effect.
 * <p>
 * Activate by registering this entity (e.g. {@code ConfigurationBuilder.enableAudit()} or by adding
 * {@code de.jexcellence.jehibernate.audit} to the scanned packages) with {@code hibernate-envers}
 * on the classpath. Populated by {@link JEHibernateRevisionListener}.
 * <p>
 * <b>DSGVO:</b> {@code userId}, {@code sourceIp} and {@code tenantId} are personal/identifying data
 * retained per revision. Define a retention period (Art. 5 Abs. 1 lit. e) and a purge process;
 * deleting a source row does <b>not</b> remove its audit history (relevant to Art. 17).
 *
 * @since 4.0
 */
@Entity
@Table(name = "jehibernate_revision")
@RevisionEntity(JEHibernateRevisionListener.class)
public class JEHibernateRevisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    @Column(name = "revision_id")
    private int id;

    @RevisionTimestamp
    @Column(name = "revision_timestamp")
    private long timestamp;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "source_ip")
    private String sourceIp;

    @Column(name = "tenant_id")
    private String tenantId;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof JEHibernateRevisionEntity other)) {
            return false;
        }
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
