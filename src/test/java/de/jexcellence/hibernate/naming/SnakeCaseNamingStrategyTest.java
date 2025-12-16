package de.jexcellence.hibernate.naming;

import org.hibernate.boot.model.naming.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SnakeCaseNamingStrategy")
class SnakeCaseNamingStrategyTest {

    private SnakeCaseNamingStrategy namingStrategy;

    @BeforeEach
    void setUp() {
        namingStrategy = new SnakeCaseNamingStrategy();
    }

    @Nested
    @DisplayName("toPhysicalTableName")
    class ToPhysicalTableName {

        @ParameterizedTest
        @CsvSource({
            "UserAccount, user_account",
            "User, user",
            "HTTPRequest, httprequest",
            "simpleTable, simple_table",
            "ALLCAPS, allcaps",
            "already_snake, already_snake"
        })
        @DisplayName("should convert camelCase to snake_case")
        void shouldConvertCamelCaseToSnakeCase(String input, String expected) {
            var identifier = Identifier.toIdentifier(input);

            var result = namingStrategy.toPhysicalTableName(identifier, null);

            assertThat(result.getText()).isEqualTo(expected);
        }

        @Test
        @DisplayName("should handle hyphenated names")
        void shouldHandleHyphenatedNames() {
            var identifier = Identifier.toIdentifier("user-account");

            var result = namingStrategy.toPhysicalTableName(identifier, null);

            assertThat(result.getText()).isEqualTo("user_account");
        }

        @Test
        @DisplayName("should preserve quoted status")
        void shouldPreserveQuotedStatus() {
            var identifier = Identifier.toIdentifier("UserAccount", true);

            var result = namingStrategy.toPhysicalTableName(identifier, null);

            assertThat(result.isQuoted()).isTrue();
        }
    }

    @Nested
    @DisplayName("toPhysicalColumnName")
    class ToPhysicalColumnName {

        @ParameterizedTest
        @CsvSource({
            "firstName, first_name",
            "lastName, last_name",
            "createdAt, created_at",
            "isActive, is_active",
            "userId, user_id"
        })
        @DisplayName("should convert camelCase column names to snake_case")
        void shouldConvertCamelCaseColumnNamesToSnakeCase(String input, String expected) {
            var identifier = Identifier.toIdentifier(input);

            var result = namingStrategy.toPhysicalColumnName(identifier, null);

            assertThat(result.getText()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("toPhysicalSequenceName")
    class ToPhysicalSequenceName {

        @Test
        @DisplayName("should convert sequence names to snake_case")
        void shouldConvertSequenceNamesToSnakeCase() {
            var identifier = Identifier.toIdentifier("UserAccountSeq");

            var result = namingStrategy.toPhysicalSequenceName(identifier, null);

            assertThat(result.getText()).isEqualTo("user_account_seq");
        }
    }
}
