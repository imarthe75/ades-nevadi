package mx.ades.modules.horarios.suplencias;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ades_suplencias")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Suplencia {

    @Id
    private UUID id = UUID.randomUUID();

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

    // Auditoría
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "creado_por", nullable = false, updatable = false)
    private String creadoPor;

    @Column(name = "creado_el", nullable = false, updatable = false)
    private LocalDateTime creadoEl = LocalDateTime.now();

    @Column(name = "actualizado_por")
    private String actualizadoPor;

    @Column(name = "actualizado_el")
    private LocalDateTime actualizadoEl;
}
