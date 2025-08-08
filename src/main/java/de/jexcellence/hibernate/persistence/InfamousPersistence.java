package de.jexcellence.hibernate.persistence;

import com.google.common.reflect.ClassPath;
import de.jexcellence.hibernate.JEHibernate;
import jakarta.persistence.Entity;
import jakarta.persistence.SharedCacheMode;
import jakarta.persistence.ValidationMode;
import jakarta.persistence.spi.ClassTransformer;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.PersistenceUnitTransactionType;
import org.hibernate.jpa.HibernatePersistenceProvider;

import javax.sql.DataSource;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * InfamousPersistence provides a programmatic alternative to XML-based persistence configuration
 * by implementing PersistenceUnitInfo interface for Hibernate JPA setup.
 *
 * <p>This utility class eliminates the need for traditional persistence.xml configuration files
 * by providing a code-based approach to define persistence unit settings. It offers the following
 * key capabilities:</p>
 * <ul>
 *   <li>Automatic discovery of JPA entities using reflection and classpath scanning</li>
 *   <li>Programmatic persistence unit configuration without XML dependencies</li>
 *   <li>Dynamic entity class loading with package-based filtering for performance</li>
 *   <li>Hibernate-specific persistence provider integration</li>
 *   <li>Resource-local transaction management configuration</li>
 *   <li>Flexible entity discovery with error-tolerant class loading</li>
 * </ul>
 *
 * <p>The class uses Google Guava's ClassPath utility to scan the classpath for classes
 * annotated with {@code @Entity}, specifically targeting the "database.entity" package
 * to optimize discovery performance and reduce scanning overhead.</p>
 *
 * <p>This approach is particularly useful in scenarios where:</p>
 * <ul>
 *   <li>XML configuration files are not desired or practical</li>
 *   <li>Dynamic entity discovery is required</li>
 *   <li>Programmatic control over persistence configuration is needed</li>
 *   <li>Simplified deployment without external configuration files is preferred</li>
 * </ul>
 *
 * <p>Usage example:</p>
 * <pre>
 * PersistenceUnitInfo persistenceUnit = InfamousPersistence.get();
 * EntityManagerFactory emf = Persistence.createEntityManagerFactory(persistenceUnit);
 * </pre>
 *
 * @author JExcellence
 * @version 1.0
 * @since 1.0
 * @see PersistenceUnitInfo
 * @see HibernatePersistenceProvider
 * @see ClassPath
 * @see Entity
 */
public class InfamousPersistence {
    
    /**
     * Private constructor to prevent instantiation of the class.
     */
    private InfamousPersistence() {
    
    }
    
