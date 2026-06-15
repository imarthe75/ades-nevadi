package mx.ades.modules.sistema;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ades_catalogos")
@Getter
@Setter
public class Catalogo extends AdesBaseEntity {

    @Column(name = "codigo", nullable = false, unique = true)
    private String codigo;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @OneToMany(mappedBy = "catalogo", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CatalogoItem> items = new ArrayList<>();
}
