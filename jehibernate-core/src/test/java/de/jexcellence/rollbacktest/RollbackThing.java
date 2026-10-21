package de.jexcellence.rollbacktest;

import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;

import java.util.concurrent.ExecutorService;

@Entity
@Table(name = "rollback_thing")
public class RollbackThing extends LongIdEntity {

    private String name;

    protected RollbackThing() {}

    public RollbackThing(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

/** Exposes the protected transaction helper so the test can make it fail after a write. */
class RollbackThingRepository extends AbstractCrudRepository<RollbackThing, Long> {
    RollbackThingRepository(ExecutorService executor, EntityManagerFactory emf) {
        super(executor, emf);
    }

    void insertThenFail(String name) {
        executeInTransaction(em -> {
            em.persist(new RollbackThing(name));
            em.flush();
            throw new IllegalStateException("abort after the insert reached the database");
        });
    }
}
