package mx.ades.modules.catalogos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import mx.ades.common.AdesBaseEntity;

import java.math.BigDecimal;

@Entity
@Table(name = "ades_niveles_educativos")
@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class NivelEducativo extends AdesBaseEntity {

    @Column(name = "nombre_nivel", nullable = false, unique = true)
    private String nombreNivel;

    @Column(name = "autoridad_educativa", nullable = false)
    private String autoridadEducativa;

    @Column(name = "tipo_ciclo", nullable = false)
    private String tipoCiclo = "ANUAL";

    @Column(name = "num_periodos_eval", nullable = false)
    private Integer numPeriodosEval = 3;

    @Column(name = "tiene_extraordinario", nullable = false)
    private Boolean tieneExtraordinario = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // ── Reglas de calificación (mig 007) ─────────────────────────────────────
    @Column(name = "escala_maxima", nullable = false, precision = 5, scale = 1)
    private BigDecimal escalaMaxima = BigDecimal.TEN;

    @Column(name = "minimo_aprobatorio", nullable = false, precision = 5, scale = 1)
    private BigDecimal minimoAprobatorio = new BigDecimal("6.0");

    // ── Reglas de promoción (mig 056) ─────────────────────────────────────────
    @Column(name = "max_materias_reprobadas", nullable = false)
    private Short maxMateriasReprobadas = 3;

    @Column(name = "min_asistencia_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal minAsistenciaPct = new BigDecimal("80.00");

    @Column(name = "permite_recursamiento", nullable = false)
    private Boolean permiteRecursamiento = true;

    @Column(name = "max_anios_reprobados", nullable = false)
    private Short maxAniosReprobados = 2;

    @Column(name = "tiene_examen_extra", nullable = false)
    private Boolean tieneExamenExtra = false;
}
