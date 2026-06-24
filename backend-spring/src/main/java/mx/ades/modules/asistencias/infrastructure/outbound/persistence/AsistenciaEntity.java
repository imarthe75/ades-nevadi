package mx.ades.modules.asistencias.infrastructure.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.util.UUID;

/**
 * Entidad JPA que mapea a la tabla {@code ades_asistencias}.
 *
 * @author ADES
 * @since 2026
 */
@Entity
@Table(name = "ades_asistencias")
@Getter
@Setter
public class AsistenciaEntity extends AdesBaseEntity {

    @Column(name = "clase_id", nullable = false)
    private UUID claseId;

    @Column(name = "estudiante_id", nullable = false)
    private UUID estudianteId;

    @Column(name = "estatus_asistencia", nullable = false)
    private String estatusAsistencia = "PRESENTE";

    @Column(name = "observacion")
    private String observacion;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
