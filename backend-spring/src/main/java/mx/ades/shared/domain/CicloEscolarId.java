package mx.ades.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record CicloEscolarId(UUID value) {

    public CicloEscolarId {
        Objects.requireNonNull(value, "cicloEscolarId no puede ser nulo");
    }

    public static CicloEscolarId of(UUID id) {
        return new CicloEscolarId(id);
    }

    public static CicloEscolarId of(String id) {
        return new CicloEscolarId(UUID.fromString(id));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
