package mx.ades.modules.evaluaciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ades_evaluaciones")
@Getter
@Setter
public class Evaluacion extends AdesBaseEntity {

    @Column(name = "nombre_evaluacion", nullable = false)
    private String nombreEvaluacion;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "grupo_id", nullable = false)
    private UUID grupoId;

    @Column(name = "materia_id", nullable = false)
    private UUID materiaId;

    @Column(name = "periodo_evaluacion_id", nullable = false)
    private UUID periodoEvaluacionId;

    @Column(name = "fecha_evaluacion", nullable = false)
    private LocalDate fechaEvaluacion;

    @Column(name = "tipo_evaluacion", nullable = false)
    private String tipoEvaluacion = "ORDINARIO";

    @Column(name = "puntaje_maximo")
    private BigDecimal puntajeMaximo = BigDecimal.TEN;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
