package mx.ades.modules.evaluaciones.domain.model;

/**
 * Estado de una entrega de actividad en el módulo evaluaciones.
 * <p>Valores: PENDIENTE (sin entregar), ENTREGADA (subida por el alumno),
 * CALIFICADA (revisada por el docente), EXCUSA (dispensa aceptada).
 * PENDIENTE y ENTREGADA son calificables; CALIFICADA representa estado final normal.</p>
 *
 * @author ADES
 * @since 2026
 */
public enum EstatusEntrega {
    PENDIENTE, ENTREGADA, CALIFICADA, EXCUSA;

    public boolean puedeCalificarse() {
        return this == ENTREGADA || this == PENDIENTE;
    }

    public boolean estaCalificada() {
        return this == CALIFICADA;
    }
}
