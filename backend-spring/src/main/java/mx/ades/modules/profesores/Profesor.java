package mx.ades.modules.profesores;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.util.UUID;

@Entity
@Table(name = "ades_profesores")
@Getter
@Setter
public class Profesor extends AdesBaseEntity {

    @Column(name = "numero_empleado")
    private String numeroEmpleado;

    @Column(name = "persona_id", nullable = false)
    private UUID personaId;

    @Column(name = "plantel_id", nullable = false)
    private UUID plantelId;

    @Column(name = "estatus_id")
    private UUID estatusId;

    @Column(name = "tipo_contrato")
    private String tipoContrato = "BASE";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
