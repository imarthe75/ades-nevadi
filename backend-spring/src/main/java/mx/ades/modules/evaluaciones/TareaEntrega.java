package mx.ades.modules.evaluaciones;

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
@Table(name = "ades_tareas_entregas")
@Getter
@Setter
public class TareaEntrega extends AdesBaseEntity {

    @Column(name = "tarea_id", nullable = false)
    private UUID tareaId;

    @Column(name = "estudiante_id", nullable = false)
    private UUID estudianteId;

    @Column(name = "fecha_entrega")
    private OffsetDateTime fechaEntrega;

    @Column(name = "es_tarde", nullable = false)
    private Boolean esTarde = false;

    @Column(name = "comentario_alumno")
    private String comentarioAlumno;

    @Column(name = "estatus_entrega", nullable = false)
    private String estatusEntrega = "PENDIENTE";

    @Column(name = "archivo_url")
    private String archivoUrl;

    @Column(name = "calificacion_obtenida")
    private BigDecimal calificacionObtenida;

    @Column(name = "comentario_profesor")
    private String comentarioProfesor;

    @Column(name = "calificado_por")
    private UUID calificadoPor;

    @Column(name = "fecha_calificacion_docente")
    private OffsetDateTime fechaCalificacionDocente;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
