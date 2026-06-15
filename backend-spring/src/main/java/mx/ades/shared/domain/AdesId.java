package mx.ades.shared.domain;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.Objects;
import java.util.UUID;

public record AdesId(UUID value) {

    public AdesId {
        Objects.requireNonNull(value, "id no puede ser nulo");
    }

    public static AdesId generate() {
        return new AdesId(UuidCreator.getTimeOrderedEpoch());
    }

    public static AdesId of(UUID id) {
        return new AdesId(id);
    }

    public static AdesId of(String id) {
        return new AdesId(UUID.fromString(id));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
