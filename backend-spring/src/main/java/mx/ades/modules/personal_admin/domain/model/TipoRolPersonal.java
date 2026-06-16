package mx.ades.modules.personal_admin.domain.model;

import java.util.Arrays;

public enum TipoRolPersonal {
    DIRECTOR, SUBDIRECTOR, COORDINADOR, SECRETARIA, PREFECTO,
    ADMINISTRATIVO, TESORERO, ORIENTADOR, PSICOPEDAGOGO, OTRO;

    public boolean esDireccion() {
        return this == DIRECTOR || this == SUBDIRECTOR;
    }

    public static TipoRolPersonal of(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("tipo_rol es requerido");
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(OTRO);
    }
}
