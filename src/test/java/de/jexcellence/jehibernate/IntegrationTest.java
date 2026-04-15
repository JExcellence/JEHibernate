package de.jexcellence.jehibernate;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.jehibernate.entity.base.UuidEntity;
import de.jexcellence.jehibernate.repository.base.AbstractCachedRepository;
import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import de.jexcellence.jehibernate.repository.query.Specifications;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

@Entity
class TestUser extends LongIdEntity {
    private String username;
    private String email;
    private boolean active;
    
    protected TestUser() {}
    
    public TestUser(String username, String email) {
        this.username = username;
        this.email = email;
        this.active = true;
    }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

@Entity
class TestPlayer extends UuidEntity {
    private String username;
    
    protected TestPlayer() {}
    
    public TestPlayer(String username) {
        this.username = username;
    }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}

class TestUserRepository extends AbstractCrudRepository<TestUser, Long> {
    public TestUserRepository(ExecutorService executor, EntityManagerFactory emf, Class<TestUser> entityClass) {
        super(executor, emf, entityClass);
    }
}

class TestPlayerRepository extends AbstractCachedRepository<TestPlayer, UUID, UUID> {
    public TestPlayerRepository(ExecutorService executor, EntityManagerFactory emf, Class<TestPlayer> entityClass) {
        super(executor, emf, entityClass, TestPlayer::getId);
    }
}

public class IntegrationTest {
    
    private JEHibernate jeHibernate;
    private TestUserRepository userRepo;
    private TestPlayerRepository playerRepo;
    
    @BeforeEach
    void setUp() {
        jeHibernate = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
                .credentials("sa", "")
                .ddlAuto("create-drop"))
            .scanPackages("de.jexcellence.jehibernate")
            .build();
        
        userRepo = jeHibernate.repositories().get(TestUserRepository.class);
        playerRepo = jeHibernate.repositories().get(TestPlayerRepository.class);
    }
    
    @AfterEach
    void tearDown() {
        if (jeHibernate != null) {
            jeHibernate.close();
        }
    }
    
    @Test
    void testBasicCrud() {
        var user = userRepo.create(new TestUser("alice", "alice@example.com"));
        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
        
        var found = userRepo.findById(user.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("alice");
        
        user.setEmail("newemail@example.com");
        var updated = userRepo.update(user);
        assertThat(updated.getEmail()).isEqualTo("newemail@example.com");
        assertThat(updated.getUpdatedAt()).isAfter(updated.getCreatedAt());
        
        userRepo.delete(user.getId());
        assertThat(userRepo.findById(user.getId())).isEmpty();
    }
    
    @Test
    void testBulkOperations() {
        var users = List.of(
            new TestUser("alice", "alice@example.com"),
            new TestUser("bob", "bob@example.com"),
            new TestUser("charlie", "charlie@example.com")
        );
        
        var created = userRepo.createAll(users);
        assertThat(created).hasSize(3);
        assertThat(created).allMatch(u -> u.getId() != null);
        
        var ids = created.stream().map(TestUser::getId).toList();
        var found = userRepo.findAllById(ids);
        assertThat(found).hasSize(3);
        
        userRepo.deleteAll(ids);
        assertThat(userRepo.count()).isZero();
    }
    
    @Test
    void testQueryBuilder() {
        userRepo.createAll(List.of(
            new TestUser("alice", "alice@gmail.com"),
            new TestUser("bob", "bob@yahoo.com"),
            new TestUser("charlie", "charlie@gmail.com")
        ));
        
        var gmailUsers = userRepo.query()
            .like("email", "%@gmail.com")
            .list();
        assertThat(gmailUsers).hasSize(2);
        
        var activeCount = userRepo.query()
            .and("active", true)
            .count();
        assertThat(activeCount).isEqualTo(3);
        
        var first = userRepo.query()
            .and("username", "alice")
            .first();
        assertThat(first).isPresent();
        assertThat(first.get().getUsername()).isEqualTo("alice");
    }
    
    @Test
    void testSpecifications() {
        userRepo.createAll(List.of(
            new TestUser("alice", "alice@example.com"),
            new TestUser("bob", "bob@example.com")
        ));
        
        var spec = Specifications.<TestUser>equal("active", true)
            .and(Specifications.like("email", "%@example.com"));
        
        var users = userRepo.findAll(spec);
        assertThat(users).hasSize(2);
        
        var count = userRepo.count(spec);
        assertThat(count).isEqualTo(2);
    }
    
    @Test
    void testAsyncOperations() throws Exception {
        var user = new TestUser("alice", "alice@example.com");
        
        var created = userRepo.createAsync(user).get();
        assertThat(created.getId()).isNotNull();
        
        var found = userRepo.findByIdAsync(created.getId()).get();
        assertThat(found).isPresent();
        
        var all = userRepo.findAllAsync().get();
        assertThat(all).hasSize(1);
        
        userRepo.deleteAsync(created.getId()).get();
        assertThat(userRepo.count()).isZero();
    }
    
    @Test
    void testUuidEntity() {
        var player = playerRepo.create(new TestPlayer("alice"));
        assertThat(player.getId()).isNotNull();
        assertThat(player.getId()).isInstanceOf(UUID.class);
        
        var found = playerRepo.findById(player.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("alice");
    }
    
    @Test
    void testCachedRepository() {
        var player = playerRepo.create(new TestPlayer("alice"));
        
        var cached = playerRepo.findByKey(player.getId());
        assertThat(cached).isPresent();
        
        assertThat(playerRepo.getCacheSize()).isGreaterThan(0);
        
        playerRepo.evictById(player.getId());
        assertThat(playerRepo.getCacheSize()).isZero();
        
        playerRepo.preload();
        assertThat(playerRepo.getCacheSize()).isEqualTo(1);
    }
    
    @Test
    void testPagination() {
        for (int i = 0; i < 25; i++) {
            userRepo.create(new TestUser("user" + i, "user" + i + "@example.com"));
        }
        
        var page1 = userRepo.findAll(0, 10);
        assertThat(page1).hasSize(10);
        
        var page2 = userRepo.findAll(1, 10);
        assertThat(page2).hasSize(10);
        
        var page3 = userRepo.findAll(2, 10);
        assertThat(page3).hasSize(5);
    }
}
