# JEHibernate

JEHibernate is a utility library designed to simplify the management of `EntityManagerFactory` instances for JPA-based applications. It provides a streamlined approach to configuring and interacting with Hibernate, making it easier to perform CRUD operations on entities.

## Features

- **Entity Management**: Provides an abstract repository for CRUD operations.
- **Asynchronous Operations**: Supports asynchronous CRUD operations using `CompletableFuture`.
- **Entity Lifecycle Management**: Includes lifecycle callbacks for entity events.
- **Configuration Management**: Loads and validates Hibernate configurations from properties files.
- **Persistence Unit Management**: Dynamically discovers and manages entity classes.

## Getting Started

### Prerequisites

- Java Development Kit (JDK) 8 or higher
- Maven or Gradle for dependency management
- A database supported by Hibernate

### Installation

Add the following dependency to your `pom.xml` if you are using Maven:

```xml
# this will be added soon, for now fork the project and build it yourself through cleanMavenDeployLocally
<dependency>
    <groupId>de.jexcellence.hibernate</groupId>
    <artifactId>jehibernate</artifactId>
    <version>1.0.0</version>
</dependency>

# or if you are using gradle:
implementation("de.jexcellence.hibernate:jehibernate:1.0.0")
```

### Example hibernate.properties
```yml
# JDBC URL for H2 in-memory database
jakarta.persistence.jdbc.url=jdbc:h2:mem:testdb

# Database credentials
jakarta.persistence.jdbc.user=sa
jakarta.persistence.jdbc.password=

# Database configuration
database.type=h2
database.name=testdb
database.port=3306
database.host=localhost

# Hibernate settings
hibernate.show_sql=true
hibernate.format_sql=true
hibernate.highlight_sql=true

# Schema generation
jakarta.persistence.schema-generation.database.action=update
```

# Using JEHibernate

This guide demonstrates how to declare an entity, create a repository, and initialize `JEHibernate` for managing your JPA-based application.

## Declaring an Entity
To declare an entity, extend the `AbstractEntity` class and annotate it with `@Entity`. This provides basic fields like `id`, `createdAt`, and `updatedAt`.

```java
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "example_entity")
public class ExampleEntity extends AbstractEntity {
    private String name;

    // Getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
```
### Creating a Repository
Create a repository by extending AbstractCRUDRepository. This provides CRUD operations for the entity.

```java
import de.jexcellence.hibernate.repository.AbstractCRUDRepository;

public class ExampleEntityRepository extends AbstractCRUDRepository<ExampleEntity, Long> {
    public ExampleEntityRepository(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory, ExampleEntity.class);
    }
}
```

### Initializing JEHibernate
Initialize JEHibernate by providing the path to your configuration file. This sets up the EntityManagerFactory.

```java
import de.jexcellence.hibernate.JEHibernate;

public class Application {
    public static void main(String[] args) {
        String configFilePath = "path/to/hibernate.properties";
        JEHibernate jeHibernate = new JEHibernate(configFilePath);

        // Use the EntityManagerFactory
        EntityManagerFactory entityManagerFactory = jeHibernate.getEntityManagerFactory();

        // Initialize the repository
        ExampleEntityRepository repository = new ExampleEntityRepository(entityManagerFactory);

        // Perform CRUD operations
        ExampleEntity entity = new ExampleEntity();
        entity.setName("Sample Name");
        repository.create(entity);

        // Close JEHibernate when done
        jeHibernate.close();
    }
}
```
