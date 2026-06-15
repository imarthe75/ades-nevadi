package mx.ades.modules.catalogos;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;
import mx.ades.modules.planteles.Plantel;

@Entity
@Table(name = "ades_grados")
@Getter
@Setter
public class Grado extends AdesBaseEntity {

    @Column(name = "numero_grado", nullable = false)
    private Integer numeroGrado;

    @Column(name = "nombre_grado", nullable = false)
    private String nombreGrado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nivel_educativo_id", nullable = false)
    private NivelEducativo nivelEducativo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plantel_id", nullable = false)
    private Plantel plantel;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
