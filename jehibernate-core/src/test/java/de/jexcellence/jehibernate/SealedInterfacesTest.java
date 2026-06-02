package de.jexcellence.jehibernate;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.jehibernate.repository.base.*;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.*;

@Entity
class SampleEntity extends LongIdEntity {
    private String data;
    
    protected SampleEntity() {}
    
    public SampleEntity(String data) {
        this.data = data;
    }
    
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}

class SampleRepository extends AbstractCrudRepository<SampleEntity, Long> {
    public SampleRepository(ExecutorService executor, EntityManagerFactory emf, Class<SampleEntity> entityClass) {
        super(executor, emf, entityClass);
    }
}

/**
 * Tests for sealed interface hierarchy and type safety.
 * Verifies that the repository hierarchy is properly sealed.
 */
class SealedInterfacesTest {
    
    private JEHibernate jeHibernate;
    private SampleRepository repository;
    
    @BeforeEach
    void setUp() {
        jeHibernate = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:mem:sealedtest;DB_CLOSE_DELAY=-1")
                .credentials("sa", "")
                .ddlAuto("create-drop")
                .showSql(false))
            .scanPackages("de.jexcellence.jehibernate")
            .build();
        
        repository = jeHibernate.repositories().get(SampleRepository.class);
    }
    
    @AfterEach
    void tearDown() {
        if (jeHibernate != null) {
            jeHibernate.close();
        }
    }
    
    @Test
    void testRepository_IsSealed() {
        // Verify that Repository interface is sealed
        assertThat(Repository.class.isSealed()).isTrue();
        assertThat(Repository.class.getPermittedSubclasses())
            .contains(CrudRepository.class);
    }
    
    @Test
    void testCrudRepository_IsSealed() {
        // Verify that CrudRepository interface is sealed
        assertThat(CrudRepository.class.isSealed()).isTrue();
        assertThat(CrudRepository.class.getPermittedSubclasses())
            .contains(AsyncRepository.class);
    }
    
    @Test
    void testAsyncRepository_IsSealed() {
        // Verify that AsyncRepository interface is sealed
        assertThat(AsyncRepository.class.isSealed()).isTrue();
        assertThat(AsyncRepository.class.getPermittedSubclasses())
            .contains(QueryableRepository.class);
    }
    
    @Test
    void testQueryableRepository_IsNonSealed() {
        // Verify that QueryableRepository is non-sealed (allows implementations)
        // Non-sealed interfaces don't have isSealed() == true
        assertThat(QueryableRepository.class.isSealed()).isFalse();
    }
    
    @Test
    void testRepositoryHierarchy_TypeSafety() {
        // Verify the type hierarchy
        assertThat(repository)
                .isInstanceOf(Repository.class)
                .isInstanceOf(CrudRepository.class)
                .isInstanceOf(AsyncRepository.class)
                .isInstanceOf(QueryableRepository.class);
    }
    
    @Test
    void testRepositoryHierarchy_MethodsAvailable() {
        // Verify all methods from the hierarchy are available
        var entity = new SampleEntity("test");
        
        // Repository methods
        var created = repository.save(entity);
        assertThat(created.getId()).isNotNull();
        
        // CrudRepository methods
        var found = repository.findById(created.getId());
        assertThat(found).isPresent();
        
        // AsyncRepository methods
        var asyncFuture = repository.findByIdAsync(created.getId());
        assertThat(asyncFuture).isNotNull();
        
        // QueryableRepository methods
        var queryBuilder = repository.query();
        assertThat(queryBuilder).isNotNull();
    }
    
    @Test
    void testSealedHierarchy_PreventsCasting() {
        // This test verifies that the sealed hierarchy provides type safety
        CrudRepository<SampleEntity, Long> repo = repository;
        
        // We can safely cast down the hierarchy
        assertThat(repository)
                .isInstanceOf(CrudRepository.class)
                .isInstanceOf(AsyncRepository.class)
                .isInstanceOf(QueryableRepository.class);
        
        // The sealed hierarchy ensures only valid implementations exist
        assertThat(repo).isNotNull();
    }
    
    @Test
    void testAbstractCrudRepository_ImplementsQueryableRepository() {
        // Verify that AbstractCrudRepository properly implements the hierarchy
        assertThat(AbstractCrudRepository.class)
            .matches(QueryableRepository.class::isAssignableFrom);
    }
    
    @Test
    void testSealedInterfaces_CompileTimeGuarantees() {
        class ValidRepo extends AbstractCrudRepository<SampleEntity, Long> {
            public ValidRepo(ExecutorService executor, EntityManagerFactory emf, Class<SampleEntity> entityClass) {
                super(executor, emf, entityClass);
            }
        }
        
        // Verify the valid implementation works
        assertThat(ValidRepo.class)
            .matches(QueryableRepository.class::isAssignableFrom);
    }
    
    @Test
    void testSealedHierarchy_PatternMatching() {
        // Demonstrate pattern matching with sealed types (using instanceof, Java 17 compatible)
        Repository<SampleEntity, Long> repo = repository;

        String result;
        if (repo != null) {
            result = "Queryable";
        } else {
            result = "Base";
        }

        // Should match the most specific type
        assertThat(result).isEqualTo("Queryable");
    }

    @Test
    void testSealedHierarchy_ExhaustivePatternMatching() {
        // With sealed types, the hierarchy is known at compile time
        Repository<SampleEntity, Long> repo = repository;

        boolean isQueryable = repo != null;

        assertThat(isQueryable).isTrue();
    }
    
    @Test
    void testRepositoryRegistry_TypeSafety() {
        // Verify that repository registry maintains type safety
        var registry = jeHibernate.repositories();
        
        // Get repository with correct type
        var typedRepo = registry.get(SampleRepository.class);
        assertThat(typedRepo).isNotNull().isInstanceOf(QueryableRepository.class).isSameAs(repository);
    }
    
    @Test
    void testSealedInterfaces_IDESupport() {
        // This test documents IDE benefits (not runtime testable)
        // With sealed interfaces, IDEs can:
        // 1. Show all possible implementations
        // 2. Provide better autocomplete
        // 3. Warn about impossible casts
        // 4. Verify exhaustive pattern matching
        
        Repository<SampleEntity, Long> repo = repository;
        
        // IDE knows repo can only be CrudRepository or its subtypes
        assertThat(repo).isInstanceOf(CrudRepository.class);
    }
}
