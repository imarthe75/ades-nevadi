package mx.ades.modules.asistencias.domain.model;

/**
 * Tipo de dominio que representa el estatus de asistencia de un alumno a una clase.
 *
 * <p>Valores posibles y su significado:
 * <ul>
 *   <li>{@code PRESENTE} — el alumno asistió a la clase.</li>
 *   <li>{@code AUSENTE} — el alumno no asistió.</li>
 *   <li>{@code TARDE} — el alumno llegó tarde (NUNCA usar TARDANZA — el {@code CHECK} real
 *       {@code chk_estatus_asistencia} de {@code ades_asistencias} solo acepta TARDE; la
 *       migración 029 ya corrigió una vez este mismo mismatch en
 *       {@code calcular_calificacion_periodo}, TARDANZA → TARDE).</li>
 *   <li>{@code JUSTIFICADO} — la ausencia fue justificada.</li>
 * </ul>
 * </p>
 *
 * @author ADES
 * @since 2026
 */
public enum EstatusAsistencia {
    PRESENTE,
    AUSENTE,
    TARDE,
    JUSTIFICADO;

    public boolean esAusencia() {
        return this == AUSENTE;
    }

    public boolean cuentaComoAsistencia() {
        return this == PRESENTE || this == TARDE || this == JUSTIFICADO;
    }
}
