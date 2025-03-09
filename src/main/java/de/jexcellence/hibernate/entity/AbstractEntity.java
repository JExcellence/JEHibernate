package de.jexcellence.hibernate.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.engine.spi.CascadingAction;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@MappedSuperclass
public abstract class AbstractEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private int version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    public AbstractEntity() {
        // Default constructor for Hibernate
    }

    @PrePersist
    protected void onPrePersist() {
        if (Objects.isNull(createdAt)) {
            createdAt = LocalDateTime.now();
        }
        if (Objects.isNull(updatedAt)) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onPreUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @PostPersist
    protected void onPostPersist() {
        // Optional post-persist logic
    }

    @PostUpdate
    protected void onPostUpdate() {
        // Optional post-update logic
    }

    @PreRemove
    protected void onPreRemove() {
        // Optional pre-remove logic
    }

    public void softDelete() {
        if (!deleted) {
            deleted = true;
            onPreUpdate();
        }
    }

    public boolean isNew() {
        return Objects.isNull(id);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        if (!isNew()) {
            throw new IllegalStateException("Id cannot be set after entity persistence.");
        }
        this.id = id;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        if (!isNew()) {
            throw new IllegalStateException("CreatedAt cannot be modified after entity persistence.");
        }
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        if (deleted) {
            softDelete();
        } else {
            this.deleted = false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "id=" + id +
                ", version=" + version +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", deleted=" + deleted +
                '}';
    }
}