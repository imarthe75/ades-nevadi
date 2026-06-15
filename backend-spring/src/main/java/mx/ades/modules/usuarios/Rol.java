package mx.ades.modules.usuarios;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

@Entity
@Table(name = "ades_roles")
@Getter
@Setter
public class Rol extends AdesBaseEntity {

    @Column(name = "nombre_rol", nullable = false, unique = true)
    private String nombreRol;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "nivel_acceso", nullable = false)
    private Integer nivelAcceso;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
