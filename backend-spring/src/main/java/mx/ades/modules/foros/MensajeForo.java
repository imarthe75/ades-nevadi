package mx.ades.modules.foros;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.util.UUID;

@Entity
@Table(name = "ades_mensajes_foro")
@Getter
@Setter
public class MensajeForo extends AdesBaseEntity {

    @Column(name = "foro_id", nullable = false)
    private UUID foroId;

    @Column(name = "asunto", nullable = false)
    private String asunto;

    @Column(name = "contenido", nullable = false)
    private String contenido;

    @Column(name = "adjunto_url")
    private String adjuntoUrl;

    @Column(name = "estado")
    private String estado = "PUBLICADO";

    @Column(name = "autor_id")
    private UUID autorId;

    @Column(name = "mensaje_padre_id")
    private UUID mensajePadreId;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
