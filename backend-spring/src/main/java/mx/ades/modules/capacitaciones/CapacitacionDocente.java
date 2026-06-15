package mx.ades.modules.capacitaciones;

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
@Table(name = "ades_capacitaciones_docente")
@Getter
@Setter
public class CapacitacionDocente extends AdesBaseEntity {

    @Column(name = "docente_id", nullable = false)
    private UUID docenteId;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "institucion", nullable = false)
    private String institucion;

    @Column(name = "tipo_certificacion")
    private String tipoCertificacion = "CURSO";

    @Column(name = "modalidad")
    private String modalidad = "PRESENCIAL";

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "duracion_hrs", nullable = false)
    private java.math.BigDecimal duracionHrs;

    @Column(name = "area_formacion")
    private String areaFormacion;

    @Column(name = "folio_certificado")
    private String folioCertificado;

    @Column(name = "certificado_url")
    private String certificadoUrl;

    @Column(name = "validado_rh")
    private Boolean validadoRh = false;

    @Column(name = "fecha_validacion")
    private LocalDateTime fechaValidacion;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
