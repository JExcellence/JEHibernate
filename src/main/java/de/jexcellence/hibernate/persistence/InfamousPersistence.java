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

/*
* We use this class to avoid the .xml variant of persistence.xml
*/
public class InfamousPersistence {

    public static PersistenceUnitInfo get() {
        return new PersistenceUnitInfo() {
            @Override
            public String getPersistenceUnitName() {
                return "JEPersistenceUnit";
            }

            @Override
            public String getPersistenceProviderClassName() {
                return HibernatePersistenceProvider.class.getName();
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

            /*
            * To find @Entity annotated classes, we will use this method to find all, without declaring them manually.
            */
            @Override
            public List<String> getManagedClassNames() {
                final List<String> foundClasses = new ArrayList<>();
                try {
                    ClassPath
                            .from(this.getClass().getClassLoader())
                            .getAllClasses()
                            .stream()
                            //here we declare a package name, mine is always called "database.entity", here it will look for the entities to reduce the amount of time looking through all classes.
                            .filter(clasSInfo -> clasSInfo.getPackageName().contains("database.entity"))
                            .map(ClassPath.ClassInfo::load)
                            .filter(clazz -> clazz.isAnnotationPresent(Entity.class))
                            .forEach(clazz -> {
                                try {
                                    this.getClass().getClassLoader().loadClass(clazz.getName());
                                    foundClasses.add(clazz.getName());
                                } catch (final Exception ignored) {
                                    //Class could not be loaded for some reason
                                    //Make sure it has one public/protected empty constructor
                                }
                            });
                } catch (
                        final Exception ignored
                ) {
                    //Something went wrong, we will return an empty list
                }
                return foundClasses;
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
                return "2.3";
            }

            @Override
            public ClassLoader getClassLoader() {
                return JEHibernate.class.getClassLoader();
            }

            @Override
            public void addTransformer(ClassTransformer classTransformer) {
                // Not implemented
            }

            @Override
            public ClassLoader getNewTempClassLoader() {
                return JEHibernate.class.getClassLoader();
            }
        };
    }
}
