package de.jexcellence.jehibernate.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * JPA attribute converter for converting between {@link UUID} and byte arrays.
 * <p>
 * This converter stores UUIDs as 16-byte binary data in the database for efficient
 * storage and indexing. The {@code autoApply = true} setting ensures this converter
 * is automatically applied to all UUID fields without explicit annotation.
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @Entity
 * public class User {
 *     @Id
 *     // Automatically converted to 16-byte binary using UuidConverter
 *     private UUID id;
 *     
 *     private UUID externalId;
 * }
 * }</pre>
 * 
 * <h2>Storage Format:</h2>
 * <ul>
 *   <li>Database: 16-byte binary (BINARY(16) or equivalent)</li>
 *   <li>Java: UUID object</li>
 *   <li>Efficient for indexing and storage compared to string representation</li>
 * </ul>
 * 
 * @since 1.0
 * @see AttributeConverter
 */
@Converter(autoApply = true)
public final class UuidConverter implements AttributeConverter<UUID, byte[]> {
    
    private static final int UUID_BYTE_LENGTH = 16;
    
    /**
     * Converts a UUID to a byte array for database storage.
     * 
     * @param uuid the UUID to convert
     * @return a 16-byte array representing the UUID, or null if the input is null
     */
    @Override
    public byte[] convertToDatabaseColumn(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(UUID_BYTE_LENGTH);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }
    
    /**
     * Converts a byte array from the database to a UUID.
     * 
     * @param bytes the byte array to convert (must be 16 bytes)
     * @return the corresponding UUID, or null if the input is null or invalid
     */
    @Override
    public UUID convertToEntityAttribute(byte[] bytes) {
        if (bytes == null || bytes.length != UUID_BYTE_LENGTH) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long mostSignificant = buffer.getLong();
        long leastSignificant = buffer.getLong();
        return new UUID(mostSignificant, leastSignificant);
    }
}