    /**
     * Creates and returns a configured PersistenceUnitInfo instance for Hibernate JPA setup.
     *
     * <p>This method returns an anonymous implementation of PersistenceUnitInfo that provides
     * all necessary configuration for a Hibernate-based persistence unit. The implementation
     * includes automatic entity discovery, transaction configuration, and provider setup.</p>
     *
     * <p>The returned PersistenceUnitInfo is configured with:</p>
     * <ul>
     *   <li>Hibernate as the persistence provider</li>
     *   <li>Resource-local transaction management</li>
     *   <li>Automatic entity class discovery from "database.entity" package</li>
     *   <li>Auto validation mode for bean validation</li>
     *   <li>Unspecified shared cache mode for flexibility</li>
     * </ul>
     *
     * <p>The entity discovery process is optimized to scan only classes within packages
     * containing "database.entity" to improve startup performance and reduce memory usage.</p>
     *
     * @return a fully configured PersistenceUnitInfo instance ready for EntityManagerFactory creation
     * @throws RuntimeException if entity discovery fails due to classpath issues
     */
    public static PersistenceUnitInfo get() {
        return new PersistenceUnitInfo() {
            
            /**
             * Returns the name of this persistence unit.
             *
             * <p>The persistence unit name is used to identify this specific configuration
             * and can be referenced when creating EntityManagerFactory instances.</p>
             *
             * @return the persistence unit name "JEPersistenceUnit"
             */
            @Override
            public String getPersistenceUnitName() {
                return "JEPersistenceUnit";
            }
            
            /**
             * Returns the fully qualified class name of the persistence provider.
             *
             * <p>This implementation uses Hibernate as the JPA persistence provider,
             * which provides comprehensive ORM capabilities and performance optimizations.</p>
             *
             * @return the Hibernate persistence provider class name
             */
            @Override
            public String getPersistenceProviderClassName() {
                return HibernatePersistenceProvider.class.getName();
            }
            
            /**
             * Returns the transaction type for this persistence unit.
             *
             * <p>Resource-local transactions are used, which means the application
             * is responsible for managing transactions rather than relying on
             * container-managed transactions (JTA).</p>
             *
             * @return RESOURCE_LOCAL transaction type for application-managed transactions
             */
            @Override
            public PersistenceUnitTransactionType getTransactionType() {
                return PersistenceUnitTransactionType.RESOURCE_LOCAL;
            }
            
            /**
             * Returns the JTA data source for this persistence unit.
             *
             * <p>Since this persistence unit uses resource-local transactions,
             * no JTA data source is configured. Data source configuration is
             * handled through Hibernate properties instead.</p>
             *
             * @return null as JTA data source is not used
             */
            @Override
            public DataSource getJtaDataSource() {
                return null;
            }
            
            /**
             * Returns the non-JTA data source for this persistence unit.
             *
             * <p>Data source configuration is handled through Hibernate properties
             * rather than programmatic DataSource configuration, allowing for
             * more flexible database connection management.</p>
             *
             * @return null as data source is configured via properties
             */
            @Override
            public DataSource getNonJtaDataSource() {
                return null;
            }
            
            /**
             * Returns the list of mapping file names for this persistence unit.
             *
             * <p>This implementation relies on annotation-based mapping rather than
             * XML mapping files, so no mapping files are specified.</p>
             *
             * @return an empty list as no XML mapping files are used
             */
            @Override
            public List<String> getMappingFileNames() {
                return Collections.emptyList();
            }
            
            /**
             * Returns the list of JAR file URLs containing entities.
             *
             * <p>Entity discovery is performed through classpath scanning rather than
             * explicit JAR file specification, providing more flexible deployment options.</p>
             *
             * @return an empty list as JAR files are not explicitly specified
             */
            @Override
            public List<URL> getJarFileUrls() {
                return Collections.emptyList();
            }
            
            /**
             * Returns the root URL of the persistence unit.
             *
             * <p>No specific root URL is required as entity discovery is performed
             * through comprehensive classpath scanning.</p>
             *
             * @return null as no specific root URL is configured
             */
            @Override
            public URL getPersistenceUnitRootUrl() {
                return null;
            }
            
            /**
             * Discovers and returns the names of all managed entity classes.
             *
             * <p>This method performs automatic entity discovery by scanning the classpath
             * for classes annotated with {@code @Entity}. The discovery process is optimized
             * to scan only packages containing "database.entity" to improve performance.</p>
             *
             * <p>The discovery process follows these steps:</p>
             * <ol>
             *   <li>Scans the entire classpath using Google Guava's ClassPath utility</li>
             *   <li>Filters classes to only those in packages containing "database.entity"</li>
             *   <li>Loads each class and checks for {@code @Entity} annotation</li>
             *   <li>Attempts to load each entity class to verify it's properly configured</li>
             *   <li>Collects successfully loaded entity class names</li>
             * </ol>
             *
             * <p>The method is fault-tolerant and will skip classes that cannot be loaded,
             * ensuring that one problematic entity doesn't prevent the entire application
             * from starting. Classes that fail to load are typically missing required
             * constructors or have other configuration issues.</p>
             *
             * @return a list of fully qualified class names of all discovered entity classes
             */
            @Override
            public List<String> getManagedClassNames() {
                final List<String> foundClasses = new ArrayList<>();
                try {
                    ClassPath
                        .from(this.getClass().getClassLoader())
                        .getAllClasses()
                        .stream()
                        .filter(classInfo -> classInfo.getPackageName().contains("database.entity"))
                        .map(ClassPath.ClassInfo::load)
                        .filter(clazz -> clazz.isAnnotationPresent(Entity.class))
                        .forEach(clazz -> {
                            try {
                                this.getClass().getClassLoader().loadClass(clazz.getName());
                                foundClasses.add(clazz.getName());
                            } catch (
                                final Exception ignored
                            ) {
                                Logger.getLogger(InfamousPersistence.class.getName()).log(Level.WARNING, "Missing @Entity annotation or default constructor is not public/protected");
                                // Entity class loading failed - typically due to missing default constructor
                                // or other configuration issues. Skip this class and continue.
                            }
                        });
                } catch (
                    final Exception ignored
                ) {
                    // Classpath scanning failed - return empty list to allow graceful degradation
                }
                return foundClasses;
            }
            
            /**
             * Indicates whether unlisted classes should be excluded from the persistence unit.
             *
             * <p>Returns false to allow inclusion of entity classes that are discovered
             * dynamically through classpath scanning, even if they're not explicitly
             * listed in the persistence unit configuration.</p>
             *
             * @return false to include dynamically discovered entity classes
             */
            @Override
            public boolean excludeUnlistedClasses() {
                return false;
            }
            
            /**
             * Returns the shared cache mode for this persistence unit.
             *
             * <p>Uses unspecified cache mode to allow Hibernate to determine the
             * appropriate caching strategy based on entity-level cache annotations
             * and configuration properties.</p>
             *
             * @return UNSPECIFIED to allow provider-determined cache behavior
             */
            @Override
            public SharedCacheMode getSharedCacheMode() {
                return SharedCacheMode.UNSPECIFIED;
            }
            
            /**
             * Returns the validation mode for this persistence unit.
             *
             * <p>Uses automatic validation mode, which enables Bean Validation
             * if it's available on the classpath, providing automatic validation
             * of entity constraints during persistence operations.</p>
             *
             * @return AUTO to enable validation when Bean Validation is available
             */
            @Override
            public ValidationMode getValidationMode() {
                return ValidationMode.AUTO;
            }
            
            /**
             * Returns the properties for this persistence unit.
             *
             * <p>Returns an empty Properties object as configuration properties
             * are typically provided through external configuration mechanisms
             * rather than being hardcoded in the persistence unit definition.</p>
             *
             * @return an empty Properties object for external property configuration
             */
            @Override
            public Properties getProperties() {
                return new Properties();
            }
            
            /**
             * Returns the JPA specification version for this persistence unit.
             *
             * <p>Specifies JPA 2.3 as the target specification version, ensuring
             * compatibility with modern JPA features and capabilities.</p>
             *
             * @return "2.3" indicating JPA 2.3 specification compliance
             */
            @Override
            public String getPersistenceXMLSchemaVersion() {
                return "2.3";
            }
            
            /**
             * Returns the class loader for this persistence unit.
             *
             * <p>Uses the class loader from JEHibernate class to ensure consistent
             * class loading behavior across the entire persistence framework.</p>
             *
             * @return the class loader used by the JEHibernate framework
             */
            @Override
            public ClassLoader getClassLoader() {
                return JEHibernate.class.getClassLoader();
            }
            
            /**
             * Adds a class transformer to this persistence unit.
             *
             * <p>This method is not implemented as class transformation is not
             * required for the current persistence unit configuration. Class
             * transformers are typically used for advanced bytecode manipulation
             * scenarios.</p>
             *
             * @param classTransformer the class transformer to add (ignored)
             */
            @Override
            public void addTransformer(final ClassTransformer classTransformer) {
                // Class transformation is not implemented for this persistence unit
            }
            
            /**
             * Returns a new temporary class loader for this persistence unit.
             *
             * <p>Returns the same class loader as the main class loader since
             * temporary class loading is not required for this configuration.
             * This ensures consistent class loading behavior.</p>
             *
             * @return the same class loader used by the JEHibernate framework
             */
            @Override
            public ClassLoader getNewTempClassLoader() {
                return JEHibernate.class.getClassLoader();
            }
        };
    }
}