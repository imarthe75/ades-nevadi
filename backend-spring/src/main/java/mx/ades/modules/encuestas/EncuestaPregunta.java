package mx.ades.modules.encuestas;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;
import mx.ades.common.StringListConverter;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ades_encuesta_preguntas")
@Getter
@Setter
public class EncuestaPregunta extends AdesBaseEntity {

    @Column(name = "encuesta_id", nullable = false)
    private UUID encuestaId;

    @Column(name = "texto", nullable = false)
    private String texto;

    @Column(name = "tipo_pregunta")
    private String tipoPregunta = "ESCALA_5";

    @Convert(converter = StringListConverter.class)
    @Column(name = "opciones", columnDefinition = "jsonb")
    private List<String> opciones;

    @Column(name = "orden")
    private Integer orden = 1;

    @Column(name = "obligatoria")
    private Boolean obligatoria = true;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
