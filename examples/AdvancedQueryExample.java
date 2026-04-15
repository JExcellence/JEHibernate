package examples;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import de.jexcellence.jehibernate.repository.query.Specifications;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;

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
}

class OrderRepository extends AbstractCrudRepository<Order, Long> {
    public OrderRepository(ExecutorService executor, EntityManagerFactory emf, Class<Order> entityClass) {
        super(executor, emf, entityClass);
    }
}

public class AdvancedQueryExample {
    
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
                new Order("alice@example.com", "PENDING", 100.0),
                new Order("bob@example.com", "COMPLETED", 250.0),
                new Order("alice@example.com", "COMPLETED", 150.0),
                new Order("charlie@example.com", "PENDING", 75.0)
            ));
            
            System.out.println("=== Query Builder Examples ===");
            
            var pendingOrders = orderRepo.query()
                .and("status", "PENDING")
                .greaterThan("amount", 50.0)
                .orderByDesc("amount")
                .list();
            System.out.println("Pending orders > $50: " + pendingOrders.size());
            
            var aliceOrders = orderRepo.query()
                .like("customerEmail", "alice%")
                .orderBy("createdAt")
                .list();
            System.out.println("Alice's orders: " + aliceOrders.size());
            
            var recentOrders = orderRepo.query()
                .greaterThan("createdAt", Instant.now().minus(1, ChronoUnit.HOURS))
                .list();
            System.out.println("Recent orders: " + recentOrders.size());
            
            System.out.println("\n=== Specification Examples ===");
            
            var completedSpec = Specifications.<Order>equal("status", "COMPLETED")
                .and(Specifications.greaterThan("amount", 100.0));
            
            var completedOrders = orderRepo.findAll(completedSpec);
            System.out.println("Completed orders > $100: " + completedOrders.size());
            
            var highValueSpec = Specifications.<Order>greaterThanOrEqual("amount", 200.0);
            var highValueCount = orderRepo.count(highValueSpec);
            System.out.println("High value orders: " + highValueCount);
            
            System.out.println("\n=== Async Query Examples ===");
            
            orderRepo.query()
                .and("status", "PENDING")
                .listAsync()
                .thenAccept(orders -> 
                    System.out.println("Async pending orders: " + orders.size()))
                .join();
            
            orderRepo.query()
                .like("customerEmail", "%@example.com")
                .countAsync()
                .thenAccept(count -> 
                    System.out.println("Async total orders: " + count))
                .join();
        }
    }
}
