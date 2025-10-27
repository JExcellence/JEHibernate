package de.jexcellence.hibernate.naming;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Physical naming strategy that converts camelCase identifiers to snake_case.
 */
public final class JENamingStrategy extends PhysicalNamingStrategyStandardImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(JENamingStrategy.class);
    private static final Pattern CAMEL_CASE = Pattern.compile("([a-z])([A-Z])");

    @Override
    public Identifier toPhysicalTableName(@NotNull final Identifier logicalName, @NotNull final JdbcEnvironment context) {
        return this.convert(logicalName);
    }

    @Override
    public Identifier toPhysicalColumnName(@NotNull final Identifier logicalName, @NotNull final JdbcEnvironment context) {
        return this.convert(logicalName);
    }

    @Override
    public Identifier toPhysicalSequenceName(@NotNull final Identifier logicalName, @NotNull final JdbcEnvironment context) {
        return this.convert(logicalName);
    }

    private Identifier convert(@NotNull final Identifier logicalName) {
        final String converted = CAMEL_CASE.matcher(logicalName.getText())
            .replaceAll("$1_$2")
            .replace('-', '_')
            .toLowerCase(Locale.ROOT);
        LOGGER.trace("Physical name: {} -> {}", logicalName.getText(), converted);
        return Identifier.toIdentifier(converted, logicalName.isQuoted());
    }
}
