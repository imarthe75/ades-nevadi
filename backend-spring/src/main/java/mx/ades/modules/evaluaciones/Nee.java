package mx.ades.modules.evaluaciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ades_nee")
@Getter
@Setter
public class Nee extends AdesBaseEntity {

    @Column(name = "alumno_id", nullable = false)
    private UUID alumnoId;

    @Column(name = "tipo_nee", nullable = false)
    private String tipoNee;

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    @Column(name = "apoyos_requeridos")
    private String apoyosRequeridos;

    @Column(name = "fecha_deteccion")
    private LocalDate fechaDeteccion;

    @Column(name = "profesional_detecta")
    private String profesionalDetecta;

    @Column(name = "activa", nullable = false)
    private Boolean activa = true;
}
