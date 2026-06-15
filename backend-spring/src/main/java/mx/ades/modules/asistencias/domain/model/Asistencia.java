package mx.ades.modules.asistencias.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.Objects;
import java.util.UUID;

/**
 * Modelo de dominio puro — sin dependencias de Spring ni JPA.
 * La entidad JPA vive en infrastructure/outbound/persistence/AsistenciaEntity.java.
 *
 * Regla SEP/UAEMEX: un alumno acredita si asistió >= 80 % de las clases impartidas.
 * TARDANZA y JUSTIFICADO cuentan como asistencia efectiva para el cálculo.
 */
public record Asistencia(
        UUID id,
        UUID claseId,
        UUID estudianteId,
        EstatusAsistencia estatus,
        String observacion
) {

    public Asistencia {
        Objects.requireNonNull(claseId,      "claseId requerido");
        Objects.requireNonNull(estudianteId, "estudianteId requerido");
        Objects.requireNonNull(estatus,      "estatus requerido");
    }

    public static Asistencia registrar(UUID claseId, UUID estudianteId,
                                       EstatusAsistencia estatus, String observacion) {
        EstatusAsistencia efectivo = (estatus != null) ? estatus : EstatusAsistencia.AUSENTE;
        return new Asistencia(
                UuidCreator.getTimeOrderedEpoch(),
                claseId, estudianteId, efectivo, observacion
        );
    }

    /**
     * Regla SEP/UAEMEX: acredita si asistió a >= 80 % de clases impartidas.
     * Con 0 clases impartidas se considera acreditado (sin datos suficientes).
     */
    public static boolean acreditaAsistencia(long totalClasesImpartidas, long clasesAsistidas) {
        if (totalClasesImpartidas == 0) return true;
        return (clasesAsistidas * 100.0 / totalClasesImpartidas) >= 80.0;
    }
}
