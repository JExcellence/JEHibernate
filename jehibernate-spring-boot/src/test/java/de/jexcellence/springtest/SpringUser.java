package de.jexcellence.springtest;

import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;

import java.util.concurrent.ExecutorService;

@Entity
@Table(name = "spring_user")
public class SpringUser extends LongIdEntity {

    private String name;

    protected SpringUser() {}

    public SpringUser(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

class SpringUserRepository extends AbstractCrudRepository<SpringUser, Long> {
    SpringUserRepository(ExecutorService executor, EntityManagerFactory emf, Class<SpringUser> entityClass) {
        super(executor, emf, entityClass);
    }
}
