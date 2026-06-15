package mx.ades.modules.disponibilidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "ades_disponibilidad_docente")
@Getter
@Setter
public class DisponibilidadDocente extends AdesBaseEntity {

    @Column(name = "profesor_id", nullable = false)
    private UUID profesorId;

    @Column(name = "dia_semana", nullable = false)
    private Short diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "disponible")
    private Boolean disponible = true;

    @Column(name = "motivo_no_disponible")
    private String motivoNoDisponible;

    @Column(name = "ciclo_escolar_id")
    private UUID cicloEscolarId;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
