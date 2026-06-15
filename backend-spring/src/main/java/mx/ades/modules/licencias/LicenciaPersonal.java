package mx.ades.modules.licencias;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ades_licencias_personal")
@Getter
@Setter
public class LicenciaPersonal extends AdesBaseEntity {

    @Column(name = "personal_id", nullable = false)
    private UUID personalId;

    @Column(name = "tipo_licencia", nullable = false)
    private String tipoLicencia;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "dias_habiles", nullable = false)
    private Integer diasHabiles = 1;

    @Column(name = "estado")
    private String estado = "PENDIENTE";

    @Column(name = "motivo")
    private String motivo;

    @Column(name = "observaciones_rh")
    private String observacionesRh;

    @Column(name = "sustituto_id")
    private UUID sustitutoId;

    @Column(name = "aprobado_por")
    private UUID aprobadoPor;

    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;

    @Column(name = "con_goce_sueldo")
    private Boolean conGoceSueldo = true;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
