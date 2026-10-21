package de.jexcellence.ctortest;

import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.jehibernate.repository.base.AbstractCachedRepository;
import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;

import java.util.concurrent.ExecutorService;

@Entity
@Table(name = "ctor_thing")
public class CtorThing extends LongIdEntity {

    private String name;

    protected CtorThing() {}

    public CtorThing(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

/** Uses the inferring constructor — no Class<T> argument. */
class CtorThingRepository extends AbstractCrudRepository<CtorThing, Long> {
    CtorThingRepository(ExecutorService executor, EntityManagerFactory emf) {
        super(executor, emf);
    }
}

/** Cached variant, also using the inferring constructor (entity resolved through the chain). */
class CachedCtorThingRepository extends AbstractCachedRepository<CtorThing, Long, Long> {
    CachedCtorThingRepository(ExecutorService executor, EntityManagerFactory emf) {
        super(executor, emf, CtorThing::getId);
    }
}
