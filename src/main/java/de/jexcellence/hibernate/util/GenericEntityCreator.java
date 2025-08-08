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

 * It uses reflection to instantiate each entity and, when available, attempts to retrieve a unique identifier
 * via a "getId" method to check if the entity already exists.
 */
public class GenericEntityCreator<T, ID> {

	private final AbstractCRUDRepository<T, ID> crudRepository;
	private final Logger logger;

	/**
	 * Constructs a new GenericEntityCreatorWithRepositories.
	 *
	 * @param crudRepository the repository instance to use for CRUD operations.
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
	 * and attempts to persist the entity if it does not already exist.
	 *
	 * @param packagePath the package path containing the entity classes
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
	 * Persists the entity if it does not already exist in the database. If the id is null, the entity is assumed
	 * to be new and is directly persisted.
	 *
	 * @param entity      the entity instance to persist
	 * @param clazz       the class of the entity
	 * @param identifier  the identifier value obtained via "getId" if available, otherwise null
	 */
	private void saveIfNotExist(
		final T entity,
		final Class<?> clazz,
		final ID identifier
	) {
		if (
			identifier == null
		) {
			this.crudRepository.createAsync(entity);
		} else if (
			this.crudRepository.findById(identifier) == null
		) {
			this.crudRepository.createAsync(entity);
		}
	}
}
