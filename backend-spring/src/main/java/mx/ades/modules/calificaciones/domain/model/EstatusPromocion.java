package mx.ades.modules.calificaciones.domain.model;

/**
 * Tipo de dominio que representa el resultado de promoción de un alumno al final del período.
 *
 * <p>Valores posibles y su significado:
 * <ul>
 *   <li>{@code APROBADO} — el alumno acreditó el período (calificación {@code >= 6} SEP
 *       o {@code >= 6} UAEMEX según escala).</li>
 *   <li>{@code REPROBADO} — el alumno no acreditó el período.</li>
 * </ul>
 * </p>
 *
 * @author ADES
 * @since 2026
 */
public enum EstatusPromocion {
    APROBADO,
    REPROBADO;

    public static EstatusPromocion from(boolean esAcreditado) {
        return esAcreditado ? APROBADO : REPROBADO;
    }

    public boolean esReprobado() {
        return this == REPROBADO;
    }
}
