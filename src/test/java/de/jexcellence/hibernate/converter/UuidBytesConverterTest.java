package de.jexcellence.hibernate.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UuidBytesConverter")
class UuidBytesConverterTest {

    private UuidBytesConverter converter;

    @BeforeEach
    void setUp() {
        converter = new UuidBytesConverter();
    }

    @Nested
    @DisplayName("convertToDatabaseColumn")
    class ConvertToDatabaseColumn {

        @Test
        @DisplayName("should convert UUID to 16-byte array")
        void shouldConvertUuidToByteArray() {
            var uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

            var bytes = converter.convertToDatabaseColumn(uuid);

            assertThat(bytes).hasSize(16);
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            var bytes = converter.convertToDatabaseColumn(null);

            assertThat(bytes).isNull();
        }

        @Test
        @DisplayName("should preserve UUID bits correctly")
        void shouldPreserveUuidBitsCorrectly() {
            var uuid = UUID.randomUUID();

            var bytes = converter.convertToDatabaseColumn(uuid);
            var restored = converter.convertToEntityAttribute(bytes);

            assertThat(restored).isEqualTo(uuid);
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute")
    class ConvertToEntityAttribute {

        @Test
        @DisplayName("should convert 16-byte array to UUID")
        void shouldConvertByteArrayToUuid() {
            var original = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
            var bytes = converter.convertToDatabaseColumn(original);

            var result = converter.convertToEntityAttribute(bytes);

            assertThat(result).isEqualTo(original);
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            var result = converter.convertToEntityAttribute(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should throw exception for invalid byte array length")
        void shouldThrowExceptionForInvalidLength() {
            var invalidBytes = new byte[10];

            assertThatThrownBy(() -> converter.convertToEntityAttribute(invalidBytes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 16 bytes");
        }

        @Test
        @DisplayName("should throw exception for empty byte array")
        void shouldThrowExceptionForEmptyArray() {
            var emptyBytes = new byte[0];

            assertThatThrownBy(() -> converter.convertToEntityAttribute(emptyBytes))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("roundtrip")
    class Roundtrip {

        @Test
        @DisplayName("should preserve UUID through conversion cycle")
        void shouldPreserveUuidThroughConversionCycle() {
            var original = UUID.randomUUID();

            var bytes = converter.convertToDatabaseColumn(original);
            var restored = converter.convertToEntityAttribute(bytes);

            assertThat(restored).isEqualTo(original);
        }

        @Test
        @DisplayName("should handle min UUID value")
        void shouldHandleMinUuidValue() {
            var minUuid = new UUID(0L, 0L);

            var bytes = converter.convertToDatabaseColumn(minUuid);
            var restored = converter.convertToEntityAttribute(bytes);

            assertThat(restored).isEqualTo(minUuid);
        }

        @Test
        @DisplayName("should handle max UUID value")
        void shouldHandleMaxUuidValue() {
            var maxUuid = new UUID(-1L, -1L);

            var bytes = converter.convertToDatabaseColumn(maxUuid);
            var restored = converter.convertToEntityAttribute(bytes);

            assertThat(restored).isEqualTo(maxUuid);
        }
    }
}
