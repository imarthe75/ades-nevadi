package mx.ades.modules.horarios.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "ades_horario_franjas")
@Getter
@Setter
public class HorarioFranja extends AdesBaseEntity {

    @Column(name = "plantel_id")
    private UUID plantelId;

    @Column(name = "ciclo_escolar_id", nullable = false)
    private UUID cicloEscolarId;

    @Column(name = "nivel_educativo_id")
    private UUID nivelEducativoId;

    @Column(name = "dia_semana", nullable = false)
    private Short diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "turno")
    private String turno = "MATUTINO";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
