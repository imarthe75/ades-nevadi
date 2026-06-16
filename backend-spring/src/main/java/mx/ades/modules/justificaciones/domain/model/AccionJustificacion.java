package mx.ades.modules.justificaciones.domain.model;

public enum AccionJustificacion {
    APROBAR, RECHAZAR;

    public boolean requiereMotivo() { return this == RECHAZAR; }

    public EstadoJustificacion estadoResultante() {
        return this == APROBAR ? EstadoJustificacion.APROBADA : EstadoJustificacion.RECHAZADA;
    }

    public static AccionJustificacion of(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("accion es requerida");
        try {
            return AccionJustificacion.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("accion debe ser APROBAR o RECHAZAR");
        }
    }
}
