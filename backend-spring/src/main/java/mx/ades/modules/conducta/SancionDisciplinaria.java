package mx.ades.modules.conducta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesAuditEntity;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ades_sanciones_disciplinarias")
@Getter
@Setter
public class SancionDisciplinaria extends AdesAuditEntity {

    @Column(name = "reporte_conducta_id", nullable = false)
    private UUID reporteConductaId;

    @Column(name = "estudiante_id", nullable = false)
    private UUID estudianteId;

    @Column(name = "tipo_sancion", nullable = false)
    private String tipoSancion;

    @Column(name = "justificacion", nullable = false)
    private String justificacion;

    @Column(name = "autorizado_por_id", nullable = false)
    private UUID autorizadoPorId;

    @Column(name = "fecha_sancion")
    private LocalDate fechaSancion = LocalDate.now();

    @Column(name = "fecha_fin_sancion")
    private LocalDate fechaFinSancion;

    // "VIGENTE" (valor anterior) no existe en el CHECK real de BD
    // (ades_sanciones_disciplinarias_estado_check: APLICADA/EN_PROCESO/CUMPLIDA/
    // APELADA/REVOCADA) — cada creación de sanción violaba el constraint desde
    // siempre (JPA envía este default en el INSERT). Hallazgo 2026-07-15.
    @Column(name = "estado")
    private String estado = "APLICADA";

    @Column(name = "notificado_padres")
    private Boolean notificadoPadres = false;

    @Column(name = "fecha_notificacion")
    private LocalDate fechaNotificacion;

    @Column(name = "medio_notificacion")
    private String medioNotificacion;

    @Column(name = "notas_adicionales")
    private String notasAdicionales;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
