/**
 * Repository dependency injection framework for JEHibernate.
 * <p>
 * This package provides a lightweight dependency injection system specifically designed for
 * repository management. It eliminates the need for manual repository instantiation and
 * provides automatic injection of repository instances into service classes.
 * </p>
 * 
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link de.jexcellence.hibernate.repository.RepositoryManager} - Central registry and factory for repositories</li>
 *   <li>{@link de.jexcellence.hibernate.repository.InjectRepository} - Annotation for marking injection points</li>
 *   <li>{@link de.jexcellence.hibernate.repository.RepositoryMetadata} - Metadata storage for registered repositories</li>
 * </ul>
 * 
 * <h2>Quick Start Guide</h2>
 * 
 * <h3>1. Define Your Repository</h3>
 * <pre>
 * public class UserRepository extends GenericCachedRepository&lt;User, UUID, UUID&gt; {
 *     public UserRepository(
 *         ExecutorService executor,
 *         EntityManagerFactory emf,
 *         Class&lt;User&gt; entityClass,
 *         Function&lt;User, UUID&gt; keyExtractor
 *     ) {
 *         super(executor, emf, entityClass, keyExtractor);
 *     }
 *     
 *     // Add custom query methods here
 *     public CompletableFuture&lt;User&gt; findByUsername(String username) {
 *         return this.findByField("username", username);
 *     }
 * }
 * </pre>
 * 
 * <h3>2. Initialize in Your Plugin Main Class</h3>
 * <pre>
 * public class MyPlugin extends JavaPlugin {
 *     {@literal @}Override
 *     public void onEnable() {
 *         // Create shared dependencies
 *         ExecutorService executor = Executors.newFixedThreadPool(4);
 *         EntityManagerFactory emf = createEntityManagerFactory();
 *         
 *         // Initialize the repository manager
 *         RepositoryManager.initialize(executor, emf);
 *         
 *         // Register repositories with cache key extractors
 *         RepositoryManager.getInstance().register(
 *             UserRepository.class,
 *             User.class,
 *             User::getId  // Key extractor for caching
 *         );
 *         
 *         RepositoryManager.getInstance().register(
 *             PlayerRepository.class,
 *             Player.class,
 *             Player::getUuid
 *         );
 *         
 *         // Create and inject into service classes
 *         UserService userService = new UserService();
 *         RepositoryManager.getInstance().injectInto(userService);
 *         
 *         // Register service with your plugin
 *         this.userService = userService;
 *     }
 *     
 *     private EntityManagerFactory createEntityManagerFactory() {
 *         // Your EMF creation logic
 *         return Persistence.createEntityManagerFactory("myPersistenceUnit");
 *     }
 * }
 * </pre>
 * 
 * <h3>3. Use in Service Classes</h3>
 * <pre>
 * public class UserService {
 *     {@literal @}InjectRepository
 *     private UserRepository userRepository;
 *     
 *     {@literal @}InjectRepository
 *     private PlayerRepository playerRepository;
 *     
 *     public User findUser(UUID id) {
 *         return userRepository.findById(id).join();
 *     }
 *     
 *     public void createUser(String username, String email) {
 *         User user = new User();
 *         user.setUsername(username);
 *         user.setEmail(email);
 *         userRepository.save(user).join();
 *     }
 *     
 *     public List&lt;User&gt; getAllUsers() {
 *         return userRepository.findAll().join();
 *     }
 * }
 * </pre>
 * 
 * <h2>Registration Patterns</h2>
 * 
 * <h3>Non-Cached Repository (AbstractCRUDRepository)</h3>
 * <pre>
 * public class SimpleRepository extends AbstractCRUDRepository&lt;Entity, Long&gt; {
 *     public SimpleRepository(
 *         ExecutorService executor,
 *         EntityManagerFactory emf,
 *         Class&lt;Entity&gt; entityClass
 *     ) {
 *         super(executor, emf, entityClass);
 *     }
 * }
 * 
 * // Registration (no key extractor needed)
 * RepositoryManager.getInstance().register(
 *     SimpleRepository.class,
 *     Entity.class
 * );
 * </pre>
 * 
 * <h3>Cached Repository with Custom Key Extractor</h3>
 * <pre>
 * public class PlayerRepository extends GenericCachedRepository&lt;Player, Long, String&gt; {
 *     public PlayerRepository(
 *         ExecutorService executor,
 *         EntityManagerFactory emf,
 *         Class&lt;Player&gt; entityClass,
 *         Function&lt;Player, String&gt; keyExtractor
 *     ) {
 *         super(executor, emf, entityClass, keyExtractor);
 *     }
 * }
 * 
 * // Registration with username as cache key
 * RepositoryManager.getInstance().register(
 *     PlayerRepository.class,
 *     Player.class,
 *     Player::getUsername  // Cache by username instead of ID
 * );
 * </pre>
 * 
 * <h2>Advanced Usage</h2>
 * 
 * <h3>Multiple Service Classes</h3>
 * <pre>
 * public class UserService {
 *     {@literal @}InjectRepository
 *     private UserRepository userRepository;
 *     
 *     // Service methods...
 * }
 * 
 * public class AdminService {
 *     {@literal @}InjectRepository
 *     private UserRepository userRepository;  // Same instance as UserService
 *     
 *     {@literal @}InjectRepository
 *     private AuditRepository auditRepository;
 *     
 *     // Admin methods...
 * }
 * 
 * // Both services share the same UserRepository instance (singleton)
 * UserService userService = new UserService();
 * AdminService adminService = new AdminService();
 * 
 * RepositoryManager manager = RepositoryManager.getInstance();
 * manager.injectInto(userService);
 * manager.injectInto(adminService);
 * </pre>
 * 
 * <h3>Lazy Initialization</h3>
 * <p>
 * Repositories are created lazily on first injection. This means you can register
 * repositories early in your plugin lifecycle, but they won't be instantiated until
 * they're actually needed.
 * </p>
 * <pre>
 * // Registration happens during plugin initialization
 * RepositoryManager.getInstance().register(UserRepository.class, User.class, User::getId);
 * 
 * // Repository instance is created here (on first injection)
 * UserService service = new UserService();
 * RepositoryManager.getInstance().injectInto(service);
 * 
 * // Subsequent injections reuse the same instance
 * AnotherService another = new AnotherService();
 * RepositoryManager.getInstance().injectInto(another);  // Reuses existing UserRepository
 * </pre>
 * 
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Initialize RepositoryManager early in your plugin's onEnable() method</li>
 *   <li>Register all repositories before creating service instances</li>
 *   <li>Use meaningful cache key extractors for GenericCachedRepository</li>
 *   <li>Keep repository constructors consistent with AbstractCRUDRepository patterns</li>
 *   <li>Inject repositories into service classes, not directly into commands or listeners</li>
 *   <li>Use private fields with @InjectRepository for better encapsulation</li>
 * </ul>
 * 
 * <h2>Thread Safety</h2>
 * <p>
 * The RepositoryManager is fully thread-safe. Repository instances are created using
 * double-checked locking to ensure only one instance exists per repository type, even
 * under concurrent access. All internal maps use ConcurrentHashMap for thread-safe operations.
 * </p>
 * 
 * <h2>Error Handling</h2>
 * 
 * <h3>Common Exceptions</h3>
 * <ul>
 *   <li><b>IllegalStateException</b> - Thrown when accessing RepositoryManager before initialization</li>
 *   <li><b>IllegalStateException</b> - Thrown when injecting a repository type that hasn't been registered</li>
 *   <li><b>IllegalArgumentException</b> - Thrown when registering a class that doesn't extend AbstractCRUDRepository</li>
 *   <li><b>NullPointerException</b> - Thrown when null parameters are passed to registration or injection methods</li>
 * </ul>
 * 
 * <h3>Example Error Handling</h3>
 * <pre>
 * try {
 *     RepositoryManager.getInstance().injectInto(service);
 * } catch (IllegalStateException e) {
 *     // Handle unregistered repository or uninitialized manager
 *     getLogger().severe("Failed to inject repositories: " + e.getMessage());
 * }
 * </pre>
 * 
 * @see de.jexcellence.hibernate.repository.RepositoryManager
 * @see de.jexcellence.hibernate.repository.InjectRepository
 * @see de.jexcellence.hibernate.repository.AbstractCRUDRepository
 * @see de.jexcellence.hibernate.repository.GenericCachedRepository
 */
package de.jexcellence.hibernate.repository;
