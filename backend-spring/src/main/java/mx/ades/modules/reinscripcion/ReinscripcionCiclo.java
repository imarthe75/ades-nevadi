package mx.ades.modules.reinscripcion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ades_reinscripcion_ciclo")
@Getter
@Setter
public class ReinscripcionCiclo extends AdesBaseEntity {

    @Column(name = "ciclo_origen_id", nullable = false)
    private UUID cicloOrigenId;

    @Column(name = "ciclo_destino_id", nullable = false)
    private UUID cicloDestinoId;

    @Column(name = "estudiante_id", nullable = false)
    private UUID estudianteId;

    @Column(name = "estado", nullable = false)
    private String estado = "PENDIENTE";

    @Column(name = "tiene_adeudos", nullable = false)
    private Boolean tieneAdeudos = false;

    @Column(name = "monto_adeudado", nullable = false)
    private BigDecimal montoAdeudado = BigDecimal.ZERO;

    @Column(name = "bloqueantes", columnDefinition = "jsonb")
    private String bloqueantes;

    @Column(name = "razon_rechazo")
    private String razonRechazo;

    @Column(name = "aprobado_por")
    private UUID aprobadoPor;

    @Column(name = "fecha_validacion")
    private OffsetDateTime fechaValidacion;

    @Column(name = "fecha_aprobacion")
    private OffsetDateTime fechaAprobacion;

    @Column(name = "promovido")
    private Boolean promovido;

    @Column(name = "grupo_destino_id")
    private UUID grupoDestinoId;
}
