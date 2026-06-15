package mx.ades.modules.planeacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ades_planeacion_clases")
@Getter
@Setter
public class Planeacion extends AdesBaseEntity {

    @Column(name = "grupo_id", nullable = false)
    private UUID grupoId;

    @Column(name = "tema_id", nullable = false)
    private UUID temaId;

    @Column(name = "fecha_planeada", nullable = false)
    private LocalDate fechaPlaneada;

    @Column(name = "descripcion_actividades")
    private String descripcionActividades;

    @Column(name = "recursos_didacticos")
    private String recursosDidacticos;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
