package examples;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import de.jexcellence.jehibernate.repository.query.Specifications;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;

@Entity
class Order extends LongIdEntity {
    private String customerEmail;
    private String status;
    private double amount;
    
    protected Order() {}
    
    public Order(String customerEmail, String status, double amount) {
        this.customerEmail = customerEmail;
        this.status = status;
        this.amount = amount;
    }
    
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    /**
     * Delegates to {@link de.jexcellence.jehibernate.entity.base.BaseEntity#equals(Object)},
     * which compares by database ID (or by identity token for transient instances).
     * Explicitly overridden here to satisfy static-analysis tools that require
     * subclasses adding fields to declare equals/hashCode.
     */
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}

class OrderRepository extends AbstractCrudRepository<Order, Long> {
    public OrderRepository(ExecutorService executor, EntityManagerFactory emf, Class<Order> entityClass) {
        super(executor, emf, entityClass);
    }
}

public class AdvancedQueryExample {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvancedQueryExample.class);
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_AMOUNT = "amount";

    public static void main(String[] args) {
        try (var jeHibernate = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:mem:testdb")
                .credentials("sa", "")
                .ddlAuto("create-drop"))
            .scanPackages("examples")
            .build()) {
            
            var orderRepo = jeHibernate.repositories().get(OrderRepository.class);
            
            orderRepo.createAll(java.util.List.of(
                new Order("alice@example.com", STATUS_PENDING, 100.0),
                new Order("bob@example.com", STATUS_COMPLETED, 250.0),
                new Order("alice@example.com", STATUS_COMPLETED, 150.0),
                new Order("charlie@example.com", STATUS_PENDING, 75.0)
            ));

            LOGGER.info("=== Query Builder Examples ===");

            var pendingOrders = orderRepo.query()
                .and(FIELD_STATUS, STATUS_PENDING)
                .greaterThan(FIELD_AMOUNT, 50.0)
                .orderByDesc(FIELD_AMOUNT)
                .list();
            LOGGER.info("Pending orders > $50: {}", pendingOrders.size());

            var aliceOrders = orderRepo.query()
                .like("customerEmail", "alice%")
                .orderBy("createdAt")
                .list();
            LOGGER.info("Alice's orders: {}", aliceOrders.size());

            var recentOrders = orderRepo.query()
                .greaterThan("createdAt", Instant.now().minus(1, ChronoUnit.HOURS))
                .list();
            LOGGER.info("Recent orders: {}", recentOrders.size());

            LOGGER.info("=== Specification Examples ===");

            var completedSpec = Specifications.<Order>equalTo(FIELD_STATUS, STATUS_COMPLETED)
                .and(Specifications.greaterThan(FIELD_AMOUNT, 100.0));

            var completedOrders = orderRepo.findAll(completedSpec);
            LOGGER.info("Completed orders > $100: {}", completedOrders.size());

            var highValueSpec = Specifications.<Order>greaterThanOrEqual(FIELD_AMOUNT, 200.0);
            var highValueCount = orderRepo.count(highValueSpec);
            LOGGER.info("High value orders: {}", highValueCount);

            LOGGER.info("=== Async Query Examples ===");

            orderRepo.query()
                .and(FIELD_STATUS, STATUS_PENDING)
                .listAsync()
                .thenAccept(orders ->
                    LOGGER.info("Async pending orders: {}", orders.size()))
                .join();

            orderRepo.query()
                .like("customerEmail", "%@example.com")
                .countAsync()
                .thenAccept(count ->
                    LOGGER.info("Async total orders: {}", count))
                .join();
        }
    }
}
