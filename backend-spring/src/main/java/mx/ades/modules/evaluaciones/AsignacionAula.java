package mx.ades.modules.evaluaciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "ades_asignaciones_aula")
@Getter
@Setter
public class AsignacionAula extends AdesBaseEntity {

    @Column(name = "clase_id")
    private UUID claseId;

    @Column(name = "aula_id", nullable = false)
    private UUID aulaId;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
