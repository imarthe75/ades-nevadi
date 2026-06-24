package mx.ades.modules.evaluaciones.domain.model;

/**
 * Tipo de ítem evaluable en el módulo evaluaciones.
 * <p>Valores: TAREA, EXAMEN, ACTIVIDAD, PROYECTO, PARTICIPACION.
 * TAREA y PROYECTO requieren entrega de archivo; EXAMEN y PARTICIPACION son presenciales.</p>
 *
 * @author ADES
 * @since 2026
 */
public enum TipoItem {
    TAREA, EXAMEN, ACTIVIDAD, PROYECTO, PARTICIPACION;

    public boolean requiereEntregaArchivo() {
        return this == TAREA || this == PROYECTO;
    }

    public boolean esPuntualEnAula() {
        return this == EXAMEN || this == PARTICIPACION;
    }
}
