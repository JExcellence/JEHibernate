package de.jexcellence.hibernate.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * UUIDConverter provides efficient conversion between UUID objects and byte arrays for optimal
 * database storage and retrieval of universally unique identifiers.
 *
 * <p>This JPA AttributeConverter automatically handles the conversion of UUID values to compact
 * byte array representations for database persistence, offering significant storage and performance
 * benefits over string-based UUID storage. The converter provides the following key advantages:</p>
 * <ul>
 *   <li><strong>Storage efficiency:</strong> 16-byte binary representation vs 36-character string</li>
 *   <li><strong>Performance optimization:</strong> Faster database operations and indexing</li>
 *   <li><strong>Automatic application:</strong> Transparent conversion for all UUID entity attributes</li>
 *   <li><strong>Database portability:</strong> Consistent binary format across different database vendors</li>
 *   <li><strong>Index efficiency:</strong> Better database index performance with fixed-length binary data</li>
 * </ul>
 *
 * <p>The converter uses Java's ByteBuffer for efficient binary data manipulation, ensuring optimal
 * performance during conversion operations. The conversion process preserves the complete UUID
 * structure by storing both the most significant and least significant 64-bit components.</p>
 *
 * <p>The {@code @Converter(autoApply = true)} annotation ensures that this converter is automatically
 * applied to all UUID entity attributes throughout the application, eliminating the need for
 * explicit converter specification on individual entity fields.</p>
 *
 * <p>Storage comparison:</p>
 * <pre>
 * String format: "550e8400-e29b-41d4-a716-446655440000" (36 characters)
 * Binary format: [85, 14, 132, 0, 226, 155, 65, 212, ...] (16 bytes)
 * Space savings: ~55% reduction in storage requirements
 * </pre>
 *
 * <p>Usage in entities:</p>
 * <pre>
 * &#64;Entity
 * public class User {
 *     &#64;Id
 *     private UUID id;  // Automatically converted to byte[] in database
 *
 *     private UUID externalId;  // Also automatically converted
 * }
 * </pre>
 *
 * <p>This converter is thread-safe and can be used concurrently across multiple threads without
 * additional synchronization requirements.</p>
 *
 * @author JExcellence
 * @version 1.0
 * @since 1.0
 * @see AttributeConverter
 * @see UUID
 * @see ByteBuffer
 * @see Converter
 */
@Converter(autoApply = true)
public class UUIDConverter implements AttributeConverter<UUID, byte[]> {
    
    /**
     * Default constructor for UUIDConverter.
     */
    public UUIDConverter() {}
    
    /**
     * Converts a UUID object to its byte array representation for database storage.
     *
     * <p>This method transforms a UUID into a compact 16-byte binary representation that
     * is optimal for database storage. The conversion process uses ByteBuffer for efficient
     * binary data manipulation and preserves the complete UUID structure.</p>
     *
     * <p>The conversion algorithm:</p>
     * <ol>
     *   <li>Creates a 16-byte ByteBuffer to hold the UUID data</li>
     *   <li>Writes the most significant 64 bits (8 bytes) to the buffer</li>
     *   <li>Writes the least significant 64 bits (8 bytes) to the buffer</li>
     *   <li>Returns the complete 16-byte array representation</li>
     * </ol>
     *
     * <p>The resulting byte array maintains the UUID's uniqueness and can be efficiently
     * stored, indexed, and queried in the database. This binary format provides significant
     * storage savings compared to string representations.</p>
     *
     * <p>Performance characteristics:</p>
     * <ul>
     *   <li>Fixed 16-byte output size for all UUIDs</li>
     *   <li>Efficient ByteBuffer-based conversion</li>
     *   <li>Optimal for database indexing and sorting</li>
     *   <li>Preserves UUID ordering properties</li>
     * </ul>
     *
     * @param uuid the UUID to convert to byte array representation
     * @return a 16-byte array containing the binary representation of the UUID
     * @throws IllegalArgumentException if uuid is null
     */
    @Override
    public byte[] convertToDatabaseColumn(final UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("UUID cannot be null for database conversion");
        }
        
        final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[16]);
        byteBuffer.putLong(uuid.getMostSignificantBits());
        byteBuffer.putLong(uuid.getLeastSignificantBits());
        return byteBuffer.array();
    }
    
    /**
     * Converts a byte array from the database back to a UUID object.
     *
     * <p>This method reconstructs a UUID object from its 16-byte binary database representation.
     * The conversion process reverses the storage transformation, restoring the original UUID
     * with complete fidelity.</p>
     *
     * <p>The reconstruction algorithm:</p>
     * <ol>
     *   <li>Wraps the 16-byte array in a ByteBuffer for efficient reading</li>
     *   <li>Reads the first 8 bytes as the most significant bits</li>
     *   <li>Reads the next 8 bytes as the least significant bits</li>
     *   <li>Constructs and returns a new UUID with the extracted bit values</li>
     * </ol>
     *
     * <p>The reconstructed UUID is identical to the original UUID that was stored,
     * maintaining all uniqueness and ordering properties. This ensures complete
     * data integrity throughout the persistence lifecycle.</p>
     *
     * <p>Performance characteristics:</p>
     * <ul>
     *   <li>Efficient ByteBuffer-based reconstruction</li>
     *   <li>Fixed-time conversion regardless of UUID value</li>
     *   <li>Minimal memory allocation overhead</li>
     *   <li>Preserves all UUID properties and behavior</li>
     * </ul>
     *
     * @param bytes the 16-byte array containing the binary UUID representation from the database
     * @return the reconstructed UUID object with identical value to the original
     * @throws IllegalArgumentException if bytes is null or not exactly 16 bytes in length
     * @throws java.nio.BufferUnderflowException if the byte array is too short to contain a complete UUID
     */
    @Override
    public UUID convertToEntityAttribute(final byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null for UUID conversion");
        }
        if (bytes.length != 16) {
            throw new IllegalArgumentException("Byte array must be exactly 16 bytes for UUID conversion, got: " + bytes.length);
        }
        
        final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        final long mostSignificantBits = byteBuffer.getLong();
        final long leastSignificantBits = byteBuffer.getLong();
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}