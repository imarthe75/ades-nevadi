package mx.ades.modules.movilidad;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ades_cambios_grupo")
@Getter
@Setter
public class CambioGrupo {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id = UuidCreator.getTimeOrderedWithRandom();

    @Column(name = "estudiante_id", nullable = false)
    private UUID estudianteId;

    @Column(name = "inscripcion_id", nullable = false)
    private UUID inscripcionId;

    @Column(name = "grupo_origen_id", nullable = false)
    private UUID grupoOrigenId;

    @Column(name = "grupo_destino_id", nullable = false)
    private UUID grupoDestinoId;

    @Column(name = "fecha_cambio", nullable = false)
    private LocalDate fechaCambio = LocalDate.now();

    @Column(name = "motivo", nullable = false)
    private String motivo;

    @Column(name = "autorizado_por_id")
    private UUID autorizadoPorId;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "ref", columnDefinition = "uuid", insertable = false, updatable = false)
    private UUID ref;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "usuario_creacion", insertable = false, updatable = false)
    private String usuarioCreacion;
}
