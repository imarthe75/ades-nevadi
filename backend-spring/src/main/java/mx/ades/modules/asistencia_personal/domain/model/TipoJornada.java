package mx.ades.modules.asistencia_personal.domain.model;

import java.util.Arrays;

public enum TipoJornada {
    COMPLETA, MEDIA, NINGUNA, INCAPACIDAD, VACACIONES, PERMISO;

    public boolean esAsistencia() {
        return this == COMPLETA || this == MEDIA;
    }

    public boolean esFalta() {
        return this == NINGUNA;
    }

    public boolean esAusenciaJustificada() {
        return this == INCAPACIDAD || this == VACACIONES || this == PERMISO;
    }

    public static TipoJornada of(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("tipo_jornada es requerido");
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("tipo_jornada inválido: " + value));
    }

    public static TipoJornada ofDefault(String value) {
        if (value == null || value.isBlank()) return COMPLETA;
        try { return of(value); } catch (IllegalArgumentException e) { return COMPLETA; }
    }
}
