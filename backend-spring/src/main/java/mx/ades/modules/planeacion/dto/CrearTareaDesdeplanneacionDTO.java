package mx.ades.modules.planeacion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO para crear tarea vinculada a una planeación semanal.
 * FASE 3: Tareas/Exámenes Vinculados
 *
 * Flujo:
 * 1. Profesor selecciona una planeación (GET /planeaciones/{grupo_id})
 * 2. Sistema carga aprendizajes vinculados a esa planeación (heredados automáticamente)
 * 3. Profesor crea tarea (POST /tareas/desde-planeacion)
 * 4. Tarea hereda: planeacion_clase_id, grupo_id, materia_id, aprendizajes_esperados[]
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrearTareaDesdeplanneacionDTO {

    @JsonProperty("planeacion_clase_id")
    private UUID planeacionClaseId;

    @JsonProperty("titulo")
    private String titulo;

    @JsonProperty("descripcion")
    private String descripcion;

    @JsonProperty("fecha_entrega")
    private LocalDate fechaEntrega;

    @JsonProperty("puntaje_maximo")
    private Double puntajeMaximo;

    @JsonProperty("permite_entrega_tarde")
    private Boolean permiteEntregaTarde;

    @JsonProperty("instrucciones_url")
    private String instruccionesUrl;

    /**
     * Opcional: Si no se proporciona, se heredan de la planeación.
     */
    @JsonProperty("aprendizajes_esperados_ids")
    private java.util.List<UUID> aprendizajesEsperadosIds;
}
