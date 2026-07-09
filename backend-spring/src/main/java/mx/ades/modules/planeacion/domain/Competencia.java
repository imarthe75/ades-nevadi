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
 * Competencia — Estándares de aprendizaje SEP y UAEMEX.
 *
 * Agrupa aprendizajes esperados bajo una competencia clave que orienta la planeación.
 * Alineadas con:
 * - Campos Formativos de NEM (primaria/secundaria)
 * - Áreas de Conocimiento CBU (preparatoria)
 */
@Entity
@Table(name = "ades_competencias", indexes = {
    @Index(name = "idx_competencias_nivel", columnList = "nivel_educativo_id"),
    @Index(name = "idx_competencias_campo", columnList = "campo_formativo"),
    @Index(name = "idx_competencias_area", columnList = "area_conocimiento")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Competencia {

    @Id
    private UUID ref;

    @Column(unique = true, nullable = false, length = 30)
    private String codigo;

    @Column(nullable = false, length = 255)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false)
    private UUID nivelEducativoId;

    @Column(length = 100)
    private String campoFormativo;

    @Column(length = 100)
    private String areaConocimiento;

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
