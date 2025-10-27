package de.jexcellence.hibernate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Base entity providing id, auditing timestamps and soft-delete support.
 */
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

    protected AbstractEntity() {
        // JPA requirement
    }

    @PrePersist
    protected void onPrePersist() {
        final LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onPreUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PostPersist
    protected void onPostPersist() {
        // hook for subclasses
    }

    @PostUpdate
    protected void onPostUpdate() {
        // hook for subclasses
    }

    @PreRemove
    protected void onPreRemove() {
        // hook for subclasses
    }

    public void softDelete() {
        if (!this.deleted) {
            this.deleted = true;
            this.onPreUpdate();
        }
    }

    public boolean isNew() {
        return this.id == null;
    }

    @Nullable
    public Long getId() {
        return this.id;
    }

    public void setId(@NotNull final Long id) {
        if (!this.isNew()) {
            throw new IllegalStateException("Id cannot be set after persistence");
        }
        this.id = Objects.requireNonNull(id, "id");
    }

    public int getVersion() {
        return this.version;
    }

    public void setVersion(final int version) {
        this.version = version;
    }

    @Nullable
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(@NotNull final LocalDateTime createdAt) {
        if (!this.isNew()) {
            throw new IllegalStateException("createdAt cannot be changed after persistence");
        }
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    @Nullable
    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    public void setUpdatedAt(@NotNull final LocalDateTime updatedAt) {
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public boolean isDeleted() {
        return this.deleted;
    }

    public void setDeleted(final boolean deleted) {
        if (deleted) {
            this.softDelete();
        } else {
            this.deleted = false;
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AbstractEntity that)) {
            return false;
        }
        return Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
            "id=" + this.id +
            ", version=" + this.version +
            ", createdAt=" + this.createdAt +
            ", updatedAt=" + this.updatedAt +
            ", deleted=" + this.deleted +
            '}';
    }
}
