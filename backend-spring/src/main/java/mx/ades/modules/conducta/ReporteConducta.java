package mx.ades.modules.conducta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ades_reportes_conducta")
@Getter
@Setter
public class ReporteConducta extends AdesBaseEntity {

    @Column(name = "estudiante_id", nullable = false)
    private UUID estudianteId;

    @Column(name = "grupo_id", nullable = false)
    private UUID grupoId;

    @Column(name = "reportado_por_id", nullable = false)
    private UUID reportadoPorId;

    @Column(name = "fecha_reporte", nullable = false)
    private LocalDate fechaReporte = LocalDate.now();

    @Column(name = "tipo_falta", nullable = false)
    private String tipoFalta;

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    @Column(name = "medida_aplicada")
    private String medidaAplicada;

    @Column(name = "requiere_seguimiento")
    private Boolean requiereSeguimiento = false;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
