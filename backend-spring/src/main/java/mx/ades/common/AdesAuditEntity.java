package mx.ades.common;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Base para tablas con auditoría completa pero SIN columna ref.
 * AdesBaseEntity extiende esta clase y agrega ref.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AdesAuditEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id = UuidCreator.getTimeOrderedWithRandom();

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_modificacion", insertable = false, updatable = false)
    private OffsetDateTime fechaModificacion;

    @Column(name = "usuario_creacion", insertable = false, updatable = false)
    private String usuarioCreacion;

    @Column(name = "usuario_modificacion", insertable = false, updatable = false)
    private String usuarioModificacion;
}
