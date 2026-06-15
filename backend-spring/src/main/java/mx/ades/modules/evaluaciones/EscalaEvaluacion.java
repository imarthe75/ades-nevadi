package mx.ades.modules.evaluaciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

@Entity
@Table(name = "ades_escalas_evaluacion")
@Getter
@Setter
public class EscalaEvaluacion extends AdesBaseEntity {

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "nivel_educativo", nullable = false)
    private String nivelEducativo;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "valores_json", columnDefinition = "jsonb")
    private String valoresJson;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
