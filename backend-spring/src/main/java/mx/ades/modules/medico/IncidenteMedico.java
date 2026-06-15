package mx.ades.modules.medico;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ades_incidentes_medicos")
@Getter
@Setter
public class IncidenteMedico extends AdesBaseEntity {

    @Column(name = "estudiante_id", nullable = false)
    private UUID estudianteId;

    @Column(name = "fecha_incidente", nullable = false)
    private LocalDateTime fechaIncidente = LocalDateTime.now();

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    @Column(name = "tratamiento_aplicado")
    private String tratamientoAplicado;

    @Column(name = "personal_salud_id")
    private UUID personalSaludId;

    @Column(name = "requirio_traslado")
    private Boolean requirioTraslado = false;

    @Column(name = "notificado_tutor")
    private Boolean notificadoTutor = false;

    @Column(name = "fecha_notificacion_tutor")
    private java.time.OffsetDateTime fechaNotificacionTutor;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
