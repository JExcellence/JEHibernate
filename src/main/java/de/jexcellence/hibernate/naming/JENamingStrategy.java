package de.jexcellence.hibernate.naming;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * Custom naming strategy for Hibernate to convert logical names to physical names.
 * Supports snake_case conversion and hyphen replacement with underscores.
 */
public class JENamingStrategy extends PhysicalNamingStrategyStandardImpl {

    private static final Logger logger = LoggerFactory.getLogger(JENamingStrategy.class);
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([a-z])([A-Z]+)");
    private static final String UNDERSCORE = "_";

    @Override
    public Identifier toPhysicalTableName(Identifier logicalName, JdbcEnvironment context) {
        return convertIdentifier(logicalName, context);
    }

    @Override
    public Identifier toPhysicalColumnName(Identifier logicalName, JdbcEnvironment context) {
        return convertIdentifier(logicalName, context);
    }

    @Override
    public Identifier toPhysicalSequenceName(Identifier logicalName, JdbcEnvironment context) {
        return convertIdentifier(logicalName, context);
    }

    private Identifier convertIdentifier(Identifier logicalName, JdbcEnvironment context) {
        String convertedName = applyNamingConvention(logicalName.getText());
        logger.trace("Converted name: {} -> {}", logicalName.getText(), convertedName);
        return new Identifier(convertedName, logicalName.isQuoted());
    }

    private String applyNamingConvention(String name) {
        String converted = CAMEL_CASE_PATTERN.matcher(name).replaceAll("$1" + UNDERSCORE + "$2")
                .replace("-", UNDERSCORE)
                .toLowerCase();
        logger.trace("Applied naming convention: {} -> {}", name, converted);
        return converted;
    }
}