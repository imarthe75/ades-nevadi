package mx.ades.modules.horarios.suplencias;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import mx.ades.common.AdesBaseEntity;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ades_suplencias")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Suplencia extends AdesBaseEntity {

    @Column(name = "profesor_ausente_id", nullable = false)
    private UUID profesorAusenteId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "timeslot_id")
    private UUID timeslotId;

    @Column(name = "horario_id")
    private UUID horarioId;

    private String motivo;

    @Column(name = "profesor_cobertura_id")
    private UUID profesorCoberturaId;

    private String estado = "PENDIENTE";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
