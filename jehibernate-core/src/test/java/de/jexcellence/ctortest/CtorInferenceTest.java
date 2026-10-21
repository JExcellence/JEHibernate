package de.jexcellence.ctortest;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.migration.MigrationConfig;
import de.jexcellence.jehibernate.migration.MigrationTool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the two-argument repository constructors that infer the entity class from the generic type
 * argument (no explicit {@code Class<T>}): a direct {@code AbstractCrudRepository} and a cached
 * repository whose entity type is resolved through the {@code AbstractCachedRepository} level.
 */
class CtorInferenceTest {

    private static final MigrationConfig MIGRATION_OFF =
        new MigrationConfig(false, MigrationTool.NONE, "classpath:db/migration");

    private static JEHibernate build() {
        return JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:mem:ctorinfer;DB_CLOSE_DELAY=-1")
                .credentials("sa", "")
                .ddlAuto("create-drop")
                .migration(MIGRATION_OFF))
            .scanPackages("de.jexcellence.ctortest")
            .build();
    }

    @Test
    void directRepositoryInfersEntityClass() {
        try (JEHibernate jeHibernate = build()) {
            var repo = jeHibernate.repositories().get(CtorThingRepository.class);

            CtorThing saved = repo.create(new CtorThing("alice"));
            assertThat(saved.getId()).isNotNull();
            assertThat(repo.findById(saved.getId())).get()
                .extracting(CtorThing::getName).isEqualTo("alice");
            assertThat(repo.count()).isEqualTo(1);
        }
    }

    @Test
    void cachedRepositoryInfersEntityClassThroughChain() {
        try (JEHibernate jeHibernate = build()) {
            var repo = jeHibernate.repositories().get(CachedCtorThingRepository.class);

            CtorThing saved = repo.create(new CtorThing("bob"));
            // findByKey hits the cache path, which requires the resolved entity class to be correct.
            assertThat(repo.findByKey(saved.getId())).get()
                .extracting(CtorThing::getName).isEqualTo("bob");
        }
    }
}
