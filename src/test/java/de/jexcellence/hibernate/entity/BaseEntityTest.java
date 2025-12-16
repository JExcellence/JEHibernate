package de.jexcellence.hibernate.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BaseEntity")
class BaseEntityTest {

    private TestEntity entity;

    @BeforeEach
    void setUp() {
        entity = new TestEntity();
    }

    @Nested
    @DisplayName("isNew")
    class IsNew {

        @Test
        @DisplayName("should return true for new entity without id")
        void shouldReturnTrueForNewEntity() {
            assertThat(entity.isNew()).isTrue();
        }

        @Test
        @DisplayName("should return false after id is set")
        void shouldReturnFalseAfterIdSet() {
            entity.setId(1L);
            assertThat(entity.isNew()).isFalse();
        }
    }

    @Nested
    @DisplayName("setId")
    class SetId {

        @Test
        @DisplayName("should set id on new entity")
        void shouldSetIdOnNewEntity() {
            entity.setId(42L);
            assertThat(entity.getId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("should throw exception when setting id on persisted entity")
        void shouldThrowExceptionWhenSettingIdOnPersistedEntity() {
            entity.setId(1L);

            assertThatThrownBy(() -> entity.setId(2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be set after persistence");
        }
    }

    @Nested
    @DisplayName("softDelete")
    class SoftDelete {

        @Test
        @DisplayName("should mark entity as deleted")
        void shouldMarkEntityAsDeleted() {
            assertThat(entity.isDeleted()).isFalse();

            entity.softDelete();

            assertThat(entity.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("should be idempotent")
        void shouldBeIdempotent() {
            entity.softDelete();
            entity.softDelete();

            assertThat(entity.isDeleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("setCreatedAt")
    class SetCreatedAt {

        @Test
        @DisplayName("should set createdAt on new entity")
        void shouldSetCreatedAtOnNewEntity() {
            var timestamp = LocalDateTime.now();
            entity.setCreatedAt(timestamp);

            assertThat(entity.getCreatedAt()).isEqualTo(timestamp);
        }

        @Test
        @DisplayName("should throw exception when changing createdAt on persisted entity")
        void shouldThrowExceptionWhenChangingCreatedAtOnPersistedEntity() {
            entity.setId(1L);

            assertThatThrownBy(() -> entity.setCreatedAt(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be changed after persistence");
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal when ids match")
        void shouldBeEqualWhenIdsMatch() {
            entity.setId(1L);
            var other = new TestEntity();
            other.setId(1L);

            assertThat(entity).isEqualTo(other);
            assertThat(entity.hashCode()).isEqualTo(other.hashCode());
        }

        @Test
        @DisplayName("should not be equal when ids differ")
        void shouldNotBeEqualWhenIdsDiffer() {
            entity.setId(1L);
            var other = new TestEntity();
            other.setId(2L);

            assertThat(entity).isNotEqualTo(other);
        }

        @Test
        @DisplayName("should handle null ids")
        void shouldHandleNullIds() {
            var other = new TestEntity();

            assertThat(entity).isEqualTo(other);
        }
    }

    @Nested
    @DisplayName("onPrePersist")
    class OnPrePersist {

        @Test
        @DisplayName("should set timestamps when null")
        void shouldSetTimestampsWhenNull() {
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();

            entity.onPrePersist();

            assertThat(entity.getCreatedAt()).isNotNull();
            assertThat(entity.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should not override existing createdAt")
        void shouldNotOverrideExistingCreatedAt() {
            var originalTimestamp = LocalDateTime.of(2020, 1, 1, 0, 0);
            entity.setCreatedAt(originalTimestamp);

            entity.onPrePersist();

            assertThat(entity.getCreatedAt()).isEqualTo(originalTimestamp);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("should include class name and fields")
        void shouldIncludeClassNameAndFields() {
            entity.setId(1L);
            entity.setVersion(5);

            var result = entity.toString();

            assertThat(result)
                .contains("TestEntity")
                .contains("id=1")
                .contains("version=5")
                .contains("deleted=false");
        }
    }

    private static class TestEntity extends BaseEntity {
    }
}
