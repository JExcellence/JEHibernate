package de.jexcellence.jehibernate.scanner;

import de.jexcellence.jehibernate.repository.base.Repository;
import de.jexcellence.jehibernate.repository.manager.RepositoryRegistry;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
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
    
    public static Map<Class<?>, Class<?>> scan(String... basePackages) {
        if (basePackages == null || basePackages.length == 0) {
            LOGGER.warn("No base packages specified for repository scanning");
            return Map.of();
        }

        LOGGER.info("Scanning for repositories in packages: {}", Arrays.toString(basePackages));

        final ClassLoader pluginClassLoader = RepositoryScanner.class.getClassLoader();
        URL pluginJarUrl = null;
        try {
            pluginJarUrl = RepositoryScanner.class.getProtectionDomain().getCodeSource().getLocation();
        } catch (final Exception ignored) {
            LOGGER.warn("Could not resolve plugin JAR URL via ProtectionDomain; falling back to classloader enumeration");
        }

        final Map<Class<?>, Class<?>> repositories = new HashMap<>();
        for (final String basePackage : basePackages) {
            repositories.putAll(scanPackage(basePackage, pluginClassLoader, pluginJarUrl));
        }

        LOGGER.info("Found {} repositories", repositories.size());
        return repositories;
    }

    @SuppressWarnings("rawtypes")
    private static Map<Class<?>, Class<?>> scanPackage(String basePackage, ClassLoader pluginClassLoader, URL pluginJarUrl) {
        final ConfigurationBuilder config = new ConfigurationBuilder()
                .addClassLoaders(pluginClassLoader)
                .setScanners(Scanners.SubTypes)
                .filterInputsBy(new FilterBuilder().includePackage(basePackage));

        if (pluginJarUrl != null) {
            config.addUrls(pluginJarUrl);
        } else {
            config.forPackage(basePackage, pluginClassLoader);
        }

        final Reflections reflections = new Reflections(config);
        final Set<Class<? extends Repository>> repoClasses = reflections.getSubTypesOf(Repository.class);

        final Map<Class<?>, Class<?>> result = new HashMap<>();
        for (final Class<?> repoClass : repoClasses) {
            if (isConcreteRepository(repoClass)) {
                final Class<?> entityClass = extractEntityClass(repoClass);
                if (entityClass != null) {
                    result.put(repoClass, entityClass);
                    LOGGER.debug("Found repository: {} for entity: {}",
                        repoClass.getSimpleName(), entityClass.getSimpleName());
                }
            }
        }
        return result;
    }

    private static boolean isConcreteRepository(Class<?> clazz) {
        return !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers());
    }

    private static Class<?> extractEntityClass(Class<?> repositoryClass) {
        Type genericSuperclass = repositoryClass.getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType parameterizedType) {
            Class<?> fromSuper = extractFirstTypeArg(parameterizedType);
            if (fromSuper != null) return fromSuper;
        }
        for (Type genericInterface : repositoryClass.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType parameterizedType) {
                Class<?> fromIface = extractEntityFromInterface(parameterizedType);
                if (fromIface != null) return fromIface;
            }
        }
        return null;
    }

    private static Class<?> extractFirstTypeArg(ParameterizedType parameterizedType) {
        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        if (typeArguments.length > 0 && typeArguments[0] instanceof Class<?> c) {
            return c;
        }
        return null;
    }

    private static Class<?> extractEntityFromInterface(ParameterizedType parameterizedType) {
        Type rawType = parameterizedType.getRawType();
        if (rawType instanceof Class<?> rawClass && Repository.class.isAssignableFrom(rawClass)) {
            return extractFirstTypeArg(parameterizedType);
        }
        return null;
    }
}
