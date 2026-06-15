package mx.ades.modules.conducta;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesAuditEntity;
import mx.ades.common.JsonMapListConverter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ades_planes_mejora")
@Getter
@Setter
public class PlanMejora extends AdesAuditEntity {

    @Column(name = "reporte_conducta_id", nullable = false)
    private UUID reporteConductaId;

    @Column(name = "estudiante_id", nullable = false)
    private UUID estudianteId;

    @Column(name = "ciclo_escolar_id")
    private UUID cicloEscolarId;

    @Column(name = "elaborado_por_id", nullable = false)
    private UUID elaboradoPorId;

    @Column(name = "fecha_elaboracion")
    private LocalDate fechaElaboracion = LocalDate.now();

    @Column(name = "objetivo_general", nullable = false)
    private String objetivoGeneral;

    @Convert(converter = JsonMapListConverter.class)
    @Column(name = "compromisos_alumno", columnDefinition = "jsonb")
    private List<Map<String, Object>> compromisosAlumno;

    @Convert(converter = JsonMapListConverter.class)
    @Column(name = "compromisos_padre", columnDefinition = "jsonb")
    private List<Map<String, Object>> compromisosPadre;

    @Convert(converter = JsonMapListConverter.class)
    @Column(name = "compromisos_escuela", columnDefinition = "jsonb")
    private List<Map<String, Object>> compromisosEscuela;

    @Column(name = "fecha_primer_seguimiento")
    private LocalDate fechaPrimerSeguimiento;

    @Column(name = "firmado_alumno")
    private Boolean firmadoAlumno = false;

    @Column(name = "firmado_padre")
    private Boolean firmadoPadre = false;

    @Column(name = "firmado_director")
    private Boolean firmadoDirector = false;

    @Column(name = "fecha_firma_alumno")
    private LocalDate fechaFirmaAlumno;

    @Column(name = "fecha_firma_padre")
    private LocalDate fechaFirmaPadre;

    @Column(name = "estado")
    private String estado = "BORRADOR";

    @Column(name = "observaciones_cierre")
    private String observacionesCierre;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
