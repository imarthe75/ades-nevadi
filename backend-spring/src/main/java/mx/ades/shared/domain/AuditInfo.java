package mx.ades.shared.domain;

import java.time.OffsetDateTime;

/**
 * Metadatos de auditoría sin dependencias JPA — se construye desde AdesBaseEntity
 * cuando el dominio necesita leer quién y cuándo creó/modificó un registro.
 */
public record AuditInfo(
    OffsetDateTime fechaCreacion,
    OffsetDateTime fechaModificacion,
    String usuarioCreacion,
    String usuarioModificacion
) {
    public static AuditInfo of(
            OffsetDateTime fechaCreacion,
            OffsetDateTime fechaModificacion,
            String usuarioCreacion,
            String usuarioModificacion) {
        return new AuditInfo(fechaCreacion, fechaModificacion, usuarioCreacion, usuarioModificacion);
    }
}
