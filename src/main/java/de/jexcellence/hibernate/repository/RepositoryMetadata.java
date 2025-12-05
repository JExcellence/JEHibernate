package de.jexcellence.hibernate.repository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

/**
 * Immutable data class that stores repository registration information.
 * <p>
 * This class holds metadata about registered repositories including the repository class,
 * entity class, and an optional key extractor function for cached repositories.
 * </p>
 * 
 * @param <T> the entity type managed by the repository
 * @param <ID> the identifier type of the entity
 * @param <K> the cache key type (for GenericCachedRepository)
 */
public final class RepositoryMetadata<T, ID, K> {

    private final Class<? extends AbstractCRUDRepository<T, ID>> repositoryClass;
    private final Class<T> entityClass;
    private final Function<T, K> keyExtractor;

    /**
     * Creates a new RepositoryMetadata instance.
     * 
     * @param repositoryClass the repository class that extends AbstractCRUDRepository
     * @param entityClass the entity class managed by the repository
     * @param keyExtractor optional key extractor function for GenericCachedRepository (may be null)
     * @throws NullPointerException if repositoryClass or entityClass is null
     * @throws IllegalArgumentException if repositoryClass does not extend AbstractCRUDRepository
     */
    public RepositoryMetadata(
        @NotNull final Class<? extends AbstractCRUDRepository<T, ID>> repositoryClass,
        @NotNull final Class<T> entityClass,
        @Nullable final Function<T, K> keyExtractor
    ) {
        this.repositoryClass = repositoryClass;
        this.entityClass = entityClass;

        if (!AbstractCRUDRepository.class.isAssignableFrom(repositoryClass)) {
            throw new IllegalArgumentException(
                "Repository class " + repositoryClass.getName() + " must extend AbstractCRUDRepository"
            );
        }
        
        this.keyExtractor = keyExtractor;
    }

    /**
     * Gets the repository class.
     * 
     * @return the repository class that extends AbstractCRUDRepository
     */
    @NotNull
    public Class<? extends AbstractCRUDRepository<T, ID>> getRepositoryClass() {
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
     * @return true if a key extractor is present, false otherwise
     */
    public boolean hasKeyExtractor() {
        return this.keyExtractor != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RepositoryMetadata<?, ?, ?> that = (RepositoryMetadata<?, ?, ?>) o;
        return Objects.equals(repositoryClass, that.repositoryClass) &&
               Objects.equals(entityClass, that.entityClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repositoryClass, entityClass);
    }

    @Override
    public String toString() {
        return "RepositoryMetadata{" +
               "repositoryClass=" + repositoryClass.getName() +
               ", entityClass=" + entityClass.getName() +
               ", hasKeyExtractor=" + hasKeyExtractor() +
               '}';
    }
}
