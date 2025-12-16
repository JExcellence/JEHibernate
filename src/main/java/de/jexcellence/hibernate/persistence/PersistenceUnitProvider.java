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
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides {@link PersistenceUnitInfo} for programmatic Hibernate bootstrap without persistence.xml.
 *
 * @author JExcellence
 * @version 1.0
 * @since 1.0
 */
@SuppressWarnings("UnstableApiUsage")
public final class PersistenceUnitProvider {

    private static final Logger LOGGER = Logger.getLogger(PersistenceUnitProvider.class.getName());

    private PersistenceUnitProvider() {
    }

    @NotNull
    public static PersistenceUnitInfo get() {
        return new PersistenceUnitInfo() {

            @Override
            @NotNull
            public String getPersistenceUnitName() {
                return "JEPersistenceUnit";
            }

            @Override
            @Nullable
            public String getPersistenceProviderClassName() {
                return null;
            }

            @Override
            @NotNull
            public String getScopeAnnotationName() {
                return "";
            }

            @Override
            @NotNull
            public List<String> getQualifierAnnotationNames() {
                return List.of();
            }

            @Override
            @NotNull
            public PersistenceUnitTransactionType getTransactionType() {
                return PersistenceUnitTransactionType.RESOURCE_LOCAL;
            }

            @Override
            @Nullable
            public DataSource getJtaDataSource() {
                return null;
            }

            @Override
            @Nullable
            public DataSource getNonJtaDataSource() {
                return null;
            }

            @Override
            @NotNull
            public List<String> getMappingFileNames() {
                return Collections.emptyList();
            }

            @Override
            @NotNull
            public List<URL> getJarFileUrls() {
                return Collections.emptyList();
            }

            @Override
            @Nullable
            public URL getPersistenceUnitRootUrl() {
                return null;
            }

            @Override
            @NotNull
            public List<String> getManagedClassNames() {
                var managed = new ArrayList<String>();
                try {
                    ClassPath
                        .from(this.getClass().getClassLoader())
                        .getAllClasses()
                        .stream()
                        .filter(info -> info.getPackageName().contains("database.entity"))
                        .forEach(info -> {
                            try {
                                var candidate = info.load();
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
            @NotNull
            public SharedCacheMode getSharedCacheMode() {
                return SharedCacheMode.UNSPECIFIED;
            }

            @Override
            @NotNull
            public ValidationMode getValidationMode() {
                return ValidationMode.AUTO;
            }

            @Override
            @NotNull
            public Properties getProperties() {
                return new Properties();
            }

            @Override
            @NotNull
            public String getPersistenceXMLSchemaVersion() {
                return "3.1";
            }

            @Override
            @NotNull
            public ClassLoader getClassLoader() {
                return JEHibernate.class.getClassLoader();
            }

            @Override
            public void addTransformer(final ClassTransformer classTransformer) {
            }

            @Override
            @NotNull
            public ClassLoader getNewTempClassLoader() {
                return JEHibernate.class.getClassLoader();
            }
        };
    }
}
