package de.jexcellence.jehibernate.scanner;

import de.jexcellence.jehibernate.repository.base.Repository;
import de.jexcellence.jehibernate.repository.manager.RepositoryRegistry;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for scanning and discovering repository classes in packages.
 * <p>
 * RepositoryScanner uses reflection to automatically discover all repository
 * implementations in specified packages, extracting their entity types for
 * automatic registration.
 * <p>
 * <b>Features:</b>
 * <ul>
 *   <li>Automatic repository discovery</li>
 *   <li>Entity type extraction from generics</li>
 *   <li>Filters out abstract classes and interfaces</li>
 *   <li>Multi-package scanning support</li>
 * </ul>
 * <p>
 * <b>Example Usage:</b>
 * <pre>{@code
 * // Automatic scanning (recommended)
 * var jeHibernate = JEHibernate.builder()
 *     .scanPackages("com.example.repositories")
 *     .build();
 * 
 * // Manual scanning
 * Map<Class<?>, Class<?>> repoToEntity = RepositoryScanner.scan("com.example");
 * repoToEntity.forEach((repoClass, entityClass) -> {
 *     System.out.println(repoClass.getSimpleName() + " -> " + entityClass.getSimpleName());
 * });
 * }</pre>
 * <p>
 * <b>How It Works:</b>
 * <ol>
 *   <li>Scans specified packages for classes implementing Repository</li>
 *   <li>Filters out abstract classes and interfaces</li>
 *   <li>Extracts entity type from Repository&lt;T, ID&gt; generic parameters</li>
 *   <li>Returns map of repository class to entity class</li>
 * </ol>
 * <p>
 * <b>Thread Safety:</b> This class is stateless and thread-safe.
 *
 * @author JEHibernate
 * @version 2.0
 * @since 1.0
 * @see EntityScanner
 * @see RepositoryRegistry
 */
public final class RepositoryScanner {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryScanner.class);
    
    private RepositoryScanner() {
    }
    
    @SuppressWarnings({"rawtypes"})
    public static Map<Class<?>, Class<?>> scan(String... basePackages) {
        if (basePackages == null || basePackages.length == 0) {
            LOGGER.warn("No base packages specified for repository scanning");
            return Map.of();
        }
        
        LOGGER.info("Scanning for repositories in packages: {}", Arrays.toString(basePackages));
        
        Map<Class<?>, Class<?>> repositories = new HashMap<>();
        
        for (String basePackage : basePackages) {
            Reflections reflections = new Reflections(
                new ConfigurationBuilder()
                    .forPackage(basePackage)
                    .setScanners(Scanners.SubTypes)
            );
            
            Set<Class<? extends Repository>> repoClasses = reflections.getSubTypesOf(Repository.class);
            
            for (Class<?> repoClass : repoClasses) {
                if (isConcreteRepository(repoClass)) {
                    Class<?> entityClass = extractEntityClass(repoClass);
                    if (entityClass != null) {
                        repositories.put(repoClass, entityClass);
                        LOGGER.debug("Found repository: {} for entity: {}", 
                            repoClass.getSimpleName(), entityClass.getSimpleName());
                    }
                }
            }
        }
        
        LOGGER.info("Found {} repositories", repositories.size());
        return repositories;
    }
    
    private static boolean isConcreteRepository(Class<?> clazz) {
        return !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers());
    }
    
    private static Class<?> extractEntityClass(Class<?> repositoryClass) {
        Type genericSuperclass = repositoryClass.getGenericSuperclass();
        
        if (genericSuperclass instanceof ParameterizedType parameterizedType) {
            Type[] typeArguments = parameterizedType.getActualTypeArguments();
            if (typeArguments.length > 0 && typeArguments[0] instanceof Class<?>) {
                return (Class<?>) typeArguments[0];
            }
        }
        
        for (Type genericInterface : repositoryClass.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType parameterizedType) {
                Type rawType = parameterizedType.getRawType();
                if (rawType instanceof Class<?> && Repository.class.isAssignableFrom((Class<?>) rawType)) {
                    Type[] typeArguments = parameterizedType.getActualTypeArguments();
                    if (typeArguments.length > 0 && typeArguments[0] instanceof Class<?>) {
                        return (Class<?>) typeArguments[0];
                    }
                }
            }
        }
        
        return null;
    }
}
