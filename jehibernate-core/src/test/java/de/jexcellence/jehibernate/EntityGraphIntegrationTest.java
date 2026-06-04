package de.jexcellence.jehibernate;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.jehibernate.migration.MigrationConfig;
import de.jexcellence.jehibernate.migration.MigrationTool;
import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

@Entity
@Table(name = "shop_order")
class ShopOrder extends LongIdEntity {

    private String reference;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ShopOrderItem> items = new ArrayList<>();

    protected ShopOrder() {}

    ShopOrder(String reference) {
        this.reference = reference;
    }

    String getReference() { return reference; }
    List<ShopOrderItem> getItems() { return items; }

    void addItem(ShopOrderItem item) {
        item.setOrder(this);
        items.add(item);
    }
}

@Entity
@Table(name = "shop_order_item")
class ShopOrderItem extends LongIdEntity {

    private String product;

    @ManyToOne(fetch = FetchType.LAZY)
    private ShopOrder order;

    protected ShopOrderItem() {}

    ShopOrderItem(String product) {
        this.product = product;
    }

    String getProduct() { return product; }
    ShopOrder getOrder() { return order; }
    void setOrder(ShopOrder order) { this.order = order; }
}

class ShopOrderRepository extends AbstractCrudRepository<ShopOrder, Long> {
    ShopOrderRepository(ExecutorService executor, EntityManagerFactory emf, Class<ShopOrder> entityClass) {
        super(executor, emf, entityClass);
    }
}

/**
 * Acceptance: an {@code @EntityGraph}-style fetch loads a {@code ShopOrder} together with
 * its {@code items} in a single SQL statement (no N+1), verified via Hibernate {@link Statistics}.
 */
class EntityGraphIntegrationTest {

    private static final MigrationConfig MIGRATION_OFF =
        new MigrationConfig(false, MigrationTool.NONE, "classpath:db/migration");

    private JEHibernate jeHibernate;
    private ShopOrderRepository orderRepo;
    private Long seededOrderId;

    @BeforeEach
    void setUp() {
        jeHibernate = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:mem:graphtest;DB_CLOSE_DELAY=-1")
                .credentials("sa", "")
                .ddlAuto("create-drop")
                .migration(MIGRATION_OFF)
                .property("hibernate.generate_statistics", true))
            .scanPackages("de.jexcellence.jehibernate")
            .build();

        orderRepo = jeHibernate.repositories().get(ShopOrderRepository.class);

        ShopOrder order = new ShopOrder("ORD-1");
        order.addItem(new ShopOrderItem("Widget"));
        order.addItem(new ShopOrderItem("Gadget"));
        order.addItem(new ShopOrderItem("Gizmo"));
        seededOrderId = orderRepo.create(order).getId();
    }

    @AfterEach
    void tearDown() {
        if (jeHibernate != null) {
            jeHibernate.close();
        }
    }

    @Test
    void entityGraphLoadsCollectionInSingleQuery() {
        Statistics stats = jeHibernate.getEntityManagerFactory()
            .unwrap(SessionFactory.class)
            .getStatistics();
        stats.clear();

        var order = orderRepo.findByIdWithGraph(seededOrderId, "items");

        assertThat(order).isPresent();
        // Collection is already initialised — no lazy access / no extra query.
        assertThat(order.get().getItems()).hasSize(3);
        assertThat(stats.getPrepareStatementCount())
            .as("ShopOrder + items fetched in a single statement (no N+1)")
            .isEqualTo(1L);
    }
}
