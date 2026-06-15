package mx.ades.modules.foros;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.util.UUID;

@Entity
@Table(name = "ades_respuestas_foro")
@Getter
@Setter
public class RespuestaForo extends AdesBaseEntity {

    @Column(name = "mensaje_id", nullable = false)
    private UUID mensajeId;

    @Column(name = "contenido", nullable = false)
    private String contenido;

    @Column(name = "adjunto_url")
    private String adjuntoUrl;

    @Column(name = "autor_id")
    private UUID autorId;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
