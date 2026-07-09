package mx.ades.modules.planeacion.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.UUID;

/**
 * Aprendizaje Esperado — Resultado de aprendizaje alineado a competencias SEP/UAEMEX.
 *
 * Representa un aprendizaje específico que los estudiantes deben lograr en una
 * grado/materia determinada. Alineado a:
 * - NEM (Educación Integral) para primaria/secundaria
 * - CBU (Colegio de Bachilleres UAEMEX) para preparatoria
 *
 * Forma parte de la cadena: Planeación → Aprendizajes → Tareas/Exámenes → Calificaciones → Boleta
 */
@Entity
@Table(name = "ades_aprendizajes_esperados", indexes = {
    @Index(name = "idx_aprendizajes_grado", columnList = "grado_id"),
    @Index(name = "idx_aprendizajes_materia", columnList = "materia_id"),
    @Index(name = "idx_aprendizajes_competencia", columnList = "competencia_id"),
    @Index(name = "idx_aprendizajes_grado_materia", columnList = "grado_id,materia_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AprendizajeEsperado {

    @Id
    private UUID ref;

    @Column(unique = true, nullable = false)
    private String codigo;

    @Column(nullable = false)
    private UUID gradoId;

    @Column(nullable = false)
    private UUID materiaId;

    @Column
    private UUID competenciaId;

    @Column(nullable = false, length = 2000)
    private String descripcion;

    @Column
    private Integer orden;

    @Column
    private Boolean activo = true;

    @Column(name = "row_version")
    private Integer rowVersion = 1;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant fechaCreacion;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant fechaModificacion;

    @CreatedBy
    @Column(nullable = false, updatable = false, length = 255)
    private String usuarioCreacion;

    @LastModifiedBy
    @Column(nullable = false, length = 255)
    private String usuarioModificacion;
}
