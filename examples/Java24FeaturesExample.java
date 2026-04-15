package examples;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.jehibernate.exception.EntityNotFoundException;
import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import de.jexcellence.jehibernate.repository.query.PageResult;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;

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
            System.out.println("\n=== Feature 1: findByIdOrThrow ===");
            try {
                Product product = productRepo.findByIdOrThrow(1L);
                System.out.println("Found: " + product.getName());
                
                // This will throw EntityNotFoundException
                productRepo.findByIdOrThrow(999L);
            } catch (EntityNotFoundException e) {
                System.out.println("Expected exception: " + e.getMessage());
            }
            
            // 2. findByIdOrCreate - create if not exists
            System.out.println("\n=== Feature 2: findByIdOrCreate ===");
            Product newProduct = productRepo.findByIdOrCreate(
                999L, 
                () -> new Product("Auto-created Product", 99.99)
            );
            System.out.println("Created: " + newProduct.getName() + " with ID: " + newProduct.getId());
            
            // 3. createOrUpdate - smart save
            System.out.println("\n=== Feature 3: createOrUpdate ===");
            Product toUpdate = new Product("Updated Product", 150.0);
            toUpdate.setId(1L);
            Product updated = productRepo.createOrUpdate(toUpdate, Product::getId);
            System.out.println("Updated product ID 1 to: " + updated.getName());
            
            // 4. PageResult record - pagination with metadata
            System.out.println("\n=== Feature 4: PageResult (Record) ===");
            PageResult<Product> page = productRepo.query()
                .greaterThan("price", 100.0)
                .orderBy("price")
                .getPage(0, 5);
            
            System.out.println("Page " + (page.pageNumber() + 1) + " of " + page.totalPages());
            System.out.println("Total elements: " + page.totalElements());
            System.out.println("Has next: " + page.hasNext());
            System.out.println("Elements in this page: " + page.numberOfElements());
            page.content().forEach(p -> 
                System.out.println("  - " + p.getName() + ": $" + p.getPrice())
            );
            
            // 5. Functional filtering with findAllMatching
            System.out.println("\n=== Feature 5: findAllMatching ===");
            var expensiveProducts = productRepo.findAllMatching(
                p -> p.getPrice() > 200.0 && p.isAvailable()
            );
            System.out.println("Found " + expensiveProducts.size() + " expensive products");
            
            // 6. Async pagination
            System.out.println("\n=== Feature 6: Async PageResult ===");
            productRepo.query()
                .like("name", "%Product%")
                .orderByDesc("price")
                .getPageAsync(0, 10)
                .thenAccept(asyncPage -> {
                    System.out.println("Async page loaded with " + 
                        asyncPage.numberOfElements() + " items");
                    System.out.println("First item: " + 
                        asyncPage.content().getFirst().getName());
                })
                .join();
            
            // 7. Pattern matching exception handling (automatic)
            System.out.println("\n=== Feature 7: Pattern Matching Exceptions ===");
            try {
                // This will trigger pattern-matched exception handling
                productRepo.executeInTransaction(em -> {
                    throw new IllegalArgumentException("Invalid data");
                });
            } catch (Exception e) {
                System.out.println("Caught: " + e.getClass().getSimpleName() + 
                    " - " + e.getMessage());
            }
            
            System.out.println("\n=== All Java 24 features demonstrated! ===");
        }
    }
}
