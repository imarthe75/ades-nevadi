package mx.ades.modules.evaluaciones.domain.model;

public enum TipoItem {
    TAREA, EXAMEN, ACTIVIDAD, PROYECTO, PARTICIPACION;

    public boolean requiereEntregaArchivo() {
        return this == TAREA || this == PROYECTO;
    }

    public boolean esPuntualEnAula() {
        return this == EXAMEN || this == PARTICIPACION;
    }
}
