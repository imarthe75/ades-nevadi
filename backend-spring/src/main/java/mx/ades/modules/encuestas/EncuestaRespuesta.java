package mx.ades.modules.encuestas;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ades_encuesta_respuestas")
@Getter
@Setter
public class EncuestaRespuesta {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id = UuidCreator.getTimeOrderedWithRandom();

    @Column(name = "encuesta_id", nullable = false)
    private UUID encuestaId;

    @Column(name = "pregunta_id", nullable = false)
    private UUID preguntaId;

    @Column(name = "respondido_por_id")
    private UUID respondidoPorId;

    @Column(name = "sesion_id", nullable = false)
    private String sesionId;

    @Column(name = "texto_respuesta")
    private String textoRespuesta;

    @Column(name = "valor_numerico")
    private java.math.BigDecimal valorNumerico;

    @Column(name = "opcion_seleccionada")
    private String opcionSeleccionada;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;
}
