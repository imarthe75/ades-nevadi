package mx.ades.modules.evaluaciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.util.UUID;

@Entity
@Table(name = "ades_rubricas")
@Getter
@Setter
public class Rubrica extends AdesBaseEntity {

    @Column(name = "nombre_rubrica", nullable = false)
    private String nombreRubrica;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "materia_id")
    private UUID materiaId;

    @Column(name = "nivel_educativo_id")
    private UUID nivelEducativoId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
