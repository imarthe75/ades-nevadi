package mx.ades.modules.asistencia_personal.domain.model;

import java.util.Arrays;

/**
 * Tipo de dominio que representa la jornada laboral registrada para el personal.
 *
 * <p>Valores posibles y su significado:
 * <ul>
 *   <li>{@code COMPLETA} — asistencia de jornada completa.</li>
 *   <li>{@code MEDIA} — asistencia de media jornada.</li>
 *   <li>{@code NINGUNA} — falta sin justificación.</li>
 *   <li>{@code INCAPACIDAD} — ausencia por incapacidad médica.</li>
 *   <li>{@code VACACIONES} — ausencia por período vacacional.</li>
 *   <li>{@code PERMISO} — ausencia por permiso autorizado.</li>
 * </ul>
 * </p>
 *
 * @author ADES
 * @since 2026
 */
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
