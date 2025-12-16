package de.jexcellence.hibernate.repository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

/**
 * Immutable data class that stores repository registration information.
 *
 * @param <T>  the entity type managed by the repository
 * @param <ID> the identifier type of the entity
 * @param <K>  the cache key type (for GenericCachedRepository)
 * @author JExcellence
 * @version 1.0
 * @since 1.0
 */
public final class RepositoryMetadata<T, ID, K> {

    private final Class<? extends BaseRepository<T, ID>> repositoryClass;
    private final Class<T> entityClass;
    private final Function<T, K> keyExtractor;

    /**
     * Creates a new RepositoryMetadata instance.
     *
     * @param repositoryClass the repository class that extends BaseRepository
     * @param entityClass     the entity class managed by the repository
     * @param keyExtractor    optional key extractor function for CachedRepository
     * @throws IllegalArgumentException if repositoryClass does not extend BaseRepository
     */
    public RepositoryMetadata(
        @NotNull final Class<? extends BaseRepository<T, ID>> repositoryClass,
        @NotNull final Class<T> entityClass,
        @Nullable final Function<T, K> keyExtractor
    ) {
        this.repositoryClass = repositoryClass;
        this.entityClass = entityClass;

        if (!BaseRepository.class.isAssignableFrom(repositoryClass)) {
            throw new IllegalArgumentException(
                "Repository class " + repositoryClass.getName() + " must extend BaseRepository"
            );
        }

        this.keyExtractor = keyExtractor;
    }

    /**
     * Gets the repository class.
     *
     * @return the repository class that extends BaseRepository
     */
    @NotNull
    public Class<? extends BaseRepository<T, ID>> getRepositoryClass() {
        return this.repositoryClass;
    }

    /**
     * Gets the entity class managed by the repository.
     *
     * @return the entity class
     */
    @NotNull
    public Class<T> getEntityClass() {
        return this.entityClass;
    }

    /**
     * Gets the optional key extractor function for cached repositories.
     *
     * @return the key extractor function, or null if not applicable
     */
    @Nullable
    public Function<T, K> getKeyExtractor() {
        return this.keyExtractor;
    }

    /**
     * Checks if this metadata includes a key extractor.
     *
     * @return {@code true} if a key extractor is present
     */
    public boolean hasKeyExtractor() {
        return this.keyExtractor != null;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        var that = (RepositoryMetadata<?, ?, ?>) o;
        return Objects.equals(this.repositoryClass, that.repositoryClass) &&
               Objects.equals(this.entityClass, that.entityClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.repositoryClass, this.entityClass);
    }

    @Override
    public String toString() {
        return "RepositoryMetadata{" +
               "repositoryClass=" + this.repositoryClass.getName() +
               ", entityClass=" + this.entityClass.getName() +
               ", hasKeyExtractor=" + this.hasKeyExtractor() +
               '}';
    }
}
