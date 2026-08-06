package de.jexcellence.versiontest;

import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;

import java.util.concurrent.ExecutorService;

@Entity
@Table(name = "versioned_thing")
public class VersionedThing extends LongIdEntity {

    private String name;

    protected VersionedThing() {}

    public VersionedThing(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

class VersionedThingRepository extends AbstractCrudRepository<VersionedThing, Long> {
    VersionedThingRepository(ExecutorService executor, EntityManagerFactory emf, Class<VersionedThing> entityClass) {
        super(executor, emf, entityClass);
    }
}
