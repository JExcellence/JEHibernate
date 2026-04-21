package examples;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

@Entity
class User extends LongIdEntity {
    private String username;
    private String email;
    private boolean active;
    
    protected User() {}
    
    public User(String username, String email) {
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

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}

class UserRepository extends AbstractCrudRepository<User, Long> {
    public UserRepository(ExecutorService executor, EntityManagerFactory emf, Class<User> entityClass) {
        super(executor, emf, entityClass);
    }
}

public class QuickStartExample {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuickStartExample.class);

    public static void main(String[] args) {
        try (var jeHibernate = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
                .credentials("sa", "")
                .ddlAuto("create-drop")
                .showSql(true))
            .scanPackages("examples")
            .build()) {
            
            var userRepo = jeHibernate.repositories().get(UserRepository.class);
            
            var user = userRepo.create(new User("alice", "alice@example.com"));
            LOGGER.info("Created user with ID: {}", user.getId());

            var found = userRepo.findById(user.getId());
            found.ifPresent(u -> LOGGER.info("Found user: {}", u.getUsername()));

            var activeUsers = userRepo.query()
                .and("active", true)
                .like("email", "%@example.com")
                .list();
            LOGGER.info("Active users: {}", activeUsers.size());

            userRepo.createAsync(new User("bob", "bob@example.com"))
                .thenAccept(created -> LOGGER.info("Async created: {}", created.getUsername()))
                .join();

            long count = userRepo.count();
            LOGGER.info("Total users: {}", count);
        }
    }
}
