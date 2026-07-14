package mx.ades.modules.evaluaciones.domain.model;

/**
 * Tipo de ítem evaluable en el módulo evaluaciones.
 * <p>Valores: TAREA, EXAMEN, PROYECTO, ASISTENCIA, COMPORTAMIENTO, PARTICIPACION,
 * LABORATORIO, OTRO — deben coincidir EXACTAMENTE (en minúsculas al persistir) con el
 * CHECK {@code chk_tareas_tipo_item} de {@code ades_tareas}, que a su vez hereda los
 * mismos valores que {@code ades_items_ponderacion.tipo_item} (ver migración 007).
 * TAREA y PROYECTO requieren entrega de archivo; EXAMEN y PARTICIPACION son presenciales.</p>
 *
 * @author ADES
 * @since 2026
 */
public enum TipoItem {
    TAREA, EXAMEN, PROYECTO, ASISTENCIA, COMPORTAMIENTO, PARTICIPACION, LABORATORIO, OTRO;

    public boolean requiereEntregaArchivo() {
        return this == TAREA || this == PROYECTO;
    }

    public boolean esPuntualEnAula() {
        return this == EXAMEN || this == PARTICIPACION;
    }
}
