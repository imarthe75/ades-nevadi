package mx.ades.modules.planeacion.domain.model;

/**
 * Máquina de estados de un tema dentro del ciclo escolar.
 * La transición PLANEADO → IMPARTIDO se registra en ades_avance_planificacion.
 *
 * PENDIENTE  — no tiene planeacion_clase asignada
 * PLANEADO   — tiene planeacion_clase pero sin avance completado
 * IMPARTIDO  — tiene avance con es_completado = TRUE
 */
public enum EstadoTema {
    PENDIENTE,
    PLANEADO,
    IMPARTIDO;

    public boolean puedeAvanzarA(EstadoTema siguiente) {
        return switch (this) {
            case PENDIENTE -> siguiente == PLANEADO;
            case PLANEADO  -> siguiente == IMPARTIDO;
            case IMPARTIDO -> false;
        };
    }

    public static EstadoTema from(boolean tieneAvanceCompletado, boolean tienePlaneacion) {
        if (tieneAvanceCompletado) return IMPARTIDO;
        if (tienePlaneacion)       return PLANEADO;
        return PENDIENTE;
    }
}
