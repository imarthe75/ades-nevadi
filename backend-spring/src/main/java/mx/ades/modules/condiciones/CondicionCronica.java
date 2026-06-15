package mx.ades.modules.condiciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.util.UUID;

@Entity
@Table(name = "ades_condiciones_cronicas")
@Getter
@Setter
public class CondicionCronica extends AdesBaseEntity {

    @Column(name = "alumno_id", nullable = false)
    private UUID alumnoId;

    @Column(name = "tipo_condicion", nullable = false)
    private String tipoCondicion;

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    @Column(name = "medicacion_nombre")
    private String medicacionNombre;

    @Column(name = "dosis")
    private String dosis;

    @Column(name = "frecuencia")
    private String frecuencia;

    @Column(name = "alergias")
    private String alergias;

    @Column(name = "medico_responsable")
    private String medicoResponsable;

    @Column(name = "telefono_medico")
    private String telefonoMedico;

    @Column(name = "activa")
    private Boolean activa = true;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
