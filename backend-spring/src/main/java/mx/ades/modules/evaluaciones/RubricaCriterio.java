package mx.ades.modules.evaluaciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ades_rubrica_criterios")
@Getter
@Setter
public class RubricaCriterio extends AdesBaseEntity {

    @Column(name = "rubrica_id", nullable = false)
    private UUID rubricaId;

    @Column(name = "nombre_criterio", nullable = false)
    private String nombreCriterio;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "ponderacion", nullable = false)
    private BigDecimal ponderacion = BigDecimal.ZERO;

    @Column(name = "orden", nullable = false)
    private Integer orden = 1;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "niveles_logro", columnDefinition = "jsonb")
    private String nivelesLogro;
}
