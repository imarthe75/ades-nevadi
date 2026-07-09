package mx.ades.modules.planeacion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO para crear/actualizar planeación semanal integrada con aprendizajes.
 * FASE 2: Planeaciones Semanales
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaneacionSemanalDTO {

    @JsonProperty("grupo_id")
    private UUID grupoId;

    @JsonProperty("materia_id")
    private UUID materiaId;

    @JsonProperty("trimestre")
    private Integer trimestre;

    @JsonProperty("semana")
    private Integer semana;

    @JsonProperty("modalidad")
    private String modalidad;  // PRESENCIAL, VIRTUAL, HIBRIDA

    @JsonProperty("fecha_inicio")
    private LocalDate fechaInicio;

    @JsonProperty("fecha_fin")
    private LocalDate fechaFin;

    @JsonProperty("temas_seleccionados")
    private List<TemaSeleccionadoDTO> temasSeleccionados;

    /**
     * Modelo anidado: tema con aprendizajes que evalúa
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TemaSeleccionadoDTO {
        @JsonProperty("tema_id")
        private UUID temaId;

        @JsonProperty("nombre_tema")
        private String nombreTema;

        @JsonProperty("aprendizajes_ids")
        private List<UUID> aprendizajesIds;

        @JsonProperty("descripcion_actividades")
        private String descripcionActividades;

        @JsonProperty("recursos_didacticos")
        private String recursoDidacticos;
    }
}
