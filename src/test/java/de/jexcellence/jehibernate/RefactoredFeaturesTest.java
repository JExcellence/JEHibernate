package de.jexcellence.jehibernate;

import de.jexcellence.jehibernate.config.DatabaseType;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jehibernate.entity.base.BaseEntity;
import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import de.jexcellence.jehibernate.repository.query.PageResult;
import de.jexcellence.jehibernate.transaction.TransactionTemplate;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the refactored features in JEHibernate 2.0:
 * - BaseEntity equals/hashCode with identityToken
 * - Fixed save() for detached entities
 * - saveAll(), deleteEntity()
 * - Session-scoped operations (withSession, withReadOnly)
 * - QueryBuilder multi-sort, OR conditions, consistent pagination
 * - TransactionTemplate
 */
public class RefactoredFeaturesTest {

    private JEHibernate jeHibernate;
    private TestUserRepository userRepo;

    // Re-use TestUser and TestUserRepository from IntegrationTest (same package)

    @BeforeEach
    void setUp() {
        jeHibernate = JEHibernate.builder()
            .configuration(config -> config
                .database(DatabaseType.H2)
                .url("jdbc:h2:mem:refactortest;DB_CLOSE_DELAY=-1")
                .credentials("sa", "")
                .ddlAuto("create-drop"))
            .scanPackages("de.jexcellence.jehibernate")
            .build();

        userRepo = jeHibernate.repositories().get(TestUserRepository.class);
    }

    @AfterEach
    void tearDown() {
        if (jeHibernate != null) {
            jeHibernate.close();
        }
    }

    // --- Phase 1A: equals/hashCode fix ---

    @Test
    void testNewEntitiesAreNotEqual() {
        var user1 = new TestUser("alice", "alice@example.com");
        var user2 = new TestUser("bob", "bob@example.com");

        assertThat(user1).isNotEqualTo(user2);
        assertThat(user1.hashCode()).isNotEqualTo(user2.hashCode());
    }

    @Test
    void testNewEntitiesInHashSet() {
        var set = new HashSet<TestUser>();
        var user1 = new TestUser("alice", "alice@example.com");
        var user2 = new TestUser("bob", "bob@example.com");
        var user3 = new TestUser("charlie", "charlie@example.com");

        set.add(user1);
        set.add(user2);
        set.add(user3);

        assertThat(set).hasSize(3);
    }

    @Test
    void testPersistedEntitiesEqualById() {
        var user = userRepo.create(new TestUser("alice", "alice@example.com"));
        var found = userRepo.findById(user.getId()).orElseThrow();

        assertThat(user.getId()).isEqualTo(found.getId());
    }

    // --- Phase 1B: Fixed save() ---

