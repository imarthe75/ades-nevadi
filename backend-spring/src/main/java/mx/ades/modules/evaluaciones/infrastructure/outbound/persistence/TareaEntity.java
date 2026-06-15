package mx.ades.modules.evaluaciones.infrastructure.outbound.persistence;

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
@Table(name = "ades_tareas")
@Getter
@Setter
public class TareaEntity extends AdesBaseEntity {

    @Column(name = "titulo", nullable = false)
    private String titulo;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "grupo_id", nullable = false)
    private UUID grupoId;

    @Column(name = "materia_id", nullable = false)
    private UUID materiaId;

    @Column(name = "tema_id")
    private UUID temaId;

    @Column(name = "periodo_evaluacion_id")
    private UUID periodoEvaluacionId;

    @Column(name = "fecha_asignacion", nullable = false)
    private LocalDate fechaAsignacion;

    @Column(name = "fecha_entrega", nullable = false)
    private LocalDate fechaEntrega;

    @Column(name = "puntaje_maximo")
    private BigDecimal puntajeMaximo = BigDecimal.TEN;

    @Column(name = "tipo_item", nullable = false)
    private String tipoItem = "tarea";

    @Column(name = "plan_trabajo_id")
    private UUID planTrabajoId;

    @Column(name = "rubrica_id")
    private UUID rubricaId;

    @Column(name = "fecha_examen")
    private LocalDate fechaExamen;

    @Column(name = "instrucciones_url")
    private String instruccionesUrl;

    @Column(name = "permite_entrega_tarde", nullable = false)
    private Boolean permiteEntregaTarde = false;

    @Column(name = "origen", nullable = false)
    private String origen = "MANUAL";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
