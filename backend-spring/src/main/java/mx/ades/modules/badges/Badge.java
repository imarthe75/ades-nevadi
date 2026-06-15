package mx.ades.modules.badges;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ades_badges")
@Getter
@Setter
public class Badge {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id = UuidCreator.getTimeOrderedWithRandom();

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "icono")
    private String icono = "pi-star";

    @Column(name = "color")
    private String color = "#D02030";

    @Column(name = "tipo")
    private String tipo;

    @Column(name = "criterio_tipo")
    private String criterioTipo = "MANUAL";

    @Column(name = "criterio_metrica")
    private String criterioMetrica;

    @Column(name = "criterio_valor")
    private java.math.BigDecimal criterioValor;

    @Column(name = "plantel_id")
    private UUID plantelId;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;
}
