package mx.ades.modules.conducta;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesAuditEntity;
import mx.ades.common.JsonMapListConverter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ades_seguimiento_plan")
@Getter
@Setter
public class SeguimientoPlan extends AdesAuditEntity {

    @Column(name = "plan_mejora_id", nullable = false)
    private UUID planMejoraId;

    @Column(name = "estudiante_id", nullable = false)
    private UUID estudianteId;

    @Column(name = "registrado_por_id", nullable = false)
    private UUID registradoPorId;

    @Column(name = "fecha_seguimiento")
    private LocalDate fechaSeguimiento = LocalDate.now();

    @Column(name = "avance")
    private String avance = "PARCIAL";

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    @Convert(converter = JsonMapListConverter.class)
    @Column(name = "compromisos_cumplidos", columnDefinition = "jsonb")
    private List<Map<String, Object>> compromisosCumplidos;

    @Column(name = "acciones_adicionales")
    private String accionesAdicionales;

    @Column(name = "nuevo_estado_plan")
    private String nuevoEstadoPlan;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
