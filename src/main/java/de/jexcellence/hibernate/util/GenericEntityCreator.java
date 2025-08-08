package de.jexcellence.hibernate.util;

import de.jexcellence.hibernate.repository.AbstractCRUDRepository;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GenericEntityCreator is a utility class that scans a specified package for all classes annotated with
 * {@code @Entity} and creates/persists instances of those entities if they do not already exist in the database.
 *
 * <p>This class uses reflection to instantiate each entity and, when available, attempts to retrieve a unique
 * identifier via a "getId" method to check if the entity already exists before persisting.</p>
 *
 * <p>The class is designed to work with any entity type that follows JPA conventions and can be used
 * for database initialization or seeding purposes.</p>
 *
 * @param <T>  the type of the entity
 * @param <ID> the type of the entity's identifier
 *
 * @author JExcellence
 * @version 1.0
 * @since 1.0
 */
public class GenericEntityCreator<T, ID> {
	
	private final AbstractCRUDRepository<T, ID> crudRepository;
	private final Logger logger;
	
	/**
	 * Constructs a new GenericEntityCreator with the specified repository and logger.
	 *
	 * <p>The repository is used for all CRUD operations, while the logger is used for
	 * tracking the entity creation process and any errors that may occur.</p>
	 *
	 * @param crudRepository the repository instance to use for CRUD operations
	 * @param logger the logger instance for logging operations and errors
	 * @throws IllegalArgumentException if crudRepository or logger is null
	 */
	public GenericEntityCreator(
		AbstractCRUDRepository<T, ID> crudRepository,
		Logger logger
	) {
		this.crudRepository = crudRepository;
		this.logger = logger;
	}
	
	/**
	 * Scans the provided package for classes annotated with {@code @Entity}, instantiates each entity,
	 * and attempts to persist the entity if it does not already exist in the database.
	 *
	 * <p>This method performs the following operations:</p>
	 * <ul>
	 *   <li>Scans the specified package for entity classes using reflection</li>
	 *   <li>Instantiates each entity using the default constructor</li>
	 *   <li>Attempts to retrieve the entity's ID using the getId() method</li>
	 *   <li>Checks if the entity exists in the database before persisting</li>
	 *   <li>Logs the entire process for monitoring and debugging</li>
	 * </ul>
	 *
	 * <p>If an entity does not have a getId() method, it will be persisted without an existence check.
	 * Any errors during entity processing are logged but do not stop the processing of other entities.</p>
	 *
	 * @param packagePath the package path containing the entity classes to scan
	 * @throws IllegalArgumentException if packagePath is null or empty
	 * @see Entity
	 * @see Reflections
	 */
	public void createEntitiesFromPackage(String packagePath) {
		logger.log(Level.INFO, "Starting createEntitiesFromPackage for package: " + packagePath);
		Reflections reflections = new Reflections(packagePath);
		Set<Class<?>> entityClasses = reflections.getTypesAnnotatedWith(Entity.class);
		
		for (Class<?> clazz : entityClasses) {
			try {
				T entity = (T) clazz.getDeclaredConstructor().newInstance();
				ID id = null;
				try {
					Method getIdMethod = clazz.getMethod("getId");
					id = (ID) getIdMethod.invoke(entity);
				} catch (NoSuchMethodException name) {
					logger.log(Level.INFO, "No getId method found for " + clazz.getSimpleName() + ". Persisting entity without existence check.");
				}
				saveIfNotExist(entity, clazz, id);
			} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
				logger.log(Level.SEVERE, "Error processing entity class: " + clazz.getSimpleName(), e);
			}
		}
		logger.log(Level.INFO, "Finished createEntitiesFromPackage for package: " + packagePath);
	}
	
	/**
	 * Persists the entity if it does not already exist in the database.
	 *
	 * <p>This method implements the following logic:</p>
	 * <ul>
	 *   <li>If the identifier is null, the entity is assumed to be new and is directly persisted</li>
	 *   <li>If the identifier is not null, checks if an entity with that ID already exists</li>
	 *   <li>Only persists the entity if no existing entity is found with the same identifier</li>
	 * </ul>
	 *
	 * <p>The persistence operation is performed asynchronously using the repository's createAsync method.</p>
	 *
	 * @param entity      the entity instance to persist
	 * @param clazz       the class of the entity (used for logging purposes)
	 * @param identifier  the identifier value obtained via "getId" if available, otherwise null
	 * @throws RuntimeException if there's an error during the persistence operation
	 */
	private void saveIfNotExist(
		final T entity,
		final Class<?> clazz,
		final ID identifier
	) {
		if (identifier == null) {
			this.crudRepository.createAsync(entity);
		} else if (this.crudRepository.findById(identifier) == null) {
			this.crudRepository.createAsync(entity);
		}
	}
}