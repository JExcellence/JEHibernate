package de.jexcellence.hibernate.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RepositoryMetadata")
class RepositoryMetadataTest {

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should create metadata without key extractor")
        void shouldCreateMetadataWithoutKeyExtractor() {
            var metadata = new RepositoryMetadata<>(
                TestRepository.class,
                TestEntity.class,
                null
            );

            assertThat(metadata.getRepositoryClass()).isEqualTo(TestRepository.class);
            assertThat(metadata.getEntityClass()).isEqualTo(TestEntity.class);
            assertThat(metadata.hasKeyExtractor()).isFalse();
            assertThat(metadata.getKeyExtractor()).isNull();
        }

        @Test
        @DisplayName("should create metadata with key extractor")
        void shouldCreateMetadataWithKeyExtractor() {
            Function<TestEntity, String> keyExtractor = TestEntity::getName;

            var metadata = new RepositoryMetadata<>(
                TestCachedRepository.class,
                TestEntity.class,
                keyExtractor
            );

            assertThat(metadata.hasKeyExtractor()).isTrue();
            assertThat(metadata.getKeyExtractor()).isEqualTo(keyExtractor);
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal when repository and entity classes match")
        void shouldBeEqualWhenClassesMatch() {
            var metadata1 = new RepositoryMetadata<>(TestRepository.class, TestEntity.class, null);
            var metadata2 = new RepositoryMetadata<>(TestRepository.class, TestEntity.class, null);

            assertThat(metadata1).isEqualTo(metadata2);
            assertThat(metadata1.hashCode()).isEqualTo(metadata2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when repository classes differ")
        void shouldNotBeEqualWhenRepositoryClassesDiffer() {
            var metadata1 = new RepositoryMetadata<>(TestRepository.class, TestEntity.class, null);
            var metadata2 = new RepositoryMetadata<>(TestCachedRepository.class, TestEntity.class, null);

            assertThat(metadata1).isNotEqualTo(metadata2);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("should include class names and key extractor status")
        void shouldIncludeClassNamesAndKeyExtractorStatus() {
            var metadata = new RepositoryMetadata<>(TestRepository.class, TestEntity.class, null);

            var result = metadata.toString();

            assertThat(result)
                .contains("TestRepository")
                .contains("TestEntity")
                .contains("hasKeyExtractor=false");
        }
    }

    private static class TestEntity {
        private String name;

        public String getName() {
            return name;
        }
    }

    private static class TestRepository extends BaseRepository<TestEntity, Long> {
        public TestRepository() {
            super(null, null, TestEntity.class);
        }
    }

    private static class TestCachedRepository extends CachedRepository<TestEntity, Long, String> {
        public TestCachedRepository() {
            super(null, null, TestEntity.class, TestEntity::getName);
        }
    }
}
