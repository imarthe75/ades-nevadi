package mx.ades.modules.comunicados;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ades_acuses_comunicado")
@Getter
@Setter
public class AcuseComunicado extends AdesBaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comunicado_id", nullable = false)
    private Comunicado comunicado;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "fecha_acuse")
    private LocalDateTime fechaAcuse = LocalDateTime.now();

    @Column(name = "ip_origen", columnDefinition = "inet")
    private String ipOrigen;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
