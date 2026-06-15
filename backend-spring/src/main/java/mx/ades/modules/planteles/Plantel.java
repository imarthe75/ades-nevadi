package mx.ades.modules.planteles;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import mx.ades.common.AdesBaseEntity;

import java.util.UUID;

@Entity
@Table(name = "ades_planteles")
@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Plantel extends AdesBaseEntity {

    @Column(name = "nombre_plantel", nullable = false)
    private String nombrePlantel;

    @Column(name = "escuela_id", nullable = false)
    private UUID escuelaId;

    @Column(name = "clave_ct")
    private String claveCt;

    @Column(name = "estatus_id")
    private UUID estatusId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
