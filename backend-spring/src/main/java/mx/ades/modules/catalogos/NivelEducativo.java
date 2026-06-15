package mx.ades.modules.catalogos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import mx.ades.common.AdesBaseEntity;

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
}
