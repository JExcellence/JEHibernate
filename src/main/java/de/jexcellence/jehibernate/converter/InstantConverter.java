package de.jexcellence.jehibernate.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * JPA attribute converter for converting between {@link Instant} and {@link Timestamp}.
 * <p>
 * This converter automatically converts Java 8 {@link Instant} objects to SQL {@link Timestamp}
 * for database storage and vice versa. The {@code autoApply = true} setting ensures this
 * converter is automatically applied to all Instant fields without explicit annotation.
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @Entity
 * public class Event {
 *     @Id
 *     private Long id;
 *     
 *     // Automatically converted using InstantConverter
 *     private Instant createdAt;
 *     
 *     private Instant updatedAt;
 * }
 * }</pre>
 * 
 * @since 1.0
 * @see AttributeConverter
 */
@Converter(autoApply = true)
public final class InstantConverter implements AttributeConverter<Instant, Timestamp> {
    
    /**
     * Converts an Instant to a Timestamp for database storage.
     * 
     * @param instant the Instant to convert
     * @return the corresponding Timestamp, or null if the input is null
     */
    @Override
    public Timestamp convertToDatabaseColumn(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
    
    /**
     * Converts a Timestamp from the database to an Instant.
     * 
     * @param timestamp the Timestamp to convert
     * @return the corresponding Instant, or null if the input is null
     */
    @Override
    public Instant convertToEntityAttribute(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
