package mx.ades.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Base para tablas con auditoría completa Y columna ref (uuidv7 inmutable). */
@MappedSuperclass
@Getter
@Setter
public abstract class AdesBaseEntity extends AdesAuditEntity {

    @Column(name = "ref", columnDefinition = "uuid", insertable = false, updatable = false)
    private UUID ref;
}
