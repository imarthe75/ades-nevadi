package mx.ades.modules.evaluaciones.domain.model;

/**
 * Estado de una entrega de actividad en el módulo evaluaciones.
 * <p>Valores: PENDIENTE (sin entregar), ENTREGADA (subida por el alumno),
 * CALIFICADA (revisada por el docente), EXCUSA (dispensa aceptada).
 * Solo ENTREGADA es calificable — un alumno no puede recibir calificación sobre algo
 * que no ha entregado (PENDIENTE); CALIFICADA y EXCUSA son estados terminales.
 * Regla unificada con {@link mx.ades.modules.entregas.domain.model.EstatusEntrega}
 * (auditoría de consistencia 2026-07-15 — hallazgo Antigravity D4: ambas copias del
 * enum tenían la misma forma pero reglas booleanas contradictorias sobre qué estados
 * son calificables; ninguna tenía consumidores todavía, así que se armonizaron sin
 * riesgo de regresión).</p>
 *
 * @author ADES
 * @since 2026
 */
public enum EstatusEntrega {
    PENDIENTE, ENTREGADA, CALIFICADA, EXCUSA;

    public boolean puedeCalificarse() {
        return this == ENTREGADA;
    }

    public boolean estaCalificada() {
        return this == CALIFICADA;
    }
}
