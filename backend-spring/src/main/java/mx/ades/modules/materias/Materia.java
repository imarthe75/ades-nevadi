package mx.ades.modules.materias;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ades_materias")
@Getter
@Setter
public class Materia extends AdesBaseEntity {

    @Column(name = "nombre_materia", nullable = false)
    private String nombreMateria;

    @Column(name = "clave_materia")
    private String claveMateria;

    @Column(name = "nivel_educativo_id", nullable = false)
    private UUID nivelEducativoId;

    /** ENUM en BD (chk_tipo_materia): OFICIAL_SEP_PRIMARIA, OFICIAL_SEP_SECUNDARIA,
     * OFICIAL_UAEMEX_PREP, NEVADI_FORMATIVA, NEVADI_ENRIQUECIMIENTO, NEVADI_ESPECIALIZADA. */
    @Column(name = "tipo_materia", nullable = false, length = 50)
    private String tipoMateria;

    /** ENUM en BD (ck_materias_campo_formativo), nullable: LENGUAJES,
     * SABERES_PENSAMIENTO_CIENTIFICO, ETICA_NATURALEZA_SOCIEDADES, HUMANO_COMUNITARIO. */
    @Column(name = "campo_formativo", length = 40)
    private String campoFormativo;

    @Column(name = "horas_semana")
    private BigDecimal horasSemana;

    @Column(name = "es_inglés", nullable = false)
    private Boolean esIngles = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
