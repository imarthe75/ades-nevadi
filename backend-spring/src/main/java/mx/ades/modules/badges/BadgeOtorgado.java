package mx.ades.modules.badges;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ades_badge_otorgados")
@Getter
@Setter
public class BadgeOtorgado {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id = UuidCreator.getTimeOrderedWithRandom();

    @Column(name = "badge_id", nullable = false)
    private UUID badgeId;

    @Column(name = "estudiante_id", nullable = false)
    private UUID estudianteId;

    @Column(name = "ciclo_id", nullable = false)
    private UUID cicloId;

    @Column(name = "motivo")
    private String motivo;

    @Column(name = "otorgado_por")
    private UUID otorgadoPor;

    @Column(name = "fecha_otorgado")
    private LocalDateTime fechaOtorgado = LocalDateTime.now();

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;
}
