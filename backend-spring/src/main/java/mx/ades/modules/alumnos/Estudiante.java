package mx.ades.modules.alumnos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ades_estudiantes")
@Getter
@Setter
public class Estudiante extends AdesBaseEntity {

    @Column(name = "matricula")
    private String matricula;

    @Column(name = "persona_id", nullable = false)
    private UUID personaId;

    @Column(name = "plantel_id", nullable = false)
    private UUID plantelId;

    @Column(name = "estatus_id")
    private UUID estatusId;

    @Column(name = "fecha_ingreso")
    private LocalDate fechaIngreso;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
