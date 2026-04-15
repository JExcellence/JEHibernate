package de.jexcellence.jehibernate.scanner;

import jakarta.persistence.Entity;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for scanning and discovering JPA entity classes in packages.
 * <p>
 * EntityScanner uses reflection to automatically discover all classes annotated
 * with {@link Entity} in specified packages, enabling zero-configuration entity
 * registration.
 * <p>
 * <b>Features:</b>
 * <ul>
 *   <li>Automatic entity discovery</li>
 *   <li>Filters out abstract classes</li>
 *   <li>Multi-package scanning support</li>
 *   <li>Fast reflection-based scanning</li>
 * </ul>
 * <p>
 * <b>Example Usage:</b>
 * <pre>{@code
 * // Automatic scanning (recommended)
 * var jeHibernate = JEHibernate.builder()
 *     .scanPackages("com.example.entities")
 *     .build();
 * 
 * // Manual scanning
 * Set<Class<?>> entities = EntityScanner.scan("com.example.entities");
 * entities.forEach(entityClass -> {
 *     System.out.println("Found entity: " + entityClass.getSimpleName());
 * });
 * }</pre>
 * <p>
 * <b>How It Works:</b>
 * <ol>
 *   <li>Scans specified packages for classes with @Entity annotation</li>
 *   <li>Filters out abstract classes (MappedSuperclass)</li>
 *   <li>Returns set of concrete entity classes</li>
 * </ol>
 * <p>
 * <b>Performance:</b> Scanning is performed once during initialization and
 * results are cached by JEHibernate.
 * <p>
 * <b>Thread Safety:</b> This class is stateless and thread-safe.
 *
 * @author JEHibernate
 * @version 2.0
 * @since 1.0
 * @see RepositoryScanner
 * @see Entity
 */
public final class EntityScanner {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityScanner.class);
    
    private EntityScanner() {
    }
    
    public static Set<Class<?>> scan(String... basePackages) {
        if (basePackages == null || basePackages.length == 0) {
            LOGGER.warn("No base packages specified for entity scanning");
            return Set.of();
        }
        
        LOGGER.info("Scanning for entities in packages: {}", Arrays.toString(basePackages));
        
        Set<Class<?>> entities = new HashSet<>();
        
        for (String basePackage : basePackages) {
            Reflections reflections = new Reflections(
                new ConfigurationBuilder()
                    .forPackage(basePackage)
                    .setScanners(Scanners.TypesAnnotated)
            );
            
            Set<Class<?>> annotated = reflections.getTypesAnnotatedWith(Entity.class);
            
            for (Class<?> clazz : annotated) {
                if (isValidEntity(clazz)) {
                    entities.add(clazz);
                    LOGGER.debug("Found entity: {}", clazz.getName());
                }
            }
        }
        
        LOGGER.info("Found {} entities", entities.size());
        return entities;
    }
    
    private static boolean isValidEntity(Class<?> clazz) {
        return !clazz.isInterface() 
            && !Modifier.isAbstract(clazz.getModifiers())
            && clazz.isAnnotationPresent(Entity.class);
    }
}
