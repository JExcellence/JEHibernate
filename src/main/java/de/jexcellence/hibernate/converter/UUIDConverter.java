package de.jexcellence.hibernate.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * A converter class that implements the AttributeConverter interface to convert UUIDs to byte arrays and vice versa.
 * This is used for persisting UUIDs in a database as byte arrays.
 */
@Converter(autoApply = true)
public class UUIDConverter implements AttributeConverter<UUID, byte[]> {

    /**
     * Converts the given UUID to a byte array representation for database storage.
     *
     * @param uuid the UUID to convert, must not be null
     * @return the byte array representation of the UUID
     */
    @Override
    public byte[] convertToDatabaseColumn(final UUID uuid) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[16]);
        byteBuffer.putLong(uuid.getMostSignificantBits());
        byteBuffer.putLong(uuid.getLeastSignificantBits());
        return byteBuffer.array();
    }

    /**
     * Converts the given byte array from the database to a UUID.
     *
     * @param bytes the byte array to convert, must not be null
     * @return the UUID representation of the byte array
     */
    @Override
    public UUID convertToEntityAttribute(final byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        long mostSignificantBits = byteBuffer.getLong();
        long leastSignificantBits = byteBuffer.getLong();
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
