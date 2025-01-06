package de.jexcellence.hibernate.naming;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

/**
 * Custom naming strategy for Hibernate to convert logical names to physical names
 * using snake_case and replacing hyphens with underscores.
 */
public class JENamingStrategy extends PhysicalNamingStrategyStandardImpl {

    /**
     * Converts the logical table name to a physical table name using the naming convention.
     *
     * @param logicalName the logical name of the table
     * @param context the JDBC environment context
     * @return the physical table name as an Identifier
     */
    @Override
    public Identifier toPhysicalTableName(Identifier logicalName, JdbcEnvironment context) {
        return new Identifier(this.applyNamingConvention(logicalName.getText()), logicalName.isQuoted());
    }

    /**
     * Converts the logical column name to a physical column name using the naming convention.
     *
     * @param logicalName the logical name of the column
     * @param context the JDBC environment context
     * @return the physical column name as an Identifier
     */
    @Override
    public Identifier toPhysicalColumnName(Identifier logicalName, JdbcEnvironment context) {
        return new Identifier(this.applyNamingConvention(logicalName.getText()), logicalName.isQuoted());
    }

    /**
     * Converts the logical sequence name to a physical sequence name using the naming convention.
     *
     * @param logicalName the logical name of the sequence
     * @param context the JDBC environment context
     * @return the physical sequence name as an Identifier
     */
    @Override
    public Identifier toPhysicalSequenceName(Identifier logicalName, JdbcEnvironment context) {
        return new Identifier(this.applyNamingConvention(logicalName.getText()), logicalName.isQuoted());
    }

    /**
     * Applies the naming convention to convert camelCase to snake_case and replace hyphens with underscores.
     *
     * @param name the original name
     * @return the name converted to snake_case with underscores
     */
    private String applyNamingConvention(String name) {
        return name.replaceAll("([a-z])([A-Z]+)", "$1_$2")
                .replace("-", "_")
                .toLowerCase();
    }
}
