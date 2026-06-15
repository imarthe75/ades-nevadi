package mx.ades.modules.sistema;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

@Entity
@Table(name = "ades_catalogo_items")
@Getter
@Setter
public class CatalogoItem extends AdesBaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalogo_id", nullable = false)
    @JsonIgnore
    private Catalogo catalogo;

    @Column(name = "valor", nullable = false)
    private String valor;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "orden", nullable = false)
    private Integer orden = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
