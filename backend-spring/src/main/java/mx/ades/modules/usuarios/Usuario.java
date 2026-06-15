package mx.ades.modules.usuarios;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ades_usuarios")
@Getter
@Setter
public class Usuario extends AdesBaseEntity {

    @Column(name = "nombre_usuario", nullable = false, unique = true)
    private String nombreUsuario;

    @Column(name = "email_institucional", unique = true)
    private String emailInstitucional;

    @Column(name = "clave_hash")
    private String claveHash;

    @Column(name = "oidc_sub", unique = true)
    private String oidcSub;

    @Column(name = "persona_id", nullable = false)
    private UUID personaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    @Column(name = "plantel_id")
    private UUID plantelId;

    @Column(name = "nivel_educativo_id")
    private UUID nivelEducativoId;

    @Column(name = "estatus_id")
    private UUID estatusId;

    @Column(name = "ultimo_acceso")
    private OffsetDateTime ultimoAcceso;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
