package examples;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.jehibernate.repository.base.AbstractCachedRepository;
import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import de.jexcellence.jehibernate.repository.query.PageResult;
import de.jexcellence.jehibernate.repository.query.Specifications;
import de.jexcellence.jehibernate.transaction.OptimisticLockRetry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

// ============================================================================
// ENTITIES
// ============================================================================

@Entity
@Table(name = "users")
class User extends LongIdEntity {
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String displayName;
    private boolean active;

    protected User() {}

    public User(String email, String displayName) {
        this.email = email;
        this.displayName = displayName;
        this.active = true;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}

@Entity
@Table(name = "products")
class Product extends LongIdEntity {
    @Column(nullable = false)
    private String name;
    private String category;
    private double price;
    private int stock;

    protected Product() {}

    public Product(String name, String category, double price, int stock) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.stock = stock;
    }

    public String getName() { return name; }
    public String getCategory() { return category; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}

// ============================================================================
// REPOSITORIES
// ============================================================================

class UserRepository extends AbstractCachedRepository<User, Long, String> {
    public UserRepository(ExecutorService executor, EntityManagerFactory emf, Class<User> entityClass) {
        super(executor, emf, entityClass,
            User::getEmail,
            AbstractCachedRepository.CacheConfig.builder()
                .expiration(Duration.ofMinutes(10))
                .maxSize(10_000)
                .expireAfterAccess(true)
                .build());
    }
}

class ProductRepository extends AbstractCrudRepository<Product, Long> {
    public ProductRepository(ExecutorService executor, EntityManagerFactory emf, Class<Product> entityClass) {
        super(executor, emf, entityClass);
    }
}

// ============================================================================
// SPRING CONFIGURATION
//
// In a real Spring Boot app, these would be @Configuration, @Service, etc.
// The comments show the Spring annotations you'd add.
// ============================================================================

/**
 * Spring Boot configuration for JEHibernate.
 *
 * <pre>{@code
 * @Configuration
 * public class JEHibernateConfig {
 *
 *     @Bean
 *     public JEHibernate jeHibernate() {
 *         return JEHibernate.builder()
 *             .configuration(config -> config
 *                 .database(DatabaseType.POSTGRESQL)
 *                 .url("jdbc:postgresql://localhost:5432/myapp")
 *                 .credentials("app", "secret")
 *                 .ddlAuto("validate")
 *                 .batchSize(50)
 *                 .connectionPool(5, 20))
 *             .scanPackages("com.example.myapp")
 *             .build();
 *     }
 *
 *     @Bean
 *     public UserRepository userRepository(JEHibernate jeh) {
 *         return jeh.repositories().get(UserRepository.class);
 *     }
 *
 *     @Bean
 *     public ProductRepository productRepository(JEHibernate jeh) {
 *         return jeh.repositories().get(ProductRepository.class);
 *     }
 *
 *     @PreDestroy
 *     public void shutdown(JEHibernate jeh) {
 *         jeh.close();
 *     }
 * }
 * }</pre>
 *
 * Or load from properties (application.yml redirects to hibernate.properties):
 * <pre>{@code
 * @Bean
 * public JEHibernate jeHibernate() {
 *     return JEHibernate.fromProperties("config/hibernate.properties");
 * }
 * }</pre>
 */
public class SpringBootExample {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringBootExample.class);
    private static final String CATEGORY_ELECTRONICS = "Electronics";

    // Simulates a Spring @Service
    static class UserService {
        private final UserRepository userRepo;

        // @Autowired (constructor injection)
        UserService(UserRepository userRepo) {
            this.userRepo = userRepo;
        }

        public User register(String email, String displayName) {
            // Check for duplicate
            if (userRepo.findByKey("email", email).isPresent()) {
                throw new IllegalArgumentException("Email already registered: " + email);
            }
            return userRepo.create(new User(email, displayName));
        }

        public Optional<User> findByEmail(String email) {
            return userRepo.findByKey("email", email);
        }

        public PageResult<User> listActiveUsers(int page, int size) {
            return userRepo.query()
                .and("active", true)
                .orderByDesc("createdAt")
                .getPage(page, size);
        }

