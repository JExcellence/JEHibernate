package de.jexcellence.hibernate.persistence;

import com.google.common.reflect.ClassPath;
import de.jexcellence.hibernate.JEHibernate;
import jakarta.persistence.Entity;
import jakarta.persistence.SharedCacheMode;
import jakarta.persistence.ValidationMode;
import jakarta.persistence.spi.ClassTransformer;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.PersistenceUnitTransactionType;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight {@link PersistenceUnitInfo} provider used to bootstrap Hibernate without persistence.xml.
 */
public final class InfamousPersistence {

    private static final Logger LOGGER = Logger.getLogger(InfamousPersistence.class.getName());

    private InfamousPersistence() {
        // utility
    }

    @NotNull
    public static PersistenceUnitInfo get() {
        return new PersistenceUnitInfo() {

            @Override
            public String getPersistenceUnitName() {
                return "JEPersistenceUnit";
            }

            @Override
            public String getPersistenceProviderClassName() {
                return null;
            }

            @Override
            public String getScopeAnnotationName() {
                return "";
            }

            @Override
            public List<String> getQualifierAnnotationNames() {
                return List.of();
            }

            @Override
            public PersistenceUnitTransactionType getTransactionType() {
                return PersistenceUnitTransactionType.RESOURCE_LOCAL;
            }

            @Override
            public DataSource getJtaDataSource() {
                return null;
            }

            @Override
            public DataSource getNonJtaDataSource() {
                return null;
            }

            @Override
            public List<String> getMappingFileNames() {
                return Collections.emptyList();
            }

            @Override
            public List<URL> getJarFileUrls() {
                return Collections.emptyList();
            }

            @Override
            public URL getPersistenceUnitRootUrl() {
                return null;
            }

            @Override
            public List<String> getManagedClassNames() {
                final List<String> managed = new ArrayList<>();
                try {
                    ClassPath
                        .from(this.getClass().getClassLoader())
                        .getAllClasses()
                        .stream()
                        .filter(info -> info.getPackageName().contains("database.entity"))
                        .forEach(info -> {
                            try {
                                final Class<?> candidate = info.load();
                                if (candidate.isAnnotationPresent(Entity.class)) {
                                    managed.add(candidate.getName());
                                }
                            } catch (final Throwable throwable) {
                                LOGGER.log(Level.WARNING, "Failed to load class {0}", info.getName());
                                LOGGER.log(Level.FINE, throwable, throwable::getMessage);
                            }
                        });
                } catch (final Exception exception) {
                    LOGGER.log(Level.SEVERE, "Classpath scanning failed", exception);
                }
                return managed;
            }

            @Override
            public boolean excludeUnlistedClasses() {
                return false;
            }

            @Override
            public SharedCacheMode getSharedCacheMode() {
                return SharedCacheMode.UNSPECIFIED;
            }

            @Override
            public ValidationMode getValidationMode() {
                return ValidationMode.AUTO;
            }

            @Override
            public Properties getProperties() {
                return new Properties();
            }

            @Override
            public String getPersistenceXMLSchemaVersion() {
                return "3.1";
            }

            @Override
            public ClassLoader getClassLoader() {
                return JEHibernate.class.getClassLoader();
            }

            @Override
            public void addTransformer(final ClassTransformer classTransformer) {
                // not required
            }

            @Override
            public ClassLoader getNewTempClassLoader() {
                return JEHibernate.class.getClassLoader();
            }
        };
    }
}
