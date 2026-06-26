package mx.ades.modules.horarios;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "ades_horarios")
@Getter
@Setter
public class Horario extends AdesBaseEntity {

    @Column(name = "grupo_id", nullable = false)
    private UUID grupoId;

    @Column(name = "materia_id", nullable = false)
    private UUID materiaId;

    @Column(name = "profesor_id", nullable = false)
    private UUID profesorId;

    @Column(name = "aula_id")
    private UUID aulaId;

    @Column(name = "ciclo_escolar_id", nullable = false)
    private UUID cicloEscolarId;

    @Column(name = "corrida_id")
    private UUID corridaId;

    @Column(name = "dia_semana", nullable = false)
    private Short diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "origen", nullable = false)
    private String origen = "ASC";

    @Column(name = "fijado", nullable = false)
    private Boolean fijado = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