        public List<User> searchUsers(String query) {
            return userRepo.query()
                .orLike("email", "%" + query + "%")
                .orLike("displayName", "%" + query + "%")
                .orderBy("displayName")
                .list();
        }

        public void deactivateUser(long userId) {
            OptimisticLockRetry.executeVoid(() -> {
                var user = userRepo.findByIdOrThrow(userId);
                user.setActive(false);
                userRepo.save(user);
            });
        }
    }

    // Simulates a Spring @Service
    static class ProductService {
        private final ProductRepository productRepo;

        ProductService(ProductRepository productRepo) {
            this.productRepo = productRepo;
        }

        public PageResult<Product> listProducts(String category, int page, int size) {
            var qb = productRepo.query()
                .greaterThan("stock", 0)
                .orderBy("name");

            if (category != null) {
                qb.and("category", category);
            }

            return qb.getPage(page, size);
        }

        public List<Product> findAffordable(double maxPrice) {
            var spec = Specifications.<Product>lessThanOrEqual("price", maxPrice)
                .and(Specifications.greaterThan("stock", 0));
            return productRepo.findAll(spec);
        }

        /**
         * Demonstrates session-scoped operation.
         * Multiple queries run in the same session for consistency.
         */
        public String generateReport() {
            return productRepo.withReadOnly(session -> {
                var allProducts = session.query(Product.class).list();
                long totalStock = allProducts.stream().mapToInt(Product::getStock).sum();
                double avgPrice = allProducts.stream().mapToDouble(Product::getPrice).average().orElse(0);
                return "Products: " + allProducts.size()
                    + ", Total stock: " + totalStock
                    + ", Avg price: " + String.format("%.2f", avgPrice);
            });
        }

        /**
         * Demonstrates batch operations.
         */
        public void importProducts(List<Product> products) {
            productRepo.saveAll(products);
        }
    }

    // ========================================================================
    // Main — demonstrates the full flow
    // ========================================================================

    public static void main(String[] args) {
        // Setup (in Spring, this is done via @Configuration + @Bean)
        var jeHibernate = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:mem:springexample;DB_CLOSE_DELAY=-1")
                .credentials("sa", "")
                .ddlAuto("create-drop")
                .showSql(false))
            .scanPackages("examples")
            .build();

        var userRepo = jeHibernate.repositories().get(UserRepository.class);
        var productRepo = jeHibernate.repositories().get(ProductRepository.class);

        var userService = new UserService(userRepo);
        var productService = new ProductService(productRepo);

        // --- User operations ---
        LOGGER.info("=== User Operations ===");

        userService.register("alice@example.com", "Alice");
        userService.register("bob@example.com", "Bob");
        userService.register("charlie@example.com", "Charlie");

        var alice = userService.findByEmail("alice@example.com");
        LOGGER.info("Found: {}", alice.map(User::getDisplayName).orElse("not found"));

        var page = userService.listActiveUsers(0, 10);
        LOGGER.info("Active users: {}", page.totalElements());

        var searchResults = userService.searchUsers("ali");
        LOGGER.info("Search 'ali': {} results", searchResults.size());

        // --- Product operations ---
        LOGGER.info("=== Product Operations ===");

        productService.importProducts(List.of(
            new Product("Widget", CATEGORY_ELECTRONICS, 29.99, 100),
            new Product("Gadget", CATEGORY_ELECTRONICS, 49.99, 50),
            new Product("Book", "Education", 14.99, 200),
            new Product("Pen", "Office", 2.99, 500)
        ));

        var electronics = productService.listProducts(CATEGORY_ELECTRONICS, 0, 10);
        LOGGER.info("Electronics: {}", electronics.totalElements());

        var affordable = productService.findAffordable(20.0);
        LOGGER.info("Under $20: {} products", affordable.size());

        var report = productService.generateReport();
        LOGGER.info("Report: {}", report);

        // --- Cleanup ---
        jeHibernate.close();
        LOGGER.info("Done.");
    }
}
