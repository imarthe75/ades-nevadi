package mx.ades.modules.asistencias.domain.model;

/**
 * Tipo de dominio que representa el estatus de asistencia de un alumno a una clase.
 *
 * <p>Valores posibles y su significado:
 * <ul>
 *   <li>{@code PRESENTE} — el alumno asistió a la clase.</li>
 *   <li>{@code AUSENTE} — el alumno no asistió.</li>
 *   <li>{@code TARDANZA} — el alumno llegó tarde (NUNCA usar TARDE).</li>
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
    TARDANZA,
    JUSTIFICADO;

    public boolean esAusencia() {
        return this == AUSENTE;
    }

    public boolean cuentaComoAsistencia() {
        return this == PRESENTE || this == TARDANZA || this == JUSTIFICADO;
    }
}
