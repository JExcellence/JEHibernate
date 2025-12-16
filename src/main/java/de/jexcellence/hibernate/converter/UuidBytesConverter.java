package de.jexcellence.hibernate.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Converts {@link UUID} values to compact {@code byte[16]} for database storage.
 *
 * @author JExcellence
 * @version 1.0
 * @since 1.0
 */
@Converter(autoApply = true)
public final class UuidBytesConverter implements AttributeConverter<UUID, byte[]> {

    private static final int UUID_BYTE_LENGTH = 16;

    @Override
    public byte @Nullable [] convertToDatabaseColumn(@Nullable final UUID uuid) {
        if (uuid == null) {
            return null;
        }
        var buffer = ByteBuffer.wrap(new byte[UUID_BYTE_LENGTH]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    @Override
    @Nullable
    public UUID convertToEntityAttribute(byte @Nullable [] bytes) {
        if (bytes == null) {
            return null;
        }
        if (bytes.length != UUID_BYTE_LENGTH) {
            throw new IllegalArgumentException("UUID column must contain exactly 16 bytes");
        }
        var buffer = ByteBuffer.wrap(bytes);
        var mostSignificant = buffer.getLong();
        var leastSignificant = buffer.getLong();
        return new UUID(mostSignificant, leastSignificant);
    }
}
