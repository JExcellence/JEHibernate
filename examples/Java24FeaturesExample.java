package examples;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.jehibernate.exception.EntityNotFoundException;
import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import de.jexcellence.jehibernate.repository.query.PageResult;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

@Entity
class Product extends LongIdEntity {
    private String name;
    private double price;
    private boolean available;
    
    protected Product() {}
    
    public Product(String name, double price) {
        this.name = name;
        this.price = price;
        this.available = true;
    }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}

class ProductRepository extends AbstractCrudRepository<Product, Long> {
    public ProductRepository(ExecutorService executor, EntityManagerFactory emf, Class<Product> entityClass) {
        super(executor, emf, entityClass);
    }
}

/**
 * Demonstrates Java 24 features in JEHibernate:
 * - Sealed interfaces for type safety
 * - Pattern matching in exception handling
 * - Records for DTOs (PageResult)
 * - Enhanced Optional methods
 * - Fluent API improvements
 */
public class Java24FeaturesExample {

    private static final Logger LOGGER = LoggerFactory.getLogger(Java24FeaturesExample.class);
    private static final String FIELD_PRICE = "price";

    public static void main(String[] args) {
        try (var jeHibernate = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:mem:java24demo;DB_CLOSE_DELAY=-1")
                .credentials("sa", "")
                .ddlAuto("create-drop")
                .showSql(true))
            .scanPackages("examples")
            .build()) {
            
            var productRepo = jeHibernate.repositories().get(ProductRepository.class);
            
            // Create sample data
            for (int i = 1; i <= 25; i++) {
                productRepo.create(new Product("Product " + i, 10.0 * i));
            }
            
            // 1. Enhanced Optional methods - findByIdOrThrow
            LOGGER.info("=== Feature 1: findByIdOrThrow ===");
            try {
                Product product = productRepo.findByIdOrThrow(1L);
                LOGGER.info("Found: {}", product.getName());

                // This will throw EntityNotFoundException
                productRepo.findByIdOrThrow(999L);
            } catch (EntityNotFoundException e) {
                LOGGER.info("Expected exception: {}", e.getMessage());
            }

            // 2. findByIdOrCreate - create if not exists
            LOGGER.info("=== Feature 2: findByIdOrCreate ===");
            Product newProduct = productRepo.findByIdOrCreate(
                999L,
                () -> new Product("Auto-created Product", 99.99)
            );
            LOGGER.info("Created: {} with ID: {}", newProduct.getName(), newProduct.getId());

            // 3. createOrUpdate - smart save
            LOGGER.info("=== Feature 3: createOrUpdate ===");
            Product toUpdate = new Product("Updated Product", 150.0);
            toUpdate.setId(1L);
            Product updated = productRepo.createOrUpdate(toUpdate, Product::getId);
            LOGGER.info("Updated product ID 1 to: {}", updated.getName());

            // 4. PageResult record - pagination with metadata
            LOGGER.info("=== Feature 4: PageResult (Record) ===");
            PageResult<Product> page = productRepo.query()
                .greaterThan(FIELD_PRICE, 100.0)
                .orderBy(FIELD_PRICE)
                .getPage(0, 5);

            LOGGER.info("Page {} of {}", page.pageNumber() + 1, page.totalPages());
            LOGGER.info("Total elements: {}", page.totalElements());
            LOGGER.info("Has next: {}", page.hasNext());
            LOGGER.info("Elements in this page: {}", page.numberOfElements());
            page.content().forEach(p ->
                LOGGER.info("  - {}: ${}", p.getName(), p.getPrice())
            );

            // 5. Functional filtering with findAllMatching
            LOGGER.info("=== Feature 5: findAllMatching ===");
            var expensiveProducts = productRepo.findAllMatching(
                p -> p.getPrice() > 200.0 && p.isAvailable()
            );
            LOGGER.info("Found {} expensive products", expensiveProducts.size());

            // 6. Async pagination
            LOGGER.info("=== Feature 6: Async PageResult ===");
            productRepo.query()
                .like("name", "%Product%")
                .orderByDesc(FIELD_PRICE)
                .getPageAsync(0, 10)
                .thenAccept(asyncPage -> {
                    LOGGER.info("Async page loaded with {} items", asyncPage.numberOfElements());
                    LOGGER.info("First item: {}", asyncPage.content().getFirst().getName());
                })
                .join();

            // 7. Pattern matching exception handling (automatic)
            LOGGER.info("=== Feature 7: Pattern Matching Exceptions ===");
            try {
                // This will trigger pattern-matched exception handling
                productRepo.executeInTransaction(em -> {
                    throw new IllegalArgumentException("Invalid data");
                });
            } catch (Exception e) {
                LOGGER.info("Caught: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            }

            LOGGER.info("=== All Java 24 features demonstrated! ===");
        }
    }
}
