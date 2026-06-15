package mx.ades.modules.aulas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.util.UUID;

@Entity
@Table(name = "ades_aulas")
@Getter
@Setter
public class Aula extends AdesBaseEntity {

    @Column(name = "nombre_aula", nullable = false)
    private String nombreAula;

    @Column(name = "plantel_id", nullable = false)
    private UUID plantelId;

    @Column(name = "tipo_aula", nullable = false)
    private String tipoAula = "AULA";

    @Column(name = "capacidad_alumnos")
    private Integer capacidadAlumnos;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
