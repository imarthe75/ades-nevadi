package mx.ades.modules.movilidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ades_bajas")
@Getter
@Setter
public class Baja extends AdesBaseEntity {

    @Column(name = "estudiante_id", nullable = false)
    private UUID estudianteId;

    @Column(name = "inscripcion_id", nullable = false)
    private UUID inscripcionId;

    @Column(name = "tipo_baja", nullable = false)
    private String tipoBaja;

    @Column(name = "motivo", nullable = false)
    private String motivo;

    @Column(name = "fecha_efectiva", nullable = false)
    private LocalDate fechaEfectiva = LocalDate.now();

    @Column(name = "fecha_reingreso")
    private LocalDate fechaReingreso;

    @Column(name = "plantel_destino")
    private String plantelDestino;

    @Column(name = "clave_ct_destino")
    private String claveCtDestino;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "autorizado_por_id")
    private UUID autorizadoPorId;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
