package mx.ades.modules.grupos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.util.UUID;

@Entity
@Table(name = "ades_grupos")
@Getter
@Setter
public class Grupo extends AdesBaseEntity {

    @Column(name = "nombre_grupo", nullable = false)
    private String nombreGrupo;

    @Column(name = "grado_id", nullable = false)
    private UUID gradoId;

    @Column(name = "ciclo_escolar_id", nullable = false)
    private UUID cicloEscolarId;

    @Column(name = "profesor_titular_id")
    private UUID profesorTitularId;

    @Column(name = "capacidad_maxima")
    private Integer capacidadMaxima = 35;

    @Column(name = "turno")
    private String turno = "MATUTINO";

    @Column(name = "estatus_id")
    private UUID estatusId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
