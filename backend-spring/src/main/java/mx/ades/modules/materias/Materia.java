package mx.ades.modules.materias;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import mx.ades.common.AdesBaseEntity;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ades_materias")
@Getter
@Setter
public class Materia extends AdesBaseEntity {

    @Column(name = "nombre_materia", nullable = false)
    private String nombreMateria;

    @Column(name = "clave_materia")
    private String claveMateria;

    @Column(name = "nivel_educativo_id", nullable = false)
    private UUID nivelEducativoId;

    @Column(name = "horas_semana")
    private BigDecimal horasSemana;

    @Column(name = "es_inglés", nullable = false)
    private Boolean esIngles = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
