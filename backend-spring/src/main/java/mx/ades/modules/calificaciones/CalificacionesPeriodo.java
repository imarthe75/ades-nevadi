package mx.ades.modules.calificaciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ades_calificaciones_periodo")
@Getter
@Setter
public class CalificacionesPeriodo extends AdesBaseEntity {

    @Column(name = "estudiante_id", nullable = false)
    private UUID estudianteId;

    @Column(name = "grupo_id", nullable = false)
    private UUID grupoId;

    @Column(name = "materia_id", nullable = false)
    private UUID materiaId;

    @Column(name = "periodo_evaluacion_id", nullable = false)
    private UUID periodoEvaluacionId;

    @Column(name = "calificacion_final", nullable = false)
    private BigDecimal calificacionFinal;

    @Column(name = "es_acreditado", insertable = false, updatable = false)
    private Boolean esAcreditado;

    @Column(name = "observaciones")
    private String observaciones;
}
