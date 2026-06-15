package mx.ades.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record PlantelId(UUID value) {

    public PlantelId {
        Objects.requireNonNull(value, "plantelId no puede ser nulo");
    }

    public static PlantelId of(UUID id) {
        return new PlantelId(id);
    }

    public static PlantelId of(String id) {
        return new PlantelId(UUID.fromString(id));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
