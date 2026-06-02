package de.jexcellence.testfixture;

import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;

import java.util.concurrent.ExecutorService;

@Entity
@Table(name = "fixture_user")
public class FixtureUser extends LongIdEntity {

    private String name;

    protected FixtureUser() {}

    public FixtureUser(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

class FixtureUserRepository extends AbstractCrudRepository<FixtureUser, Long> {
    FixtureUserRepository(ExecutorService executor, EntityManagerFactory emf, Class<FixtureUser> entityClass) {
        super(executor, emf, entityClass);
    }
}
