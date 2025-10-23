package de.jexcellence.hibernate.persistence;

import com.google.common.reflect.ClassPath;
import de.jexcellence.hibernate.JEHibernate;
import jakarta.persistence.Entity;
import jakarta.persistence.SharedCacheMode;
import jakarta.persistence.ValidationMode;
import jakarta.persistence.spi.ClassTransformer;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.PersistenceUnitTransactionType;

import javax.sql.DataSource;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InfamousPersistence {
	
	private InfamousPersistence() {
	}
	
	public static PersistenceUnitInfo get() {
		return new PersistenceUnitInfo() {
			
			@Override
			public String getPersistenceUnitName() {
				return "JEPersistenceUnit";
			}
			
			/**
			 * UPDATED: Return null and let Hibernate 7.x use its default provider
			 * Hibernate 7.x automatically detects the persistence provider
			 */
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
			
			/**
			 * UPDATED: More efficient entity discovery for Hibernate 7.x
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
						.forEach(classInfo -> {
							try {
								Class<?> clazz = classInfo.load();
								if (clazz.isAnnotationPresent(Entity.class)) {
									foundClasses.add(clazz.getName());
								}
							} catch (Throwable t) {
								Logger.getLogger(InfamousPersistence.class.getName())
								      .log(Level.WARNING, "Failed to load class: " + classInfo.getName(), t);
							}
						});
				} catch (Exception e) {
					Logger.getLogger(InfamousPersistence.class.getName())
					      .log(Level.SEVERE, "Classpath scanning failed", e);
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
			
			/**
			 * UPDATED: JPA 3.1 for Hibernate 7.x compatibility
			 */
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
			}
			
			@Override
			public ClassLoader getNewTempClassLoader() {
				return JEHibernate.class.getClassLoader();
			}
		};
	}
}