    @Test
    void testSaveNewEntity() {
        var user = new TestUser("alice", "alice@example.com");
        var saved = userRepo.save(user);
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void testSaveExistingEntity() {
        var user = userRepo.create(new TestUser("alice", "alice@example.com"));
        user.setEmail("updated@example.com");
        var saved = userRepo.save(user);
        assertThat(saved.getEmail()).isEqualTo("updated@example.com");
    }

    // --- Phase 6A: saveAll() ---

    @Test
    void testSaveAll() {
        var users = List.of(
            new TestUser("alice", "alice@example.com"),
            new TestUser("bob", "bob@example.com")
        );
        var saved = userRepo.saveAll(users);
        assertThat(saved).hasSize(2);
        assertThat(saved).allMatch(u -> u.getId() != null);
    }

    @Test
    void testSaveAllMixed() {
        // Create one entity first
        var existing = userRepo.create(new TestUser("alice", "alice@example.com"));
        existing.setEmail("updated@example.com");

        var newUser = new TestUser("bob", "bob@example.com");

        // saveAll with mix of new and existing
        var saved = userRepo.saveAll(List.of(existing, newUser));
        assertThat(saved).hasSize(2);
    }

    // --- Phase 6A: deleteEntity() ---

    @Test
    void testDeleteEntity() {
        var user = userRepo.create(new TestUser("alice", "alice@example.com"));
        assertThat(userRepo.count()).isEqualTo(1);

        userRepo.deleteEntity(user);
        assertThat(userRepo.count()).isZero();
    }

    // --- Phase 6A: existsBy() ---

    @Test
    void testExistsBy() {
        userRepo.create(new TestUser("alice", "alice@example.com"));

        var exists = userRepo.existsBy(
            (root, query, cb) -> cb.equal(root.get("username"), "alice")
        );
        assertThat(exists).isTrue();

        var notExists = userRepo.existsBy(
            (root, query, cb) -> cb.equal(root.get("username"), "nonexistent")
        );
        assertThat(notExists).isFalse();
    }

    // --- Phase 2: Session management ---

    @Test
    void testWithSession() {
        var user = userRepo.create(new TestUser("alice", "alice@example.com"));

        var result = userRepo.withSession(session -> {
            var found = session.find(TestUser.class, user.getId());
            assertThat(found).isPresent();
            found.get().setEmail("session-updated@example.com");
            session.merge(found.get());
            return found.get();
        });

        assertThat(result.getEmail()).isEqualTo("session-updated@example.com");
    }

    @Test
    void testWithReadOnly() {
        userRepo.create(new TestUser("alice", "alice@example.com"));

        var users = userRepo.withReadOnly(session ->
            session.query(TestUser.class).and("username", "alice").list()
        );
        assertThat(users).hasSize(1);
    }

    @Test
    void testJEHibernateWithSession() {
        var user = userRepo.create(new TestUser("alice", "alice@example.com"));

        var found = jeHibernate.withSession(session ->
            session.find(TestUser.class, user.getId())
        );
        assertThat(found).isPresent();
    }

    // --- Phase 3: QueryBuilder enhancements ---

    @Test
    void testMultiSort() {
        userRepo.createAll(List.of(
            new TestUser("charlie", "c@example.com"),
            new TestUser("alice", "a@example.com"),
            new TestUser("bob", "b@example.com"),
            new TestUser("alice", "az@example.com")
        ));

        var users = userRepo.query()
            .orderBy("username")
            .orderByDesc("email")
            .list();

        assertThat(users.get(0).getUsername()).isEqualTo("alice");
        assertThat(users.get(0).getEmail()).isEqualTo("az@example.com"); // desc
    }

    @Test
    void testOrConditions() {
        userRepo.createAll(List.of(
            new TestUser("alice", "alice@gmail.com"),
            new TestUser("bob", "bob@yahoo.com"),
            new TestUser("charlie", "charlie@outlook.com")
        ));

        var users = userRepo.query()
            .or("username", "alice")
            .or("username", "charlie")
            .list();

        assertThat(users).hasSize(2);
        assertThat(users).extracting(TestUser::getUsername)
            .containsExactlyInAnyOrder("alice", "charlie");
    }

    @Test
    void testConsistentPageResult() {
        for (int i = 0; i < 25; i++) {
            userRepo.create(new TestUser("user" + i, "user" + i + "@example.com"));
        }

        PageResult<TestUser> page = userRepo.query()
            .and("active", true)
            .getPage(0, 10);

        assertThat(page.content()).hasSize(10);
        assertThat(page.totalElements()).isEqualTo(25);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.hasPrevious()).isFalse();
        assertThat(page.isFirst()).isTrue();
    }

    @Test
    void testStream() {
        userRepo.createAll(List.of(
            new TestUser("alice", "alice@example.com"),
            new TestUser("bob", "bob@example.com"),
            new TestUser("charlie", "charlie@example.com")
        ));

        try (var stream = userRepo.query().stream()) {
            var count = stream.filter(u -> u.getUsername().startsWith("a")).count();
            assertThat(count).isEqualTo(1);
        }
    }

    // --- Phase 6C: TransactionTemplate ---

    @Test
    void testTransactionTemplate() {
        TransactionTemplate template = jeHibernate.transactionTemplate();

        var user = template.execute(em -> {
            var u = new TestUser("alice", "alice@example.com");
            em.persist(u);
            return u;
        });
        assertThat(user.getId()).isNotNull();

        var found = template.executeReadOnly(em ->
            em.find(TestUser.class, user.getId())
        );
        assertThat(found).isNotNull();
        assertThat(found.getUsername()).isEqualTo("alice");
    }

    // --- Phase 6A: refresh ---

    @Test
    void testRefresh() {
        var user = userRepo.create(new TestUser("alice", "alice@example.com"));
        user.setEmail("dirty-modification@example.com");
        var refreshed = userRepo.refresh(user);
        assertThat(refreshed.getEmail()).isEqualTo("alice@example.com");
    }

    // --- Convenience methods from original ---

    @Test
    void testFindByIdOrThrow() {
        var user = userRepo.create(new TestUser("alice", "alice@example.com"));
        var found = userRepo.findByIdOrThrow(user.getId());
        assertThat(found.getUsername()).isEqualTo("alice");
    }

    @Test
    void testFindByIdOrCreate() {
        var user = userRepo.findByIdOrCreate(999L,
            () -> new TestUser("default", "default@example.com"));
        assertThat(user.getUsername()).isEqualTo("default");
        assertThat(user.getId()).isNotNull();
    }
}
