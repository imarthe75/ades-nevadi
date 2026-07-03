package mx.ades.modules.asistencias;

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
@Table(name = "ades_clases")
@Getter
@Setter
public class Clase extends AdesBaseEntity {

    @Column(name = "horario_id")
    private UUID horarioId;

    @Column(name = "grupo_id", nullable = false)
    private UUID grupoId;

    @Column(name = "materia_id", nullable = false)
    private UUID materiaId;

    @Column(name = "profesor_id", nullable = false)
    private UUID profesorId;

    @Column(name = "fecha_clase", nullable = false)
    private LocalDate fechaClase;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "tema_visto")
    private String temaVisto;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "estatus_clase", nullable = false)
    private String estatusClase = "PROGRAMADA";

    @Column(name = "modalidad", nullable = false)
    private String modalidad = "PRESENCIAL";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
