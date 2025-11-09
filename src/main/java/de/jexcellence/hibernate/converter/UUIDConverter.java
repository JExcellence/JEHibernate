package de.jexcellence.hibernate.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Persists {@link UUID} values as compact {@code byte[16]} columns.
 */
@Converter(autoApply = true)
public final class UUIDConverter implements AttributeConverter<UUID, byte[]> {

    @Override
    public byte @Nullable [] convertToDatabaseColumn(@Nullable final UUID uuid) {
        if (uuid == null) {
            return null;
        }
        final ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
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
        if (bytes.length != 16) {
            throw new IllegalArgumentException("UUID column must contain exactly 16 bytes");
        }
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        final long mostSignificant = buffer.getLong();
        final long leastSignificant = buffer.getLong();
        return new UUID(mostSignificant, leastSignificant);
    }
}
